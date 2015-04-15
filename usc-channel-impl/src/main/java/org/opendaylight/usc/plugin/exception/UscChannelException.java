/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin.exception;

/**
 * An exception that occurred on the physical USC channel with an agent.
 */
public class UscChannelException extends UscException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new UscChannelException
     * 
     * @param msg
     *            the error message
     */
    public UscChannelException(String msg) {
        super(msg);
    }

}
