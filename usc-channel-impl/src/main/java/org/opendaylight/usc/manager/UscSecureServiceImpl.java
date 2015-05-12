/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.manager;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

import java.io.File;
import java.security.SecureRandom;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

import org.opendaylight.usc.crypto.dtls.DtlsClient;
import org.opendaylight.usc.crypto.dtls.DtlsClientHandler;
import org.opendaylight.usc.crypto.dtls.DtlsServer;
import org.opendaylight.usc.crypto.dtls.DtlsServerHandler;
import org.opendaylight.usc.manager.api.UscConfigurationService;
import org.opendaylight.usc.manager.api.UscSecureService;
import org.opendaylight.usc.util.UscServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security Manager of USC, it includes all of security related staff for USC
 */
public class UscSecureServiceImpl implements UscSecureService {

    private static final Logger LOG = LoggerFactory
            .getLogger(UscSecureServiceImpl.class);
    private static UscSecureServiceImpl serviceImpl = new UscSecureServiceImpl();
    private final SecureRandom secureRandom = new SecureRandom();
    private final File privateKeyFile;
    private final File publicCertChainFile;
    private final File trustCertChainFile;

    /**
     * create a security manager class using UscManager
     * 
     * @param manager
     *            security manager
     */
    private UscSecureServiceImpl() {
        UscConfigurationService configService = UscServiceUtils
                .getService(UscConfigurationService.class);
        if (configService == null) {
            LOG.error("The configuration service is not initialized!Using the default data to initialize");
            File rootPath = new File("etc/usc/certificates");
            privateKeyFile = new File(rootPath, "client.key.pem");
            publicCertChainFile = new File(rootPath, "client.pem");
            trustCertChainFile = new File(rootPath, "rootCA.pem");
            return;
        }
        File rootPath = new File(configService.getConfigStringValue(UscConfigurationService.SECURITY_FILES_ROOT));
        
        privateKeyFile = new File(rootPath,
                configService.getConfigStringValue(UscConfigurationService.PRIVATE_KEY_FILE));
        if (!privateKeyFile.canRead()) {
            LOG.error("Unable to read private key " + privateKeyFile.getAbsolutePath());
        }
        
        publicCertChainFile = new File(rootPath,
                configService.getConfigStringValue(UscConfigurationService.PUBLIC_CERTIFICATE_CHAIN_FILE));
        if (!publicCertChainFile.canRead()) {
            LOG.error("Unable to read public cert " + publicCertChainFile.getAbsolutePath());
        }

        trustCertChainFile = new File(rootPath,
                configService.getConfigStringValue(UscConfigurationService.TRUST_CERTIFICATE_CHAIN_FILE));
        if (!trustCertChainFile.canRead()) {
            LOG.error("Unable to read trust cert " + trustCertChainFile.getAbsolutePath());
        }
    }

    /**
     * get unique security service instance
     * 
     * @return
     */
    public static UscSecureService getInstance() {
        return serviceImpl;
    }

    @Override
    public ChannelOutboundHandler getTcpServerHandler(Channel ch)
            throws SSLException {
        SslContext sslServerCtx = SslContext.newServerContext(null,
                trustCertChainFile, null, publicCertChainFile, privateKeyFile,
                null, null, null, IdentityCipherSuiteFilter.INSTANCE, null, 0,
                0);
        // mutual authentication as server
        SSLEngine sslServerEngine = sslServerCtx.newEngine(ch.alloc());
        // require client (mutual) authentication
        sslServerEngine.setNeedClientAuth(true);
        return new SslHandler(sslServerEngine);
    }

    @Override
    public ChannelOutboundHandler getTcpClientHandler(Channel ch)
            throws SSLException {
        SslContext sslClientCtx = SslContext.newClientContext(null,
                trustCertChainFile, null, publicCertChainFile, privateKeyFile,
                null, null, null, IdentityCipherSuiteFilter.INSTANCE, null, 0,
                0);
        // mutual authentication as client
        return sslClientCtx.newHandler(ch.alloc());
    }

    @Override
    public ChannelOutboundHandler getUdpServerHandler(Channel ch) {
        final DtlsServer dtlsServer = new DtlsServer(trustCertChainFile,
                publicCertChainFile, privateKeyFile);
        return new DtlsServerHandler(dtlsServer, secureRandom);
    }

    @Override
    public ChannelOutboundHandler getUdpClientHandler(Channel ch) {
        final DtlsClient dtlsClient = new DtlsClient(null, trustCertChainFile,
                publicCertChainFile, privateKeyFile);
        return new DtlsClientHandler(dtlsClient, secureRandom);
    }

}
