/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.RpcRegistration;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.usc.manager.UscManagerService;
import org.opendaylight.usc.manager.monitor.UscAsynchronousEventHandler;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.channel.rev150101.UscChannelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * USC Provider registers and offers channel related services
 */
public class UscProvider implements BindingAwareProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory
            .getLogger(UscProvider.class);
    private RpcRegistration<UscChannelService> uscChannelService;
    private DataBroker dataService;

    public UscProvider(DataBroker dataService) {
        if (dataService == null) {
            LOG.error("Shard data service is null!");
        }
        this.dataService = dataService;
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        if (dataService == null) {
            LOG.error("Shard data service is null!");
        }
        UscManagerService.getInstance().init(dataService);
        UscChannelServiceImpl service = new UscChannelServiceImpl();
        uscChannelService = session.addRpcImplementation(UscChannelService.class, service);
        LOG.info("UscProvider Session Initiated");
    }

    @Override
    public void close() throws Exception {
        UscAsynchronousEventHandler.closeExecutorService();
        uscChannelService.close();
        UscManagerService.getInstance().destroy();
        LOG.info("UscProvider Closed");
    }
}
