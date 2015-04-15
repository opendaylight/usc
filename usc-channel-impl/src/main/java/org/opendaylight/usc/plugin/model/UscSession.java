/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin.model;

/**
 * Representation of an USC session.
 */
public interface UscSession {

    /**
     * The physical USC channel that this session's traffic is traveling over.
     * 
     * @return channel
     */
    public abstract UscChannel getChannel();

    /**
     * The session ID corresponding to this session.
     * 
     * @return session id
     */
    public abstract int getSessionId();

    /**
     * The port number of the service on the device.
     * 
     * @return port
     */
    public abstract int getPort();

}
