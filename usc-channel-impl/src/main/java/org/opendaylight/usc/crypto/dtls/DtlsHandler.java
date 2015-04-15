/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.usc.crypto.dtls;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.bouncycastle.crypto.tls.DTLSTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DtlsHandler extends ChannelDuplexHandler {

    private static final Logger log = LoggerFactory.getLogger(DtlsHandler.class);

    public static class ChannelContext {
        public final ChannelHandlerContext ctx;
        public final ChannelPromise promise;

        public ChannelContext(ChannelHandlerContext ctx, ChannelPromise promise) {
            super();
            this.ctx = ctx;
            this.promise = promise;
        }
    }

    private final LinkedBlockingQueue<ChannelContext> writeCtxQueue = new LinkedBlockingQueue<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    protected final DtlsHandlerTransport rawTransport = new DtlsHandlerTransport();
    private final DtlsEngine engine = new DtlsEngine(rawTransport);

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        rawTransport.setChannel(ctx.channel());

        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    log.trace(getName() + " init start ");

                    final DTLSTransport encTransport = getDtlsTransport();
                    engine.initialize(encTransport);
                    log.trace(getName() + " init end ");
                } catch (IOException | InterruptedException | ExecutionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
        if (obj instanceof DatagramPacket) {
            DatagramPacket msg = (DatagramPacket) obj;

            log.trace(getName() + " channelRead ");

            // send packet to underlying transport for consumption
            ArrayList<DatagramPacket> packets = engine.read(msg);
            for (DatagramPacket packet : packets) {
                super.channelRead(ctx, packet);
            }

        } else {
            super.channelRead(ctx, obj);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object obj, ChannelPromise promise) throws Exception {
        if (obj instanceof DatagramPacket) {
            // this is the unencryped data written by the app
            DatagramPacket msg = (DatagramPacket) obj;

            log.trace(getName() + " write " + msg);

            // flush the queue when channel initialized
            if (engine.isInitialized()) {
                // assume messages are one-to-one between raw and encrypted
                writeCtxQueue.add(new ChannelContext(ctx, promise));
            }
            engine.write(msg);
        } else if (obj instanceof DtlsPacket) {
            // used to passthrough the data for handshake packets

            // this is the underlying traffic written by this handler
            DtlsPacket msg = (DtlsPacket) obj;

            ChannelContext context = writeCtxQueue.poll();
            if (context != null) {
                super.write(context.ctx, msg.packet, context.promise);
            } else {
                super.write(ctx, msg.packet, promise);
            }
        } else {
            super.write(ctx, obj, promise);
        }
    }

    protected String getName() {
        return this.getClass().toString();
    }

    protected abstract DTLSTransport getDtlsTransport() throws IOException;

}
