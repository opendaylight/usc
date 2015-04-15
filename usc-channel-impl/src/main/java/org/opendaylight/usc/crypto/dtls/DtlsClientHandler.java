/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.usc.crypto.dtls;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.SecureRandom;

import org.bouncycastle.crypto.tls.DTLSClientProtocol;
import org.bouncycastle.crypto.tls.DTLSTransport;

/**
 * @author gwu
 *
 */
public class DtlsClientHandler extends DtlsHandler {

    private final DtlsClient mclient;
    private final SecureRandom secureRandom;

    public DtlsClientHandler(DtlsClient dtlsClient, SecureRandom secureRandom) {
        this.mclient = dtlsClient;
        this.secureRandom = secureRandom;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.opendaylight.usc.crypto.DtlsHandler#getDtlsTransport()
     */
    @Override
    protected DTLSTransport getDtlsTransport() throws IOException {
        DTLSClientProtocol clientProtocol = new DTLSClientProtocol(secureRandom);
        return clientProtocol.connect(mclient, rawTransport);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
            ChannelPromise future) throws Exception {
        rawTransport.setRemoteAddress((InetSocketAddress) remoteAddress);

        super.connect(ctx, remoteAddress, localAddress, future);
    }

}
