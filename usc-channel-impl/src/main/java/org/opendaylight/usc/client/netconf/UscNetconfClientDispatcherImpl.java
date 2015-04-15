/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.client.netconf;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.util.Timer;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;

import java.io.Closeable;
import java.net.InetSocketAddress;

import org.opendaylight.controller.netconf.client.NetconfClientDispatcher;
import org.opendaylight.controller.netconf.client.NetconfClientDispatcherImpl;
import org.opendaylight.controller.netconf.client.NetconfClientSession;
import org.opendaylight.controller.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.controller.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.controller.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.protocol.framework.ReconnectStrategy;
import org.opendaylight.protocol.framework.SessionNegotiatorFactory;
import org.opendaylight.usc.manager.UscManagerService;
import org.opendaylight.usc.plugin.UscPluginTcp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class extends the NetconfClientDispatcherImpl with USC.
 */
public class UscNetconfClientDispatcherImpl implements NetconfClientDispatcher, Closeable {

    private static final GlobalEventExecutor executor = GlobalEventExecutor.INSTANCE;

    private static final Logger LOG = LoggerFactory.getLogger(UscNetconfClientDispatcherImpl.class);

    private final UscPluginTcp plugin;

    private final LocalEventLoopGroup group = new LocalEventLoopGroup();

    private final Timer timer;

    private final NetconfClientDispatcherImpl fallbackDispatcher;

    protected interface PipelineInitializer<S> {
        /**
         * Initializes channel by specifying the handlers in its pipeline. Handlers are protocol specific, therefore
         * this method needs to be implemented in protocol specific Dispatchers.
         *
         * @param channel
         *            whose pipeline should be defined, also to be passed to {@link SessionNegotiatorFactory}
         * @param promise
         *            to be passed to {@link SessionNegotiatorFactory}
         */
        void initializeChannel(Channel channel, Promise<S> promise);
    }

    /**
     * Constructs a new UscNetconfClientDispatcherImpl
     * 
     * @param bossGroup
     * @param workerGroup
     * @param timer
     * @param manager
     */
    public UscNetconfClientDispatcherImpl(final EventLoopGroup bossGroup, final EventLoopGroup workerGroup,
            final Timer timer) {
        LOG.warn("UscNetconfClientDispatcherImpl constructor");
        this.timer = timer;
        //initializing USC Manager Service for USC Plugin even the USC feature is not installed.
        UscManagerService.getInstance().init();
        plugin = UscManagerService.getInstance().getPluginTcp();
        this.fallbackDispatcher = new NetconfClientDispatcherImpl(bossGroup, workerGroup, timer);
    }

    @Override
    public Future<NetconfClientSession> createClient(final NetconfClientConfiguration clientConfiguration) {
        switch (clientConfiguration.getProtocol()) {
        case TCP:
            if (plugin.isChannelAvailable(clientConfiguration.getAddress())) {
                return createTcpClient(clientConfiguration);
            } else {
                LOG.warn("UscNetconfClientDispatcherImpl createClient using fallback for TCP");
                return fallbackDispatcher.createClient(clientConfiguration);
            }

        case SSH:
            // SSH doesn't support LocalChannel addresses, so use fallback
            LOG.warn("UscNetconfClientDispatcherImpl createClient using fallback for SSH");
            return fallbackDispatcher.createClient(clientConfiguration);

        default:
            throw new IllegalArgumentException("Unknown client protocol " + clientConfiguration.getProtocol());
        }
    }

    @Override
    public Future<Void> createReconnectingClient(final NetconfReconnectingClientConfiguration clientConfiguration) {
        switch (clientConfiguration.getProtocol()) {
        case TCP:
            if (plugin.isChannelAvailable(clientConfiguration.getAddress())) {
                // SSH doesn't support LocalChannel addresses, so only TCP is supported
                return createReconnectingTcpClient(clientConfiguration);
            } else {
                LOG.warn("UscNetconfClientDispatcherImpl createReconnectingClient using fallback for TCP");
                return fallbackDispatcher.createReconnectingClient(clientConfiguration);
            }
            
        case SSH:
            // SSH doesn't support LocalChannel addresses, so use fallback
            LOG.warn("UscNetconfClientDispatcherImpl createReconnectingClient using fallback for SSH");
            return fallbackDispatcher.createReconnectingClient(clientConfiguration);

        default:
            throw new IllegalArgumentException("Unknown client protocol " + clientConfiguration.getProtocol());
        }
    }

