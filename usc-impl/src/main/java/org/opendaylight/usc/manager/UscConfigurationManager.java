/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager;

import java.util.Hashtable;

import org.opendaylight.usc.manager.api.UscConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration Manager class for USC
 */
public class UscConfigurationManager implements UscConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(UscConfigurationManager.class);
    private static UscConfigurationManager manager = new UscConfigurationManager();

    private Hashtable<String, Object> configData = new Hashtable<String, Object>();

    private UscConfigurationManager() {

    }

    /**
     * Get the unique UscConfigurationManager instance object
     * 
     * @return UscConfigurationManager instance object
     */
    public final static UscConfigurationManager getInstance() {
        return manager;
    }

    /**
     * Initialize a property with the key and value
     * 
     * @param key
     *            property key string
     * @param value
     *            property value object
     */
    public void initProperty(String key, Object value) {
        if (key != null && value != null) {
            configData.put(key, value);
            LOG.info("Initialize the property for key = " + key + ", value = " + value);
        } else {
            LOG.error("Initialize the property is null!key = " + key + ", value = " + value);
        }
    }

    @Override
    public String getConfigStringValue(String key) {
        if (configData.containsKey(key)) {
            return (String) configData.get(key);
        }
        return null;
    }

    @Override
    public int getConfigIntValue(String key) {
        if (configData.containsKey(key)) {
            return ((Integer) configData.get(key)).intValue();
        }
        return Integer.MIN_VALUE;
    }

}
