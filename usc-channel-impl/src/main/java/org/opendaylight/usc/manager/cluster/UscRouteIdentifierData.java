/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.cluster;

import io.netty.channel.Channel;
import akka.actor.ActorRef;

/**
 * all related data of a route identifier
 *
 */
public class UscRouteIdentifierData {
    private ActorRef actorRef;
    private UscRouteIdentifier remoteRouteIdentifier;
    private int localSessionId;
    private Channel agentChannel;

    /**
     * contructor by actor of remote caller, remote route identifier, local
     * session identifier and local agent channel
     * 
     * @param actorRef
     *            actor of remote caller
     * @param remoteRouteIdentifier
     *            remote route identifier
     * @param localSessionId
     *            local session identifier
     * @param agentChannel
     *            local agent channel
     */
    public UscRouteIdentifierData(ActorRef actorRef, UscRouteIdentifier remoteRouteIdentifier, int localSessionId,
            Channel agentChannel) {
        this.actorRef = actorRef;
        this.remoteRouteIdentifier = remoteRouteIdentifier;
        this.localSessionId = localSessionId;
        this.agentChannel = agentChannel;
    }

    /**
     * get remote session identifier
     * 
     * @return remote session identifier
     */
    public int getRemoteSessionId() {
        return remoteRouteIdentifier.getSessionId();
    }

    /**
     * get local session identifier
     * 
     * @return local session identifier
     */
    public int getLocalSessionId() {
        return localSessionId;
    }

    /**
     * get call back actor
     * 
     * @return call back actor
     */
    public ActorRef getActorRef() {
        return actorRef;
    }

    /**
     * get remote route identifier
     * 
     * @return remote route identifier
     */
    public UscRouteIdentifier getRemoteRouteIdentifier() {
        return remoteRouteIdentifier;
    }

    /**
     * get local route identifier
     * 
     * @return local route identifier
     */
    public UscRouteIdentifier getLocalRouteIdentifier() {
        return new UscRouteIdentifier(remoteRouteIdentifier, localSessionId, remoteRouteIdentifier.getApplicationPort());
    }

    /**
     * get local agent channel
     * 
     * @return local agent channel
     */
    public Channel getAgentChannel() {
        return agentChannel;
    }

    @Override
    public String toString() {
        return remoteRouteIdentifier.toString() + ",localSessionId is " + localSessionId + ", remote actor is "
                + actorRef;
    }
}