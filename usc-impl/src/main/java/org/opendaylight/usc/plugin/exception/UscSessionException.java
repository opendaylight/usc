/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin.exception;

import org.opendaylight.usc.protocol.UscError.ErrorCode;

/**
 * An exception that occurred in a USC session.
 */
public class UscSessionException extends UscException {

    private static final long serialVersionUID = 1L;
    private final ErrorCode errorCode;

    /**
     * Constructs a new UscSessionException
     * 
     * @param errorCode
     *            the error code that has occurred
     */
    public UscSessionException(ErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    /**
     * Returns the associated error code.
     * 
     * @return error code
     */
    public ErrorCode getErrorCode() {
        return this.errorCode;
    }

    @Override
    public String toString() {
        return super.toString() + "(" + this.errorCode + ")";
    }
}
