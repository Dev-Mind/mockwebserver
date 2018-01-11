/*
 * Copyright (C) 2016 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.devmind.mockwebserver.internal.tls;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.UUID;

/**
 * A certificate and its private key. This can be used on the server side by HTTPS servers, or on
 * the client side to verify those HTTPS servers. A held certificate can also be used to sign other
 * held certificates, as done in practice by certificate authorities.
 */
public final class HeldCertificate {
    public final X509Certificate certificate;
    public final KeyPair keyPair;

    public HeldCertificate(X509Certificate certificate, KeyPair keyPair) {
        this.certificate = certificate;
        this.keyPair = keyPair;
    }

    public static final class Builder {
        static {
            Security.addProvider(new BouncyCastleProvider());
        }

        private final long duration = 1000L * 60 * 60 * 24; // One day.
        private String hostname;
        private String serialNumber = "1";

        public Builder serialNumber(String serialNumber) {
            this.serialNumber = serialNumber;
            return this;
        }

        /**
         * Set this certificate's name. Typically this is the URL hostname for TLS certificates. This is
         * the CN (common name) in the certificate. Will be a random string if no value is provided.
         */
        public Builder commonName(String hostname) {
            this.hostname = hostname;
            return this;
        }

        public HeldCertificate build() throws GeneralSecurityException, OperatorCreationException {
            // Subject, public & private keys for this certificate.
            KeyPair heldKeyPair = generateKeyPair();
            X500Name subject = hostname != null
                    ? new X500Name("CN=" + hostname)
                    : new X500Name("CN=" + UUID.randomUUID());

            // Generate & sign the certificate.
            long now = System.currentTimeMillis();

            SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(heldKeyPair.getPublic().getEncoded());

            X509v3CertificateBuilder certGen = new X509v3CertificateBuilder(
                    subject,
                    new BigInteger(serialNumber),
                    new Date(now),
                    new Date(now + duration),
                    subject,
                    publicKeyInfo);

            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(heldKeyPair.getPrivate());
            X509CertificateHolder certHolder = certGen.build(signer);
            X509Certificate cert = (new JcaX509CertificateConverter().getCertificate(certHolder));

            CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
            X509Certificate certificate = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(cert.getEncoded()));

            return new HeldCertificate(certificate, heldKeyPair);
        }

        public KeyPair generateKeyPair() throws GeneralSecurityException {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
            keyPairGenerator.initialize(1024, new SecureRandom());
            return keyPairGenerator.generateKeyPair();
        }
    }
}
