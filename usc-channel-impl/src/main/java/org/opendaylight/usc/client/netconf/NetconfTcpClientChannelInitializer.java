/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.usc.client.netconf;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;

import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.nettyutil.AbstractChannelInitializer;
import org.opendaylight.protocol.framework.SessionListenerFactory;

class NetconfTcpClientChannelInitializer extends AbstractChannelInitializer<NetconfClientSession> implements
        SessionListenerFactory<NetconfClientSessionListener> {

    private final NetconfClientSessionNegotiatorFactory negotiatorFactory;
    private final NetconfClientSessionListener sessionListener;

    NetconfTcpClientChannelInitializer(final NetconfClientSessionNegotiatorFactory negotiatorFactory,
            final NetconfClientSessionListener sessionListener) {
        this.negotiatorFactory = negotiatorFactory;
        this.sessionListener = sessionListener;
    }

    @Override
    protected void initializeSessionNegotiator(final Channel ch, final Promise<NetconfClientSession> promise) {
        ch.pipeline().addAfter(NETCONF_MESSAGE_DECODER, AbstractChannelInitializer.NETCONF_SESSION_NEGOTIATOR,
                negotiatorFactory.getSessionNegotiator(this, ch, promise));
    }

    @Override
    public NetconfClientSessionListener getSessionListener() {
        return sessionListener;
    }
}