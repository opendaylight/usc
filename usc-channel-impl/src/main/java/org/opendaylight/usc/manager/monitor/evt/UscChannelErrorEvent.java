/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor.evt;

import org.opendaylight.usc.manager.monitor.evt.base.UscErrorEvent;
import org.opendaylight.usc.manager.monitor.evt.base.UscErrorLevel;
import org.opendaylight.usc.plugin.exception.UscChannelException;
import org.opendaylight.usc.plugin.model.UscChannelImpl;

/**
 * channel error event
 */
public class UscChannelErrorEvent extends UscErrorEvent {

    /**
     * create a channel error event
     * 
     * @param type
     *            channel type
     * @param deviceId
     *            device id which can identify a particular channel
     * @param errorCode
     *            error code
     * @param level
     *            error level
     * @param message
     *            error message
     */
    public UscChannelErrorEvent(String deviceId, String type, int errorCode, UscErrorLevel level, String message) {
        super(deviceId, type, errorCode, level, message);
    }

    /**
     * create a channel error event
     * 
     * @param channel
     *            the channel which error happens
     * @param ex
     *            the exception class of error
     */
    public UscChannelErrorEvent(UscChannelImpl channel, UscChannelException ex) {
        this(channel.getDevice().toString(), channel.getType().name(), 0, UscErrorLevel.ERROR, ex.getMessage());
    }

    @Override
    public String toString() {
        return "UscChannelErrorEvent:" + super.toString();
    }

}
