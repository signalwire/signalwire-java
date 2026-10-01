/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.utils;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * An HTTP session for fetching user-supplied URLs.
 *
 * <p>Checking a URL with {@link UrlValidator#validateUrl} before fetching it isn't enough on its
 * own: the server can redirect to an internal address. This session checks the URL of every request
 * it sends — redirects included, each hop followed by hand — and refuses any private, internal or
 * invalid target. {@code SWML_ALLOW_PRIVATE_URLS} turns the check off, as it does for {@code
 * validateUrl}.
 *
 * <p>It connects directly, ignoring the JVM's proxy configuration, because through a proxy the
 * address check can't apply. Set {@code SWML_URL_FETCH_USE_PROXY} to use the default proxy
 * selector, with a proxy that restricts destinations itself.
 */
public final class PublicSession {

  /** The most redirects one fetch follows (the reference's Requests default). */
  public static final int MAX_REDIRECTS = 30;

  private final boolean allowPrivate;
  private final Map<String, String> headers = new LinkedHashMap<>();

  /** A session that refuses private and internal targets. */
  public PublicSession() {
    this(false);
  }

  /**
   * A session; {@code allowPrivate} turns the address check off.
   *
   * @param allowPrivate when {@code true}, private and internal targets are allowed
   */
  public PublicSession(boolean allowPrivate) {
    this.allowPrivate = allowPrivate;
  }

  /**
   * The headers sent with every request (mutable, like a Requests session's {@code headers}).
   *
   * @return the live header map.
   */
  public Map<String, String> getHeaders() {
    return headers;
  }

  /** Raised when a request (or a redirect hop) targets a private, internal or invalid URL. */
  public static final class BlockedUrlException extends IOException {
    private static final long serialVersionUID = 1L;

    BlockedUrlException(String message) {
      super(message);
    }
  }

  /**
   * GET {@code url}, checking it — and, when {@code followRedirects}, each redirect target — before
   * it is requested.
   *
   * @param url the URL to fetch
   * @param timeout per-request timeout
   * @param followRedirects follow redirects (each hop checked) rather than returning the 3xx
   * @return the final response
   * @throws BlockedUrlException when a URL in the chain is private, internal or invalid
   * @throws IOException on a transport failure or too many redirects
   * @throws InterruptedException when interrupted while waiting for the response
   */
  public HttpResponse<String> get(String url, Duration timeout, boolean followRedirects)
      throws IOException, InterruptedException {
    HttpClient.Builder builder =
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(timeout);
    if (directOnly()) {
      builder.proxy(HttpClient.Builder.NO_PROXY);
    } else {
      builder.proxy(ProxySelector.getDefault());
    }
    HttpClient client = builder.build();
    String target = url;
    for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
      if (!UrlValidator.validateUrl(target, allowPrivate)) {
        throw new BlockedUrlException(
            "URL rejected: "
                + com.signalwire.sdk.security.SecurityUtils.redactUrl(target)
                + " is private, internal or invalid");
      }
      HttpRequest.Builder req = HttpRequest.newBuilder().uri(URI.create(target)).timeout(timeout);
      headers.forEach(req::header);
      HttpResponse<String> response =
          client.send(req.GET().build(), HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      boolean redirect =
          status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
      if (!followRedirects || !redirect) {
        return response;
      }
      String location = response.headers().firstValue("Location").orElse(null);
      if (location == null) {
        return response;
      }
      target = URI.create(target).resolve(location).toString();
    }
    throw new IOException("Too many redirects fetching " + url);
  }

  /** True when requests must bypass proxies, so the address check applies. */
  private boolean directOnly() {
    return !(allowPrivate
        || envTrue("SWML_ALLOW_PRIVATE_URLS")
        || envTrue("SWML_URL_FETCH_USE_PROXY"));
  }

  private static boolean envTrue(String name) {
    String v = System.getenv(name);
    if (v == null) {
      return false;
    }
    String low = v.toLowerCase(Locale.ROOT);
    return "1".equals(low) || "true".equals(low) || "yes".equals(low);
  }
}
