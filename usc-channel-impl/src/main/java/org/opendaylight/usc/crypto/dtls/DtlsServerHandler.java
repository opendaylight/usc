/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.usc.crypto.dtls;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;

import java.io.IOException;
import java.security.SecureRandom;

import org.bouncycastle.crypto.tls.DTLSServerProtocol;
import org.bouncycastle.crypto.tls.DTLSTransport;

/**
 * @author gwu
 *
 */
public class DtlsServerHandler extends DtlsHandler {

    private final DtlsServer mserver;
    private final SecureRandom secureRandom;

    public DtlsServerHandler(DtlsServer dtlsServer, SecureRandom secureRandom) {
        this.mserver = dtlsServer;
        this.secureRandom = secureRandom;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.opendaylight.usc.crypto.DtlsHandler#getDtlsTransport()
     */
    @Override
    protected DTLSTransport getDtlsTransport() throws IOException {
        DTLSServerProtocol serverProtocol = new DTLSServerProtocol(secureRandom);
        return serverProtocol.accept(mserver, rawTransport);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
        if (obj instanceof DatagramPacket) {
            DatagramPacket msg = (DatagramPacket) obj;
            rawTransport.setRemoteAddress(msg.sender());
        }

        super.channelRead(ctx, obj);
    }

}
