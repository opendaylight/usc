/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin;

import org.opendaylight.usc.manager.UscRouteBrokerService;
import org.opendaylight.usc.manager.cluster.UscChannelIdentifier;
import org.opendaylight.usc.manager.cluster.UscRouteIdentifier;
import org.opendaylight.usc.manager.cluster.message.UscRemoteChannelEventMessage;
import org.opendaylight.usc.plugin.exception.UscSessionException;
import org.opendaylight.usc.plugin.model.UscChannelImpl;
import org.opendaylight.usc.protocol.UscError;
import org.opendaylight.usc.protocol.UscFrame;
import org.opendaylight.usc.protocol.UscHeader;
import org.opendaylight.usc.util.UscServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;

/**
 * This class handles the device response for remote server,this handler like a
 * remote dummy server
 */
@Sharable
public class UscRemoteServerHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(UscRemoteServerHandler.class);
    private UscRouteBrokerService broker;

    public UscRemoteServerHandler() {

    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object data) throws Exception {
        UscRouteIdentifier localRouteId = null;

        // get local route identifier
        if (data instanceof UscFrame) {
            LOG.trace("Read data from Usc Agent: " + data);
            // communicate with agent
            final UscHeader header = ((UscFrame) data).getHeader();
            final UscChannelImpl connection = ctx.channel().attr(UscPlugin.CHANNEL).get();
            // for remote session
            localRouteId = new UscRouteIdentifier(connection.getDevice().getInetAddress(), connection.getType(),
                    header.getSessionId(), header.getApplicationPort());
        } else {
            LOG.trace("Read data from None Usc Agent: " + data);
            // communicate directly with device
            localRouteId = ctx.channel().attr(UscPlugin.RUOTE_IDENTIFIER).get();
        }

        if (broker == null) {
            broker = UscServiceUtils.getService(UscRouteBrokerService.class);
        }
        if (broker == null) {
            LOG.error("Broker service is null!Can't check if it is response from remote channel.Route id is "
                    + localRouteId);
        } else if (broker.isRemoteSession(localRouteId)) {
            byte[] payload = null;
            // get content, after reading the readable data become zero,can't
            // use again
            if (data instanceof UscFrame) {
                payload = getPayloadFromByteBuf(((UscFrame) data).getPayload());
            } else if (data instanceof DatagramPacket) {
                payload = getPayloadFromByteBuf(((DatagramPacket) data).content());
            } else {
                payload = getPayloadFromByteBuf((ByteBuf) data);
            }
            if (data instanceof UscError) {
                // propagate exception to the client channel
                UscSessionException ex = new UscSessionException(((UscError) data).getErrorCode());
                // send error message back to remote request controller
                broker.sendException(localRouteId, ex);
            } else {
                broker.sendResponse(localRouteId, payload);
            }
            LOG.trace("It is response from local remote channel.Sending message to route id (" + localRouteId
                    + ").messsage is " + new String(payload));
            return;
        }

        ReferenceCountUtil.retain(data);
        // propagate the data to rest of handlers in pipeline
        ctx.fireChannelRead(data);
    }

    private byte[] getPayloadFromByteBuf(ByteBuf buf) {
        int length = buf.readableBytes();
        byte[] ret = new byte[length];
        for (int i = 0; i < length; i++) {
            ret[i] = buf.readByte();
        }
        return ret;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOG.trace("UscRemoteServerHandler channelInactive()");
        if (broker == null) {
            broker = UscServiceUtils.getService(UscRouteBrokerService.class);
        }
        if (broker == null) {
            LOG.warn("Broker service is null!Can't broadcast the channel close event to all other remote controller in cluster.");
        } else {
            UscChannelIdentifier remoteChannel = null;
            final UscChannelImpl connection = ctx.channel().attr(UscPlugin.CHANNEL).get();
            if (connection != null) {
                remoteChannel = new UscChannelIdentifier(connection.getDevice().getInetAddress(),
                        connection.getType());
            } else {
                // communicate directly with device
                UscRouteIdentifier localRouteId = ctx.channel().attr(UscPlugin.RUOTE_IDENTIFIER).get();
                if (localRouteId != null) {
                    remoteChannel = localRouteId;
                }
            }
            if (remoteChannel != null) {
                UscRemoteChannelEventMessage message = new UscRemoteChannelEventMessage(remoteChannel,
                        UscRemoteChannelEventMessage.ChannelEventType.CLOSE);
                broker.broadcastMessage(message);
            }
        }
        ctx.fireChannelInactive();
    }
}
