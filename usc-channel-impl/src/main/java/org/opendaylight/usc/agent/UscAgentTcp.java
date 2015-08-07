/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.usc.agent;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opendaylight.usc.manager.UscConfigurationServiceImpl;
import org.opendaylight.usc.manager.api.UscSecureService;
import org.opendaylight.usc.plugin.UscFrameDecoderTcp;
import org.opendaylight.usc.plugin.UscFrameEncoderTcp;
import org.opendaylight.usc.plugin.UscPlugin;
import org.opendaylight.usc.plugin.model.UscSessionImpl;
import org.opendaylight.usc.protocol.UscControl;
import org.opendaylight.usc.util.UscServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.SettableFuture;

public class UscAgentTcp implements Runnable, AutoCloseable {
	private static final Logger LOG = LoggerFactory
			.getLogger(UscAgentTcp.class);
	static final int PORT = Integer
			.parseInt(System.getProperty("port", "1068"));
	final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
	final EventLoopGroup workerGroup = new NioEventLoopGroup();
	final ServerBootstrap b = new ServerBootstrap();

	final EventLoopGroup callHomeGroup = new NioEventLoopGroup();
	final Bootstrap cb = new Bootstrap();

	private Channel agentServerChannel = null;
	private ConcurrentMap<Integer, SettableFuture<Boolean>> closeFuture = new ConcurrentHashMap<>();
	private UscSecureService secureService = null;

	public UscAgentTcp(boolean callHome) {
		this(callHome, InetAddress.getLoopbackAddress());
	}
	
	public UscAgentTcp(boolean callHome, InetAddress host) {
		final UscAgentTcp agent = this;
        UscConfigurationServiceImpl.setDefaultPropertyFilePath("src/test/resources/etc/usc/usc.properties");
        secureService = UscServiceUtils.getService(UscSecureService.class);
		b.group(bossGroup, workerGroup);
		b.channel(NioServerSocketChannel.class);
		b.handler(new LoggingHandler("UscAgentTcp server handler", LogLevel.TRACE));
		b.childHandler(new ChannelInitializer<NioSocketChannel>() {
			@Override
			public void initChannel(NioSocketChannel ch) throws Exception {
				if (secureService == null) {
					LOG.error("UscSecureService is not initialized!");
					return;
				}
				ChannelPipeline p = ch.pipeline();
				agentServerChannel = ch;
				p.addLast(new LoggingHandler("UscAgentTcp PLUGIN5", LogLevel.TRACE));
				p.addLast(secureService.getTcpServerHandler(ch));
				p.addLast(new LoggingHandler("UscAgentTcp PLUGIN4", LogLevel.TRACE));
				p.addLast(new UscFrameEncoderTcp());
				p.addLast(new LoggingHandler("UscAgentTcp PLUGIN3", LogLevel.TRACE));
				p.addLast(new UscFrameDecoderTcp());
				p.addLast(new LoggingHandler("UscAgentTcp PLUGIN2", LogLevel.TRACE));
				p.addLast(new UscAgentTcpHandler(agent, ch));
				p.addLast(new LoggingHandler("UscAgentTcp PLUGIN1", LogLevel.TRACE));
			}
		});

		if (callHome) {
			cb.group(callHomeGroup);
			cb.channel(NioSocketChannel.class);
			cb.handler(new ChannelInitializer<NioSocketChannel>() {
				@Override
				public void initChannel(NioSocketChannel ch) throws Exception {
					if (secureService == null) {
						LOG.error("UscSecureService is not initialized!");
						return;
					}
					ChannelPipeline p = ch.pipeline();
					agentServerChannel = ch;
					p.addLast(new LoggingHandler("UscAgentTcp Handler5", LogLevel.TRACE));
					p.addLast(secureService.getTcpClientHandler(ch));
					p.addLast(new LoggingHandler("UscAgentTcp Handler4", LogLevel.TRACE));
					p.addLast(new UscFrameEncoderTcp());
					p.addLast(new LoggingHandler("UscAgentTcp Handler3", LogLevel.TRACE));
					p.addLast(new UscFrameDecoderTcp());
					p.addLast(new LoggingHandler("UscAgentTcp Handler2", LogLevel.TRACE));
					p.addLast(new UscAgentTcpHandler(agent, ch));
					p.addLast(new LoggingHandler("UscAgentTcp Handler1", LogLevel.TRACE));
				}
			});

			try {
				cb.connect(host, 1069).sync();
			} catch (Exception e) {
				// TODO Auto-generated catch block
			    
			    System.err.println("USC Plugin call home port not available; call home disabled");
				// e.printStackTrace();
			}
		}
	}
	
	
	 protected ConcurrentMap<Integer, SettableFuture<Boolean>> getCloseFuture() {
		return closeFuture;
	}


	public SettableFuture<Boolean> closeClientInternalConnection(Channel clientChannel) {
		try {
			UscSessionImpl session = clientChannel.attr(UscPlugin.SESSION).get().get();
			closeFuture.remove(session.getSessionId());
			closeFuture.putIfAbsent(session.getSessionId(),
					SettableFuture.<Boolean> create());

			UscControl data = new UscControl(session.getPort(),
					session.getSessionId(), 1);
			if (agentServerChannel != null)
				agentServerChannel.writeAndFlush(data);

			LOG.trace("UscAgentTcp closeClientInternalConnection port#: "
					+ session.getPort() + " ,session#: "
					+ session.getSessionId());
			return closeFuture.get(session.getSessionId());
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	    
	@Override
	public void run() {
		// Start the server.
		try {
			ChannelFuture f = b.bind(PORT).sync();
			System.out.println("UscAgentTcp initialized");
			
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

		callHomeGroup.shutdownGracefully();
	}

	public static void main(String[] args) throws Exception {
		boolean callHome = false;
		InetAddress host = InetAddress.getLoopbackAddress();
		if (args.length > 0) {
			try {
				InetAddress ip = InetAddress.getByName(args[0]);
				callHome = true; 
				host = ip;
			}catch(UnknownHostException e) {
				System.err.println("Argument " + args[0] + " must be an iP address (callhome IP).");
        		System.exit(1);
			}
		}
		
		try (UscAgentTcp agent = new UscAgentTcp(callHome, host)) {
			agent.run();
		}
	}

}
