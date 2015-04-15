/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.usc.manager.api.UscConfigurationService;
import org.opendaylight.usc.manager.api.UscSecureService;
import org.opendaylight.usc.manager.api.UscShardService;
import org.opendaylight.usc.plugin.UscPluginTcp;
import org.opendaylight.usc.plugin.UscPluginUdp;
import org.opendaylight.usc.util.UscServiceUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.impl.rev150101.UscImplModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * the main class for USC Manager Module
 */
public class UscManagerService {

    private static final Logger LOG = LoggerFactory
            .getLogger(UscManagerService.class);
    private static UscManagerService manager = new UscManagerService();

    private UscConfigurationService configService;
    private UscSecureService securityService;
    private UscShardServiceImpl shardService;
    private UscTopologyService topoService;
    private UscMonitorService monitorService;

    private UscPluginTcp pluginTcp;
    private UscPluginUdp pluginUdp;

    private UscManagerService() {

    }

    /**
     * get unique UscManager instance
     * 
     * @return UscManager instance
     */
    public static UscManagerService getInstance() {
        return manager;
    }

    /**
     * initialize the data broker for operating the shard data of USC
     * 
     * @param dataService
     *            DataBroker Service instance
     */
    public void init(DataBroker dataService) {
        // configuration service must init at first
        configService = UscConfigurationServiceImpl.getInstance();
        // register configure service first, all other services depend on it
        UscServiceUtils.registerService(this, UscConfigurationService.class,
                configService, null);

        securityService = UscSecureServiceImpl.getInstance();
        UscServiceUtils.registerService(this, UscSecureService.class,
                securityService, null);
        LOG.info("Configuration service and Secure service is initilzated!");
        if (dataService != null) {
            UscShardServiceImpl.getInstance().init(dataService);
            shardService = UscShardServiceImpl.getInstance();
            UscServiceUtils.registerService(this, UscShardService.class,
                    shardService, null);

            // topology service depend on shard service
            topoService = UscTopologyService.getInstance();
            topoService.init();
            UscServiceUtils.registerService(this, UscTopologyService.class,
                    topoService, null);

            // monitor service depend on topology service
            monitorService = UscMonitorService.getInstance();
            UscServiceUtils.registerService(this, UscMonitorService.class,
                    monitorService, null);
            LOG.info("Shard service ,Topology Service and Monitor service is initilzated!");
        } else {
            LOG.error("Since dataBroker is null,Shard service ,Topology Service and Monitor service is not initilzated!");
        }
    }

    /**
     * initialize Usc Manager without parameter,only for Netconf client dispatcher UscPluin
     * 
     */
    public void init() {
        // configuration service must init at first
        configService = UscConfigurationServiceImpl.getInstance();
        // register configure service first, all other services depend on it
        UscServiceUtils.registerService(this, UscConfigurationService.class,
                configService, null);

        securityService = UscSecureServiceImpl.getInstance();
        UscServiceUtils.registerService(this, UscSecureService.class,
                securityService, null);
        shardService = UscShardServiceImpl.getInstance();
        UscServiceUtils.registerService(this, UscShardService.class,
                shardService, null);

        // topology service depend on shard service
        topoService = UscTopologyService.getInstance();
        topoService.init();
        UscServiceUtils.registerService(this, UscTopologyService.class,
                topoService, null);

        // monitor service depend on topology service
        monitorService = UscMonitorService.getInstance();
        UscServiceUtils.registerService(this, UscMonitorService.class,
                monitorService, null);
    }

    /**
     * Destroy USC Manager sub module
     */
    public void destroy() {
        if (topoService != null) {
            topoService.destory();
        }
    }

    public UscPluginTcp getPluginTcp() {
        if (pluginTcp == null) {
            pluginTcp = new UscPluginTcp();
            pluginTcp.addMonitorEventListener(monitorService
                    .getChannelListener());
        }
        return pluginTcp;
    }

    public UscPluginUdp getPluginUdp() {
        if (pluginUdp == null) {
            pluginUdp = new UscPluginUdp();
            pluginUdp.addMonitorEventListener(monitorService
                    .getChannelListener());
        }
        return pluginUdp;
    }
}
