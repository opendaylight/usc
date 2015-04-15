/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin.model;

import io.netty.channel.Channel;
import io.netty.channel.local.LocalChannel;

import org.opendaylight.usc.plugin.UscPlugin;
import org.opendaylight.usc.plugin.UscSessionManager;

/**
 * Implementation of a physical USC channel.
 */
public class UscChannelImpl extends UscSessionManager implements UscChannel {

    private final UscDevice device;
    private final Channel channel;
    private final boolean isCallHome;
    private final ChannelType type;

    /**
     * Constructs a new UscChannelImpl.
     * 
     * @param plugin
     * @param device
     * @param channel
     * @param isCallHome
     * @param type
     */
    public UscChannelImpl(UscPlugin plugin, UscDevice device, Channel channel, boolean isCallHome, ChannelType type) {
        super(plugin);
        this.device = device;
        this.channel = channel;
        this.isCallHome = isCallHome;
        this.type = type;

        this.channel.attr(UscPlugin.CHANNEL).set(this);
    }

    @Override
    protected UscSessionImpl createSession(int sessionId, int port, LocalChannel channel) {
        return new UscSessionImpl(this, sessionId, port, channel);
    }

    @Override
    public UscDevice getDevice() {
        return device;
    }

    public Channel getChannel() {
        return channel;
    }

    @Override
    public boolean isCallHome() {
        return isCallHome;
    }

    @Override
    public ChannelType getType() {
        return type;
    }

}
