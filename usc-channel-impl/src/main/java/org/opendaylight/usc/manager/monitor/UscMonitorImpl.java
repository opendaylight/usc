/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.monitor;

import org.opendaylight.usc.manager.UscMonitorService;
import org.opendaylight.usc.manager.api.UscEvent;
import org.opendaylight.usc.manager.api.UscMonitor;
import org.opendaylight.usc.util.UscServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Channel monitor listener
 */
public class UscMonitorImpl implements UscMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(UscMonitorImpl.class);

    private UscMonitorService monitorService;

    /**
     * create a Channel monitor listener using given monitor manager
     */
    public UscMonitorImpl() {

    }

    @Override
    public void onEvent(UscEvent event) {
        LOG.info("Receive event:" + event);
        if (monitorService == null) {
            monitorService = UscServiceUtils.getService(UscMonitorService.class);
            if (monitorService == null) {
                LOG.error("Failed to get UscMonitorService!");
                return;
            }
        }
        if (monitorService.getEventHandlerList().containsKey(event.getClass())) {
            UscEventHandler handler = monitorService.getEventHandlerList().get(event.getClass());
            handler.handle(event);
        } else {
            LOG.warn("Find a unknown event, the corresponding event handler is not register.EventType = "
                    + event.getClass().toString());
        }
    }

}
