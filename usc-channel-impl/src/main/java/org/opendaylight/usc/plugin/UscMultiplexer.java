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
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;

import org.opendaylight.usc.manager.monitor.evt.UscSessionTransactionEvent;
import org.opendaylight.usc.plugin.model.UscChannel;
import org.opendaylight.usc.plugin.model.UscSessionImpl;
import org.opendaylight.usc.protocol.UscData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class takes all the inputs from the various client sessions and multiplexes them into the combined USC channel.
 */
@Sharable
public class UscMultiplexer extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(UscMultiplexer.class);
    public static final int MAX_PAYLOAD_SIZE = 64512;// 63K
    private final UscPlugin plugin;

    /**
     * Constructs a new UscMultiplexer
     *
     * @param plugin
     *            The instance of UscPlugin on whose behalf this demultiplexer is managing session state.
     */
    public UscMultiplexer(UscPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf payload = (ByteBuf) msg;
        LOG.trace("UscMultiplexer.channelRead: " + payload);

        Channel ch = ctx.channel();
        Channel outboundChannel = ch.attr(UscPlugin.DIRECT_CHANNEL).get();
        if (outboundChannel != null) {
            if (plugin.getChannelType() == UscChannel.ChannelType.DTLS
                || plugin.getChannelType() == UscChannel.ChannelType.UDP) {
                DatagramPacket packet = new DatagramPacket(payload, (InetSocketAddress) outboundChannel.remoteAddress());
                LOG.trace("UscMultiplexer.channelRead: convert payload to DatagramPacket " + packet);
                outboundChannel.write(packet);
            } else
                outboundChannel.write(msg);
        } else {
            try {
                UscSessionImpl session = ch.attr(UscPlugin.SESSION).get().get();
                outboundChannel = session.getChannel().getChannel();

                UscData reply = null;
                ByteBuf subPayload = null;
                int length = payload.readableBytes();
                int bytesOut = length;
                int index = 0;
                int realLength = 0;
                while (length > 0) {
                    realLength = (length > MAX_PAYLOAD_SIZE) ? MAX_PAYLOAD_SIZE : length;
                    subPayload = payload.copy(index, realLength);
                    index += realLength;
                    length -= realLength;

                    reply = new UscData(session.getPort(), session.getSessionId(), subPayload);
                    LOG.trace("Send data to Java Agent " + reply);
                    outboundChannel.writeAndFlush(reply);
                }
                plugin.sendEvent(new UscSessionTransactionEvent(session, 0, bytesOut));
            } finally {
                payload.release();
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        LOG.trace("UscMultiplexer.channelReadComplete");
        Channel ch = ctx.channel();
        Channel outboundChannel = ch.attr(UscPlugin.DIRECT_CHANNEL).get();
        if (outboundChannel != null) {
            outboundChannel.flush();
        } else {
            UscSessionImpl session = ch.attr(UscPlugin.SESSION).get().get();
            outboundChannel = session.getChannel().getChannel();
            outboundChannel.flush();
        }
    }
}
