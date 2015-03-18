/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

import org.opendaylight.usc.manager.monitor.UscMonitorTargetAdapter;
import org.opendaylight.usc.plugin.model.UscChannel.ChannelType;
import org.opendaylight.usc.plugin.model.UscChannelImpl;
import org.opendaylight.usc.plugin.model.UscDevice;
import org.opendaylight.usc.plugin.model.UscSessionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.SettableFuture;

/**
 * This is the base class for all UscPlugin classes. This handles common
 * connection setup and channel/session management capabilities.
 */
public abstract class UscPlugin extends UscMonitorTargetAdapter implements AutoCloseable {

    /**
     * Constant used for setting the UscChannel attribute on a netty channel.
     */
    public static final AttributeKey<UscChannelImpl> CHANNEL = AttributeKey.valueOf("channel");

    /**
     * Constant used for setting the UscSession attribute on a netty channel.
     */
    public static final AttributeKey<UscSessionImpl> SESSION = AttributeKey.valueOf("session");

    private static final Logger log = LoggerFactory.getLogger(UscPlugin.class);
    private static final LocalAddress localServerAddr = new LocalAddress("usc-local-server");
    private static final UscExceptionHandler uscExceptionHandler = new UscExceptionHandler();

    /**
     * Map from client SocketAddress to serverChildChannel
     */
    private final ConcurrentMap<SocketAddress, SettableFuture<LocalChannel>> serverChannels = new ConcurrentHashMap<>();
    private final UscConnectionManager connectionManager = new UscConnectionManager(this);
    private final EventLoopGroup localGroup = new LocalEventLoopGroup();
    private final UscDemultiplexer demuxer = new UscDemultiplexer(this);
    private final UscMultiplexer muxer = new UscMultiplexer(this);

    protected UscPlugin() {
        log.debug("UscPlugin " + this + "started");

        final ServerBootstrap localServerBootstrap = new ServerBootstrap();
        localServerBootstrap.group(localGroup);
        localServerBootstrap.channel(LocalServerChannel.class);
        // serverBootstrap.handler(new LoggingHandler(LogLevel.TRACE));
        localServerBootstrap.childHandler(new ChannelInitializer<LocalChannel>() {
            @Override
            public void initChannel(final LocalChannel serverChildChannel) throws Exception {
                log.debug("initChannel");
                ChannelPipeline p = serverChildChannel.pipeline();
                // p.addLast(new LoggingHandler(LogLevel.TRACE));

                // register the child channel by address for lookup outside
                LocalAddress localAddress = serverChildChannel.remoteAddress();
                serverChannels.putIfAbsent(localAddress, SettableFuture.<LocalChannel> create());
                serverChannels.get(localAddress).set(serverChildChannel);

                p.addLast(getMultiplexer());

            }
        });

        // Start the server.
        final ChannelFuture serverChannelFuture = localServerBootstrap.bind(localServerAddr);
        log.debug("serverChannel: " + serverChannelFuture);
    }

    protected void initAgentPipeline(ChannelPipeline p, ChannelHandler securityHandler) {

        p.addLast(new LoggingHandler("PLUGIN5", LogLevel.TRACE));

        // security handler
        p.addLast("securityHandler", securityHandler);
        p.addLast(new LoggingHandler("PLUGIN4", LogLevel.TRACE));

        // Encoders
        // UscFrameEncoder is Sharable
        p.addLast("frameEncoder", getFrameEncoder());
        p.addLast(new LoggingHandler("PLUGIN3", LogLevel.TRACE));

        // Decoders
        // UscFrameDecoderUdp is Sharable
        p.addLast("frameDecoder", getFrameDecoder());
        p.addLast(new LoggingHandler("PLUGIN2", LogLevel.TRACE));

        // demultiplexer
        p.addLast(getDemultiplexer());

        p.addLast(new LoggingHandler("PLUGIN1", LogLevel.TRACE));
    }

    protected ChannelInboundHandler getMultiplexer() {
        return muxer;
    }

    protected UscDemultiplexer getDemultiplexer() {
        return demuxer;
    }

    protected abstract ChannelOutboundHandler getFrameEncoder();

    protected abstract ChannelInboundHandler getFrameDecoder();

    /**
     * Initiates a client session to a device service as specified by the
     * address parameter.
     * 
     * @param clientBootstrap
     *            the Netty bootstrap to use to create the session
     * @param address
     *            the IP address and port of the device service
     * @return the Netty ChannelFuture that can be used to communicate with the
     *         device service
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public ChannelFuture connect(Bootstrap clientBootstrap, final InetSocketAddress address)
            throws InterruptedException, ExecutionException {

        // Connect to USC Agent to the device if one's not already created
        final UscChannelImpl connection;
        try {
            connection = connectionManager.getConnection(new UscDevice(address.getAddress()), getChannelType());
        } catch (Exception e) {
            return new DefaultChannelPromise(null).setFailure(e);
        }

        final ChannelFuture clientChannelFuture = clientBootstrap.connect(localServerAddr);
        clientChannelFuture.channel().pipeline().addLast(uscExceptionHandler);

        clientChannelFuture.addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                final Channel clientChannel = future.channel();
                SocketAddress localAddress = clientChannel.localAddress();
                serverChannels.putIfAbsent(localAddress, SettableFuture.<LocalChannel> create());

                // wait for the peer to populate
                LocalChannel serverChannel = serverChannels.get(localAddress).get();
                assert serverChannel != null;
                // remove the entry from the map as its purpose is complete
                serverChannels.remove(localAddress);

                UscSessionImpl session = connection.addSession(address.getPort(), serverChannel);

                // these attributes are used by unit test cases
                clientChannel.attr(SESSION).set(session);

                // these attributes are used by UscMultiplexer
                serverChannel.attr(SESSION).set(session);

            }
        });

        return clientChannelFuture;
    }

    protected abstract ChannelType getChannelType();

    protected abstract Channel connectToAgent(UscDevice device) throws InterruptedException;

    @Override
    public void close() {
        localGroup.shutdownGracefully();

        log.debug("UscPlugin " + this + "closed");
    }

    protected void addCallHomeConnection(InetSocketAddress address, Channel channel) {
        final UscDevice device = new UscDevice(address.getAddress());
        connectionManager.addConnection(device, channel, true, getChannelType());
    }

}
