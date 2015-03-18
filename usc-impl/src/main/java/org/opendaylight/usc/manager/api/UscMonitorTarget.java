/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.api;

/**
 * USC event monitor target interface which should be implements by each event
 * monitor target(like UscPlugin)
 */
public interface UscMonitorTarget {

    /**
     * Add event monitor listener to listener queue
     * 
     * @param listener
     *            listener object
     */
    public void addMonitorEventListener(UscMonitorListener listener);

    /**
     * Remove event monitor listener from listener queue
     * 
     * @param listener
     *            listener listener object
     */
    public void removeMonitorEventListener(UscMonitorListener listener);

    /**
     * Send USC Event to all of listeners in listener queue
     * 
     * @param event
     */
    public void sendEvent(UscEvent event);

}
