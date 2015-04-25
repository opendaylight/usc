/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The USC packet header.
 */
public class UscHeader {

    /**
     * Length of the header by bytes.
     */
    public static final int HEADER_LENGTH = 8;

    /**
     * Offset to the payload length field in bytes.
     */
    public static final int PAYLOAD_LENGTH_OFFSET = 6;

    /**
     * The length of the payload length field in bytes.
     */
    public static final int PAYLOAD_LENGTH_SIZE = 2;

    /**
     * USC protocol version number.
     */
    public static final int USC_VERSION = 1;

    /**
     * Types of USC packets.
     */
    public enum OperationType {
        DATA(1),
        CONTROL(2),
        ERROR(3);
        private final int value;

        private OperationType(int value) {
            this.value = value;
        }

        public static OperationType valueOf(int v) {
            for (OperationType value : values()) {
                if (value.value == v) {
                    return value;
                }
            }
            return null;
        }
    }

    private final int uscVersion;
    private final OperationType operationType;
    private final int applicationPort;
    private final int sessionId;
    private final int payloadLength;

    /**
     * Initializes the USC header.
     * 
     * @param uscVersion
     *            USC version number
     * @param operationType
     *            USC packet type
     * @param applicationPort
     *            the port number of the service on the device
     * @param sessionId
     *            the session ID
     * @param payloadLength
     *            the length of the payload in bytes
     */
    public UscHeader(int uscVersion, OperationType operationType, int applicationPort, int sessionId, int payloadLength) {
        super();
        this.uscVersion = uscVersion;
        this.operationType = operationType;
        this.applicationPort = applicationPort;
        this.sessionId = sessionId;
        this.payloadLength = payloadLength;
    }

    /**
     * Set or unset a specific bit in a byte value
     * 
     * @param value
     * @param offset
     * @param target
     * @return the resulting byte value
     */
    public static byte setBit(byte value, int offset, boolean target) {
        if (target) {
            return (byte) (value | (1 << offset));
        } else {
            return (byte) (value & ~(1 << offset));
        }
    }

    /**
     * Set or unset a range of bits in a byte value
     * 
     * @param value
     * @param offset
     * @param size
     * @param target
     * @return the resulting byte value
     */
    public static byte setBitsAsInteger(byte value, int offset, int size, int target) {
        int shiftedTarget = (target & ((1 << size) - 1)) << offset;
        int mask = ~(((1 << size) - 1) << offset);
        return (byte) (value & mask | shiftedTarget);
    }

    /**
     * Checks whether a bit is set in a byte value
     * 
     * @param value
     * @param offset
     * @return
     */
    public static boolean isBitSet(byte value, int offset) {
        return ((value >>> offset) & 1) != 0;
    }

    /**
     * Returns a range of bits in a byte value as an integer
     * 
     * @param value
     * @param offset
     * @param size
     * @return the value of the bits
     */
    public static int getBitsAsInteger(byte value, int offset, int size) {
        return (value >>> offset) & ((1 << size) - 1);
    }

    /**
     * Constructs an USC header from a byte stream
     * 
     * @param buf
     * @return the USC header
     */
    public static UscHeader fromByteBuffer(ByteBuffer buf) {
        byte byte0 = buf.get(0);
        // byte 0, bits [0, 4)
        final int uscVersion = getBitsAsInteger(byte0, 0, 4);

        // byte 0, bits [4, 8)
        final OperationType operationType = OperationType.valueOf(getBitsAsInteger(byte0, 4, 4));

        // byte 1 reserved

        // bytes [2, 4)
        final int applicationPort = buf.getChar(2);

        // bytes [4, 6)
        final int sessionId = buf.getChar(4);

        // bytes [6, 8)
        final int payloadLength = buf.getChar(PAYLOAD_LENGTH_OFFSET);

        return new UscHeader(uscVersion, operationType, applicationPort, sessionId, payloadLength);
    }

    /**
     * Constructs an USC header from a byte stream
     * 
     * @param bytes buffer
     * @return the USC header
     */
    public static UscHeader getFromBytes(final byte[] bytes) {
        final ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        return fromByteBuffer(buf);
    }

    /**
     * Returns the USC version
     * 
     * @return the USC version
     */
    public int getUscVersion() {
        return uscVersion;
    }

    /**
     * Returns the USC packet type
     * 
     * @return the USC packet type
     */
    public OperationType getOperationType() {
        return operationType;
    }

    /**
     * Returns the port number of the service on the device
     * 
     * @return the port number of the service on the device
     */
    public int getApplicationPort() {
        return applicationPort;
    }

    /**
     * Returns the session ID
     * 
     * @return the session ID
     */
    public int getSessionId() {
        return sessionId;
    }

    /**
     * Returns the the payload length in bytes
     * 
     * @return the payload length in bytes
     */
    public int getPayloadLength() {
        return payloadLength;
    }

    /**
     * Returns the length of the USC header in bytes
     * 
     * @return the length of the USC header in bytes
     */
    public int length() {
        return HEADER_LENGTH;
    }

    /**
     * Constructs a byte stream representation of this USC header
     * 
     * @return the resulting byte stream
     */
    public ByteBuffer toByteBuffer() {

        byte byte0 = 0;
        byte0 = setBitsAsInteger(byte0, 0, 4, uscVersion);
        if (operationType != null) {
            byte0 = setBitsAsInteger(byte0, 4, 4, operationType.value);
        }

        ByteBuffer buf = ByteBuffer.allocate(HEADER_LENGTH).order(ByteOrder.BIG_ENDIAN);
        buf.put(0, byte0);
        buf.putChar(2, (char) applicationPort);
        buf.putChar(4, (char) sessionId);
        buf.putChar(PAYLOAD_LENGTH_OFFSET, (char) payloadLength);

        return buf;
    }

}
