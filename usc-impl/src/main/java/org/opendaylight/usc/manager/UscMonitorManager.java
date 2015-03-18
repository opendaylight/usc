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
import org.opendaylight.usc.manager.api.UscMonitorListener;
import org.opendaylight.usc.manager.monitor.UscChannelEventHandler;
import org.opendaylight.usc.manager.monitor.UscChannelListener;
import org.opendaylight.usc.manager.monitor.UscEventHandler;
import org.opendaylight.usc.manager.monitor.UscSessionEventHandler;
import org.opendaylight.usc.manager.monitor.evt.UscChannelCloseEvent;
import org.opendaylight.usc.manager.monitor.evt.UscChannelCreateEvent;
import org.opendaylight.usc.manager.monitor.evt.UscChannelErrorEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionCloseEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionCreateEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionErrorEvent;
import org.opendaylight.usc.manager.monitor.evt.UscSessionTransactionEvent;

/**
 * Monitor Manager of USC
 */
public class UscMonitorManager {

    private static UscMonitorManager monitorManager = new UscMonitorManager();

    private UscManager manager;
    private UscMonitorListener channelListener;
    private Hashtable<Class<? extends UscEvent>, UscEventHandler> eventHandlerList;
    
    private UscMonitorManager() {

    }

    /**
     * get monitor listener
     * 
     * @return monitor listener
     */
    public UscMonitorListener getChannelListener() {
        return channelListener;
    }

    /**
     * get unique monitor manager of USC
     * 
     * @return monitor manager
     */
    public static UscMonitorManager getInstance() {
        return monitorManager;
    }

    /**
     * init monitor manager using UscManager
     * 
     * @param manager
     *            UscManager instance
     */
    public void init(UscManager manager) {
        this.manager = manager;
        channelListener = new UscChannelListener(this);
        initEventReceiverList();
    }

    private void initEventReceiverList() {
        eventHandlerList = new Hashtable<Class<? extends UscEvent>, UscEventHandler>();
        UscChannelEventHandler cHandler = new UscChannelEventHandler(manager.getUscTopologyManager());
        UscSessionEventHandler sHandler = new UscSessionEventHandler(manager.getUscTopologyManager());
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
