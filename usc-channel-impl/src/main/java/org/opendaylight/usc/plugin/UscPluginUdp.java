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
import io.netty.channel.local.LocalAddress;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.net.InetSocketAddress;

import javax.net.ssl.SSLException;

import org.opendaylight.usc.manager.UscRouteBrokerService;
import org.opendaylight.usc.manager.api.UscConfigurationService;
import org.opendaylight.usc.manager.api.UscSecureService;
import org.opendaylight.usc.plugin.model.UscChannel.ChannelType;
import org.opendaylight.usc.plugin.model.UscDevice;
import org.opendaylight.usc.util.UscServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UscPlugin implemented for UDP traffic.
 */
public class UscPluginUdp extends UscPlugin {

    private static final Logger log = LoggerFactory
            .getLogger(UscPluginUdp.class);

    private final UscSecureService secureService;
    private final UscConfigurationService configService;
    private final EventLoopGroup agentGroup = new NioEventLoopGroup();
    private final Bootstrap agentBootstrap = new Bootstrap();
    private Bootstrap directBootstrap = null;
    private EventLoopGroup directGroup = null;

    private final ChannelInboundHandlerAdapter callHomeHandler = new ChannelInboundHandlerAdapter() {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg)
                throws Exception {
            final Channel ch = ctx.channel();
            final InetSocketAddress remoteAddress = ((DatagramPacket) msg)
                    .sender();
            // this is to deal with UDP channels which don't by default have
            // remote address
            if (ch.remoteAddress() == null) {
                ch.connect(remoteAddress);
            }
            addCallHomeConnection(remoteAddress, ch);
            super.channelRead(ctx, msg);
            ch.pipeline().remove(this);
        }
    };

    /**
     * Constructs a new UscPluginUdp
     */
    public UscPluginUdp() {
        super(new LocalAddress("usc-local-server-udp"));

        UscRouteBrokerService routeBroker = UscServiceUtils
                .getService(UscRouteBrokerService.class);
        if (routeBroker != null) {
            routeBroker.setConnetionManager(ChannelType.UDP,
                    super.getConnectionManager());
            routeBroker.setConnetionManager(ChannelType.DTLS,
                    super.getConnectionManager());
        } else {
            log.error("UscRouteBrokerService is not found, failed to set connection manager for all UDP Channel!");
        }

        configService = UscServiceUtils
                .getService(UscConfigurationService.class);
        secureService = UscServiceUtils.getService(UscSecureService.class);
        agentBootstrap.group(agentGroup);
        agentBootstrap.channel(NioDatagramChannel.class);
        agentBootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(final Channel ch) throws Exception {
                if (secureService == null) {
                    log.error("UscSecureService is not initialized!");
                    return;
                }
                ChannelPipeline p = ch.pipeline();
                ChannelHandler dtlsHandler = secureService
                        .getUdpClientHandler(ch);
                initAgentPipeline(p, dtlsHandler);
            }
        });

        final Bootstrap callHomeServerUdpBootstrap = new Bootstrap();
        callHomeServerUdpBootstrap.group(agentGroup);
        callHomeServerUdpBootstrap.channel(NioDatagramChannel.class);
        callHomeServerUdpBootstrap
                .handler(new ChannelInitializer<NioDatagramChannel>() {

                    @Override
                    public void initChannel(final NioDatagramChannel ch)
                            throws Exception {
                        if (secureService == null) {
                            log.error("UscSecureService is not initialized!");
                            return;
                        }
                        ChannelPipeline p = ch.pipeline();

                        // no remoteAddress yet until data received, so need a
                        // handler
                        // to add the channel
                        p.addLast("callHomeHandler", callHomeHandler);

                        p.addLast(new LoggingHandler(LogLevel.TRACE));

                        ChannelHandler dtlsHandler = secureService
                                .getUdpServerHandler(ch);
                        initAgentPipeline(ch.pipeline(), dtlsHandler);

                    }
                });
        if (configService == null) {
            log.error("UscConfigurationService is not initialized!");
            return;
        }
        int pluginPort = configService
                .getConfigIntValue(UscConfigurationService.USC_PLUGIN_PORT);
        final ChannelFuture callHomeChannelUdpFuture = callHomeServerUdpBootstrap
                .bind(pluginPort);
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
        if (directGroup != null)
            directGroup.shutdownGracefully();
    }

    @Override
    protected Channel connectToAgent(UscDevice device)
            throws InterruptedException {
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
        } catch (InterruptedException e) {
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
            directBootstrap.channel(NioDatagramChannel.class);
            directBootstrap.handler(new ChannelInitializer<Channel>() {
                @Override
                public void initChannel(final Channel ch) throws Exception {
                    if (secureService == null) {
                        log.error("UscSecureService is not initialized!");
                        return;
                    }
                    ChannelPipeline p = ch.pipeline();
                    ChannelHandler dtlsHandler = secureService
                            .getUdpClientHandler(ch);
                    initDirectPipeline(p, dtlsHandler);
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
        return ChannelType.DTLS;
    }

    /**
     * Returns the security handler for server-side use. Currently this is DTLS.
     *
     * @param ch
     *            The physical channel that the traffic will be sent through.
     * @return The channel handler, or null if the security service was not
     *         properly initialized.
     * @throws SSLException
     */
    public static ChannelHandler getSecureServerHandler(Channel ch) throws SSLException {
        UscSecureService service = UscServiceUtils.getService(UscSecureService.class);
        if (service == null) {
            log.error("UscSecureService is not initialized!");
            return null;
        }
        return service.getUdpServerHandler(ch);
    }

    /**
     * Returns the security handler for client-side use. Currently this is DTLS.
     *
     * @param ch
     *            The physical channel that the traffic will be sent through.
     * @return The channel handler, or null if the security service was not
     *         properly initialized.
     * @throws SSLException
     */
    public static ChannelHandler getSecureClientHandler(Channel ch) throws SSLException {
        UscSecureService service = UscServiceUtils.getService(UscSecureService.class);
        if (service == null) {
            log.error("UscSecureService is not initialized!");
            return null;
        }
        return service.getUdpClientHandler(ch);
    }

}
