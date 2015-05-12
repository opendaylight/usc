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
 * channel close event class
 */
public class UscChannelCloseEvent extends UscMonitorEvent {

    /**
     * create a channel close event using the device id
     * 
     * @param type
     *            channel type
     * @param deviceId
     *            device id which can identify a particular channel
     */
    public UscChannelCloseEvent(String deviceId, String type) {
        super(deviceId, type);
    }

    /**
     * create a channel close event using the channel
     * 
     * @param channel
     *            closing USC channel
     */
    public UscChannelCloseEvent(UscChannel channel) {
        this(channel.getDevice().toString(), channel.getType().name());
    }

    @Override
    public String toString() {
        return "UscChannelCloseEvent:" + super.toString();
    }
}
