/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor.evt;

import org.opendaylight.usc.manager.monitor.evt.base.UscMonitorEvent;
import org.opendaylight.usc.plugin.model.UscChannel;

/**
 * channel create event class
 */
public class UscChannelCreateEvent extends UscMonitorEvent {

    private boolean isCallHome;

    /**
     * create a channel create event
     * 
     * @param deviceId
     *            device id which can identify a particular channel
     * @param isCallHome
     *            if the channel created by call home way, true for yes, false
     *            for no
     * @param type
     *            channel type,like tcp, udp
     */
    public UscChannelCreateEvent(String deviceId, boolean isCallHome, String type) {
        super(deviceId, type);
        this.isCallHome = isCallHome;
    }

    /**
     * create a channel create event using channel info
     * 
     * @param channel
     *            channel created
     */
    public UscChannelCreateEvent(UscChannel channel) {
        this(channel.getDevice().toString(), channel.isCallHome(), channel.getType().name());
    }

    /**
     * if the channel created by call home way
     * 
     * @return true for yes, false for no
     */
    public boolean isCallHome() {
        return isCallHome;
    }

    @Override
    public String toString() {
        return "UscChannelCreateEvent:" + super.toString() + ",type is " + getType() + ",isCallHome is " + isCallHome();
    }
}
