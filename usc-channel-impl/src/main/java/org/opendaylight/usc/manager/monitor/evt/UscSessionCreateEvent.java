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

/*
 * create session create event
 */
public class UscSessionCreateEvent extends UscSessionEvent {

    private int port;

    /**
     * create session create event
     * 
     * @param deviceId
     *            device id which identify a channel which contains the session
     * @param sessionId
     *            session id
     * @param port
     *            terminal point port,like the port of the lister port of the
     *            apps of agent sides
     */
    public UscSessionCreateEvent(String deviceId, String sessionId, int port) {
        super(deviceId, sessionId);
        this.port = port;
    }

    /**
     * create session create event
     * 
     * @param session
     *            session created
     */
    public UscSessionCreateEvent(UscSession session) {
        this(session.getChannel().getDevice().toString(), Integer.toString(session.getSessionId()), session.getPort());
    }

    /**
     * get the port which session related
     * 
     * @return port which session related
     */
    public int getPort() {
        return port;
    }

}
