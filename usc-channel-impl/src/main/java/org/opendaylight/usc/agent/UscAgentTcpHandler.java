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
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
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
public class UscAgentTcpHandler extends SimpleChannelInboundHandler<UscFrame> {

    private static final Logger LOG = LoggerFactory.getLogger(UscAgentTcpHandler.class);

    public static final AttributeKey<Integer> SESSION_ID = AttributeKey.valueOf("agentTcpSessionId");
    public static final AttributeKey<Integer> PORT = AttributeKey.valueOf("agentTcpPort");

    final EventLoopGroup clientGroup = new NioEventLoopGroup();
    final Bootstrap cb = new Bootstrap();

    final HashMap<Integer, Channel> clients = new HashMap<>();

    private final UscAgentTcp agent;
    final SocketChannel plugin;

    class ClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

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
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf payload) throws Exception {

            LOG.trace("Got reply " + payload);
            System.out.println("Got reply " + payload);
            Channel ch = ctx.channel();
            int sessionId = ch.attr(SESSION_ID).get();
            int port = ch.attr(PORT).get();
            UscData reply = new UscData(port, sessionId, payload.copy());
            LOG.trace("Send to plugin " + reply);
            System.out.println("Send to plugin " + reply);
            plugin.writeAndFlush(reply);
        }

    };

    public UscAgentTcpHandler(UscAgentTcp agent, SocketChannel ch) {
    	this.agent = agent;
        this.plugin = ch;
        cb.group(clientGroup);
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<NioSocketChannel>() {

            @Override
            protected void initChannel(NioSocketChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new LoggingHandler("UscAgentTcpHandler", LogLevel.TRACE));
                p.addLast(new ClientHandler());
            }
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, UscFrame frame) throws InterruptedException {

        final UscHeader header = frame.getHeader();

        final int sessionId = header.getSessionId();
        final int port = header.getApplicationPort();

        Channel client = clients.get(sessionId);
        if (frame instanceof UscData) {
        	LOG.trace("UscAgentTcpHandler: read uscData " + frame.toString());
            System.out.println("UscAgentTcpHandler: read uscData " + frame.toString());
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
        	LOG.trace("UscAgentTcpHandler: read UscControl " + control.toString());
        	 System.out.println("UscAgentTcpHandler: read UscControl " + control.toString());
        	// close it
        	if(control.getControlCode() == UscControl.ControlCode.TERMINATION_REQUEST) {
        		if(client != null)
        		 {
        			client.close();
        			clients.remove(sessionId);
        		}
        		
        		// send back the response
            	UscControl data = new UscControl(port, sessionId, UscControl.ControlCode.TERMINATION_RESPONSE.getCode());
            	plugin.writeAndFlush(data);
            	LOG.trace("UscAgentTcpHandler send TERMINATION_RESPONSE");
            	 System.out.println("UscAgentTcpHandler send TERMINATION_RESPONSE");
        	}
        	else if(control.getControlCode() == UscControl.ControlCode.TERMINATION_RESPONSE) {
        		LOG.trace("UscAgentTcp received control message TERMINATION_RESPONSE, port#: " + port + " ,session#: " + sessionId);
        		 System.out.println("UscAgentTcp received control message TERMINATION_RESPONSE, port#: " + port + " ,session#: " + sessionId);
        		SettableFuture<Boolean> status = agent.getCloseFuture().get(sessionId);
        		status.set(true);
        		
        		try {
        			LOG.trace("UscAgentTcp termination status: " + status.get());
        			System.out.println("UscAgentTcp termination status: " + status.get());
        		}catch(Exception e) {
        			;
        		}
        	}
        	else if(control.getControlCode() == UscControl.ControlCode.ECHO) {
        		// send back the response
            	UscControl data = new UscControl(port, sessionId, UscControl.ControlCode.ECHO.getCode());
            	plugin.writeAndFlush(data);
            	LOG.trace("UscAgentUdpHandler send ECHO back.");
        	}
        }
    }

}
