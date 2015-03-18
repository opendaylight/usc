/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor.evt;

import org.opendaylight.usc.manager.monitor.evt.base.UscErrorEvent;
import org.opendaylight.usc.manager.monitor.evt.base.UscErrorLevel;
import org.opendaylight.usc.plugin.exception.UscSessionException;
import org.opendaylight.usc.plugin.model.UscSession;

/**
 * session error event
 */
public class UscSessionErrorEvent extends UscErrorEvent {

    private String sessionId;

    /**
     * create a channel error event
     * 
     * @param deviceId
     *            device id which can identify a particular channel
     * @param sessionId
     *            session id which can identify a session of a channel
     * @param errorCode
     *            error code
     * @param level
     *            error level
     * @param message
     *            error message
     */
    public UscSessionErrorEvent(String deviceId, String sessionId, int errorCode, UscErrorLevel level, String message) {
        super(deviceId, errorCode, level, message);
        this.sessionId = sessionId;
    }

    /**
     * create a session error event
     * 
     * @param session
     *            the session which error happens
     * @param ex
     *            the exception class of error
     */
    public UscSessionErrorEvent(UscSession session, UscSessionException ex) {
        this(session.getChannel().getDevice().toString(), Integer.toString(session.getSessionId()), ex.getErrorCode()
                .getCode(), UscErrorLevel.ERROR, ex.getErrorCode().name());
    }

    /**
     * get session id of the error
     * 
     * @return get session id of the error
     */
    public String getSessionId() {
        return sessionId;
    }

}
