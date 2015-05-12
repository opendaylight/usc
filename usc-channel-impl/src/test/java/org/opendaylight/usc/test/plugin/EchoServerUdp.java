/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test.plugin;

import org.opendaylight.usc.manager.api.UscSecureService;
import org.opendaylight.usc.util.UscServiceUtils;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * UDP echo server.
 */
public class EchoServerUdp implements Runnable, AutoCloseable {
    static final int PORT = Integer.parseInt(System.getProperty("port", "2007"));
    EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    Bootstrap b = new Bootstrap();
    private final UscSecureService secureService = UscServiceUtils.getService(UscSecureService.class);

    public EchoServerUdp(final boolean enableEncryption) {
        b.group(bossGroup)
         .channel(NioDatagramChannel.class)
         .handler(new ChannelInitializer<DatagramChannel>() {
            @Override
            public void initChannel(DatagramChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                
                if(enableEncryption) {
                	p.addLast(new LoggingHandler("EchoServerUdp Handler 3", LogLevel.TRACE));
                	p.addLast(secureService.getUdpServerHandler(ch));
                }
                p.addLast(new LoggingHandler("EchoServerUdp Handler 2", LogLevel.TRACE));
                p.addLast(new EchoServerUdpHandler());
                p.addLast(new LoggingHandler("EchoServerUdp Handler 1", LogLevel.TRACE));
            }
        });

    }

    @Override
    public void run() {
        try {
            ChannelFuture f = b.bind(PORT).sync();
            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws Exception {
        bossGroup.shutdownGracefully();
    }

    public static void main(String[] args) throws Exception {
        try (EchoServerUdp server = new EchoServerUdp(false)) {
            server.run();
        }
    }

}
