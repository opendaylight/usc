/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test.plugin;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opendaylight.usc.protocol.UscHeader;
import org.opendaylight.usc.protocol.UscHeader.OperationType;
import org.opendaylight.usc.test.AbstractTest;

/**
 * Test suite for USC headers.
 */
public class UscHeaderTest extends AbstractTest {
    @Test
    public void testGetUscVersion() {
        byte[] bytes = new byte[16];

        bytes[0] = 0;
        assertEquals(0, UscHeader.getFromBytes(bytes).getUscVersion());

        bytes[0] = 1;
        assertEquals(1, UscHeader.getFromBytes(bytes).getUscVersion());

        bytes[0] = 2;
        assertEquals(2, UscHeader.getFromBytes(bytes).getUscVersion());

        bytes[0] = 1 << 4;
        assertEquals(0, UscHeader.getFromBytes(bytes).getUscVersion());

        bytes[0] = (1 << 4) + 1;
        assertEquals(1, UscHeader.getFromBytes(bytes).getUscVersion());

        bytes[0] = 3 << 4;
        assertEquals(0, UscHeader.getFromBytes(bytes).getUscVersion());
    }

    @Test
    public void testMessageType() {
        byte[] bytes = new byte[16];

        bytes[0] = 0;
        assertEquals(null, UscHeader.getFromBytes(bytes).getOperationType());

        bytes[0] = 1;
        assertEquals(null, UscHeader.getFromBytes(bytes).getOperationType());

        bytes[0] = 1 << 5;
        assertEquals(OperationType.CONTROL, UscHeader.getFromBytes(bytes).getOperationType());
    }

    @Test
    public void testGetApplicationPort() {
        byte[] bytes = new byte[16];

        bytes[2] = 0;
        bytes[3] = 0;
        assertEquals(0, UscHeader.getFromBytes(bytes).getApplicationPort());

        bytes[2] = 1;
        bytes[3] = 0;
        assertEquals(1 << 8, UscHeader.getFromBytes(bytes).getApplicationPort());

        bytes[2] = 0;
        bytes[3] = 1;
        assertEquals(1, UscHeader.getFromBytes(bytes).getApplicationPort());
    }

    @Test
    public void testGetSessionId() {
        byte[] bytes = new byte[16];

        bytes[4] = 0;
        bytes[5] = 0;
        assertEquals(0, UscHeader.getFromBytes(bytes).getSessionId());

        bytes[4] = 1;
        bytes[5] = 0;
        assertEquals(1 << 8, UscHeader.getFromBytes(bytes).getSessionId());

        bytes[4] = 0;
        bytes[5] = 1;
        assertEquals(1, UscHeader.getFromBytes(bytes).getSessionId());
    }

    @Test
    public void testPayloadLength() {
        byte[] bytes = new byte[16];

        bytes[6] = 0;
        bytes[7] = 0;
        assertEquals(0, UscHeader.getFromBytes(bytes).getPayloadLength());

        bytes[6] = 1;
        bytes[7] = 0;
        assertEquals(1 << 8, UscHeader.getFromBytes(bytes).getPayloadLength());

        bytes[6] = 0;
        bytes[7] = 1;
        assertEquals(1, UscHeader.getFromBytes(bytes).getPayloadLength());
    }

    @Test
    public void testSetBit() {
        assertEquals(0b0000, UscHeader.setBit((byte) 0, 0, false));
        assertEquals(0b0001, UscHeader.setBit((byte) 0, 0, true));
        assertEquals(0b0000, UscHeader.setBit((byte) 0, 1, false));
        assertEquals(0b0010, UscHeader.setBit((byte) 0, 1, true));
        assertEquals(0b1110, UscHeader.setBit((byte) 0b1111, 0, false));
        assertEquals(0b1111, UscHeader.setBit((byte) 0b1111, 0, true));
        assertEquals(0b1101, UscHeader.setBit((byte) 0b1111, 1, false));
        assertEquals(0b1111, UscHeader.setBit((byte) 0b1111, 1, true));

    }

