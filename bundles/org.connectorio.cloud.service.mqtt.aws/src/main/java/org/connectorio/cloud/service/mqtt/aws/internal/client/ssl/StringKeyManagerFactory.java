/*
 * Copyright (C) 2019-2021 ConnectorIO Sp. z o.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.connectorio.cloud.service.mqtt.aws.internal.client.ssl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import javax.net.ssl.KeyManagerFactory;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StringKeyManagerFactory {

  private final Logger logger = LoggerFactory.getLogger(StringKeyManagerFactory.class);
  private final char[] password;
  private final String privateKey;
  private final String certificate;

  public StringKeyManagerFactory(char[] password, String privateKey, String certificate) {
    this.password = password;
    this.privateKey = privateKey;
    this.certificate = certificate;
  }

  public KeyManagerFactory createKeyManager() throws Exception {
    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, null); // initialize

    Object privateKeyObj = parse(password, privateKey);
    Object certificateObj = parse(password, certificate);

    if (privateKeyObj instanceof PrivateKey && certificateObj instanceof Certificate) {
      keyStore.setKeyEntry("key", (PrivateKey) privateKeyObj, password, new Certificate[] {(Certificate) certificateObj});
      keyManagerFactory.init(keyStore, password);
    }

    return keyManagerFactory;
  }

  private Object parse(char[] password, String value) throws IOException, PKCSException, CertificateException {
    try (PEMParser pemParser = new PEMParser(new StringReader(value))) {
      Object o = pemParser.readObject();
      if (o instanceof PKCS8EncryptedPrivateKeyInfo) { // encrypted private key in pkcs8-format
        PKCS8EncryptedPrivateKeyInfo epki = (PKCS8EncryptedPrivateKeyInfo) o;
        JcePKCSPBEInputDecryptorProviderBuilder builder = new JcePKCSPBEInputDecryptorProviderBuilder().setProvider("BC");
        InputDecryptorProvider idp = builder.build(password);
        PrivateKeyInfo pki = epki.decryptPrivateKeyInfo(idp);
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();//.setProvider("BC");
        PrivateKey privateKey = converter.getPrivateKey(pki);
        logger.info("Crated encrypted private key: {}", privateKey);
        return privateKey;
      } else if (o instanceof PEMKeyPair) {
        PEMKeyPair pkp = (PEMKeyPair) o;
        PrivateKeyInfo pki = pkp.getPrivateKeyInfo();
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();//.setProvider("BC");
        PrivateKey privateKey = converter.getPrivateKey(pki);
        logger.info("Crated key pair: {}", privateKey);
        return privateKey;
      } else if (o instanceof X509CertificateHolder) {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        InputStream in = new ByteArrayInputStream(((X509CertificateHolder) o).getEncoded());
        Certificate certificate = certFactory.generateCertificate(in);
        logger.info("Crated certificate: {}", certificate);
        return certificate;
      } else {
        throw new PKCSException("Invalid encrypted private key class: " + o.getClass().getName());
      }
    }
  }

}
