/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager;

import java.util.Hashtable;

import org.opendaylight.usc.manager.api.UscEvent;
import org.opendaylight.usc.manager.monitor.UscChannelEventHandler;
import org.opendaylight.usc.manager.monitor.UscEventHandler;
import org.opendaylight.usc.manager.monitor.UscSessionEventHandler;
import org.opendaylight.usc.manager.monitor.evt.UscChannelCloseEvent;
import org.opendaylight.usc.manager.monitor.evt.UscChannelCreateEvent;
import org.opendaylight.usc.manager.monitor.evt.UscChannelErrorEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionCloseEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionCreateEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionErrorEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionTransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitor Manager of USC
 */
public class UscMonitorService {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(UscMonitorService.class);
    private static UscMonitorService monitorManager = new UscMonitorService();
    private Hashtable<Class<? extends UscEvent>, UscEventHandler> eventHandlerList;

    private UscMonitorService() {
        initEventHandlerList();
    }

    /**
     * get unique monitor manager of USC
     * 
     * @return monitor manager
     */
    public static UscMonitorService getInstance() {
        return monitorManager;
    }

    private void initEventHandlerList() {
        eventHandlerList = new Hashtable<Class<? extends UscEvent>, UscEventHandler>();
        UscChannelEventHandler cHandler = new UscChannelEventHandler();
        UscSessionEventHandler sHandler = new UscSessionEventHandler();
        eventHandlerList.put(UscChannelCreateEvent.class, cHandler);
        eventHandlerList.put(UscChannelCloseEvent.class, cHandler);
        eventHandlerList.put(UscChannelErrorEvent.class, cHandler);
        eventHandlerList.put(UscSessionCreateEvent.class, sHandler);
        eventHandlerList.put(UscSessionCloseEvent.class, sHandler);
        eventHandlerList.put(UscSessionTransactionEvent.class, sHandler);
        eventHandlerList.put(UscSessionErrorEvent.class, sHandler);
    }

    /**
     * register event handler for an event type
     * 
     * @param eventType
     *            the type of event
     * @param handler
     *            the event handler
     */
    public void registerEventHandler(Class<? extends UscEvent> eventType, UscEventHandler handler) {
        if (eventType != null && handler != null)
            eventHandlerList.put(eventType, handler);
    }

    /**
     * get all of registered event handlers
     * 
     * @return event handler list
     */
    public Hashtable<Class<? extends UscEvent>, UscEventHandler> getEventHandlerList() {
        return eventHandlerList;
    }

}
