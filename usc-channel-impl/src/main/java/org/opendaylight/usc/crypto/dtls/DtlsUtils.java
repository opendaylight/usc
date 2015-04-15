/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.usc.crypto.dtls;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.tls.Certificate;
import org.bouncycastle.crypto.tls.DefaultTlsAgreementCredentials;
import org.bouncycastle.crypto.tls.DefaultTlsEncryptionCredentials;
import org.bouncycastle.crypto.tls.DefaultTlsSignerCredentials;
import org.bouncycastle.crypto.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.crypto.tls.TlsAgreementCredentials;
import org.bouncycastle.crypto.tls.TlsContext;
import org.bouncycastle.crypto.tls.TlsEncryptionCredentials;
import org.bouncycastle.crypto.tls.TlsSignerCredentials;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

public class DtlsUtils {
    static String fingerprint(org.bouncycastle.asn1.x509.Certificate c) throws IOException {
        byte[] der = c.getEncoded();
        byte[] sha1 = sha256DigestOf(der);
        byte[] hexBytes = Hex.encode(sha1);
        String hex = new String(hexBytes, "ASCII").toUpperCase();

        StringBuffer fp = new StringBuffer();
        int i = 0;
        fp.append(hex.substring(i, i + 2));
        while ((i += 2) < hex.length()) {
            fp.append(':');
            fp.append(hex.substring(i, i + 2));
        }
        return fp.toString();
    }

    static byte[] sha256DigestOf(byte[] input) {
        SHA256Digest d = new SHA256Digest();
        d.update(input, 0, input.length);
        byte[] result = new byte[d.getDigestSize()];
        d.doFinal(result, 0);
        return result;
    }

    static TlsAgreementCredentials loadAgreementCredentials(TlsContext context, String[] certResources,
            String keyResource) throws IOException {
        Certificate certificate = loadCertificateChain(certResources);
        AsymmetricKeyParameter privateKey = loadPrivateKeyResource(keyResource);

        return new DefaultTlsAgreementCredentials(certificate, privateKey);
    }

    static TlsEncryptionCredentials loadEncryptionCredentials(TlsContext context, String[] certResources,
            String keyResource) throws IOException {
        Certificate certificate = loadCertificateChain(certResources);
        AsymmetricKeyParameter privateKey = loadPrivateKeyResource(keyResource);

        return new DefaultTlsEncryptionCredentials(context, certificate, privateKey);
    }

    static TlsSignerCredentials loadSignerCredentials(TlsContext context, String[] certResources, String keyResource,
            SignatureAndHashAlgorithm signatureAndHashAlgorithm) throws IOException {
        Certificate certificate = loadCertificateChain(certResources);
        AsymmetricKeyParameter privateKey = loadPrivateKeyResource(keyResource);

        return new DefaultTlsSignerCredentials(context, certificate, privateKey, signatureAndHashAlgorithm);
    }

    static Certificate loadCertificateChain(String[] resources) throws IOException {
        org.bouncycastle.asn1.x509.Certificate[] chain = new org.bouncycastle.asn1.x509.Certificate[resources.length];
        for (int i = 0; i < resources.length; ++i) {
            chain[i] = loadCertificateResource(resources[i]);
        }
        return new Certificate(chain);
    }

    static org.bouncycastle.asn1.x509.Certificate loadCertificateResource(String resource) throws IOException {
        PemObject pem = loadPemResource(resource);
        if (pem.getType().endsWith("CERTIFICATE")) {
            return org.bouncycastle.asn1.x509.Certificate.getInstance(pem.getContent());
        }
        throw new IllegalArgumentException("'resource' doesn't specify a valid certificate");
    }

    static AsymmetricKeyParameter loadPrivateKeyResource(String resource) throws IOException {
        PemObject pem = loadPemResource(resource);
        if (pem.getType().endsWith("RSA PRIVATE KEY")) {
            RSAPrivateKey rsa = RSAPrivateKey.getInstance(pem.getContent());
            return new RSAPrivateCrtKeyParameters(rsa.getModulus(), rsa.getPublicExponent(), rsa.getPrivateExponent(),
                    rsa.getPrime1(), rsa.getPrime2(), rsa.getExponent1(), rsa.getExponent2(), rsa.getCoefficient());
        }
        if (pem.getType().endsWith("PRIVATE KEY")) {
            return PrivateKeyFactory.createKey(pem.getContent());
        }
        throw new IllegalArgumentException("'resource' doesn't specify a valid private key");
    }

    static PemObject loadPemResource(String resource) throws IOException {
        // InputStream s = TlsTestUtils.class.getResourceAsStream(resource);
        InputStream s = new FileInputStream(resource);
        PemReader p = new PemReader(new InputStreamReader(s));
        PemObject o = p.readPemObject();
        p.close();
        return o;
    }

}
