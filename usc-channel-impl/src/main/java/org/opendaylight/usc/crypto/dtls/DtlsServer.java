/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.usc.crypto.dtls;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Vector;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.crypto.tls.AlertDescription;
import org.bouncycastle.crypto.tls.AlertLevel;
import org.bouncycastle.crypto.tls.CertificateRequest;
import org.bouncycastle.crypto.tls.CipherSuite;
import org.bouncycastle.crypto.tls.ClientCertificateType;
import org.bouncycastle.crypto.tls.DefaultTlsServer;
import org.bouncycastle.crypto.tls.HashAlgorithm;
import org.bouncycastle.crypto.tls.ProtocolVersion;
import org.bouncycastle.crypto.tls.SignatureAlgorithm;
import org.bouncycastle.crypto.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.crypto.tls.TlsEncryptionCredentials;
import org.bouncycastle.crypto.tls.TlsSignerCredentials;
import org.bouncycastle.crypto.tls.TlsUtils;
import org.bouncycastle.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DtlsServer extends DefaultTlsServer {

    private static final Logger log = LoggerFactory.getLogger(DtlsServer.class);

    private final File root;
    private final File cert;
    private final File key;

    public DtlsServer(File root, File cert, File key) {
        super();
        this.root = root;
        this.cert = cert;
        this.key = key;
    }

    public void notifyAlertRaised(short alertLevel, short alertDescription, String message, Throwable cause) {
        PrintStream out = (alertLevel == AlertLevel.fatal) ? System.err : System.out;
        out.println("DTLS server raised alert: " + AlertLevel.getText(alertLevel) + ", "
                + AlertDescription.getText(alertDescription));
        if (message != null) {
            out.println(message);
        }
        if (cause != null) {
            cause.printStackTrace(out);
        }
    }

    public void notifyAlertReceived(short alertLevel, short alertDescription) {
        PrintStream out = (alertLevel == AlertLevel.fatal) ? System.err : System.out;
        out.println("DTLS server received alert: " + AlertLevel.getText(alertLevel) + ", "
                + AlertDescription.getText(alertDescription));
        out.close();
    }

    protected int[] getCipherSuites() {
        return Arrays.concatenate(super.getCipherSuites(), new int[] {
                CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_ESTREAM_SALSA20_SHA1, CipherSuite.TLS_ECDHE_RSA_WITH_SALSA20_SHA1,
                CipherSuite.TLS_RSA_WITH_ESTREAM_SALSA20_SHA1, CipherSuite.TLS_RSA_WITH_SALSA20_SHA1, });
    }

    public CertificateRequest getCertificateRequest() throws IOException {
        Vector<SignatureAndHashAlgorithm> serverSigAlgs = null;

        if (TlsUtils.isSignatureAlgorithmsExtensionAllowed(serverVersion)) {
            short[] hashAlgorithms = new short[] { HashAlgorithm.sha512, HashAlgorithm.sha384, HashAlgorithm.sha256,
                    HashAlgorithm.sha224, HashAlgorithm.sha1 };
            short[] signatureAlgorithms = new short[] { SignatureAlgorithm.rsa };

            serverSigAlgs = new Vector<SignatureAndHashAlgorithm>();
            for (int i = 0; i < hashAlgorithms.length; ++i) {
                for (int j = 0; j < signatureAlgorithms.length; ++j) {
                    serverSigAlgs.addElement(new SignatureAndHashAlgorithm(hashAlgorithms[i], signatureAlgorithms[j]));
                }
            }
        }

        Vector<X500Name> certificateAuthorities = new Vector<X500Name>();
        certificateAuthorities.add(DtlsUtils.loadCertificateResource(root.getAbsolutePath()).getSubject());

        return new CertificateRequest(new short[] { ClientCertificateType.rsa_sign }, serverSigAlgs,
                certificateAuthorities);
    }

    public void notifyClientCertificate(org.bouncycastle.crypto.tls.Certificate clientCertificate) throws IOException {
        Certificate[] chain = clientCertificate.getCertificateList();
        log.trace("Received client certificate chain of length " + chain.length);
        for (int i = 0; i != chain.length; i++) {
            Certificate entry = chain[i];
            // TODO Create fingerprint based on certificate signature algorithm digest
            log.trace("    fingerprint:SHA-256 " + DtlsUtils.fingerprint(entry) + " (" + entry.getSubject() + ")");
        }
    }

    protected ProtocolVersion getMaximumVersion() {
        return ProtocolVersion.DTLSv12;
    }

    protected ProtocolVersion getMinimumVersion() {
        return ProtocolVersion.DTLSv10;
    }

    protected TlsEncryptionCredentials getRSAEncryptionCredentials() throws IOException {
        return DtlsUtils.loadEncryptionCredentials(context,
                new String[] { cert.getAbsolutePath(), root.getAbsolutePath() }, key.getAbsolutePath());
    }

    protected TlsSignerCredentials getRSASignerCredentials() throws IOException {
        /*
         * TODO Note that this code fails to provide default value for the client supported algorithms if it wasn't
         * sent.
         */
        SignatureAndHashAlgorithm signatureAndHashAlgorithm = null;
        Vector<?> sigAlgs = supportedSignatureAlgorithms;
        if (sigAlgs != null) {
            for (int i = 0; i < sigAlgs.size(); ++i) {
                SignatureAndHashAlgorithm sigAlg = (SignatureAndHashAlgorithm) sigAlgs.elementAt(i);
                if (sigAlg.getSignature() == SignatureAlgorithm.rsa) {
                    signatureAndHashAlgorithm = sigAlg;
                    break;
                }
            }

            if (signatureAndHashAlgorithm == null) {
                return null;
            }
        }
        return DtlsUtils.loadSignerCredentials(context,
                new String[] { cert.getAbsolutePath(), root.getAbsolutePath() }, key.getAbsolutePath(),
                signatureAndHashAlgorithm);
    }
}
