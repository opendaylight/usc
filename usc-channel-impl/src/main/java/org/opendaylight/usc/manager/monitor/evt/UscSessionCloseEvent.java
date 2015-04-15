/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor.evt;

import org.opendaylight.usc.manager.monitor.evt.base.UscSessionEvent;
import org.opendaylight.usc.plugin.model.UscSession;

/**
 * session close event
 */
public class UscSessionCloseEvent extends UscSessionEvent {

    /**
     * create session close event
     * 
     * @param deviceId
     *            device id which identify a channel which contains the session
     * @param sessionId
     *            session id
     */
    public UscSessionCloseEvent(String deviceId, String sessionId) {
        super(deviceId, sessionId);
    }

    /**
     * create session close event
     * 
     * @param session
     *            session closed
     */
    public UscSessionCloseEvent(UscSession session) {
        this(session.getChannel().getDevice().toString(), Integer.toString(session.getSessionId()));

    }

}
