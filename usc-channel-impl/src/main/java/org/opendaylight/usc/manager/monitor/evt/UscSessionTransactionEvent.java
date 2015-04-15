/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor.evt;

import org.opendaylight.usc.manager.monitor.evt.base.UscTransactionEvent;
import org.opendaylight.usc.plugin.model.UscSession;

/**
 * the session transaction event for in and out bytes the channel transaction
 * data will calculate through all of sessions of it
 */
public class UscSessionTransactionEvent extends UscTransactionEvent {

    private String sessionId;

    /**
     * create session transaction event
     * 
     * @param deviceId
     *            device id which can identify the channel which contains the
     *            session
     * @param sessionId
     *            session id which can identify a session
     * @param bytesIn
     *            bytes in number
     * @param bytesOut
     *            bytes out number
     */
    public UscSessionTransactionEvent(String deviceId, String sessionId,
            long bytesIn, long bytesOut) {
        super(deviceId, bytesIn, bytesOut);
        this.sessionId = sessionId;
    }

    /**
     * create session transaction event
     * 
     * @param session
     *            the session which transaction happens
     * @param bytesIn
     *            bytes in number
     * @param bytesOut
     *            bytes out number
     */
    public UscSessionTransactionEvent(UscSession session, long bytesIn,
            long bytesOut) {
        this(session.getChannel().getDevice().toString(), session
                .getSessionId() + "", bytesIn, bytesOut);
    }

    /**
     * get the id of session which transaction happens
     * 
     * @return session id
     */
    public String getSessionId() {
        return sessionId;
    }

}
