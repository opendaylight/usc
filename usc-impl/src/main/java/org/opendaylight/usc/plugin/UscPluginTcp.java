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

import org.opendaylight.usc.manager.UscManager;
import org.opendaylight.usc.manager.api.UscConfiguration;
import org.opendaylight.usc.manager.api.UscSecureChannel;
import org.opendaylight.usc.plugin.model.UscChannel.ChannelType;
import org.opendaylight.usc.plugin.model.UscDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UscPlugin implemented for TCP traffic.
 */
public class UscPluginTcp extends UscPlugin {

    private static final Logger log = LoggerFactory.getLogger(UscPluginTcp.class);

    private final UscManager manager = UscManager.getInstance();
    private final UscSecureChannel securityManager = manager.getSecurityManager();
    private final EventLoopGroup agentGroup = new NioEventLoopGroup();
    private final Bootstrap agentBootstrap = new Bootstrap();

    /**
     * Constructs a new UscPluginTcp
     */
    public UscPluginTcp() {
        super();

        agentBootstrap.group(agentGroup);
        agentBootstrap.channel(NioSocketChannel.class);
        agentBootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(final Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                initAgentPipeline(p, securityManager.getTcpClientHandler(ch));
            }
        });

        final ServerBootstrap callHomeServerTcpBootstrap = new ServerBootstrap();
        callHomeServerTcpBootstrap.group(agentGroup);
        callHomeServerTcpBootstrap.channel(NioServerSocketChannel.class);
        // callHomeServerTcpBootstrap.handler(new
        // LoggingHandler(LogLevel.TRACE));
        callHomeServerTcpBootstrap.childHandler(new ChannelInitializer<NioSocketChannel>() {

            @Override
            public void initChannel(final NioSocketChannel channel) throws Exception {
                log.debug("Received call home TCP connection");

                ChannelPipeline p = channel.pipeline();

                addCallHomeConnection(channel.remoteAddress(), channel);

                initAgentPipeline(p, securityManager.getTcpServerHandler(channel));
            }
        });
        final ChannelFuture callHomeChannelTcpFuture = callHomeServerTcpBootstrap.bind(manager
                .getConfigurationManager().getConfigIntValue(UscConfiguration.USC_PLUGIN_PORT));
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
    }

    @Override
    protected Channel connectToAgent(UscDevice device) throws InterruptedException {
        final int agentPort = manager.getConfigurationManager().getConfigIntValue(UscConfiguration.USC_AGENT_PORT);
        return agentBootstrap.connect(device.getInetAddress(), agentPort).sync().channel();
    }

    @Override
    protected ChannelType getChannelType() {
        return ChannelType.TLS;
    }

}
