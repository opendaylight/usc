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
import io.netty.handler.codec.MessageToByteEncoder;

import org.opendaylight.usc.protocol.UscFrame;

/**
 * This class encodes an UscFrame packet into its raw TCP byte stream payload
 * for transmission.
 */
@Sharable
public class UscFrameEncoderTcp extends MessageToByteEncoder<UscFrame> {

    private static UscFrameEncoderTcp INSTANCE = new UscFrameEncoderTcp();

    /**
     * Returns the singleton instance.
     * 
     * @return the singleton instance
     */
    public static UscFrameEncoderTcp getInstance() {
        return INSTANCE;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, UscFrame msg, ByteBuf out) throws Exception {
        out.writeBytes(msg.getHeader().toByteBuffer());
        out.writeBytes(msg.getPayload());
    }

}
