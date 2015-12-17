/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

/**
 * Handler implementation for the echo server.
 */
@Sharable
public class EchoServerUdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {
	private static final Logger LOG = LoggerFactory.getLogger(EchoServerUdpHandler.class);
	
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
    	LOG.trace("channelRead0: " + msg);
        DatagramPacket reply = new DatagramPacket(msg.content().copy(), msg.sender());
        ctx.writeAndFlush(reply);

    }

}
