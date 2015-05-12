/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.api;

/**
 * Configuration interface for USC
 * 
 */
public interface UscConfigurationService {

    /**
     * the configuration key of the plug-in listen port of USC
     */
    public final static String USC_PLUGIN_PORT = "org.opendaylight.usc.PluginPort";
    /**
     * the configuration key of the agent listen port of USC
     */
    public final static String USC_AGENT_PORT = "org.opendaylight.usc.AgentPort";
    /**
     * the configuration key of the max number of errors of session or USC
     * channel links
     */
    public final static String USC_MAX_ERROR_NUMER = "org.opendaylight.usc.MaxErrorNumber";
    /**
     * the configuration key of the max thread pool size for the thread pool of
     * asynchronous event handler
     */
    public final static String USC_MAX_THREAD_NUMBER = "org.opendaylight.usc.MaxThreadNumber";
    /**
     * the configuration key of the flag if log the event error
     */
    public final static String USC_LOG_ERROR_EVENT = "org.opendaylight.usc.LogErrorEvent";
    /**
     * the configuration key of the root path of security related files
     */
    public final static String SECURITY_FILES_ROOT = "org.opendaylight.usc.SecurityFilesRoot";
    /**
     * the configuration key of the trust X.509 certificate chain file for
     * authenticating peer side,in PEM format
     */
    public final static String TRUST_CERTIFICATE_CHAIN_FILE = "org.opendaylight.usc.TrustChainFile";
    /**
     * the configuration key of the public X.509 certificate chain file of local
     * side, in PEM format
     */
    public final static String PUBLIC_CERTIFICATE_CHAIN_FILE = "org.opendaylight.usc.PubicChainFile";
    /**
     * the configuration key of the PKCS#8 private key file of local side, in
     * PEM format
     */
    public final static String PRIVATE_KEY_FILE = "org.opendaylight.usc.PrivateKeyFile";
    /**
     * the configuration key of the configuration file path of akka cluster
     */
    public final static String AKKA_CLUSTER_FILE = "org.opendaylight.usc.CusterConfigurationFile";
    /**
     * the configuration file path for configuration initialization
     */
    public final static String PROPERTY_FILE_PATH = "etc/usc/usc.properties";

    /**
     * get String value configuration
     * 
     * @param key
     *            the configuration key string
     * @return String value, if the key not exists, return null
     */
    public String getConfigStringValue(String key);

    /**
     * get int value configuration
     * 
     * @param key
     *            the configuration key String
     * @return int value if the key not exists, return Integer.MIN_VALUE
     */
    public int getConfigIntValue(String key);

    /**
     * get boolean value configuration
     * 
     * @param key
     *            the configuration key String
     * @return boolean value if the key not exists, return false
     */
    public boolean isConfigAsTure(String key);

}
