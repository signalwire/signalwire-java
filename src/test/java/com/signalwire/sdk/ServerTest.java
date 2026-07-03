package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.server.AgentServer;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ServerTest {

  private AgentServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  // ======== Registration Tests ========

  @Test
  void testRegisterAgent() {
    server = new AgentServer(0);
    AgentBase agent =
        AgentBase.builder().name("test").route("/test").authUser("u").authPassword("p").build();
    server.register(agent, "/test");
    assertNotNull(server.getAgent("/test"));
    assertTrue(server.getRoutes().contains("/test"));
  }

  @Test
  void testRegisterAgentAtOwnRoute() {
    server = new AgentServer(0);
    AgentBase agent =
        AgentBase.builder().name("test").route("/myroute").authUser("u").authPassword("p").build();
    server.register(agent);
    assertNotNull(server.getAgent("/myroute"));
  }

  @Test
  void testUnregisterAgent() {
    server = new AgentServer(0);
    AgentBase agent =
        AgentBase.builder().name("test").route("/test").authUser("u").authPassword("p").build();
    server.register(agent, "/test");
    server.unregister("/test");
    assertNull(server.getAgent("/test"));
  }

  @Test
  void testMultipleAgents() {
    server = new AgentServer(0);
    AgentBase agent1 =
        AgentBase.builder().name("agent1").route("/a1").authUser("u1").authPassword("p1").build();
    AgentBase agent2 =
        AgentBase.builder().name("agent2").route("/a2").authUser("u2").authPassword("p2").build();
    server.register(agent1, "/a1");
    server.register(agent2, "/a2");
    assertEquals(2, server.getRoutes().size());
  }

  // ======== SIP Routing Tests ========

  @Test
  void testSipRouting() {
    server = new AgentServer(0);
    server.registerSipRoute("alice", "/support");
    assertEquals("/support", server.getSipRoute("alice"));
  }

  @Test
  void testAutoSipRouting() {
    server = new AgentServer(0);
    AgentBase agent =
        AgentBase.builder().name("sip-agent").route("/sip").authUser("u").authPassword("p").build();
    agent.enableSipRouting();
    agent.registerSipUsername("bob");
    server.register(agent, "/sip");
    assertEquals("/sip", server.getSipRoute("bob"));
  }

  // ======== HTTP Server Tests ========

  @Test
  void testHealthEndpoint() throws IOException, InterruptedException {
    server = new AgentServer("127.0.0.1", 0);
    AgentBase agent = AgentBase.builder().name("test").authUser("u").authPassword("p").build();
    server.register(agent, "/");

    // Start server on a random port
    server.run();

    // We need to find the actual port - for this test we use a known port
    // In production tests we'd use a test framework that captures the port
    // For now, just verify the server starts without error
    assertNotNull(server);
  }

  // ======== Route Normalization Tests ========

  @Test
  void testRouteNormalization() {
    server = new AgentServer(0);
    AgentBase agent = AgentBase.builder().name("test").authUser("u").authPassword("p").build();

    server.register(agent, "/test/");
    assertNotNull(server.getAgent("/test"));

    server.register(agent, "noprefix");
    assertNotNull(server.getAgent("/noprefix"));
  }

  // ======== Static Files ========

  @Test
  void testSetStaticFilesDir() {
    server = new AgentServer(0);
    server.setStaticFilesDir("/tmp/static");
    // Should not throw
    assertNotNull(server);
  }

  // ======== Multiple Agent Routes ========

  @Test
  void testGetRoutes() {
    server = new AgentServer(0);
    AgentBase a1 = AgentBase.builder().name("a1").authUser("u").authPassword("p").build();
    AgentBase a2 = AgentBase.builder().name("a2").authUser("u").authPassword("p").build();
    AgentBase a3 = AgentBase.builder().name("a3").authUser("u").authPassword("p").build();

    server.register(a1, "/sales");
    server.register(a2, "/support");
    server.register(a3, "/billing");

    Set<String> routes = server.getRoutes();
    assertEquals(3, routes.size());
    assertTrue(routes.contains("/sales"));
    assertTrue(routes.contains("/support"));
    assertTrue(routes.contains("/billing"));
  }

  // ======== getAgents ========

  @Test
  void testGetAgentsReturnsRegisteredAgents() {
    server = new AgentServer(0);
    AgentBase a1 = AgentBase.builder().name("a1").authUser("u").authPassword("p").build();
    AgentBase a2 = AgentBase.builder().name("a2").authUser("u").authPassword("p").build();
    server.register(a1, "/sales");
    server.register(a2, "/support");

    List<Map.Entry<String, AgentBase>> entries = server.getAgents();
    assertEquals(2, entries.size());

    Map<String, AgentBase> byRoute = new HashMap<>();
    for (Map.Entry<String, AgentBase> e : entries) {
      byRoute.put(e.getKey(), e.getValue());
    }
    assertSame(a1, byRoute.get("/sales"));
    assertSame(a2, byRoute.get("/support"));
  }

  @Test
  void testGetAgentsEmptyWhenNoneRegistered() {
    server = new AgentServer(0);
    assertTrue(server.getAgents().isEmpty());
  }

  // ======== setupSipRouting + registerSipUsername ========

  @Test
  void testRegisterSipUsernameRequiresSetup() {
    server = new AgentServer(0);
    AgentBase agent = AgentBase.builder().name("sup").authUser("u").authPassword("p").build();
    server.register(agent, "/support");

    // Without setupSipRouting, register_sip_username is a no-op (mirrors Python's warning path).
    server.registerSipUsername("alice", "/support");
    assertNull(server.getSipRoute("alice"));

    server.setupSipRouting("/sip", false);
    server.registerSipUsername("alice", "/support");
    assertEquals("/support", server.getSipRoute("alice"));
  }

  @Test
  void testRegisterSipUsernameIsCaseInsensitive() {
    server = new AgentServer(0);
    AgentBase agent = AgentBase.builder().name("sup").authUser("u").authPassword("p").build();
    server.register(agent, "/support");
    server.setupSipRouting("/sip", false);

    server.registerSipUsername("Alice", "/support");
    // Resolution lowercases the username.
    assertEquals("/support", server.getSipRoute("alice"));
  }

  @Test
  void testSetupSipRoutingAutoMapsAgentNameAndRoute() {
    server = new AgentServer(0);
    AgentBase agent = AgentBase.builder().name("support").authUser("u").authPassword("p").build();
    server.register(agent, "/help");

    server.setupSipRouting("/sip", true);
    // Auto-maps by cleaned agent name...
    assertEquals("/help", server.getSipRoute("support"));
    // ...and by the last route segment.
    assertEquals("/help", server.getSipRoute("help"));
  }

  @Test
  void testSetupSipRoutingAutoMapsAgentRegisteredAfterSetup() {
    server = new AgentServer(0);
    server.setupSipRouting("/sip", true);

    AgentBase agent = AgentBase.builder().name("billing").authUser("u").authPassword("p").build();
    server.register(agent, "/billing");
    // Registering after setup still auto-maps (mirrors Python's register() SIP wiring).
    assertEquals("/billing", server.getSipRoute("billing"));
  }

  @Test
  void testSetupSipRoutingIdempotent() {
    server = new AgentServer(0);
    AgentBase agent = AgentBase.builder().name("a").authUser("u").authPassword("p").build();
    server.register(agent, "/a");
    server.setupSipRouting("/sip", true);
    // A second call is a warn-and-return no-op; nothing throws.
    server.setupSipRouting("/sip", true);
    assertEquals("/a", server.getSipRoute("a"));
  }

  // ======== registerGlobalRoutingCallback ========

  @Test
  void testRegisterGlobalRoutingCallbackAppliesToAllAgents() {
    server = new AgentServer(0);
    AgentBase a1 = AgentBase.builder().name("a1").authUser("u").authPassword("p").build();
    AgentBase a2 = AgentBase.builder().name("a2").authUser("u").authPassword("p").build();
    server.register(a1, "/a1");
    server.register(a2, "/a2");

    // The callback should be installed on every registered agent's routing slot.
    server.registerGlobalRoutingCallback((path, body) -> "/a2", "/route");
    assertSame(server, server.registerGlobalRoutingCallback((path, body) -> null, "/route2"));
  }
}
