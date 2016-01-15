/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.protocol;

import java.util.Arrays;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.opendaylight.usc.protocol.UscHeader.OperationType;

/**
 * An USC Error packet.
 */
public class UscError extends UscFrame {

    /**
     * The possible error codes that can be encountered in an USC session.
     */
    public static enum ErrorCode {

        EAGAIN(11), 
        EPIPE(32), 
        EADDRINUSE(98), 
        ENETDOWN(100), 
        ENETUNREACH(101), 
        ENETRESET(102), 
        ECONNABORTED(103), 
        ECONNRESET(104), 
        EISCONN(106), 
        ENOTCONN(107), 
        ESHUTDOWN(108), 
        ETIMEDOUT(110), 
        ECONNREFUSED(111), 
        E_OTHER(0);

        private final int code;

        private ErrorCode(int code) {
            this.code = code;
        }

        public int getCode() {
            return this.code;
        }

        @Override
        public String toString() {
            return this.code + "=" + this.name();
        }
    }

    private final static int PAYLOAD_LENGTH = 2;

    private final ErrorCode errorCode;

    /**
     * Constructs a new UscError
     * 
     * @param port
     * @param sessionId
     * @param errorCode
     */
    public UscError(int port, int sessionId, int errorCode) {
        super(OperationType.ERROR, port, sessionId, PAYLOAD_LENGTH);

        this.errorCode = Arrays.stream(ErrorCode.values()).filter(e -> e.code == errorCode).findAny()
                .orElse(ErrorCode.E_OTHER);
    }

    @Override
    public ByteBuf getPayload() {
        return Unpooled.copyShort(errorCode.code);
    }

    /**
     * Returns the error code
     * 
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

}
