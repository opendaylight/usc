/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin.model;

/**
 * Representation of a physical USC channel.
 */
public interface UscChannel {

    /**
     * Enumerated Types of Channels
     */
    public enum ChannelType {
        TCP, UDP, TLS, DTLS;
    }

    /**
     * The device to which the USC channel is established.
     * 
     * @return device
     */
    public abstract UscDevice getDevice();

    /**
     * Whether this USC channel was established using the Call Home mechanism.
     * 
     * @return call home
     */
    public abstract boolean isCallHome();

    /**
     * The type of USC channel.
     * 
     * @return type
     */
    public abstract ChannelType getType();

}
