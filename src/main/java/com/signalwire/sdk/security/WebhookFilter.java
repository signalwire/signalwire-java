/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.security;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet filter that enforces SignalWire webhook signature validation on incoming requests.
 *
 * <p>Wraps the request in a body-caching wrapper before reading the body so that downstream
 * handlers (including framework parsers) can re-read the same raw bytes. This is the canonical Java
 * framework adapter for webhook signature validation.
 *
 * <p>Behavior:
 *
 * <ol>
 *   <li>Read the raw request body once and cache it.
 *   <li>Extract {@code X-SignalWire-Signature} (or {@code X-Twilio-Signature}) from the request
 *       headers.
 *   <li>Reconstruct the public URL — uses {@code X-Forwarded-Proto} / {@code X-Forwarded-Host} when
 *       {@code trustProxy} is set, otherwise falls back to {@link
 *       HttpServletRequest#getRequestURL()} + {@link HttpServletRequest#getQueryString()}.
 *   <li>Call {@link WebhookValidator#validateWebhookSignature(String, String, String, String)}.
 *   <li>On invalid: respond {@code 403 Forbidden} and do <b>not</b> call {@code chain.doFilter}.
 *   <li>On valid: forward via {@code chain.doFilter} with the body-caching request wrapper so
 *       downstream handlers can re-read the body.
 * </ol>
 *
 * <p>The filter intentionally does <b>not</b> log the signing key, the incoming signature, or which
 * scheme branch tripped — disclosing those weakens the constant-time defense and gives attackers
 * data to differentiate scheme branches with.
 *
 * <p>Note: this class targets the {@code javax.servlet} API (Servlet 4.x / Tomcat 9 / Jetty 9).
 * Apps on Servlet 5.x / Jakarta EE 9+ should bridge via a {@code javax → jakarta} shim or copy this
 * class into their codebase with the import package swapped.
 */
public class WebhookFilter implements Filter {

  private final String signingKey;
  private final boolean trustProxy;

  /**
   * Construct a filter with proxy trust disabled.
   *
   * @param signingKey customer's Signing Key. Must be non-empty; an empty value is a programming
   *     error and the constructor throws {@link IllegalArgumentException}.
   */
  public WebhookFilter(String signingKey) {
    this(signingKey, false);
  }

  /**
   * Construct a filter.
   *
   * @param signingKey customer's Signing Key. Required.
   * @param trustProxy when {@code true}, honor {@code X-Forwarded-Proto} / {@code X-Forwarded-Host}
   *     for URL reconstruction. {@code false} by default since proxy headers are spoofable; only
   *     enable when you control the proxy chain.
   */
  public WebhookFilter(String signingKey, boolean trustProxy) {
    if (signingKey == null || signingKey.isEmpty()) {
      throw new IllegalArgumentException("signingKey is required");
    }
    this.signingKey = signingKey;
    this.trustProxy = trustProxy;
  }

  /**
   * No-op: every setting is supplied to the constructor rather than through filter config.
   *
   * @param filterConfig the servlet container's filter config (unused).
   */
  @Override
  public void init(FilterConfig filterConfig) {
    // No init-time configuration — all state passed to the constructor.
  }

  /** No-op: the filter holds no resources to release. */
  @Override
  public void destroy() {
    // No resources to release.
  }

  /**
   * Validate the request's SignalWire webhook signature and reject it if it does not check out.
   *
   * <p>Caches the body first so downstream handlers can still read it, then takes the signature
   * from {@code X-SignalWire-Signature} (falling back to {@code X-Twilio-Signature}) and validates
   * it against the reconstructed URL. A MISSING signature header is rejected exactly like an
   * invalid one: HTTP 403 with no body detail, and the chain is not invoked. A non-HTTP request is
   * passed through untouched, since it cannot carry a signed webhook.
   *
   * <p>Nothing about the failure is logged — not the signing key, not the presented signature, not
   * which branch rejected it — because that detail is what an attacker would use to probe the
   * comparison.
   *
   * @param request the incoming request.
   * @param response the response to write a 403 to on rejection.
   * @param chain the filter chain, invoked only on a valid signature, with the body-caching
   *     wrapper.
   * @throws IOException if reading the body or writing the response fails.
   * @throws ServletException if the downstream chain raises one.
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest)) {
      // Non-HTTP request — let it through unchanged. Filter only
      // applies to HTTP signed webhook traffic.
      chain.doFilter(request, response);
      return;
    }
    HttpServletRequest httpReq = (HttpServletRequest) request;
    HttpServletResponse httpResp = (HttpServletResponse) response;

    // 1. Cache the body so downstream handlers can re-read it.
    CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(httpReq);
    String rawBody = wrapped.getCachedBodyAsString();

    // 2. Extract signature header.
    String signature = httpReq.getHeader(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER);
    if (signature == null || signature.isEmpty()) {
      signature = httpReq.getHeader(WebhookValidator.TWILIO_COMPAT_SIGNATURE_HEADER);
    }
    if (signature == null || signature.isEmpty()) {
      // Missing header — reject with 403, no body detail.
      httpResp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    // 3. Reconstruct the public URL.
    String url = reconstructUrl(httpReq);

    // 4. Validate. Catch IllegalArgumentException (e.g. somehow a null
    //    body) and treat as invalid rather than 500-ing.
    boolean ok;
    try {
      ok = WebhookValidator.validateWebhookSignature(signingKey, signature, url, rawBody);
    } catch (IllegalArgumentException ex) {
      ok = false;
    }
    if (!ok) {
      httpResp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    // 5. Forward with the cached-body wrapper so downstream parsers can
    //    consume the body.
    chain.doFilter(wrapped, response);
  }

  /**
   * Reconstruct the public URL the platform POSTed to. When {@code trustProxy} is set, prefer
   * {@code X-Forwarded-Proto} / {@code X-Forwarded-Host}. Otherwise rely on the servlet container's
   * view of the URL.
   */
  private String reconstructUrl(HttpServletRequest req) {
    String pathAndQuery = req.getRequestURI();
    String query = req.getQueryString();
    if (query != null && !query.isEmpty()) {
      pathAndQuery = pathAndQuery + "?" + query;
    }

    if (trustProxy) {
      String fwdHost = req.getHeader("X-Forwarded-Host");
      String fwdProto = req.getHeader("X-Forwarded-Proto");
      if (fwdHost != null && !fwdHost.isEmpty()) {
        String proto = (fwdProto != null && !fwdProto.isEmpty()) ? fwdProto : "https";
        return proto + "://" + fwdHost + pathAndQuery;
      }
    }

    StringBuilder url = new StringBuilder(req.getRequestURL().toString());
    if (query != null && !query.isEmpty()) {
      url.append('?').append(query);
    }
    return url.toString();
  }

  // ------------------------------------------------------------------
  // Cached-body wrapper. Servlet streams aren't naively re-readable, so we
  // copy the bytes into memory once on first read and serve subsequent
  // getInputStream / getReader calls from the buffer.
  // ------------------------------------------------------------------

  /**
   * {@link HttpServletRequestWrapper} that buffers the request body so it can be read multiple
   * times (once by the filter for signature validation, once by the downstream handler for
   * parsing).
   */
  public static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
      super(request);
      // Read the entire body into memory. Real-world webhook bodies are
      // small (KBs), so this is fine; if a deployment has multi-MB
      // bodies, the surrounding container should rate-limit upstream.
      this.cachedBody = request.getInputStream().readAllBytes();
    }

    /** Snapshot of the cached body bytes. Defensive copy. */
    public byte[] getCachedBody() {
      return cachedBody.clone();
    }

    /** Cached body decoded as UTF-8 (or the request's declared charset). */
    public String getCachedBodyAsString() {
      String enc = getCharacterEncoding();
      Charset cs = (enc != null && !enc.isEmpty()) ? Charset.forName(enc) : StandardCharsets.UTF_8;
      return new String(cachedBody, cs);
    }

    /**
     * A stream over the cached body, so the body can be read again after the filter consumed it.
     *
     * @return a stream over the cached bytes.
     */
    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
      return new ServletInputStream() {
        /**
         * Read one byte from the cached body.
         *
         * @return the byte, or {@code -1} at the end.
         */
        @Override
        public int read() {
          return bais.read();
        }

        /**
         * Read a block from the cached body.
         *
         * @param b the destination buffer.
         * @param off offset into the buffer.
         * @param len maximum bytes to read.
         * @return the number of bytes read, or {@code -1} at the end.
         */
        @Override
        public int read(byte[] b, int off, int len) {
          return bais.read(b, off, len);
        }

        /**
         * Whether the cached body has been fully consumed.
         *
         * @return {@code true} when nothing remains.
         */
        @Override
        public boolean isFinished() {
          return bais.available() == 0;
        }

        /**
         * Always ready: the body is already in memory, so a read never blocks.
         *
         * @return {@code true}.
         */
        @Override
        public boolean isReady() {
          return true;
        }

        /**
         * Not supported — the signed-webhook flow is synchronous request/response, so the cached
         * stream offers no async path.
         *
         * @param readListener the listener (ignored).
         * @throws UnsupportedOperationException always.
         */
        @Override
        public void setReadListener(ReadListener readListener) {
          // Async I/O not supported on the cached stream; the
          // signed-webhook flow is request/response synchronous,
          // so this is fine.
          throw new UnsupportedOperationException(
              "ReadListener not supported on cached body stream");
        }
      };
    }

    /**
     * A reader over the cached body, decoded with the request's declared charset or UTF-8.
     *
     * @return a reader over the cached bytes.
     */
    @Override
    public BufferedReader getReader() {
      String enc = getCharacterEncoding();
      Charset cs = (enc != null && !enc.isEmpty()) ? Charset.forName(enc) : StandardCharsets.UTF_8;
      return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cachedBody), cs));
    }
  }
}
