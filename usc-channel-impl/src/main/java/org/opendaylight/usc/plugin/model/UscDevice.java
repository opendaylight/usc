/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin.model;

import java.net.InetAddress;

/**
 * Representation of the network device to which a USC channel is connected.
 */
public class UscDevice {

    private final InetAddress inetAddress;
    private final int port;

    /**
     * Constructs a new UscDevice
     * 
     * @param inetAddress
     *            the IP address of the network device
     */
    public UscDevice(InetAddress inetAddress) {
        super();
        this.inetAddress = inetAddress;
        this.port = -1;
    }
    
    public UscDevice(InetAddress inetAddress, int port) {
        super();
        this.inetAddress = inetAddress;
        this.port = port;
    }

    /**
     * 
     * @return the IP address of the device
     */
    public InetAddress getInetAddress() {
        return inetAddress;
    }

    
    public int getPort() {
		return port;
	}

	@Override
    public int hashCode() {
        return inetAddress.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof UscDevice) && inetAddress.equals(((UscDevice) obj).inetAddress);
    }

    @Override
    public String toString() {
        return inetAddress.toString();
    }

}
