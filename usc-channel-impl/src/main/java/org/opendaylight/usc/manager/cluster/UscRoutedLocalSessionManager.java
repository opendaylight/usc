/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.cluster;

import io.netty.channel.local.LocalChannel;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map all of request sessions which are created from local to remote channel
 */
public class UscRoutedLocalSessionManager {

    private ConcurrentHashMap<UscRouteIdentifier, LocalChannel> sessionMap = new ConcurrentHashMap<UscRouteIdentifier, LocalChannel>();

    /**
     * check if it is a route identifier of remote communication
     * 
     * @param localRouteId
     * @return
     */
    public boolean isRemoteMessage(UscRouteIdentifier localRouteId) {
        return sessionMap.containsKey(localRouteId);
    }

    /**
     * add a entry of local route identifier and server local channel for
     * sending response to request caller like Netconf plug-in
     * 
     * @param localRouteId
     * @param serverChannel
     */
    public void addEntry(UscRouteIdentifier localRouteId, LocalChannel serverChannel) {
        if (!sessionMap.containsKey(localRouteId)) {
            sessionMap.put(localRouteId, serverChannel);
        }
    }

    /**
     * remove a entry of local route identifier for the session closed
     * 
     * @param localRouteId
     */
    public void removeEntry(UscRouteIdentifier localRouteId) {
        sessionMap.remove(localRouteId);
    }

    /**
     * get the server channel of particular local route identifier
     * 
     * @param localRouteId
     *            local route identifier
     * @return mapped server channel for the particular route identifier
     */
    public LocalChannel getServerChannel(UscRouteIdentifier localRouteId) {
        return sessionMap.get(localRouteId);
    }

    /**
     * remove remote channel related all local sessions
     * 
     * @param remoteChannel
     *            remote channel identifier
     */
    public void removeAll(UscRemoteChannelIdentifier remoteChannel) {
        UscRouteIdentifier routeId = null;
        for (Entry<UscRouteIdentifier, LocalChannel> entry : sessionMap.entrySet()) {
            routeId = entry.getKey();
            if (remoteChannel.equals(routeId)) {
                entry.getValue().close();
                sessionMap.remove(routeId);
            }
        }
    }
}
