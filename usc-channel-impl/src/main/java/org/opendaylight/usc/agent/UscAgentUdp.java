/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.usc.agent;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opendaylight.usc.manager.api.UscSecureService;
import org.opendaylight.usc.plugin.UscFrameDecoderUdp;
import org.opendaylight.usc.plugin.UscFrameEncoderUdp;
import org.opendaylight.usc.plugin.UscPlugin;
import org.opendaylight.usc.plugin.model.UscSessionImpl;
import org.opendaylight.usc.protocol.UscControl;
import org.opendaylight.usc.util.UscServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.SettableFuture;

public class UscAgentUdp implements Runnable, AutoCloseable {
	private static final Logger LOG = LoggerFactory
			.getLogger(UscAgentUdp.class);
	static final int PORT = Integer
			.parseInt(System.getProperty("port", "1068"));
	final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
	final Bootstrap b = new Bootstrap();
	final EventLoopGroup callHomeGroup = new NioEventLoopGroup();
	final Bootstrap cb = new Bootstrap();

	private Channel agentServerChannel = null;
	private ConcurrentMap<Integer, SettableFuture<Boolean>> closeFuture = new ConcurrentHashMap<>();
	private final UscSecureService secureService = UscServiceUtils
			.getService(UscSecureService.class);

	public UscAgentUdp(boolean callHome) {
		final UscAgentUdp agent = this;
		b.group(bossGroup);
		b.channel(NioDatagramChannel.class);
		b.handler(new ChannelInitializer<NioDatagramChannel>() {
			@Override
			public void initChannel(NioDatagramChannel ch) throws Exception {
				ChannelPipeline p = ch.pipeline();
				agentServerChannel = ch;
				p.addLast(new ChannelInboundHandlerAdapter() {

					@Override
					public void channelRead(ChannelHandlerContext ctx,
							Object msg) throws Exception {
						final Channel ch = ctx.channel();
						final InetSocketAddress remoteAddress = ((DatagramPacket) msg)
								.sender();

						// this is to deal with UDP channels which don't by
						// default have remote address
						if (ch.remoteAddress() == null) {
							ch.connect(remoteAddress);
						}
						ch.pipeline().remove(this);
						super.channelRead(ctx, msg);
					}

				});
				if (secureService == null) {
					LOG.error("UscSecureService is not initialized!");
					return;
				}
				p.addLast(new LoggingHandler("LOG5", LogLevel.TRACE));
				p.addLast(secureService.getUdpServerHandler(ch));
				p.addLast(new LoggingHandler("LOG4", LogLevel.TRACE));
				p.addLast(new UscFrameEncoderUdp());
				p.addLast(new LoggingHandler("LOG3", LogLevel.TRACE));
				p.addLast(new UscFrameDecoderUdp());
				p.addLast(new LoggingHandler("LOG2", LogLevel.TRACE));
				p.addLast(new UscAgentUdpHandler(agent, ch));
				p.addLast(new LoggingHandler("LOG1", LogLevel.TRACE));

			}
		});

		if (callHome) {
			cb.group(callHomeGroup);
			cb.channel(NioDatagramChannel.class);
			cb.handler(new ChannelInitializer<NioDatagramChannel>() {
				@Override
				public void initChannel(NioDatagramChannel ch) throws Exception {
					ChannelPipeline p = ch.pipeline();
					agentServerChannel = ch;
					p.addLast(new ChannelInboundHandlerAdapter() {

						@Override
						public void channelRead(ChannelHandlerContext ctx,
								Object msg) throws Exception {
							final Channel ch = ctx.channel();
							final InetSocketAddress remoteAddress = ((DatagramPacket) msg)
									.sender();

							// this is to deal with UDP channels which don't by
							// default have remote address
							if (ch.remoteAddress() == null) {
								ch.connect(remoteAddress);
							}
							ch.pipeline().remove(this);
							super.channelRead(ctx, msg);
						}

					});
					if (secureService == null) {
						LOG.error("UscSecureService is not initialized!");
						return;
					}
					p.addLast(new LoggingHandler("LOG2-5", LogLevel.TRACE));
					p.addLast(secureService.getUdpClientHandler(ch));
					p.addLast(new LoggingHandler("LOG2-4", LogLevel.TRACE));
					p.addLast(new UscFrameEncoderUdp());
					p.addLast(new LoggingHandler("LOG2-3", LogLevel.TRACE));
					p.addLast(new UscFrameDecoderUdp());
					p.addLast(new LoggingHandler("LOG2-2", LogLevel.TRACE));
					p.addLast(new UscAgentUdpHandler(agent, ch));
					p.addLast(new LoggingHandler("LOG2-1", LogLevel.TRACE));

				}
			});

			try {
				InetSocketAddress recipient = new InetSocketAddress(
						InetAddress.getLoopbackAddress(), 1069);
				cb.connect(recipient).sync().channel();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	
	protected ConcurrentMap<Integer, SettableFuture<Boolean>> getCloseFuture() {
		return closeFuture;
	}


	public SettableFuture<Boolean> closeClientInternalConnection(Channel clientChannel) {
		try {
			UscSessionImpl session = clientChannel.attr(UscPlugin.SESSION)
					.get().get();
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

		callHomeGroup.shutdownGracefully();
	}

	public static void main(String[] args) throws Exception {
		try (UscAgentUdp agent = new UscAgentUdp(true)) {
			agent.run();
		}
	}

}
