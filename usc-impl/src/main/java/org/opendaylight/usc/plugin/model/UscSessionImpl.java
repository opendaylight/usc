/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.plugin.model;

import io.netty.channel.local.LocalChannel;

/**
 * Implementation of a USC Session.
 */
public class UscSessionImpl implements UscSession {

    private final UscChannelImpl connection;
    private final int sessionId;
    private final int port;
    private final LocalChannel serverChannel;

    /**
     * Constructs a new UscSessionImpl
     * 
     * @param connection
     * @param sessionId
     * @param port
     * @param serverChannel
     */
    public UscSessionImpl(UscChannelImpl connection, int sessionId, int port, LocalChannel serverChannel) {
        super();
        this.connection = connection;
        this.sessionId = sessionId;
        this.port = port;
        this.serverChannel = serverChannel;
    }

    @Override
    public UscChannelImpl getChannel() {
        return connection;
    }

    @Override
    public int getSessionId() {
        return sessionId;
    }

    @Override
    public int getPort() {
        return port;
    }

    /**
     * Returns the server channel
     * 
     * @return server channel
     */
    public LocalChannel getServerChannel() {
        return serverChannel;
    }

}
