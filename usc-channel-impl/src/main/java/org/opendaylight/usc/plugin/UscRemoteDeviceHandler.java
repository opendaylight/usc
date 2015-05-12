/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin;

import org.opendaylight.usc.manager.UscRouteBrokerService;
import org.opendaylight.usc.manager.cluster.UscRouteIdentifier;
import org.opendaylight.usc.manager.cluster.message.UscRemoteDataMessage;
import org.opendaylight.usc.util.UscServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.util.ReferenceCountUtil;

/**
 * This handler only for remote device, it will be added into the localServer
 * pipeline.this handler like a remote dummy device to process all of
 * communication with the remote device
 */
@Sharable
public class UscRemoteDeviceHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(UscRemoteDeviceHandler.class);

    private UscRouteBrokerService broker;
    private ByteBuf buffer = Unpooled.buffer(10000);
    private UscRouteIdentifier routeId;

    public UscRemoteDeviceHandler() {
    }

    private boolean isRemote(ChannelHandlerContext ctx) {
        routeId = ctx.channel().attr(UscPlugin.RUOTE_IDENTIFIER).get();
        if (routeId != null) {
            LOG.trace("UscRemoteDeviceHandler:Channel read finished for route id(" + routeId + ")");
            if (broker == null) {
                broker = UscServiceUtils.getService(UscRouteBrokerService.class);
            }
            if (broker != null) {
                if (broker.isLocalRemoteSession(routeId)) {
                    return true;
                } else {
                    LOG.debug("It's not local to remote channel(" + routeId + ") message, pass it to other handler");
                }
            } else {
                LOG.error("Broker service is null! Can't check if it is remote channel message, and pass it to other handler.");
            }
        }
        return false;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (isRemote(ctx)) {
            ByteBuf payload = (ByteBuf) msg;
            byte[] data = getPayloadFromByteBuf(payload);
            writeBuffer(data);
            return;
        }
        ReferenceCountUtil.retain(msg);
        // propagate the data to rest of handlers in pipeline
        ctx.fireChannelRead(msg);
    }

    private byte[] getPayloadFromByteBuf(ByteBuf buf) {
        int length = buf.readableBytes();
        byte[] ret = new byte[length];
        for (int i = 0; i < length; i++) {
            ret[i] = buf.readByte();
        }
        return ret;
    }

    private void writeBuffer(byte[] data) {
        int newSize = 0;
        int oldSize = buffer.capacity();
        int length = data.length;
        if (buffer.writableBytes() <= length) {
            if (length > oldSize) {
                newSize = length * 2;
            } else {
                newSize = 2 * oldSize;
            }
            buffer.capacity(newSize);
        }
        buffer.writeBytes(data);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (isRemote(ctx)) {
            broker.removeLocalSession(routeId);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (isRemote(ctx)) {
            byte[] data = getPayloadFromByteBuf(buffer);
            LOG.trace("Read complete,send message to remote channel: " + routeId + ",message is " + new String(data));
            broker.sendRequest(new UscRemoteDataMessage(routeId, data, true));
            buffer.clear();
            return;
        }
        // propagate the data to rest of handlers in pipeline
        ctx.fireChannelReadComplete();
    }
}
