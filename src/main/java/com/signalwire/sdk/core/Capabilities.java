/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reading what a client says it can do.
 *
 * <p>A browser client (the SignalWire address widget, or anything speaking the same convention)
 * declares its rendering capabilities in the user variables it sends at dial time, under {@code
 * vars.userVariables.capabilities}. <b>These are declarations of what the client can RENDER, not
 * grants of authority</b> — treat them as hints for what to offer, never as permission for anything
 * privileged. <b>Absence means no</b>: every method resolves errors and missing data to "not
 * declared".
 *
 * <p>Static-only: the reference exposes these as module-level functions of {@code
 * signalwire.core.capabilities}.
 */
public final class Capabilities {

  private Capabilities() {}

  /**
   * Return the user variables ({@code vars.userVariables}) from a SWML request body.
   *
   * @param bodyParams the SWML request body (any shape)
   * @return the user variables, or an empty map when any level is missing or malformed
   */
  public static Map<String, Object> userVariables(Object bodyParams) {
    if (!(bodyParams instanceof Map<?, ?> body)) {
      return new LinkedHashMap<>();
    }
    Object vars = body.get("vars");
    if (!(vars instanceof Map<?, ?> varsMap)) {
      return new LinkedHashMap<>();
    }
    Object uv = varsMap.get("userVariables");
    if (!(uv instanceof Map<?, ?> uvMap)) {
      return new LinkedHashMap<>();
    }
    Map<String, Object> out = new LinkedHashMap<>();
    uvMap.forEach((k, v) -> out.put(String.valueOf(k), v));
    return out;
  }

  /**
   * Return the capability names the client declared as truthy. Accepts a full SWML request body or
   * an already-extracted user-variables map.
   *
   * @param bodyParams SWML request body, or a user-variables map
   * @return the names whose declared value is truthy; empty when nothing was declared or the
   *     payload was malformed
   */
  public static Set<String> declaredCapabilities(Object bodyParams) {
    Map<String, Object> variables = userVariables(bodyParams);
    if (variables.isEmpty() && bodyParams instanceof Map<?, ?> direct) {
      Map<String, Object> copy = new LinkedHashMap<>();
      direct.forEach((k, v) -> copy.put(String.valueOf(k), v));
      variables = copy;
    }
    Object capabilities = variables.get("capabilities");
    if (!(capabilities instanceof Map<?, ?> caps)) {
      return Collections.emptySet();
    }
    Set<String> out = new LinkedHashSet<>();
    caps.forEach(
        (name, value) -> {
          if (name instanceof String s && truthy(value)) {
            out.add(s);
          }
        });
    return Collections.unmodifiableSet(out);
  }

  /**
   * Whether the client declared {@code name} (explicitly, as truthy).
   *
   * @param bodyParams SWML request body, or a user-variables map
   * @param name capability name, e.g. {@code "display_content"}
   * @return {@code true} only when explicitly declared truthy
   */
  public static boolean hasCapability(Object bodyParams, String name) {
    return declaredCapabilities(bodyParams).contains(name);
  }

  /** JSON truthiness: false/null/0/""/empty containers are not a declaration. */
  private static boolean truthy(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    if (value instanceof Number n) {
      return n.doubleValue() != 0.0;
    }
    if (value instanceof CharSequence s) {
      return s.length() > 0;
    }
    if (value instanceof Map<?, ?> m) {
      return !m.isEmpty();
    }
    if (value instanceof Collection<?> c) {
      return !c.isEmpty();
    }
    return true;
  }
}
