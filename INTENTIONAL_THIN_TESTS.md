# INTENTIONAL_THIN_TESTS — methods exempt from the no-cheat-tests audit

The `audit_no_cheat_tests.py` script flags methods in test files whose bodies
have no assertion call. This is generally a code smell — but for harness
infrastructure (helper classes that wrap the mock server's HTTP control plane
or the system under test), the methods are by design pass-through wrappers
without assertions. They are exercised indirectly by the actual `@Test`
methods that use them.

## Format

Each entry: `- <file:line> — <one-sentence justification>` — one line per
intentional thin helper. The audit script consumes this list as an allowlist.

## Entries

- src/test/java/com/signalwire/sdk/rest/MockTest.java:163 — harness helper that POSTs to /__mock__/journal/reset; no assertion required because it is invoked between every test, and a transport failure would surface in the next request rather than this method
- src/test/java/com/signalwire/sdk/relay/RelayMockTest.java:212 — harness helper that POSTs to /__mock__/journal/reset and /__mock__/scenarios/reset; mirrors MockTest.reset and is exercised by every test through @BeforeEach
- src/test/java/com/signalwire/sdk/relay/RelayMockTest.java:222 — harness helper that queues scripted post-RPC events for a method; exercised by ActionsMockTest.playResolvesOnFinishedEvent and friends, which assert on the resulting action.waitForCompletion()
- src/test/java/com/signalwire/sdk/relay/RelayMockTest.java:229 — harness helper that queues a dial-dance scenario; exercised by OutboundCallMockTest.dialResolvesToCallWithWinnerId and friends, which assert on the returned Call
- src/test/java/com/signalwire/sdk/relay/RelayMockTest.java:468 — AutoCloseable.close() on the (RelayClient, Harness) tuple — try-with-resources cleanup helper
- src/test/java/com/signalwire/sdk/relay/ConnectMockTest.java:356 — WebSocket.Listener onOpen callback that just calls ws.request(1) to enable backpressure; the SUT here is the mock-relay's auth path, asserted on the result of ws.send/await
- src/test/java/com/signalwire/sdk/security/WebhookFilterTest.java:274 — FakeChain.doFilter is a counter-incrementing test scaffolding stand-in for a real FilterChain; @Test methods using it assert on chain.invocations() afterward
- src/test/java/com/signalwire/sdk/security/WebhookFilterTest.java:366 — ServletInputStream.setReadListener override on the cached-body stream; servlet 4.x mandates this method but our cached buffer is sync-only, so it just throws — exercised indirectly by every filter test that consumes the body
- src/test/java/com/signalwire/sdk/security/WebhookFilterTest.java:474 — FakeResponse output-stream write(int) byte-sink that buffers into the test's ByteArrayOutputStream; the @Test noSchemeDetailLeakedOn403 asserts on getWrittenBytes() afterward
- src/test/java/com/signalwire/sdk/security/WebhookFilterTest.java:484 — ServletOutputStream.setWriteListener stub on the test response; servlet 4.x mandates the override but the test response is sync-only
- src/test/java/com/signalwire/sdk/skills/WebSearchSkillTest.java:554 — AutoCloseable.close() on the in-test StubServer that mimics Google CSE (and serves the candidate content pages for the deadline/timeout tests); teardown helper invoked via try-with-resources, exercised by every @Test that uses the stub
- src/test/java/com/signalwire/sdk/tls/TlsSupport.java:220 — AutoCloseable.close() on the spawned --tls mock handle; try-with-resources cleanup that destroys the mock subprocess, exercised by the TLS capability @Tests that wrap the handle
- src/test/java/com/signalwire/sdk/tls/TlsWssMockTest.java:78 — Java-WebSocket onOpen callback that sends the signalwire.connect frame over the verified WSS link; the SUT (the TLS handshake + RELAY auth) is asserted afterward on the connectResult protocol string and the mock journal, mirroring ConnectMockTest.java's onOpen exemption
- src/test/java/com/signalwire/sdk/tls/TlsWssMockTest.java:85 — Java-WebSocket onMessage callback that completes the connect-result future from the server's JSON-RPC response; the @Test asserts on that future's protocol value, not inside the callback
- src/test/java/com/signalwire/sdk/tls/TlsWssMockTest.java:95 — Java-WebSocket onClose callback that fails the pending future if the socket closes early; failure surfaces as the @Test's connectResult.get() throwing, asserted there
- src/test/java/com/signalwire/sdk/tls/TlsWssMockTest.java:103 — Java-WebSocket onError callback that records the handshake exception for the negative subtest; the @Test asserts the empty-trust client is rejected (assertFalse + SSL-exception check) after this fires
