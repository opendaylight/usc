/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.agent;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.util.HashMap;

import org.opendaylight.usc.protocol.UscControl;
import org.opendaylight.usc.protocol.UscData;
import org.opendaylight.usc.protocol.UscError;
import org.opendaylight.usc.protocol.UscFrame;
import org.opendaylight.usc.protocol.UscHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.SettableFuture;

/**
 * @author gwu
 *
 */
public class UscAgentUdpHandler extends SimpleChannelInboundHandler<UscFrame> {

    private static final Logger LOG = LoggerFactory.getLogger(UscAgentUdpHandler.class);

    public static final AttributeKey<Integer> SESSION_ID = AttributeKey.valueOf("agentUdpSessionId");
    public static final AttributeKey<Integer> PORT = AttributeKey.valueOf("agentUdpPort");

    final EventLoopGroup clientGroup = new NioEventLoopGroup();
    final Bootstrap cb = new Bootstrap();

    final HashMap<Integer, Channel> clients = new HashMap<>();
    final DatagramChannel plugin;
    private final UscAgentUdp agent;
    
    class ClientHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) throws Exception {
            Channel ch = ctx.channel();
            int sessionId = ch.attr(SESSION_ID).get();
            int port = ch.attr(PORT).get();
            if (e instanceof ConnectException) {
                UscError reply = new UscError(port, sessionId, UscError.ErrorCode.ECONNREFUSED.getCode());
                plugin.writeAndFlush(reply);
            } else if (e instanceof PortUnreachableException) {
                UscError reply = new UscError(port, sessionId, UscError.ErrorCode.ENETUNREACH.getCode());
                plugin.writeAndFlush(reply);
            } else {
                UscError reply = new UscError(port, sessionId, UscError.ErrorCode.E_OTHER.getCode());
                plugin.writeAndFlush(reply);
                super.exceptionCaught(ctx, e);
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {

        	LOG.trace("Got reply " + packet);

            ByteBuf payload = packet.content();

            Channel ch = ctx.channel();
            int sessionId = ch.attr(SESSION_ID).get();
            int port = ch.attr(PORT).get();
            UscData reply = new UscData(port, sessionId, payload.copy());
            LOG.trace("Send to plugin " + reply);
            plugin.writeAndFlush(reply);
        }

    };

    public UscAgentUdpHandler(UscAgentUdp agent, NioDatagramChannel ch) {
    	this.agent = agent;
        this.plugin = ch;
        cb.group(clientGroup);
        cb.channel(NioDatagramChannel.class);
        cb.handler(new ChannelInitializer<NioDatagramChannel>() {

            @Override
            protected void initChannel(NioDatagramChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new LoggingHandler(LogLevel.INFO));
                p.addLast(new ClientHandler());
                p.addLast(new LoggingHandler(LogLevel.INFO));
            }
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, UscFrame frame) throws Exception {

        final UscHeader header = frame.getHeader();

        final int sessionId = header.getSessionId();
        final int port = header.getApplicationPort();

        Channel client = clients.get(sessionId);
        if (frame instanceof UscData) {
            if (client == null) {
                try {
                    client = cb.connect(InetAddress.getLoopbackAddress(), port).sync().channel();
                    client.attr(SESSION_ID).set(sessionId);
                    client.attr(PORT).set(port);
                    clients.put(sessionId, client);
                } catch (Exception e) {
                    if (e instanceof ConnectException) {
                        UscError reply = new UscError(port, sessionId, UscError.ErrorCode.ECONNREFUSED.getCode());
                        plugin.writeAndFlush(reply);
                    } else if (e instanceof PortUnreachableException) {
                        UscError reply = new UscError(port, sessionId, UscError.ErrorCode.ENETUNREACH.getCode());
                        plugin.writeAndFlush(reply);
                    } else {
                        UscError reply = new UscError(port, sessionId, UscError.ErrorCode.E_OTHER.getCode());
                        plugin.writeAndFlush(reply);
                        throw e;
                    }
                }
            }
            if (client != null) {
                client.writeAndFlush(frame.getPayload());
            }
        }
        else if(frame instanceof UscControl) {
        	UscControl control = (UscControl)frame;
        	
        	// close it
        	if(control.getControlCode() == UscControl.ControlCode.TERMINATION_REQUEST) {
        		if(client != null)
        		 {
        			client.close();
        			clients.remove(sessionId);
        		}
        		
        		// send back the response
            	UscControl data = new UscControl(port, sessionId, 2);
            	plugin.writeAndFlush(data);
            	LOG.trace("UscAgentUdpHandler send TERMINATION_RESPONSE");
        	}
        	else if(control.getControlCode() == UscControl.ControlCode.TERMINATION_RESPONSE) {
        		LOG.trace("UscAgentUdpHandler received control message TERMINATION_RESPONSE, port#: " + port + " ,session#: " + sessionId);
        		SettableFuture<Boolean> status = agent.getCloseFuture().get(sessionId);
        		status.set(true);
        		
        		try {
        			LOG.trace("UscAgentUdp termination status: " + status.get());
        		}catch(Exception e) {
        			;
        		}
        	}
        	
        }

    }

}
