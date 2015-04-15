/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.usc.crypto.dtls;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.DatagramPacket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

import org.bouncycastle.crypto.tls.DTLSTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DtlsEngine {

    private static final Logger log = LoggerFactory.getLogger(DtlsEngine.class);

    private DTLSTransport encTransport = null;
    private final DtlsHandlerTransport rawTransport;

    private final LinkedBlockingQueue<DatagramPacket> writeQueue = new LinkedBlockingQueue<>();

    public DtlsEngine(DtlsHandlerTransport rawTransport) {
        this.rawTransport = rawTransport;
    }

    public ArrayList<DatagramPacket> read(DatagramPacket msg) throws InterruptedException, ExecutionException,
            IOException {

        log.trace("DtlsEngine read " + msg);
        // add to queue irrespective of whether initialized or not;
        // this way the protocol handshake can retrieve them
        rawTransport.enqueue(msg);

        ArrayList<DatagramPacket> packets = new ArrayList<>();
        if (encTransport != null) {
            byte buf[] = new byte[encTransport.getReceiveLimit()];
            while (rawTransport.hasPackets()) {
                int bytesRead = encTransport.receive(buf, 0, buf.length, 100);
                if (bytesRead > 0) {
                    packets.add(new DatagramPacket(Unpooled.copiedBuffer(buf, 0, bytesRead), rawTransport
                            .getRemoteAddress()));
                }
            }
        }
        return packets;
    }

    private static void write(DTLSTransport encTransport, DatagramPacket packet) throws IOException {
        ByteBuf byteBuf = packet.content();
        int readableBytes = byteBuf.readableBytes();
        log.trace("DtlsEngine write " + packet);
        byte buf[] = new byte[encTransport.getSendLimit()];
        byteBuf.readBytes(buf, 0, readableBytes);
        byteBuf.release();
        encTransport.send(buf, 0, readableBytes);
    }

    public void write(DatagramPacket packet) throws IOException, InterruptedException, ExecutionException {
        if (encTransport != null) {
            write(encTransport, packet);
        } else {
            writeQueue.add(packet);
        }
    }

    public void initialize(DTLSTransport encTransport) throws InterruptedException, ExecutionException, IOException {
        // first send all queued up messages
        ArrayList<DatagramPacket> packets = new ArrayList<>();
        writeQueue.drainTo(packets);
        for (DatagramPacket packet : packets) {
            write(encTransport, packet);
        }

        // expose this to the outside world last to avoid race conditions
        this.encTransport = encTransport;
    }

    public boolean isInitialized() {
        return encTransport != null;
    }

}
