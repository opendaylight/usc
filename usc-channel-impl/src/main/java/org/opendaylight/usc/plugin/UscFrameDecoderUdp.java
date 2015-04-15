/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

import org.opendaylight.usc.protocol.UscFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class decodes the raw UDP USC packets into UscFrame packets.
 */
@Sharable
public class UscFrameDecoderUdp extends MessageToMessageDecoder<DatagramPacket> {

    private static final Logger log = LoggerFactory.getLogger(UscFrameDecoderUdp.class);

    private static final UscFrameDecoderUdp INSTANCE = new UscFrameDecoderUdp();

    /**
     * Returns the singleton instance.
     * 
     * @return the singleton instance
     */
    public static UscFrameDecoderUdp getInstance() {
        return INSTANCE;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {

        ByteBuf buf = msg.content();
        log.trace("UscFrameDecoderUdp.decode " + buf);

        if (buf == null) {
            return;
        }

        out.add(UscFrame.getFromByteBuf(buf));
    }

}
