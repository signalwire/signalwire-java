/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link WebhookFilter}.
 *
 * <p>Spring's {@code MockHttpServletRequest} would be ideal but pulling in
 * Spring just for tests would balloon the SDK's test dependencies. Instead
 * we hand-roll minimal in-memory implementations of {@link HttpServletRequest}
 * and {@link HttpServletResponse} that cover only the surface the filter
 * actually exercises (body bytes, headers, status, request URL/URI).
 */
class WebhookFilterTest {

    private static final String SIGNING_KEY = "PSKtest1234567890abcdef";
    // Vector A from porting-sdk/webhooks.md.
    private static final String VECTOR_A_URL = "https://example.ngrok.io/webhook";
    private static final String VECTOR_A_BODY =
            "{\"event\":\"call.state\",\"params\":"
                    + "{\"call_id\":\"abc-123\",\"state\":\"answered\"}}";
    private static final String VECTOR_A_SIG =
            "c3c08c1fefaf9ee198a100d5906765a6f394bf0f";

    @Test
    @DisplayName("Valid signature: chain called once, response stays 200")
    void validSignatureForwardsToChain() throws IOException, ServletException {
        FakeRequest req = new FakeRequest("POST", VECTOR_A_URL, VECTOR_A_BODY);
        req.setHeader(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER, VECTOR_A_SIG);
        FakeResponse resp = new FakeResponse();
        FakeChain chain = new FakeChain();

        new WebhookFilter(SIGNING_KEY).doFilter(req.asServlet(), resp.asServlet(), chain);

        assertEquals(1, chain.invocations(), "filter chain should be called exactly once on valid sig");
        assertEquals(200, resp.getStatus(), "default 200 stays untouched on valid sig");
    }

    @Test
    @DisplayName("Invalid signature: 403 returned, chain NOT called")
    void invalidSignatureRejected() throws IOException, ServletException {
        FakeRequest req = new FakeRequest("POST", VECTOR_A_URL, VECTOR_A_BODY);
        req.setHeader(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER, "DEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEF");
        FakeResponse resp = new FakeResponse();
        FakeChain chain = new FakeChain();

        new WebhookFilter(SIGNING_KEY).doFilter(req.asServlet(), resp.asServlet(), chain);

        assertEquals(403, resp.getStatus());
        assertEquals(0, chain.invocations(), "filter chain MUST NOT be called when sig is invalid");
    }

    @Test
    @DisplayName("Missing signature header: 403, chain NOT called")
    void missingHeaderRejected() throws IOException, ServletException {
        FakeRequest req = new FakeRequest("POST", VECTOR_A_URL, VECTOR_A_BODY);
        // No signature header set.
        FakeResponse resp = new FakeResponse();
        FakeChain chain = new FakeChain();

        new WebhookFilter(SIGNING_KEY).doFilter(req.asServlet(), resp.asServlet(), chain);

        assertEquals(403, resp.getStatus());
        assertEquals(0, chain.invocations());
    }

    @Test
    @DisplayName("X-Twilio-Signature alias is accepted as fallback")
    void twilioSignatureAliasAccepted() throws IOException, ServletException {
        FakeRequest req = new FakeRequest("POST", VECTOR_A_URL, VECTOR_A_BODY);
        // Set ONLY the legacy alias header. Per the spec, the cXML/Compat
        // surface accepts this alongside the canonical header.
        req.setHeader(WebhookValidator.TWILIO_COMPAT_SIGNATURE_HEADER, VECTOR_A_SIG);
        FakeResponse resp = new FakeResponse();
        FakeChain chain = new FakeChain();

        new WebhookFilter(SIGNING_KEY).doFilter(req.asServlet(), resp.asServlet(), chain);

        assertEquals(1, chain.invocations(), "alias header must be accepted");
        assertEquals(200, resp.getStatus());
    }

