/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.utils;

import com.signalwire.sdk.logging.Logger;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.function.Function;

/**
 * SSRF-prevention guard for user-supplied URLs.
 * <p>
 * Mirrors Python's <code>signalwire.utils.url_validator.validate_url</code>:
 * rejects non-http(s) schemes, missing hostnames, and any URL whose
 * hostname resolves to a private / loopback / link-local / cloud-metadata
 * IP. The {@code allowPrivate} parameter (or the
 * {@code SWML_ALLOW_PRIVATE_URLS} env var with value "1", "true" or "yes",
 * case-insensitive) bypasses the IP-blocklist check.
 * <p>
 * Projected onto the Python free function name {@code validate_url} via
 * scripts/enumerate_signatures.py.
 */
public final class UrlValidator {

    private static final Logger LOG = Logger.getLogger("signalwire.url_validator");

    /**
     * The cross-port SSRF block list. Order matches the Python reference
     * for ease of cross-language review.
     */
    private static final String[] BLOCKED_NETWORKS = {
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "127.0.0.0/8",
            "169.254.0.0/16", // link-local / cloud metadata
            "0.0.0.0/8",
            "::1/128",
            "fc00::/7",  // IPv6 private (ULA)
            "fe80::/10", // IPv6 link-local
    };

    /**
     * Pluggable DNS resolver. Tests inject a fake to keep the suite
     * hermetic; production calls {@link InetAddress#getAllByName(String)}.
     */
    static volatile Function<String, InetAddress[]> resolver = hostname -> {
        try {
            return InetAddress.getAllByName(hostname);
        } catch (Exception e) {
            return null;
        }
    };

    private UrlValidator() {
        // utility class
    }

    /**
     * Validate that a URL is safe to fetch.
     *
     * @param url           URL string to validate.
     * @param allowPrivate  when true, bypass the IP-blocklist check.
     * @return true when the URL is safe to fetch, false otherwise.
     */
    public static boolean validateUrl(String url, boolean allowPrivate) {
        if (url == null) {
            LOG.warn("URL rejected: null URL");
            return false;
        }
        URI parsed;
        try {
            parsed = new URI(url);
        } catch (URISyntaxException e) {
            LOG.warn("URL validation error: %s", e.getMessage());
            return false;
        }

        String scheme = parsed.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            LOG.warn("URL rejected: invalid scheme %s", scheme);
            return false;
        }

        String hostname = parsed.getHost();
        if (hostname == null || hostname.isEmpty()) {
            LOG.warn("URL rejected: no hostname");
            return false;
        }

        if (allowPrivate || envAllowsPrivate()) {
            return true;
        }

        InetAddress[] resolved = resolver.apply(hostname);
        if (resolved == null || resolved.length == 0) {
            LOG.warn("URL rejected: could not resolve hostname %s", hostname);
            return false;
        }

        for (InetAddress addr : resolved) {
            for (String cidr : BLOCKED_NETWORKS) {
                if (cidrContains(cidr, addr)) {
                    LOG.warn(
                            "URL rejected: %s resolves to blocked IP %s (in %s)",
                            hostname, addr.getHostAddress(), cidr);
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean envAllowsPrivate() {
        String v = System.getenv("SWML_ALLOW_PRIVATE_URLS");
        if (v == null) return false;
        String low = v.toLowerCase();
        return low.equals("1") || low.equals("true") || low.equals("yes");
    }

    /**
     * Test whether {@code addr} falls inside {@code cidr}.
     * Handles both IPv4 (<code>a.b.c.d/N</code>) and IPv6
     * (<code>::1/128</code>, <code>fc00::/7</code>, ...).
     */
    private static boolean cidrContains(String cidr, InetAddress addr) {
        try {
            int slash = cidr.indexOf('/');
            String netStr = cidr.substring(0, slash);
            int prefix = Integer.parseInt(cidr.substring(slash + 1));
            InetAddress net = InetAddress.getByName(netStr);
            byte[] netBytes = net.getAddress();
            byte[] addrBytes = addr.getAddress();
            if (netBytes.length != addrBytes.length) {
                return false; // IPv4 vs IPv6 mismatch
            }
            int fullBytes = prefix / 8;
            int remBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (netBytes[i] != addrBytes[i]) return false;
            }
            if (remBits > 0) {
                int mask = (0xFF << (8 - remBits)) & 0xFF;
                if ((netBytes[fullBytes] & mask) != (addrBytes[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Internal accessor for tests. */
    static List<String> blockedNetworks() {
        return List.of(BLOCKED_NETWORKS);
    }
}
