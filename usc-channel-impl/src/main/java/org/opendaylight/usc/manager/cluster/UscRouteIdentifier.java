/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.cluster;

import java.io.Serializable;
import java.net.InetAddress;

import org.opendaylight.usc.plugin.model.UscChannel.ChannelType;

/**
 * the route identifier for identify the routed source or target for routing
 * remote request
 *
 */
@SuppressWarnings("serial")
public class UscRouteIdentifier extends UscRemoteChannelIdentifier implements Serializable {
    private int sessionId;
    private int applicationPort;

    /**
     * constructor by remote channel and session identifier and application port
     * 
     * @param remoteChannel
     *            remote channel
     * @param sessionId
     *            session identifier
     * @param applicationPort
     *            application port
     */
    public UscRouteIdentifier(UscRemoteChannelIdentifier remoteChannel, int sessionId, int applicationPort) {
        super(remoteChannel.getInetAddress(), remoteChannel.getChannelType());
        this.sessionId = sessionId;
        this.applicationPort = applicationPort;
    }

    /**
     * constructor using device address,channel type,session identifier and
     * application type
     * 
     * @param inetAddress
     *            device address
     * @param type
     *            channel type
     * @param sessionId
     *            session identifier
     * @param applicationPort
     *            application port
     */
    public UscRouteIdentifier(InetAddress inetAddress, ChannelType type, int sessionId, int applicationPort) {
        super(inetAddress, type);
        this.sessionId = sessionId;
        this.applicationPort = applicationPort;
    }

    /**
     * get session identifier
     * 
     * @return session identifier
     */
    public int getSessionId() {
        return sessionId;
    }

    @Override
    public String toString() {
        return super.toString() + ", sessionId = " + sessionId;
    }

    @Override
    public boolean equals(Object obj) {
        UscRouteIdentifier other = (UscRouteIdentifier) obj;
        if (getInetAddress().getHostAddress().equalsIgnoreCase(other.getInetAddress().getHostAddress())
                && getChannelType().name().equalsIgnoreCase(other.getChannelType().name())
                && getSessionId() == other.getSessionId() && getApplicationPort() == other.getApplicationPort()) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() * sessionId * applicationPort;
    }

    /**
     * check if the route identifier using same device
     * 
     * @param other
     *            another route identifier
     * @return true for using same device, false for others
     */
    public boolean hasSameDevice(UscRouteIdentifier other) {
        if (this.getInetAddress().equals(other.getInetAddress())) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * get application port
     * 
     * @return application port
     */
    public int getApplicationPort() {
        return applicationPort;
    }

}
