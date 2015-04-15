/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor.evt.base;

/**
 * data transaction event
 */
public class UscTransactionEvent extends UscMonitorEvent {

    private long bytesIn;
    private long bytesOut;

    /**
     * create a data transaction event
     * 
     * @param deviceId
     *            device id which can identify a channel error happens
     * @param bytesIn
     *            bytes in number
     * @param bytesOut
     *            bytes out number
     */
    public UscTransactionEvent(String deviceId, long bytesIn, long bytesOut) {
        super(deviceId);
        this.bytesIn = bytesIn;
        this.bytesOut = bytesOut;
    }

    /**
     * get bytes in number
     * 
     * @return bytes in number
     */
    public long getBytesIn() {
        return bytesIn;
    }

    /**
     * get bytes out number
     * 
     * @return bytes out number
     */
    public long getBytesOut() {
        return bytesOut;
    }

}
