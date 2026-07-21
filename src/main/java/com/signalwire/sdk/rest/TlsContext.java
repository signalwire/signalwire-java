/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Builds TLS material that trusts a custom CA bundle, for the fleet A5 CA-var contract.
 *
 * <p>The two fleet-standard env vars — {@code SIGNALWIRE_REST_CA_FILE} (REST HTTP transport, via
 * {@link #fromCaFile}) and {@code SIGNALWIRE_RELAY_CA_FILE} (RELAY WebSocket transport, via {@link
 * #socketFactory}) — each name a PEM CA bundle. When set, the owning transport loads that bundle
 * here and trusts ONLY it, so a private / self-signed CA is honored. Mirrors the Python reference
 * (rest/_base.py:163, relay/client.py:131).
 */
public final class TlsContext {

  private TlsContext() {}

  /**
   * Load one or more PEM certificates from {@code caFile} into an {@link SSLContext} whose trust
   * manager trusts exactly those certificates.
   *
   * @param caFile path to a PEM CA bundle
   * @return an SSLContext trusting the bundle
   * @throws IllegalStateException if the file cannot be read or contains no certificates
   */
  public static SSLContext fromCaFile(String caFile) {
    try {
      byte[] pem = Files.readAllBytes(Path.of(caFile));
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      Collection<? extends Certificate> certs;
      try (InputStream in = new java.io.ByteArrayInputStream(pem)) {
        certs = cf.generateCertificates(in);
      }
      if (certs.isEmpty()) {
        throw new IllegalStateException("no certificates found in " + caFile);
      }
      KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
      ks.load(null, null);
      int i = 0;
      for (Certificate cert : certs) {
        ks.setCertificateEntry("ca-" + (i++), cert);
      }
      TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(ks);
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, tmf.getTrustManagers(), null);
      return ctx;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to build TLS context from SIGNALWIRE CA file '" + caFile + "': " + e.getMessage(),
          e);
    }
  }

  /** An {@link SSLSocketFactory} trusting the PEM CA bundle at {@code caFile} (RELAY transport). */
  public static SSLSocketFactory socketFactory(String caFile) {
    return fromCaFile(caFile).getSocketFactory();
  }
}
