/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity tests for {@link UrlValidator#validateUrl(String, boolean)}.
 * Mirrors signalwire-python tests/unit/utils/test_url_validator.py.
 * The DNS resolver is stubbed via the package-private static field so
 * the suite does not require network access.
 */
class UrlValidatorTest {

    private Function<String, InetAddress[]> originalResolver;

    @BeforeEach
    void saveResolver() {
        originalResolver = UrlValidator.resolver;
    }

    @AfterEach
    void restoreResolver() {
        UrlValidator.resolver = originalResolver;
    }

    private void stubResolver(String ip) {
        UrlValidator.resolver = host -> {
            try {
                return new InetAddress[]{ InetAddress.getByName(ip) };
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private void stubFailedResolver() {
        UrlValidator.resolver = host -> null;
    }

    // --- Scheme -----------------------------------------------------------

    @Test
    void httpSchemeAllowed() {
        stubResolver("1.2.3.4");
        assertTrue(UrlValidator.validateUrl("http://example.com", false));
    }

    @Test
    void httpsSchemeAllowed() {
        stubResolver("1.2.3.4");
        assertTrue(UrlValidator.validateUrl("https://example.com", false));
    }

    @Test
    void ftpSchemeRejected() {
        assertFalse(UrlValidator.validateUrl("ftp://example.com", false));
    }

    @Test
    void fileSchemeRejected() {
        assertFalse(UrlValidator.validateUrl("file:///etc/passwd", false));
    }

    @Test
    void javascriptSchemeRejected() {
        // "javascript:alert(1)" parses as opaque URI (no host); rejected.
        assertFalse(UrlValidator.validateUrl("javascript:alert(1)", false));
    }

    // --- Hostname --------------------------------------------------------

    @Test
    void noHostnameRejected() {
        assertFalse(UrlValidator.validateUrl("http://", false));
    }

    @Test
    void unresolvableHostnameRejected() {
        stubFailedResolver();
        assertFalse(UrlValidator.validateUrl("http://nonexistent.invalid", false));
    }

    // --- Blocked ranges ---------------------------------------------------

    @Test
    void loopbackIpv4Rejected() {
        stubResolver("127.0.0.1");
        assertFalse(UrlValidator.validateUrl("http://localhost", false));
    }

    @Test
    void rfc1918_10Rejected() {
        stubResolver("10.0.0.5");
        assertFalse(UrlValidator.validateUrl("http://internal", false));
    }

    @Test
    void rfc1918_192Rejected() {
        stubResolver("192.168.1.1");
        assertFalse(UrlValidator.validateUrl("http://router", false));
    }

    @Test
    void rfc1918_172Rejected() {
        stubResolver("172.16.0.1");
        assertFalse(UrlValidator.validateUrl("http://corp", false));
    }

    @Test
    void linkLocalMetadataRejected() {
        stubResolver("169.254.169.254");
        assertFalse(UrlValidator.validateUrl("http://metadata", false));
    }

    @Test
    void zeroIpRejected() {
        stubResolver("0.0.0.0");
        assertFalse(UrlValidator.validateUrl("http://void", false));
    }

    @Test
    void ipv6LoopbackRejected() {
        stubResolver("::1");
        assertFalse(UrlValidator.validateUrl("http://[::1]", false));
    }

    @Test
    void ipv6LinkLocalRejected() {
        stubResolver("fe80::1");
        assertFalse(UrlValidator.validateUrl("http://link-local", false));
    }

    @Test
    void ipv6PrivateRejected() {
        stubResolver("fc00::1");
        assertFalse(UrlValidator.validateUrl("http://ipv6-private", false));
    }

    @Test
    void publicIpAllowed() {
        stubResolver("8.8.8.8");
        assertTrue(UrlValidator.validateUrl("http://dns.google", false));
    }

    // --- allow_private bypass --------------------------------------------

    @Test
    void allowPrivateParamBypassesCheck() {
        // No resolver stub: allow_private short-circuits BEFORE DNS.
        assertTrue(UrlValidator.validateUrl("http://10.0.0.5", true));
    }

    @Test
    void blockedNetworksHasAllNine() {
        assertEquals(9, UrlValidator.blockedNetworks().size(),
                "blocked-networks must list all 9 SSRF ranges");
    }
}
