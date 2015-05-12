/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Properties;

import org.opendaylight.usc.manager.api.UscConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration Manager class for USC
 */
public class UscConfigurationServiceImpl implements UscConfigurationService {

    private static final Logger LOG = LoggerFactory
            .getLogger(UscConfigurationServiceImpl.class);
    private static UscConfigurationServiceImpl manager;

    private static String defaultPropertyFilePath;

    public static void setDefaultPropertyFilePath(String path) {
        defaultPropertyFilePath = path;
    }

    private Hashtable<String, Object> configData = new Hashtable<String, Object>();

    private UscConfigurationServiceImpl() {
        loadFromPropertyFile();
    }

    /**
     * Get the unique UscConfigurationManager instance object
     * 
     * @return UscConfigurationManager instance object
     */
    public final static UscConfigurationServiceImpl getInstance() {
        if (manager == null) {
            manager = new UscConfigurationServiceImpl();
        }
        return manager;
    }

    private void loadFromPropertyFile() {
        Properties prop = new Properties();
        // InputStream inputStream = getClass().getClassLoader()
        // .getResourceAsStream(filename);
        InputStream inputStream;
        try {
            if (defaultPropertyFilePath == null) {
                defaultPropertyFilePath = UscConfigurationService.PROPERTY_FILE_PATH;
            }
            inputStream = new FileInputStream(defaultPropertyFilePath);
            LOG.debug("Found the USC properties file, initializing configuration service.");
            try {
                prop.load(inputStream);
                // initialize configuration properties
                setIntPropertyFromFile(prop,
                        UscConfigurationService.USC_AGENT_PORT, true);
                setIntPropertyFromFile(prop,
                        UscConfigurationService.USC_PLUGIN_PORT, true);
                setIntPropertyFromFile(prop,
                        UscConfigurationService.USC_MAX_ERROR_NUMER, true);
                setIntPropertyFromFile(prop,
                        UscConfigurationService.USC_MAX_THREAD_NUMBER, true);
                setBooleanPropertyFromFile(prop,
                        UscConfigurationService.USC_LOG_ERROR_EVENT);
                setStringPropertyFromFile(prop,
                        UscConfigurationService.SECURITY_FILES_ROOT);
                setStringPropertyFromFile(prop,
                        UscConfigurationService.PRIVATE_KEY_FILE);
                setStringPropertyFromFile(prop,
                        UscConfigurationService.PUBLIC_CERTIFICATE_CHAIN_FILE);
                setStringPropertyFromFile(prop,
                        UscConfigurationService.TRUST_CERTIFICATE_CHAIN_FILE);
                setStringPropertyFromFile(prop,
                        UscConfigurationService.AKKA_CLUSTER_FILE);
            } catch (IOException e) {
                LOG.warn("Failed to load properties from USC properties file, using the default data. Error message is "
                        + e.getMessage());
            }
        } catch (FileNotFoundException e1) {
            LOG.info("Didn't find the USC properties file, using the default data.filename is "
                    + UscConfigurationService.PROPERTY_FILE_PATH
                    + ", error message is " + e1.getMessage());
        }
    }

    private void setBooleanPropertyFromFile(Properties prop, String key) {
        String value = prop.getProperty(key);
        if (value != null) {
            initProperty(key, new Boolean(value));
        }
    }

    private void setIntPropertyFromFile(Properties prop, String key,
            boolean bgZero) {
        String value = prop.getProperty(key);
        if (value != null) {
            int ret = Integer.parseInt(value);
            if (!bgZero || (bgZero && ret > 0)) {
                initProperty(key, ret);
            }
        }
    }

    private void setStringPropertyFromFile(Properties prop, String key) {
        String value = prop.getProperty(key);
        if (value != null) {
            initProperty(key, value);
        }
    }

    /**
     * Initialize a property with the key and value
     * 
     * @param key
     *            property key string
     * @param value
     *            property value object
     */
    private void initProperty(String key, Object value) {
        if (key != null && value != null) {
            configData.put(key, value);
            LOG.trace("Initialize the property for key = " + key + ", value = "
                    + value);
        } else {
            LOG.error("Initialize the property is null!key = " + key
                    + ", value = " + value);
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

    @Override
    public boolean isConfigAsTure(String key) {
        if (configData.containsKey(key)) {
            return ((Boolean) configData.get(key));
        }
        return false;
    }

}
