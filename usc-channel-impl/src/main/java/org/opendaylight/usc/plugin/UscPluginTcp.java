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
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import org.opendaylight.usc.manager.UscRouteBrokerService;
import org.opendaylight.usc.manager.api.UscConfigurationService;
import org.opendaylight.usc.manager.api.UscSecureService;
import org.opendaylight.usc.plugin.model.UscChannel.ChannelType;
import org.opendaylight.usc.plugin.model.UscDevice;
import org.opendaylight.usc.util.UscServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UscPlugin implemented for TCP traffic.
 */
public class UscPluginTcp extends UscPlugin {

    private static final Logger log = LoggerFactory
            .getLogger(UscPluginTcp.class);

    private final UscSecureService secureService;
    private final UscConfigurationService configService;
    private final EventLoopGroup agentGroup = new NioEventLoopGroup();
    private final Bootstrap agentBootstrap = new Bootstrap();
    private EventLoopGroup directGroup = null;
    private Bootstrap directBootstrap = null;

    /**
     * Constructs a new UscPluginTcp
     */
    public UscPluginTcp() {
        super();

        UscRouteBrokerService routeBroker = UscServiceUtils
                .getService(UscRouteBrokerService.class);
        if (routeBroker != null) {
            routeBroker.setConnetionManager(ChannelType.TCP,
                    super.getConnectionManager());
            routeBroker.setConnetionManager(ChannelType.TLS,
                    super.getConnectionManager());
        } else {
            log.error("UscRouteBrokerService is not found, failed to set connection manager for all TCP Channel!");
        }

        configService = UscServiceUtils
                .getService(UscConfigurationService.class);
        secureService = UscServiceUtils.getService(UscSecureService.class);
        agentBootstrap.group(agentGroup);
        agentBootstrap.channel(NioSocketChannel.class);
        agentBootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(final Channel ch) throws Exception {
                if (secureService == null) {
                    log.error("UscSecureService is not initialized!");
                    return;
                }
                ChannelPipeline p = ch.pipeline();
                initAgentPipeline(p, secureService.getTcpClientHandler(ch));
            }
        });

        final ServerBootstrap callHomeServerTcpBootstrap = new ServerBootstrap();
        callHomeServerTcpBootstrap.group(agentGroup);
        callHomeServerTcpBootstrap.channel(NioServerSocketChannel.class);
        // callHomeServerTcpBootstrap.handler(new
        // LoggingHandler(LogLevel.TRACE));
        callHomeServerTcpBootstrap
                .childHandler(new ChannelInitializer<NioSocketChannel>() {

                    @Override
                    public void initChannel(final NioSocketChannel channel)
                            throws Exception {
                        if (secureService == null) {
                            log.error("UscSecureService is not initialized!");
                            return;
                        }
                        log.debug("Received call home TCP connection");

                        ChannelPipeline p = channel.pipeline();

                        addCallHomeConnection(channel.remoteAddress(), channel);

                        initAgentPipeline(p,
                                secureService.getTcpServerHandler(channel));
                    }
                });
        if (configService == null) {
            log.error("UscConfigurationService is not initialized!");
            return;
        }
        final ChannelFuture callHomeChannelTcpFuture = callHomeServerTcpBootstrap
                .bind(configService
                        .getConfigIntValue(UscConfigurationService.USC_PLUGIN_PORT));
        log.debug("callHomeChannelTcpFuture : " + callHomeChannelTcpFuture);
    }

    @Override
    protected ChannelOutboundHandler getFrameEncoder() {
        return UscFrameEncoderTcp.getInstance();
    }

    @Override
    protected ChannelInboundHandler getFrameDecoder() {
        // UscFrameDecoderTcp is NOT Sharable
        return new UscFrameDecoderTcp();
    }

    @Override
    public void close() {
        super.close();
        agentGroup.shutdownGracefully();
        if (directGroup != null)
            directGroup.shutdownGracefully();
    }

    @Override
    protected Channel connectToAgent(UscDevice device) throws Exception {
        if (configService == null) {
            log.error("UscConfigurationService is not initialized!");
            return null;
        }
        final int agentPort = configService
                .getConfigIntValue(UscConfigurationService.USC_AGENT_PORT);
        Channel channel = null;

        try {
            channel = agentBootstrap
                    .connect(device.getInetAddress(), agentPort).sync()
                    .channel();
        } catch (Exception e) {
            throw e;
        }

        return channel;
    }

    @Override
    protected Channel connectToDeviceDirectly(UscDevice device)
            throws Exception {
        Channel channel = null;

        if (directBootstrap == null) {
            directBootstrap = new Bootstrap();
            directGroup = new NioEventLoopGroup();

            directBootstrap.group(directGroup);
            directBootstrap.channel(NioSocketChannel.class);
            directBootstrap.handler(new ChannelInitializer<Channel>() {
                @Override
                public void initChannel(final Channel ch) throws Exception {
                    if (secureService == null) {
                        log.error("UscSecureService is not initialized!");
                        return;
                    }
                    ChannelPipeline p = ch.pipeline();
                    initDirectPipeline(p, secureService.getTcpClientHandler(ch));
                }
            });
        }

        try {
            channel = directBootstrap
                    .connect(device.getInetAddress(), device.getPort()).sync()
                    .channel();
        } catch (Exception e) {
            throw e;
        }

        return channel;
    }

    @Override
    protected ChannelType getChannelType() {
        return ChannelType.TLS;
    }

}
