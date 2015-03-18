/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor;

import org.opendaylight.usc.manager.UscTopologyManager;
import org.opendaylight.usc.manager.api.UscEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionCloseEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionCreateEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionErrorEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionTransactionEvent;
import org.opendaylight.usc.manager.topology.UscTopologyFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.link.attributes.Session;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.session.attributes.Alarm;

/**
 * the event handler of all of session related events
 */
public class UscSessionEventHandler implements UscEventHandler {

    private UscTopologyManager topoManager;

    /**
     * create a session event handler using given topology manager
     * 
     * @param topoManager
     *            topology manager
     */
    public UscSessionEventHandler(UscTopologyManager topoManager) {
        this.topoManager = topoManager;

    }

    @Override
    public void handle(UscEvent event) {
        if (event instanceof UscSessionCreateEvent) {
            UscSessionCreateEvent evt = (UscSessionCreateEvent) event;
            Session session = UscTopologyFactory.createSession(evt.getSessionId(), evt.getPort() + "");
            topoManager.addSession(evt.getDeviceId(), session);
        } else if (event instanceof UscSessionCloseEvent) {
            UscSessionCloseEvent evt = (UscSessionCloseEvent) event;
            topoManager.removeSession(evt.getDeviceId(), evt.getSessionId());
        } else if (event instanceof UscSessionTransactionEvent) {
            UscSessionTransactionEvent evt = (UscSessionTransactionEvent) event;
            topoManager.updateSessionTransaction(evt.getDeviceId(), evt.getSessionId(), evt.getBytesIn(),
                    evt.getBytesOut());
        } else if (event instanceof UscSessionErrorEvent) {
            UscSessionErrorEvent evt = (UscSessionErrorEvent) event;
            Alarm alarm = UscTopologyFactory.createSessionAlram(evt.getErrorId(), evt.getErrorCode() + "",
                    evt.getMessage());
            topoManager.addSessionError(evt.getDeviceId(), evt.getSessionId(), alarm);
        }
    }

}
