/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.usc.plugin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Sharable
public class Demultiplexer extends SimpleChannelInboundHandler<Object> {
    private static final Logger LOG = LoggerFactory.getLogger(Demultiplexer.class);

    public Demultiplexer(UscPlugin plugin) {

    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        LOG.trace("Demultiplexer.channelRead0: " + msg);

        Channel channel = ctx.channel();
        Channel serverChannel = channel.attr(UscPlugin.LOCAL_SERVER_CHANNEL).get();
        ReferenceCountUtil.retain(msg);

        if (msg instanceof DatagramPacket) {
            ByteBuf payload = ((DatagramPacket) msg).content();
            serverChannel.writeAndFlush(payload);
        } else {
            serverChannel.writeAndFlush(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
    }
}
