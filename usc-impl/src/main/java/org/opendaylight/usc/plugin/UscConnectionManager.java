/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

import java.util.concurrent.ConcurrentHashMap;

import org.opendaylight.usc.manager.monitor.evt.UscChannelCloseEvent;
import org.opendaylight.usc.manager.monitor.evt.UscChannelCreateEvent;
import org.opendaylight.usc.plugin.model.UscChannel;
import org.opendaylight.usc.plugin.model.UscChannelImpl;
import org.opendaylight.usc.plugin.model.UscDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * This class manages the collection of connections (channels) that are
 * currently established to this controller node.
 */
public class UscConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(UscConnectionManager.class);

    private final UscPlugin plugin;

    /**
     * Map from host name to agentChannel
     */
    private final ConcurrentHashMap<UscDevice, UscChannelImpl> connections = new ConcurrentHashMap<>();

    protected UscConnectionManager(UscPlugin plugin) {
        this.plugin = plugin;
    }

    protected UscChannelImpl getConnection(UscDevice device, UscChannel.ChannelType type) throws InterruptedException {
        UscChannelImpl connection = connections.get(device);
        if (connection == null) {
            return addConnection(device, plugin.connectToAgent(device), false, type);
        } else {
            return connection;
        }
    }

    protected UscChannelImpl addConnection(final UscDevice device, final Channel channel, final boolean isCallHome,
            final UscChannel.ChannelType type) {
        // standard idiom for double-checked locking
        UscChannelImpl connection = connections.get(device);
        if (connection == null) {
            final UscChannelImpl newConnection = new UscChannelImpl(plugin, device, channel, isCallHome, type);
            connection = connections.putIfAbsent(device, newConnection);
            if (connection == null) {
                channel.closeFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        removeConnection(newConnection);
                        log.trace("agentChannel for device " + device + " closed");
                    }
                });
                plugin.sendEvent(new UscChannelCreateEvent(newConnection));
                connection = newConnection;
            } else {
                // previous entry exists; put failed; close the new channel
                channel.close();
            }
        }
        return connection;
    }

    private boolean removeConnection(UscChannelImpl connection) {
        // don't re-close the connection here since we should only reach this
        // point after the connection has been closed
        boolean isRemoved = connections.remove(connection.getDevice(), connection);
        if (isRemoved) {
            connection.removeAllSessions();
            plugin.sendEvent(new UscChannelCloseEvent(connection));
        }
        return isRemoved;
    }

    @VisibleForTesting
    public int getConnectionCount() {
        return connections.size();
    }

    @VisibleForTesting
    public int getSessionCount() {
        int count = 0;
        for (UscChannelImpl connection : connections.values()) {
            count += connection.getSessionCount();
        }
        return count;
    }

}