    /**
     * Create a client but use a pre-configured bootstrap. This method however replaces the ChannelInitializer in the
     * bootstrap. All other configuration is preserved.
     *
     * @param address
     *            remote address
     */
    protected Future<NetconfClientSession> createClient(final InetSocketAddress address,
            final ReconnectStrategy strategy, final Bootstrap bootstrap,
            final PipelineInitializer<NetconfClientSession> initializer) {
        final ProtocolSessionPromise<NetconfClientSession> p = new ProtocolSessionPromise<>(plugin, executor, address,
                strategy, bootstrap);

        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(final Channel ch) {
                initializer.initializeChannel(ch, p);
            }
        });

        p.connect();
        LOG.debug("Client created.");
        return p;
    }

    private Future<NetconfClientSession> createTcpClient(final NetconfClientConfiguration currentConfiguration) {

        LOG.debug("Creating TCP client with configuration: {}", currentConfiguration);
        LOG.warn("UscNetconfClientDispatcherImpl createTcpClient");

        final Bootstrap b = new Bootstrap();
        b.group(group);
        b.channel(LocalChannel.class);

        return createClient(currentConfiguration.getAddress(), currentConfiguration.getReconnectStrategy(), b,
                new PipelineInitializer<NetconfClientSession>() {
                    @Override
                    public void initializeChannel(final Channel ch, final Promise<NetconfClientSession> promise) {
                        new NetconfTcpClientChannelInitializer(getNegotiatorFactory(currentConfiguration),
                                currentConfiguration.getSessionListener()).initialize(ch, promise);
                    }
                });
    }

    private Future<Void> createReconnectingTcpClient(final NetconfReconnectingClientConfiguration currentConfiguration) {
        LOG.debug("Creating reconnecting TCP client with configuration: {}", currentConfiguration);
        LOG.warn("UscNetconfClientDispatcherImpl createReconnectingTcpClient");

        final Bootstrap b = new Bootstrap();
        b.group(group);
        b.channel(LocalChannel.class);

        final ReconnectPromise p = new ReconnectPromise(executor, this, currentConfiguration.getAddress(),
                currentConfiguration.getConnectStrategyFactory(), b, new PipelineInitializer<NetconfClientSession>() {
                    @Override
                    public void initializeChannel(final Channel ch, final Promise<NetconfClientSession> promise) {
                        new NetconfTcpClientChannelInitializer(getNegotiatorFactory(currentConfiguration),
                                currentConfiguration.getSessionListener()).initialize(ch, promise);
                    }
                });

        p.connect();
        return p;
    }

    private Future<NetconfClientSession> createSshClient(final NetconfClientConfiguration currentConfiguration) {
        LOG.debug("Creating SSH client with configuration: {}", currentConfiguration);
        LOG.warn("UscNetconfClientDispatcherImpl createSshClient");

        final Bootstrap b = new Bootstrap();
        b.group(group);
        b.channel(LocalChannel.class);

        return createClient(currentConfiguration.getAddress(), currentConfiguration.getReconnectStrategy(), b,
                new PipelineInitializer<NetconfClientSession>() {

                    @Override
                    public void initializeChannel(final Channel ch, final Promise<NetconfClientSession> promise) {
                        new NetconfSshClientChannelInitializer(currentConfiguration.getAuthHandler(),
                                getNegotiatorFactory(currentConfiguration), currentConfiguration.getSessionListener())
                                .initialize(ch, promise);
                    }

                });
    }

    private Future<Void> createReconnectingSshClient(final NetconfReconnectingClientConfiguration currentConfiguration) {
        LOG.debug("Creating reconnecting SSH client with configuration: {}", currentConfiguration);
        LOG.warn("UscNetconfClientDispatcherImpl createReconnectingSshClient");

        final Bootstrap b = new Bootstrap();
        b.group(group);
        b.channel(LocalChannel.class);

        final ReconnectPromise p = new ReconnectPromise(GlobalEventExecutor.INSTANCE, this,
                currentConfiguration.getAddress(), currentConfiguration.getConnectStrategyFactory(), b,
                new PipelineInitializer<NetconfClientSession>() {
                    @Override
                    public void initializeChannel(final Channel ch, final Promise<NetconfClientSession> promise) {
                        new NetconfSshClientChannelInitializer(currentConfiguration.getAuthHandler(),
                                getNegotiatorFactory(currentConfiguration), currentConfiguration.getSessionListener())
                                .initialize(ch, promise);
                    }
                });

        p.connect();
        return p;
    }

    @Override
    public void close() {
        group.shutdownGracefully();
    }

    private NetconfClientSessionNegotiatorFactory getNegotiatorFactory(final NetconfClientConfiguration cfg) {
        return new NetconfClientSessionNegotiatorFactory(timer, cfg.getAdditionalHeader(),
                cfg.getConnectionTimeoutMillis());
    }

}
