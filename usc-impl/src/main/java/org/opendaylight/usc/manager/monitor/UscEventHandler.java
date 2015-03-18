/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor;

import org.opendaylight.usc.manager.api.UscEvent;

/**
 * Event handler interface for handle given event
 */
public interface UscEventHandler {

    /**
     * Handles the given event
     * 
     * @param event
     *            the event to handle
     */
    public void handle(UscEvent event);

}
