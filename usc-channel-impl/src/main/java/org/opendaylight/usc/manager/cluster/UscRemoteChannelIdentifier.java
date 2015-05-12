/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager.cluster;

import java.io.Serializable;
import java.net.InetAddress;

import org.opendaylight.usc.plugin.model.UscChannel.ChannelType;

/**
 * Remote channel identifier for identify a particular USC device with the
 * particular type like TCP,UDP
 *
 */
@SuppressWarnings("serial")
public class UscRemoteChannelIdentifier implements Serializable {
    public static final String CHANNEL_TYPE_PREFIX = "Remote";
    private ChannelType type;
    private final InetAddress inetAddress;

    /**
     * constructor
     * 
     * @param inetAddress
     *            the ip address of remote device
     * @param type
     *            the channel type of remote channel
     */
    public UscRemoteChannelIdentifier(InetAddress inetAddress, ChannelType type) {
        this.inetAddress = inetAddress;
        this.type = type;
    }

    /**
     * 
     * @return the IP address of the device
     */
    public InetAddress getInetAddress() {
        return inetAddress;
    }

    /**
     * get the channel type of remote channel
     * 
     * @return
     */
    public ChannelType getChannelType() {
        return type;
    }

    /**
     * get channel type object through the type string
     * 
     * @param type
     *            the type string of a channel
     * @return the corresponding channel type
     */
    public static ChannelType getChannelTypeByString(String type) {
        for (ChannelType tmp : ChannelType.values()) {
            if (type.equalsIgnoreCase(tmp.name())) {
                return tmp;
            }
        }
        return null;
    }

    /**
     * get ip string from InetAddress,specially remove the slash prefix
     * 
     * @param address
     *            the InetAddress object
     * @return ip string
     */
    public static String getIpString(InetAddress address) {
        String ret = address.toString();
        if (ret.indexOf('/') == 0) {
            return ret.substring(1, ret.length());
        }
        return ret;
    }

    /**
     * get ip string of current remote channel
     * 
     * @return
     */
    public String getIp() {
        return getIpString(inetAddress);
    }

    /**
     * get type string of remote channel
     * 
     * @return
     */
    public String getRemoteChannelType() {
        return CHANNEL_TYPE_PREFIX + "-" + type.name();
    }

    @Override
    public String toString() {
        return "Device IP = " + getIp() + ", channel type = " + getChannelType();
    }

    @Override
    public boolean equals(Object obj) {
        UscRemoteChannelIdentifier other = (UscRemoteChannelIdentifier) obj;
        if (getInetAddress().getHostAddress().equalsIgnoreCase(other.getInetAddress().getHostAddress())
                && getChannelType().name().equalsIgnoreCase(other.getChannelType().name())) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return inetAddress.hashCode() * type.ordinal();
    }

}