    @Test
    @DisplayName("Body forwarded to handler: handler reads bytes downstream")
    void rawBodyForwardedToHandler() throws IOException, ServletException {
        FakeRequest req = new FakeRequest("POST", VECTOR_A_URL, VECTOR_A_BODY);
        req.setHeader(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER, VECTOR_A_SIG);
        FakeResponse resp = new FakeResponse();

        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain readingChain = (request, response) -> {
            // Downstream handler reads via getInputStream — confirms cached
            // body wrapper was substituted in.
            byte[] bytes = ((HttpServletRequest) request).getInputStream().readAllBytes();
            seen.set(new String(bytes, StandardCharsets.UTF_8));
        };

        new WebhookFilter(SIGNING_KEY).doFilter(req.asServlet(), resp.asServlet(), readingChain);

        assertEquals(VECTOR_A_BODY, seen.get(),
                "downstream handler must observe the exact raw body bytes");
        assertEquals(200, resp.getStatus());
    }

    @Test
    @DisplayName("Body re-readable across getInputStream and getReader")
    void bodyReadableMultipleWaysViaWrapper() throws IOException, ServletException {
        FakeRequest req = new FakeRequest("POST", VECTOR_A_URL, VECTOR_A_BODY);
        req.setHeader(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER, VECTOR_A_SIG);
        FakeResponse resp = new FakeResponse();

        AtomicReference<byte[]> bytesSeen = new AtomicReference<>();
        AtomicReference<String> readerSeen = new AtomicReference<>();
        FilterChain dualReadChain = (request, response) -> {
            HttpServletRequest http = (HttpServletRequest) request;
            // First read — bytes via the input stream.
            bytesSeen.set(http.getInputStream().readAllBytes());
            // Second read — same bytes, via the reader. The cached-body
            // wrapper rebuilds a fresh stream each call so multiple
            // downstream consumers see the body.
            try (BufferedReader r = http.getReader()) {
                StringBuilder sb = new StringBuilder();
                int ch;
                while ((ch = r.read()) >= 0) sb.append((char) ch);
                readerSeen.set(sb.toString());
            }
        };

        new WebhookFilter(SIGNING_KEY).doFilter(req.asServlet(), resp.asServlet(), dualReadChain);

        assertArrayEquals(VECTOR_A_BODY.getBytes(StandardCharsets.UTF_8), bytesSeen.get());
        assertEquals(VECTOR_A_BODY, readerSeen.get());
    }

    @Test
    @DisplayName("Constructor rejects empty signing key")
    void constructorRejectsEmptyKey() {
        assertThrows(IllegalArgumentException.class, () -> new WebhookFilter(""));
        assertThrows(IllegalArgumentException.class, () -> new WebhookFilter(null));
    }

