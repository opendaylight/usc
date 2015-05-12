/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.cluster.message;

import java.net.InetAddress;

import org.opendaylight.usc.manager.cluster.UscRouteIdentifier;

@SuppressWarnings("serial")
public class UscRemoteDataMessage extends UscRemoteMessage {
    private final boolean request;
    private final byte[] payload;

    public UscRemoteDataMessage(UscRouteIdentifier routeId, byte[] payload,
            boolean request) {
        super(routeId);
        this.payload = payload;
        this.request = request;
    }

    public byte[] getPayload() {
        return payload;
    }

    public boolean isRequest() {
        return request;
    }

    public InetAddress getInetAddress() {
        if (routeIdentifier != null) {
            return routeIdentifier.getInetAddress();
        } else {
            return null;
        }
    }

    public int getSessionId() {
        if (routeIdentifier != null) {
            return routeIdentifier.getSessionId();
        } else {
            return -1;
        }
    }

    @Override
    public String toString() {
        return super.toString() + ",request is " + request + ",payload is "
                + new String(payload);
    }
}
