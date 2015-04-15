/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.usc.test;

import org.opendaylight.usc.manager.api.UscSecureService;
import org.opendaylight.usc.util.UscServiceUtils;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class EchoServerTcp implements Runnable, AutoCloseable {

    static final int PORT = Integer.parseInt(System.getProperty("port", "2007"));
    EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    EventLoopGroup workerGroup = new NioEventLoopGroup();
    ServerBootstrap b = new ServerBootstrap();
    private final UscSecureService secureService = UscServiceUtils.getService(UscSecureService.class);
    
    public EchoServerTcp(final boolean enableEncryption) {
        b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).option(ChannelOption.SO_BACKLOG, 100)
                .handler(new LoggingHandler(LogLevel.INFO)).childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        if(enableEncryption) {
                        	p.addLast(new LoggingHandler("EchoServerTcp Handler 3", LogLevel.INFO));
                        	p.addLast(secureService.getTcpServerHandler(ch));
                        }
                        p.addLast(new LoggingHandler("EchoServerTcp Handler 2", LogLevel.TRACE));
                        p.addLast(new EchoServerTcpHandler());
                        p.addLast(new LoggingHandler("EchoServerTcp Handler 1", LogLevel.TRACE));
                    }
                });

    }

    @Override
    public void run() {
        // Start the server.
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
        workerGroup.shutdownGracefully();
    }

    public static void main(String[] args) throws Exception {
        try (EchoServerTcp server = new EchoServerTcp(false)) {
            server.run();
        }
    }

}
