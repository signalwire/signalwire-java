/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behavioural proof that the client-tree accessors {@code RestClient} inherits from the generated
 * {@link com.signalwire.sdk.rest.namespaces.generated.ResourceTree} are reachable from a caller and
 * actually issue the right wire request.
 *
 * <p>Why this exists: these accessors are declared on {@code ResourceTree}, not on {@code
 * RestClient}, so a reflection walker that reads only {@code getDeclaredMethods()} records ZERO
 * members for {@code RestClient} and the parity audit reads that as "22 missing resources." This
 * test is the regression guard proving the opposite — the capability ships; only the enumerator was
 * blind. If someone later flattens or removes the {@code extends ResourceTree} wiring, this goes
 * red rather than silently reducing the public API to what the audit happens to see.
 *
 * <p>Each case reaches a resource through the inherited accessor path and asserts a request lands
 * on the shared mock with the expected method and path.
 */
class ResourceTreeReachabilityMockTest {

  private RestClient client;
  private MockTest.Harness mock;

  @BeforeEach
  void setUp() {
    MockTest.Bound bound = MockTest.newClient();
    this.client = bound.client;
    this.mock = bound.harness;
  }

  @Test
  @DisplayName("client.calling() — inherited accessor reaches the calling namespace on the wire")
  void callingIsReachable() {
    client
        .calling()
        .dial(
            com.signalwire.sdk.rest.namespaces.generated.Calling.DialRequest.builder()
                .extras(java.util.Map.of("url", "https://example.com/swml", "to", "+15551234567"))
                .build());

    MockTest.JournalEntry j = mock.last();
    assertEquals("POST", j.method, "method");
    assertEquals("/api/calling/calls", j.path, "path");
  }

  @Test
  @DisplayName("client.fabric() — inherited accessor reaches a fabric resource on the wire")
  void fabricIsReachable() {
    client.fabric().subscribers().list();

    MockTest.JournalEntry j = mock.last();
    assertEquals("GET", j.method, "method");
    assertEquals("/api/fabric/resources/subscribers", j.path, "path");
  }

  @Test
  @DisplayName("client.video() — inherited accessor reaches a video resource on the wire")
  void videoIsReachable() {
    client.video().rooms().list();

    MockTest.JournalEntry j = mock.last();
    assertEquals("GET", j.method, "method");
    assertEquals("/api/video/rooms", j.path, "path");
  }

  @Test
  @DisplayName("client.phoneNumbers() — inherited top-level resource accessor reaches the wire")
  void phoneNumbersIsReachable() {
    client.phoneNumbers().list();

    MockTest.JournalEntry j = mock.last();
    assertEquals("GET", j.method, "method");
    assertEquals("/api/relay/rest/phone_numbers", j.path, "path");
  }

  @Test
  @DisplayName("every ResourceTree accessor is callable on a RestClient and returns non-null")
  void everyAccessorIsNonNull() {
    // The 22 client-tree accessors the parity audit reports as "missing". Reaching each one
    // through the RestClient reference proves inheritance wires them all, not just the four
    // exercised on the wire above.
    assertNotNull(client.addresses(), "addresses");
    assertNotNull(client.calling(), "calling");
    assertNotNull(client.chat(), "chat");
    assertNotNull(client.datasphere(), "datasphere");
    assertNotNull(client.fabric(), "fabric");
    assertNotNull(client.importedNumbers(), "importedNumbers");
    assertNotNull(client.logs(), "logs");
    assertNotNull(client.lookup(), "lookup");
    assertNotNull(client.messages(), "messages");
    assertNotNull(client.mfa(), "mfa");
    assertNotNull(client.numberGroups(), "numberGroups");
    assertNotNull(client.phoneNumbers(), "phoneNumbers");
    assertNotNull(client.project(), "project");
    assertNotNull(client.projects(), "projects");
    assertNotNull(client.pubsub(), "pubsub");
    assertNotNull(client.queues(), "queues");
    assertNotNull(client.recordings(), "recordings");
    assertNotNull(client.registry(), "registry");
    assertNotNull(client.shortCodes(), "shortCodes");
    assertNotNull(client.sipProfile(), "sipProfile");
    assertNotNull(client.verifiedCallers(), "verifiedCallers");
    assertNotNull(client.video(), "video");
  }
}
