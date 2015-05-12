/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.cluster;

import io.netty.channel.Channel;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import akka.actor.ActorRef;

/**
 * Map all of response session which are created from remote, when the response
 * comes from agent, it can find the correct call back actor
 */
public class UscRoutedRemoteSessionManager {
    /**
     * key is local route identifier
     */
    private ConcurrentHashMap<UscRouteIdentifier, UscRouteIdentifierData> sessionMap = new ConcurrentHashMap<UscRouteIdentifier, UscRouteIdentifierData>();

    /**
     * check if this message is the response message for any remote request
     * 
     * @param localRouteId
     * @return
     */
    public boolean isRemoteSession(UscRouteIdentifier localRouteId) {
        return sessionMap.containsKey(localRouteId);
    }

    /**
     * add a new entry of remote session for returning the response which is
     * back from agent
     * 
     * @param data
     *            the related data of a route identifier
     * @return true for local route id not exist, false for exist
     */
    public boolean addEntry(UscRouteIdentifierData data) {
        UscRouteIdentifier localRouteId = data.getLocalRouteIdentifier();
        if (!sessionMap.containsKey(localRouteId)) {
            sessionMap.put(localRouteId, data);
        }
        return false;
    }

    /**
     * get remote route identifier by the local route identifier
     * 
     * @param localRouteId
     *            local route identifier
     * @return remote route identifier
     */
    public UscRouteIdentifier getRemoteRouteIdentifier(UscRouteIdentifier localRouteId) {
        if (sessionMap.containsKey(localRouteId)) {
            return sessionMap.get(localRouteId).getRemoteRouteIdentifier();
        }
        return null;
    }

    /**
     * get call back actor by local route identifier
     * 
     * @param localRouteId
     *            local route identifier
     * @return call back actor
     */
    public ActorRef getActorRef(UscRouteIdentifier localRouteId) {
        UscRouteIdentifierData data = sessionMap.get(localRouteId);
        if (data != null) {
            return data.getActorRef();
        }
        return null;
    }

    /**
     * get agent channel by the local route identifier for sending request to
     * agent channel
     * 
     * @param localRouteId
     *            local route identifier
     * @return agent channel
     */
    public Channel getAgentChannel(UscRouteIdentifier localRouteId) {
        UscRouteIdentifierData data = sessionMap.get(localRouteId);
        if (data != null) {
            return data.getAgentChannel();
        }
        return null;
    }

    /**
     * get local route identifier from remote route identifier
     * 
     * @param remoteRouteId
     *            remote route identifier
     * @return local route identifier if it is managed
     */
    public UscRouteIdentifier getLocalRouteIdentifier(UscRouteIdentifier remoteRouteId) {
        for (@SuppressWarnings("rawtypes")
        Entry entry : sessionMap.entrySet()) {
            UscRouteIdentifier localId = (UscRouteIdentifier) entry.getKey();
            if (localId.hasSameDevice(remoteRouteId)) {
                UscRouteIdentifierData data = (UscRouteIdentifierData) entry.getValue();
                if (data.getRemoteSessionId() == remoteRouteId.getSessionId()) {
                    return data.getLocalRouteIdentifier();
                }
            }
        }
        return null;
    }
}
