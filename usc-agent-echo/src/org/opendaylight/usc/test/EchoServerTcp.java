/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test;

import org.opendaylight.usc.manager.UscConfigurationServiceImpl;
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

/**
 * TCP Echo Server.
 */
public class EchoServerTcp implements Runnable, AutoCloseable {
    private static int PORT = Integer.parseInt(System.getProperty("port", "2007"));
    private EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private EventLoopGroup workerGroup = new NioEventLoopGroup();
    private ServerBootstrap b = new ServerBootstrap();
    private UscSecureService secureService = null;

    /**
     * Initializes a TCP Based Echo Server with the default port 2007
     * @param enableEncryption If true, then enable encryption.
     */
    public EchoServerTcp(final boolean enableEncryption) {
    	this(enableEncryption, PORT);
    }

    /**
     * Initializes a TCP Based Echo Server with the specified port
     * @param enableEncryption If true, then enable encryption.
     * @param port The port to bind to.
     */
    public EchoServerTcp(final boolean enableEncryption, int port) {
    	PORT = port;
        UscConfigurationServiceImpl.setDefaultPropertyFilePath("resources/etc/usc/usc.properties");
        secureService = UscServiceUtils.getService(UscSecureService.class);
        b.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .option(ChannelOption.SO_BACKLOG, 100)
        .handler(new LoggingHandler("EchoServerTcp server handler", LogLevel.TRACE))
        .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                    	System.out.println("EchoServerTcp initChannel");
                        ChannelPipeline p = ch.pipeline();
                        if(enableEncryption) {
                        	p.addLast(new LoggingHandler("EchoServerTcp Handler 3", LogLevel.TRACE));
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
            System.out.println("EchoServerTcp initialized");
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
        if(args.length > 0) {
            try {
                int port = Integer.parseInt(args[0]);
                PORT = port;
            }catch (NumberFormatException e) {
                System.err.println("Argument " + args[0] + " must be an integer (port #).");
                System.exit(1);
            }
        }
        try (EchoServerTcp server = new EchoServerTcp(false)) {
            server.run();
        }
    }

}
