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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalChannel;

import org.opendaylight.usc.manager.monitor.evt.UscChannelErrorEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionErrorEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionTransactionEvent;
import org.opendaylight.usc.plugin.exception.UscChannelException;
import org.opendaylight.usc.plugin.exception.UscSessionException;
import org.opendaylight.usc.plugin.model.UscChannelImpl;
import org.opendaylight.usc.plugin.model.UscSessionImpl;
import org.opendaylight.usc.protocol.UscData;
import org.opendaylight.usc.protocol.UscError;
import org.opendaylight.usc.protocol.UscFrame;
import org.opendaylight.usc.protocol.UscHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class handles the demultiplexing of the raw USC traffic into the
 * respective client sessions.
 */
@Sharable
public class UscDemultiplexer extends SimpleChannelInboundHandler<UscFrame> {

    private static final Logger log = LoggerFactory.getLogger(UscDemultiplexer.class);

    private final UscPlugin plugin;

    /**
     * Constructs a new UscDemultiplexer
     * 
     * @param plugin
     *            The instance of UscPlugin on whose behalf this demultiplexer
     *            is managing session state.
     */
    public UscDemultiplexer(UscPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, UscFrame frame) throws Exception {
        log.trace("UscDemultiplexer.channelRead: " + frame);

        log.trace("UscDemultiplexer " + this + ".channelRead: " + frame);

        final UscHeader header = frame.getHeader();

        final int sessionId = header.getSessionId();

        final UscChannelImpl connection = ctx.channel().attr(UscPlugin.CHANNEL).get();

        final UscSessionImpl session = connection.getSession(sessionId);
        final LocalChannel serverChannel = session.getServerChannel();
        if (frame instanceof UscError) {
            // propagate exception to the client channel
            UscSessionException ex = new UscSessionException(((UscError) frame).getErrorCode());
            serverChannel.writeAndFlush(ex);

            plugin.sendEvent(new UscSessionErrorEvent(session, ex));

        } else if (frame instanceof UscData) {

            if (serverChannel != null) {
                log.trace("write session " + sessionId + " to " + serverChannel + ": " + frame.getPayload());

                ByteBuf payload = frame.getPayload();

                plugin.sendEvent(new UscSessionTransactionEvent(session, payload.readableBytes(), 0));

                serverChannel.writeAndFlush(payload);
            } else {
                UscChannelException ex = new UscChannelException("write unknown session " + sessionId + "; discard");
                plugin.sendEvent(new UscChannelErrorEvent(session.getChannel(), ex));
                throw ex;
            }
        } else {
            UscChannelException ex = new UscChannelException("unexpected UscFrame object " + frame);
            plugin.sendEvent(new UscChannelErrorEvent(session.getChannel(), ex));
            throw ex;
        }
    }

}
