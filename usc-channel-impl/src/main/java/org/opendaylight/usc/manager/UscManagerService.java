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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * the main class for USC Manager Module
 */
public class UscManagerService {

    private static final Logger LOG = LoggerFactory.getLogger(UscManagerService.class);
    private static UscManagerService manager = new UscManagerService();

    private UscConfigurationService configService;
    private UscSecureService securityService;
    private UscShardServiceImpl shardService;
    private UscTopologyService topoService;
    private UscMonitorService monitorService;
    private UscRouteBrokerService brokerService;

    private UscPluginTcp pluginTcp;
    private UscPluginUdp pluginUdp;
    private boolean initFlag = false;

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
        if (!initFlag) {
            init();
        }
        if (dataService != null) {
            UscShardServiceImpl.getInstance().init(dataService);
            shardService = UscShardServiceImpl.getInstance();
            UscServiceUtils.registerService(this, UscShardService.class, shardService, null);

            // topology service depend on shard service
            topoService = UscTopologyService.getInstance();
            topoService.init();
            UscServiceUtils.registerService(this, UscTopologyService.class, topoService, null);

            // monitor service depend on topology service
            monitorService = UscMonitorService.getInstance();
            UscServiceUtils.registerService(this, UscMonitorService.class, monitorService, null);
            
            getPluginTcp();
            getPluginUdp();
            
            LOG.info("Shard service ,Topology Service and Monitor service are initilzated!");
        } else {
            LOG.error("Since dataBroker is null,Shard service ,Topology Service and Monitor service are not initilzated!");
        }
    }

    /**
     * initialize Usc Manager without parameter,only for Netconf client
     * dispatcher UscPluin
     * 
     */
    public void init() {
        if (initFlag)
            return;
        // configuration service must init at first
        configService = UscConfigurationServiceImpl.getInstance();
        // register configure service first, all other services depend on it
        UscServiceUtils.registerService(this, UscConfigurationService.class, configService, null);

        securityService = UscSecureServiceImpl.getInstance();
        UscServiceUtils.registerService(this, UscSecureService.class, securityService, null);

        // initialize broker service
        brokerService = UscRouteBrokerService.getInstance();
        brokerService.init();
        UscServiceUtils.registerService(this, UscRouteBrokerService.class, UscRouteBrokerService.getInstance(), null);
        initFlag = true;
        LOG.info("Configuration service ,Secure service and Route broker Service are initilzated for UscPlugin! ");
    }

    /**
     * Destroy USC Manager sub module
     */
    public void destroy() {
        if (topoService != null) {
            topoService.destory();
        }
        if (brokerService != null) {
            brokerService.destroy();
        }
    }

    public UscPluginTcp getPluginTcp() {
        if (pluginTcp == null) {
            pluginTcp = new UscPluginTcp();
            if (monitorService == null) {
                monitorService = UscMonitorService.getInstance();
            }
        }
        return pluginTcp;
    }

    public UscPluginUdp getPluginUdp() {
        if (pluginUdp == null) {
            pluginUdp = new UscPluginUdp();
        }
        return pluginUdp;
    }
}
