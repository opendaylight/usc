/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor.evt.base;

import org.opendaylight.usc.manager.api.UscEvent;

/**
 * USC monitor event
 */
public class UscMonitorEvent implements UscEvent {

    private String deviceId;

    /**
     * create a monitor event
     * 
     * @param deviceId
     *            device id which can identify a channel error happens
     */
    public UscMonitorEvent(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * get device id
     * 
     * @return device id
     */
    public String getDeviceId() {
        return deviceId;
    }

}