    @Test
    @DisplayName("Non-HTTP request passes through unchanged")
    void nonHttpRequestPassesThrough() throws IOException, ServletException {
        // Build a generic ServletRequest dynamic proxy that's NOT an
        // HttpServletRequest — the filter must still call the chain.
        ServletRequest plainReq = (ServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ServletRequest.class},
                (proxy, method, args) -> null);
        ServletResponse plainResp = (ServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ServletResponse.class},
                (proxy, method, args) -> null);
        FakeChain chain = new FakeChain();

        new WebhookFilter(SIGNING_KEY).doFilter(plainReq, plainResp, chain);
        assertEquals(1, chain.invocations(), "non-HTTP request flows through unchanged");
    }

    @Test
    @DisplayName("Trust proxy: X-Forwarded-Host honored when validating signature")
    void trustProxyHonorsForwardedHeaders() throws IOException, ServletException {
        // Request comes in to the LOCAL URL (e.g. /webhook on localhost),
        // but X-Forwarded-Host points to the external URL the platform
        // signed against. With trustProxy=true the filter validates against
        // the forwarded URL and accepts.
        FakeRequest req = new FakeRequest(
                "POST",
                "http://localhost:8080/webhook",  // what the servlet sees
                VECTOR_A_BODY);
        req.setHeader(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER, VECTOR_A_SIG);
        req.setHeader("X-Forwarded-Proto", "https");
        req.setHeader("X-Forwarded-Host", "example.ngrok.io");
        FakeResponse resp = new FakeResponse();
        FakeChain chain = new FakeChain();

        new WebhookFilter(SIGNING_KEY, true).doFilter(req.asServlet(), resp.asServlet(), chain);

        assertEquals(200, resp.getStatus());
        assertEquals(1, chain.invocations());
    }

    @Test
    @DisplayName("Default (trustProxy=false) ignores X-Forwarded-Host")
    void noTrustProxyIgnoresForwardedHost() throws IOException, ServletException {
        // Same situation as the previous test but with trustProxy=false. The
        // filter sees a localhost URL, which won't match the Vector A digest.
        FakeRequest req = new FakeRequest(
                "POST",
                "http://localhost:8080/webhook",
                VECTOR_A_BODY);
        req.setHeader(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER, VECTOR_A_SIG);
        req.setHeader("X-Forwarded-Proto", "https");
        req.setHeader("X-Forwarded-Host", "example.ngrok.io");
        FakeResponse resp = new FakeResponse();
        FakeChain chain = new FakeChain();

        // trustProxy defaults to false.
        new WebhookFilter(SIGNING_KEY).doFilter(req.asServlet(), resp.asServlet(), chain);

        assertEquals(403, resp.getStatus(),
                "without trust_proxy the validator uses the local URL → digest mismatch → 403");
        assertEquals(0, chain.invocations());
    }

    @Test
    @DisplayName("403 response body is empty (no scheme details leaked)")
    void noSchemeDetailLeakedOn403() throws IOException, ServletException {
        FakeRequest req = new FakeRequest("POST", VECTOR_A_URL, VECTOR_A_BODY);
        req.setHeader(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER, "wrong-signature");
        FakeResponse resp = new FakeResponse();
        FakeChain chain = new FakeChain();

        new WebhookFilter(SIGNING_KEY).doFilter(req.asServlet(), resp.asServlet(), chain);

        assertEquals(403, resp.getStatus());
        assertTrue(resp.getWrittenBytes().length == 0,
                "403 response body must be empty — must not disclose which "
                        + "branch (Scheme A vs B) failed");
    }

    // ==================================================================
    // Hand-rolled HttpServletRequest / HttpServletResponse / FilterChain
    // ==================================================================

    private static final class FakeChain implements FilterChain {
        private final AtomicInteger calls = new AtomicInteger();

        int invocations() {
            return calls.get();
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            calls.incrementAndGet();
        }
    }

    /**
     * Minimal {@link HttpServletRequest} for filter tests. Only the surface
     * the filter actually touches is implemented; everything else is
     * served by a JDK dynamic proxy that throws, so any accidental new
     * dependency on an unimplemented method fails loudly.
     */
    private static final class FakeRequest {
        // Returned to the test as an HttpServletRequest via the proxy below.
        private final String method;
        private final String url;
        private final byte[] body;
        private final Map<String, String> headers = new HashMap<>();
        private String characterEncoding = "UTF-8";

        FakeRequest(String method, String url, String body) {
            this.method = method;
            this.url = url;
            this.body = body.getBytes(StandardCharsets.UTF_8);
            // Provide a sensible Content-Length so isReady etc. behave.
            headers.put("content-length", String.valueOf(this.body.length));
        }

        FakeRequest setHeader(String name, String value) {
            headers.put(name.toLowerCase(Locale.ROOT), value);
            return this;
        }

        // Wrap this fake as an HttpServletRequest via a JDK dynamic proxy.
        private HttpServletRequest asServlet;

        @SuppressWarnings("unchecked")
        HttpServletRequest asServlet() {
            if (asServlet == null) {
                asServlet = (HttpServletRequest) Proxy.newProxyInstance(
                        FakeRequest.class.getClassLoader(),
                        new Class<?>[]{HttpServletRequest.class},
                        new Handler(this));
            }
            return asServlet;
        }
    }

    /**
     * Dynamic-proxy handler that translates an HttpServletRequest method
     * call into the corresponding state on a {@link FakeRequest}. Methods
     * we don't need return null/0/false rather than throwing, because some
     * servlet versions probe optional methods (e.g. servletPath, contextPath)
     * during request URL reconstruction.
     */
    private static final class Handler implements InvocationHandler {
        private final FakeRequest req;
        private final ByteArrayInputStream stream;

        Handler(FakeRequest req) {
            this.req = req;
            this.stream = new ByteArrayInputStream(req.body);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            switch (name) {
                case "getMethod":
                    return req.method;
                case "getInputStream":
                    return new ServletInputStream() {
                        @Override
                        public int read() {
                            return stream.read();
                        }

                        @Override
                        public int read(byte[] b, int off, int len) {
                            return stream.read(b, off, len);
                        }

                        @Override
                        public boolean isFinished() {
                            return stream.available() == 0;
                        }

                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setReadListener(javax.servlet.ReadListener readListener) {
                            throw new UnsupportedOperationException();
                        }
                    };
                case "getHeader":
                    return req.headers.get(((String) args[0]).toLowerCase(Locale.ROOT));
                case "getHeaders": {
                    String key = ((String) args[0]).toLowerCase(Locale.ROOT);
                    String v = req.headers.get(key);
                    return v == null
                            ? Collections.enumeration(Collections.emptyList())
                            : Collections.enumeration(List.of(v));
                }
                case "getHeaderNames":
                    return Collections.enumeration(req.headers.keySet());
                case "getRequestURL": {
                    // Return up to (but not including) the query string.
                    int q = req.url.indexOf('?');
                    return new StringBuffer(q >= 0 ? req.url.substring(0, q) : req.url);
                }
                case "getRequestURI": {
                    int schemeEnd = req.url.indexOf("://");
                    int pathStart = req.url.indexOf('/', schemeEnd + 3);
                    int q = req.url.indexOf('?');
                    int end = q >= 0 ? q : req.url.length();
                    return pathStart >= 0 ? req.url.substring(pathStart, end) : "/";
                }
                case "getQueryString": {
                    int q = req.url.indexOf('?');
                    return q >= 0 ? req.url.substring(q + 1) : null;
                }
                case "getCharacterEncoding":
                    return req.characterEncoding;
                case "setCharacterEncoding":
                    req.characterEncoding = (String) args[0];
                    return null;
                case "getContentLength":
                    return req.body.length;
                case "getContentLengthLong":
                    return (long) req.body.length;
                case "getReader":
                    // The filter doesn't use this on the inbound side; if a
                    // test downstream handler asks for it on the wrapper,
                    // they get it via CachedBodyHttpServletRequest, not us.
                    return new BufferedReader(new java.io.InputStreamReader(
                            new ByteArrayInputStream(req.body), StandardCharsets.UTF_8));
                case "toString":
                    return "FakeRequest(" + req.method + " " + req.url + ")";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    // Most other methods (getParameter, getCookies,
                    // getServletPath, etc.) — return type-appropriate
                    // defaults so URL reconstruction in the filter degrades
                    // gracefully instead of throwing.
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) return false;
                    if (rt == int.class) return 0;
                    if (rt == long.class) return 0L;
                    if (rt.isPrimitive()) return 0;
                    return null;
            }
        }
    }

    private static final class FakeResponse {
        private int status = 200;
        private final java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        private final Map<String, String> headers = new HashMap<>();
        private HttpServletResponse asServlet;

        int getStatus() {
            return status;
        }

        byte[] getWrittenBytes() {
            return body.toByteArray();
        }

        @SuppressWarnings("unchecked")
        HttpServletResponse asServlet() {
            if (asServlet == null) {
                asServlet = (HttpServletResponse) Proxy.newProxyInstance(
                        FakeResponse.class.getClassLoader(),
                        new Class<?>[]{HttpServletResponse.class},
                        (proxy, method, args) -> {
                            String n = method.getName();
                            switch (n) {
                                case "setStatus":
                                    status = (int) args[0];
                                    return null;
                                case "getStatus":
                                    return status;
                                case "setHeader":
                                    headers.put(((String) args[0]).toLowerCase(Locale.ROOT),
                                            (String) args[1]);
                                    return null;
                                case "addHeader":
                                    headers.put(((String) args[0]).toLowerCase(Locale.ROOT),
                                            (String) args[1]);
                                    return null;
                                case "getHeader":
                                    return headers.get(((String) args[0]).toLowerCase(Locale.ROOT));
                                case "getOutputStream":
                                    return new ServletOutputStream() {
                                        @Override
                                        public void write(int b) {
                                            body.write(b);
                                        }

                                        @Override
                                        public boolean isReady() {
                                            return true;
                                        }

                                        @Override
                                        public void setWriteListener(WriteListener writeListener) {
                                            throw new UnsupportedOperationException();
                                        }
                                    };
                                case "getWriter":
                                    return new PrintWriter(new StringWriter());
                                default:
                                    Class<?> rt = method.getReturnType();
                                    if (rt == boolean.class) return false;
                                    if (rt == int.class) return 0;
                                    if (rt == long.class) return 0L;
                                    if (rt.isPrimitive()) return 0;
                                    return null;
                            }
                        });
            }
            return asServlet;
        }
    }

}
