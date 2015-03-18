/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.usc.manager.api.UscConfiguration;
import org.opendaylight.usc.manager.api.UscMonitorListener;
import org.opendaylight.usc.manager.api.UscSecureChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * the main class for USC Manager Module
 */
public class UscManager {

    private static final Logger LOG = LoggerFactory.getLogger(UscManager.class);
    private static UscManager manager = new UscManager();

    private UscConfiguration configManager;
    private UscSecureChannel securityManager;
    private UscShardDataManager shardManager;
    private UscTopologyManager topoManager;
    private UscMonitorManager monitorManager;

    private UscManager() {
        configManager = UscConfigurationManager.getInstance();
    }

    /**
     * get unique UscManager instance
     * 
     * @return UscManager instance
     */
    public static UscManager getInstance() {
        return manager;
    }

    /**
     * initialize the data broker for operating the shard data of USC
     * 
     * @param dataService
     *            DataBroker Service instance
     */
    public void init(DataBroker dataService) {
        if (dataService != null) {
            shardManager = new UscShardDataManager(dataService);
            topoManager = new UscTopologyManager(shardManager);
            monitorManager = UscMonitorManager.getInstance();
            monitorManager.init(this);
        } else {
            LOG.error("dataBroker is null!");
        }
    }

    /**
     * get shard data manager
     * 
     * @return shard data manager
     */
    public UscShardDataManager getShardDataManager() {
        if (shardManager == null) {
            LOG.error("dataBroker is not initlization!");
        }
        return shardManager;
    }

    /**
     * get channel listener for monitoring USC Plugin
     * 
     * @return monitor listener
     */
    public UscMonitorListener getChannelListener() {
        return monitorManager.getChannelListener();
    }

    /**
     * get configuration manager of USC
     * 
     * @return configuration manager
     */
    public UscConfiguration getConfigurationManager() {
        return configManager;
    }

    /**
     * get security manager of USC
     * 
     * @return security manager
     */
    public UscSecureChannel getSecurityManager() {
        if (securityManager == null)
            securityManager = new UscSecurityManager(this);
        return securityManager;
    }

    /**
     * get topology manager of USC
     * 
     * @return topology manager
     */
    public UscTopologyManager getUscTopologyManager() {
        return topoManager;
    }

    /**
     * destory USC Manager sub module
     */
    public void destroy() {
        if (topoManager != null) {
            topoManager.destory();
        }
    }

}