    @Test
    public void testSetBitsAsInteger() {

        assertEquals(0b0000, UscHeader.setBitsAsInteger((byte) 0, 0, 1, 0b00));
        assertEquals(0b0001, UscHeader.setBitsAsInteger((byte) 0, 0, 1, 0b01));
        assertEquals(0b0000, UscHeader.setBitsAsInteger((byte) 0, 0, 1, 0b10));
        assertEquals(0b0001, UscHeader.setBitsAsInteger((byte) 0, 0, 1, 0b11));

        assertEquals(0b1110, UscHeader.setBitsAsInteger((byte) 0b1111, 0, 1, 0b00));
        assertEquals(0b1111, UscHeader.setBitsAsInteger((byte) 0b1111, 0, 1, 0b01));
        assertEquals(0b1110, UscHeader.setBitsAsInteger((byte) 0b1111, 0, 1, 0b10));
        assertEquals(0b1111, UscHeader.setBitsAsInteger((byte) 0b1111, 0, 1, 0b11));

        assertEquals(0b0000, UscHeader.setBitsAsInteger((byte) 0, 0, 2, 0b00));
        assertEquals(0b0001, UscHeader.setBitsAsInteger((byte) 0, 0, 2, 0b01));
        assertEquals(0b0010, UscHeader.setBitsAsInteger((byte) 0, 0, 2, 0b10));
        assertEquals(0b0011, UscHeader.setBitsAsInteger((byte) 0, 0, 2, 0b11));

        assertEquals(0b0000, UscHeader.setBitsAsInteger((byte) 0, 1, 1, 0b00));
        assertEquals(0b0010, UscHeader.setBitsAsInteger((byte) 0, 1, 1, 0b01));
        assertEquals(0b0000, UscHeader.setBitsAsInteger((byte) 0, 1, 1, 0b10));
        assertEquals(0b0010, UscHeader.setBitsAsInteger((byte) 0, 1, 1, 0b11));

        assertEquals(0b0000, UscHeader.setBitsAsInteger((byte) 0, 1, 2, 0b00));
        assertEquals(0b0010, UscHeader.setBitsAsInteger((byte) 0, 1, 2, 0b01));
        assertEquals(0b0100, UscHeader.setBitsAsInteger((byte) 0, 1, 2, 0b10));
        assertEquals(0b0110, UscHeader.setBitsAsInteger((byte) 0, 1, 2, 0b11));

        assertEquals(0b1101, UscHeader.setBitsAsInteger((byte) 0b1111, 1, 1, 0b00));
        assertEquals(0b1111, UscHeader.setBitsAsInteger((byte) 0b1111, 1, 1, 0b01));
        assertEquals(0b1101, UscHeader.setBitsAsInteger((byte) 0b1111, 1, 1, 0b10));
        assertEquals(0b1111, UscHeader.setBitsAsInteger((byte) 0b1111, 1, 1, 0b11));

        assertEquals(0b1001, UscHeader.setBitsAsInteger((byte) 0b1111, 1, 2, 0b00));
        assertEquals(0b1011, UscHeader.setBitsAsInteger((byte) 0b1111, 1, 2, 0b01));
        assertEquals(0b1101, UscHeader.setBitsAsInteger((byte) 0b1111, 1, 2, 0b10));
        assertEquals(0b1111, UscHeader.setBitsAsInteger((byte) 0b1111, 1, 2, 0b11));

    }

    @Test
    public void testToBytes() {
        UscHeader header = new UscHeader(2, OperationType.DATA, 0x0304, 0x0506, 0x0708);
        byte[] bytes = new byte[8];
        header.toByteBuffer().get(bytes);

        assertArrayEquals(new byte[] { 0x12, 0x0, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8 }, bytes);
    }

    @Test
    public void testFromBytes() {
        byte[] bytes = new byte[] { 0x12, 0x0, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8 };
        UscHeader header = UscHeader.getFromBytes(bytes);

        assertEquals(2, header.getUscVersion());
        assertEquals(OperationType.DATA, header.getOperationType());
        assertEquals(0x0304, header.getApplicationPort());
        assertEquals(0x0506, header.getSessionId());
        assertEquals(0x0708, header.getPayloadLength());
    }
}
