/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.opendaylight.usc.manager.UscManager;
import org.opendaylight.usc.manager.api.UscConfiguration;
import org.opendaylight.usc.manager.api.UscSecureChannel;
import org.opendaylight.usc.plugin.model.UscChannel.ChannelType;
import org.opendaylight.usc.plugin.model.UscDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UscPlugin implemented for UDP traffic.
 */
public class UscPluginUdp extends UscPlugin {

    private static final Logger log = LoggerFactory.getLogger(UscPluginUdp.class);

    private final UscManager manager = UscManager.getInstance();
    private final UscSecureChannel securityManager = manager.getSecurityManager();
    private final EventLoopGroup agentGroup = new NioEventLoopGroup();
    private final Bootstrap agentBootstrap = new Bootstrap();

    private final ChannelInboundHandlerAdapter callHomeHandler = new ChannelInboundHandlerAdapter() {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            final Channel ch = ctx.channel();
            final InetSocketAddress remoteAddress = ((DatagramPacket) msg).sender();
            // this is to deal with UDP channels which don't by default have
            // remote address
            if (ch.remoteAddress() == null) {
                ch.connect(remoteAddress);
            }
            addCallHomeConnection(remoteAddress, ch);
            super.channelRead(ctx, msg);
        }
    };

    /**
     * Constructs a new UscPluginUdp
     */
    public UscPluginUdp() {
        super();

        agentBootstrap.group(agentGroup);
        agentBootstrap.channel(NioDatagramChannel.class);
        agentBootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(final Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();

                ChannelHandler dtlsHandler = securityManager.getUdpClientHandler(ch);
                initAgentPipeline(p, dtlsHandler);
            }
        });

        final Bootstrap callHomeServerUdpBootstrap = new Bootstrap();
        callHomeServerUdpBootstrap.group(agentGroup);
        callHomeServerUdpBootstrap.channel(NioDatagramChannel.class);
        callHomeServerUdpBootstrap.handler(new ChannelInitializer<NioDatagramChannel>() {

            @Override
            public void initChannel(final NioDatagramChannel ch) throws Exception {

                ChannelPipeline p = ch.pipeline();

                // no remoteAddress yet until data received, so need a handler
                // to add the channel
                p.addLast("callHomeHandler", callHomeHandler);

                p.addLast(new LoggingHandler(LogLevel.TRACE));

                ChannelHandler dtlsHandler = securityManager.getUdpServerHandler(ch);
                initAgentPipeline(ch.pipeline(), dtlsHandler);

            }
        });

        int pluginPort = manager.getConfigurationManager().getConfigIntValue(UscConfiguration.USC_PLUGIN_PORT);
        final ChannelFuture callHomeChannelUdpFuture = callHomeServerUdpBootstrap.bind(new InetSocketAddress(
                InetAddress.getLoopbackAddress(), pluginPort));
        log.debug("callHomeChannelUdpFuture : " + callHomeChannelUdpFuture);
        try {
            callHomeChannelUdpFuture.sync();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    protected ChannelOutboundHandler getFrameEncoder() {
        return UscFrameEncoderUdp.getInstance();
    }

    @Override
    protected ChannelInboundHandler getFrameDecoder() {
        return UscFrameDecoderUdp.getInstance();
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
        return ChannelType.DTLS;
    }

}
