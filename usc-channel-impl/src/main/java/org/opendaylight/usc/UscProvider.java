/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.usc.manager.UscManagerService;
import org.opendaylight.usc.manager.monitor.UscAsynchronousEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * USC Provider registers and offers channel related services
 */
public class UscProvider implements AutoCloseable {

    private static final Logger LOG = LoggerFactory
            .getLogger(UscProvider.class);
    private final DataBroker dataService;

    public UscProvider(DataBroker dataService) {
        if (dataService == null) {
            LOG.error("Shard data service is null!");
        }
        this.dataService = dataService;
    }

    public void init() {
        if (dataService == null) {
            LOG.error("Shard data service is null!");
        }
        UscManagerService.getInstance().init(dataService);
        LOG.info("UscProvider Initiated");
    }

    @Override
    public void close() {
        UscAsynchronousEventHandler.closeExecutorService();
        UscManagerService.getInstance().destroy();
        LOG.info("UscProvider Closed");
    }
}
