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
import java.util.Hashtable;
import java.util.Vector;

import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.crypto.tls.AlertDescription;
import org.bouncycastle.crypto.tls.AlertLevel;
import org.bouncycastle.crypto.tls.CertificateRequest;
import org.bouncycastle.crypto.tls.CipherSuite;
import org.bouncycastle.crypto.tls.ClientCertificateType;
import org.bouncycastle.crypto.tls.DefaultTlsClient;
import org.bouncycastle.crypto.tls.MaxFragmentLength;
import org.bouncycastle.crypto.tls.ProtocolVersion;
import org.bouncycastle.crypto.tls.SignatureAlgorithm;
import org.bouncycastle.crypto.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.crypto.tls.TlsAuthentication;
import org.bouncycastle.crypto.tls.TlsCredentials;
import org.bouncycastle.crypto.tls.TlsExtensionsUtils;
import org.bouncycastle.crypto.tls.TlsSession;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DtlsClient extends DefaultTlsClient {

    private static final Logger log = LoggerFactory.getLogger(DtlsClient.class);

    protected TlsSession session;
    private final File root;
    private final File cert;
    private final File key;

    public DtlsClient(TlsSession session, File root, File cert, File key) {
        this.session = session;
        this.root = root;
        this.cert = cert;
        this.key = key;
    }

    public TlsSession getSessionToResume() {
        return this.session;
    }

    public void notifyAlertRaised(short alertLevel, short alertDescription, String message, Throwable cause) {
        PrintStream out = (alertLevel == AlertLevel.fatal) ? System.err : System.out;
        out.println("DTLS client raised alert: " + AlertLevel.getText(alertLevel) + ", "
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
        out.println("DTLS client received alert: " + AlertLevel.getText(alertLevel) + ", "
                + AlertDescription.getText(alertDescription));
        out.close();
    }

    public ProtocolVersion getClientVersion() {
        return ProtocolVersion.DTLSv12;
    }

    public ProtocolVersion getMinimumVersion() {
        return ProtocolVersion.DTLSv10;
    }

    public int[] getCipherSuites() {
        return Arrays.concatenate(super.getCipherSuites(), new int[] {
                CipherSuite.DRAFT_TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256, });
    }

    public Hashtable<?, ?> getClientExtensions() throws IOException {
        Hashtable<?, ?> clientExtensions = TlsExtensionsUtils.ensureExtensionsInitialised(super.getClientExtensions());
        TlsExtensionsUtils.addEncryptThenMACExtension(clientExtensions);
        // TODO[draft-ietf-tls-session-hash-01] Enable once code-point assigned (only for compatible server though)
        // TlsExtensionsUtils.addExtendedMasterSecretExtension(clientExtensions);
        TlsExtensionsUtils.addMaxFragmentLengthExtension(clientExtensions, MaxFragmentLength.pow2_9);
        TlsExtensionsUtils.addTruncatedHMacExtension(clientExtensions);
        return clientExtensions;
    }

    public void notifyServerVersion(ProtocolVersion serverVersion) throws IOException {
        super.notifyServerVersion(serverVersion);

        log.trace("Negotiated " + serverVersion);
    }

    public TlsAuthentication getAuthentication() throws IOException {
        return new TlsAuthentication() {
            public void notifyServerCertificate(org.bouncycastle.crypto.tls.Certificate serverCertificate)
                    throws IOException {
                Certificate[] chain = serverCertificate.getCertificateList();
                log.trace("Received server certificate chain of length " + chain.length);
                for (int i = 0; i != chain.length; i++) {
                    Certificate entry = chain[i];
                    // TODO Create fingerprint based on certificate signature algorithm digest
                    log.trace("    fingerprint:SHA-256 " + DtlsUtils.fingerprint(entry) + " (" + entry.getSubject()
                            + ")");
                }
            }

            public TlsCredentials getClientCredentials(CertificateRequest certificateRequest) throws IOException {
                short[] certificateTypes = certificateRequest.getCertificateTypes();
                if (certificateTypes == null || !Arrays.contains(certificateTypes, ClientCertificateType.rsa_sign)) {
                    return null;
                }

                SignatureAndHashAlgorithm signatureAndHashAlgorithm = null;
                Vector<?> sigAlgs = certificateRequest.getSupportedSignatureAlgorithms();
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
        };
    }

    public void notifyHandshakeComplete() throws IOException {
        super.notifyHandshakeComplete();

        TlsSession newSession = context.getResumableSession();
        if (newSession != null) {
            byte[] newSessionID = newSession.getSessionID();
            String hex = Hex.toHexString(newSessionID);

            if (this.session != null && Arrays.areEqual(this.session.getSessionID(), newSessionID)) {
                log.trace("Resumed session: " + hex);
            } else {
                log.trace("Established session: " + hex);
            }

            this.session = newSession;
        }
    }
}
