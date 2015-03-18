/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

import java.io.File;
import java.security.SecureRandom;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

import org.opendaylight.usc.manager.api.UscConfiguration;
import org.opendaylight.usc.manager.api.UscSecureChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security Manager of USC, it includes all of security related staff for USC
 */
public class UscSecurityManager implements UscSecureChannel {

    private static final Logger LOG = LoggerFactory.getLogger(UscSecurityManager.class);

    private SslContext sslServerCtx;
    private SslContext sslClientCtx;

    /**
     * create a security manager class using UscManager
     * 
     * @param manager
     *            security manager
     */
    public UscSecurityManager(UscManager manager) {
        File rootPath = new File(manager.getConfigurationManager().getConfigStringValue(
                UscConfiguration.SECURITY_FILES_ROOT));
        File key = new File(rootPath, "client.key.pem");
        File cert = new File(rootPath, "client.pem");
        File root = new File(rootPath, "rootCA.pem");
        try {
            sslServerCtx = SslContext.newServerContext(null, root, null, cert, key, null, null, null,
                    IdentityCipherSuiteFilter.INSTANCE, null, 0, 0);
            sslClientCtx = SslContext.newClientContext(null, root, null, cert, key, null, null, null,
                    IdentityCipherSuiteFilter.INSTANCE, null, 0, 0);
        } catch (SSLException e) {
            LOG.error("Failed to initialize the security server and client handler", e);
        }
    }

    @Override
    public ChannelOutboundHandler getTcpServerHandler(Channel ch) {
        // mutual authentication as server
        SSLEngine sslServerEngine = sslServerCtx.newEngine(ch.alloc());
        // require client (mutual) authentication
        sslServerEngine.setNeedClientAuth(true);
        return new SslHandler(sslServerEngine);
    }

    @Override
    public ChannelOutboundHandler getTcpClientHandler(Channel ch) {
        // mutual authentication as client
        return sslClientCtx.newHandler(ch.alloc());
    }

    @Override
    public ChannelOutboundHandler getUdpServerHandler(Channel ch) {
        // TODO implement DTLS handler
        return new ChannelDuplexHandler();
    }

    @Override
    public ChannelOutboundHandler getUdpClientHandler(Channel ch) {
        // TODO implement DTLS handler
        return new ChannelDuplexHandler();
    }

}
