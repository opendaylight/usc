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

import org.opendaylight.usc.manager.monitor.evt.UscSessionTransactionEvent;
import org.opendaylight.usc.plugin.model.UscSessionImpl;
import org.opendaylight.usc.protocol.UscControl;
import org.opendaylight.usc.protocol.UscData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class takes all the inputs from the various client sessions and multiplexes them into the combined USC channel.
 */
@Sharable
public class UscMultiplexer extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(UscMultiplexer.class);

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
        
        LOG.trace("UscMultiplexer.channelRead: localServerChannel = " + ch);
        LOG.trace("UscMultiplexer.channelRead: localServerChannel.pipeline = " + ch.pipeline());
        
        Channel outboundChannel = ch.attr(UscPlugin.DIRECT_CHANNEL).get();
        if(outboundChannel != null) {
        	outboundChannel.write(msg);
        }
        else {
        	UscSessionImpl session = ch.attr(UscPlugin.SESSION).get().get();
        	outboundChannel = session.getChannel().getChannel();

        	UscData data = new UscData(session.getPort(), session.getSessionId(), payload);

        	plugin.sendEvent(new UscSessionTransactionEvent(session, 0, payload.readableBytes()));
        
        	outboundChannel.write(data);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();
        UscSessionImpl session = ch.attr(UscPlugin.SESSION).get().get();
        Channel outboundChannel = session.getChannel().getChannel();
        outboundChannel.flush();
    }

}
