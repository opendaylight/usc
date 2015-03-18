/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor.evt.base;

import java.util.UUID;

/**
 * USC error event
 */
public class UscErrorEvent extends UscMonitorEvent {

    private String errorId;
    private int errorCode;
    private String message;
    private UscErrorLevel level = UscErrorLevel.UNKNOWN;

    /**
     * create a error event
     * 
     * @param deviceId
     *            device id which can identify the channel which error happens
     * @param errorCode
     *            error code
     * @param level
     *            error level
     * @param message
     *            error message
     */
    public UscErrorEvent(String deviceId, int errorCode, UscErrorLevel level, String message) {
        super(deviceId);
        errorId = UUID.randomUUID().toString();
        this.errorCode = errorCode;
        this.level = level;
        this.message = message;
    }

    /**
     * get error id
     * 
     * @return error id
     */
    public String getErrorId() {
        return errorId;
    }

    /**
     * get error code
     * 
     * @return error code
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * get error message
     * 
     * @return error message
     */
    public String getMessage() {
        return message;
    }

    /**
     * get error level
     * 
     * @return error level
     */
    public UscErrorLevel getLevel() {
        return level;
    }

}
