/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc;

import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.RpcRegistration;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.usc.manager.UscManager;
import org.opendaylight.usc.manager.monitor.UscAsynchronousEventHandler;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.rev150101.UscService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * USC Provider registers and offers channel related services.
 */
public class UscProvider implements BindingAwareProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(UscProvider.class);
    private RpcRegistration<UscService> uscService;

    @Override
    public void onSessionInitiated(ProviderContext session) {
        UscServiceImpl service = new UscServiceImpl(UscManager.getInstance().getShardDataManager());
        uscService = session.addRpcImplementation(UscService.class, service);
        // TODO Add Routing Logic process
        // RoutingLogicManager routingLogicManager=
        // session.getSALService(RoutingLogicManager.class);
        // RpcActorMathcer uscMatcher = new UscRpcActorMatcher();
        // RoutingLogic routingLogic = new
        // BasicRpcMessageRoutingLogic(uscMatcher);
        // routingLogicManager.replceRoutingLogic(routingLogic);
        LOG.info("UscProvider Session Initiated");
    }

    @Override
    public void close() throws Exception {
        UscAsynchronousEventHandler.closeExecutorService();
        uscService.close();
        UscManager.getInstance().destroy();
        LOG.info("UscProvider Closed");
    }

}
