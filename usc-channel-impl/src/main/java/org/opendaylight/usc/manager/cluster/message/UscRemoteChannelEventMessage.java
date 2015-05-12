/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.cluster.message;

import org.opendaylight.usc.manager.cluster.UscRemoteChannelIdentifier;
import org.opendaylight.usc.manager.cluster.UscRouteIdentifier;

@SuppressWarnings("serial")
public class UscRemoteChannelEventMessage extends UscRemoteMessage {
    private ChannelEventType eventType = ChannelEventType.UNKNOWN;

    public UscRemoteChannelEventMessage(UscRemoteChannelIdentifier remoteChannel, ChannelEventType type) {
        super(new UscRouteIdentifier(remoteChannel, -1, -1));
        this.eventType = type;
    }

    public boolean isCreate() {
        return eventType.equals(ChannelEventType.CREATE);
    }

    public boolean isClose() {
        return eventType.equals(ChannelEventType.CLOSE);
    }

    @Override
    public String toString() {
        return "UscRemoteChannelEventMessage:" + super.toString() + ",type is " + eventType.name();
    }

    public enum ChannelEventType {
        UNKNOWN, CREATE, CLOSE;
    }
}
