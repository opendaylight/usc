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
public interface UscConfiguration {

    /**
     * the plug-in listen port of USC
     */
    public final static String USC_PLUGIN_PORT = "USC Plugin Port";
    /**
     * the agent listen port of USC
     */
    public final static String USC_AGENT_PORT = "USC Agent Port";
    /**
     * the max number of errors of session or USC channel links
     */
    public final static String USC_MAX_ERROR_NUMER = "USC Max Error Number";
    /**
     * the max thread pool size for the thread pool of asynchronous event
     * handler
     */
    public final static String USC_MAX_THREAD_NUMBER = "USC Max Thread Pool Size";
    /**
     * the root path of security related files
     */
    public final static String SECURITY_FILES_ROOT = "USC Security Root Path";

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

}
