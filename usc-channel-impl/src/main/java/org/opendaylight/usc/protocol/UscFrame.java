/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.protocol;

import java.io.IOException;

import org.opendaylight.usc.protocol.UscHeader.OperationType;

import io.netty.buffer.ByteBuf;

/**
 * Base class of a UscFrame packet.
 */
public abstract class UscFrame {

    private final UscHeader header;

    /**
     * Constructs a new UscFrame
     * 
     * @param operationType
     * @param port
     * @param sessionId
     * @param payloadLength
     */
    public UscFrame(OperationType operationType, int port, int sessionId, int payloadLength) {
        this.header = new UscHeader(UscHeader.USC_VERSION, operationType, port, sessionId, payloadLength);
    }

    /**
     * Returns the USC header
     * 
     * @return the USC header
     */
    public UscHeader getHeader() {
        return header;
    }

    /**
     * Length of the frame in bytes, inclusive of both the header and the.
     * payload
     * 
     * @return Length of the frame in bytes.
     */
    public int length() {
        return header.length() + header.getPayloadLength();
    }

    /**
     * To be implemented by subclasses to return the payload as a ByteBuf.
     * 
     * @param out
     *            the buffer to write to
     */
    public abstract ByteBuf getPayload();

    /**
     * Decodes a ByteBuf into a UscFrame
     * 
     * @param buf
     * @return
     * @throws IOException
     */
    public static UscFrame getFromByteBuf(ByteBuf buf) throws IOException {
        final UscHeader header = UscHeader.fromByteBuffer(buf.nioBuffer(0, UscHeader.HEADER_LENGTH));
        buf.readerIndex(UscHeader.HEADER_LENGTH);

        final int port = header.getApplicationPort();
        final int sessionId = header.getSessionId();

        final UscFrame result;
        switch (header.getOperationType()) {
        case DATA:
            result = new UscData(port, sessionId, buf.copy());
            break;
        case CONTROL:
            result = new UscControl(port, sessionId, buf.readUnsignedShort());
            break;
        case ERROR:
            result = new UscError(port, sessionId, buf.readUnsignedShort());
            break;
        default:
            result = null;
            throw new IOException("Invalid operation type");
        }
        return result;
    }

}
