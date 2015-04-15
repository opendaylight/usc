/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.api;

/**
 * USC Monitor Listener, Adding implements class to UscMonitorTarget can receive
 * UscEvent
 */
public interface UscMonitorListener {

    /**
     * when USC event happens, this method will be called
     * 
     * @param event
     *            event information data object
     */
    public void onEvent(UscEvent event);

}
