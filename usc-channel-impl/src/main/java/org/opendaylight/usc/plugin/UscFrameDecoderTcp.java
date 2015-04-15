/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.nio.ByteOrder;

import org.opendaylight.usc.protocol.UscFrame;
import org.opendaylight.usc.protocol.UscHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class decodes the an raw TCP byte stream into UscFrame packets.
 */
public class UscFrameDecoderTcp extends LengthFieldBasedFrameDecoder {

    private static final Logger log = LoggerFactory.getLogger(UscFrameDecoderTcp.class);

    /**
     * Constructs a new UscFrameDecoderTcp
     */
    public UscFrameDecoderTcp() {
        super(ByteOrder.BIG_ENDIAN, UscHeader.HEADER_LENGTH + Character.MAX_VALUE, UscHeader.PAYLOAD_LENGTH_OFFSET,
                UscHeader.PAYLOAD_LENGTH_SIZE, 0, 0, false);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {

        ByteBuf buf = (ByteBuf) super.decode(ctx, in);
        log.trace("UscFrameDecoderTcp.decode");

        if (buf == null) {
            return null;
        }

        return UscFrame.getFromByteBuf(buf);
    }

    @Override
    protected ByteBuf extractFrame(ChannelHandlerContext ctx, ByteBuf buffer, int index, int length) {
        // we avoid making a copy here since UscFrameDecoder already makes a
        // copy of the data
        return buffer.slice(index, length);
    }

}
