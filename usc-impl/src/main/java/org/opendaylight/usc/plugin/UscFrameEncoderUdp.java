/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.net.InetSocketAddress;
import java.util.List;

import org.opendaylight.usc.protocol.UscFrame;

/**
 * This class encodes a UscFrame packet into a raw UDP packet for transmission.
 */
@Sharable
public class UscFrameEncoderUdp extends MessageToMessageEncoder<UscFrame> {

    private static UscFrameEncoderUdp INSTANCE = new UscFrameEncoderUdp();

    /**
     * Returns the singleton instance.
     * 
     * @return the singleton instance
     */
    public static UscFrameEncoderUdp getInstance() {
        return INSTANCE;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, UscFrame msg, List<Object> out) throws Exception {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(msg.getHeader().toByteBuffer());
        buf.writeBytes(msg.getPayload());
        DatagramPacket packet = new DatagramPacket(buf, (InetSocketAddress) ctx.channel().remoteAddress());
        out.add(packet);
    }

}
