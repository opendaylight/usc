/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor.evt.base;

/**
 * session event
 */
public class UscSessionEvent extends UscMonitorEvent {

    private String sessionId;

    /**
     * create a session event
     * 
     * @param type
     *            channel type
     * @param deviceId
     *            device id which can identify a channel which contains this
     *            session
     * @param sessionId
     *            session id
     */
    public UscSessionEvent(String deviceId, String type, String sessionId) {
        super(deviceId, type);
        this.sessionId = sessionId;
    }

    /**
     * get session id
     * 
     * @return session id
     */
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String toString() {
        return super.toString() + ",session Id is " + sessionId;
    }
}
