/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real-behavior tests for {@link ConfigLoader} (parity with Python config_loader.ConfigLoader). */
class ConfigLoaderTest {

  private Path writeJson(Path dir, String name, String json) throws IOException {
    Path p = dir.resolve(name);
    Files.write(p, json.getBytes(StandardCharsets.UTF_8));
    return p;
  }

  @Test
  void loadsJsonConfig(@TempDir Path dir) throws IOException {
    Path p = writeJson(dir, "cfg.json", "{\"a\": 1}");
    ConfigLoader loader = new ConfigLoader(List.of(p.toString()));

    assertTrue(loader.hasConfig());
    assertEquals(p.toString(), loader.getConfigFile());
    assertEquals(1.0, ((Number) loader.getConfig().get("a")).doubleValue());
  }

  @Test
  void noConfigWhenMissing() {
    ConfigLoader loader = new ConfigLoader(List.of("/nonexistent/definitely-not-here.json"));

    assertFalse(loader.hasConfig());
    assertNull(loader.getConfigFile());
    assertTrue(loader.getConfig().isEmpty());
  }

  @Test
  void getDotPath(@TempDir Path dir) throws IOException {
    writeJsonAt(dir, "{\"security\": {\"ssl_enabled\": true, \"nested\": {\"x\": \"y\"}}}");
    ConfigLoader loader = new ConfigLoader(List.of(dir.resolve("cfg.json").toString()));

    assertEquals(Boolean.TRUE, loader.get("security.ssl_enabled"));
    assertEquals("y", loader.get("security.nested.x"));
    assertEquals("fallback", loader.get("security.missing", "fallback"));
  }

  @Test
  void envVarSubstitutionWithDefault(@TempDir Path dir) throws IOException {
    writeJsonAt(dir, "{\"v\": \"${SW_MISSING_VAR_XYZ|fallbackval}\"}");
    ConfigLoader loader = new ConfigLoader(List.of(dir.resolve("cfg.json").toString()));

    assertEquals("fallbackval", loader.get("v"));
  }

  @Test
  void substituteCoercesTypes(@TempDir Path dir) throws IOException {
    // Uses PATH which is guaranteed set; instead exercise coercion via literal defaults.
    writeJsonAt(
        dir,
        "{\"n\": \"${SW_UNSET_N|42}\", \"f\": \"${SW_UNSET_F|3.5}\", \"b\": \"${SW_UNSET_B|true}\"}");
    ConfigLoader loader = new ConfigLoader(List.of(dir.resolve("cfg.json").toString()));

    assertEquals(42L, loader.get("n"));
    assertEquals(3.5, (Double) loader.get("f"), 1e-9);
    assertEquals(Boolean.TRUE, loader.get("b"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void getSectionSubstitutesRecursively(@TempDir Path dir) throws IOException {
    writeJsonAt(
        dir,
        "{\"server\": {\"host\": \"${SW_UNSET_HOST|example.com}\", \"list\": [\"a\", \"static\"]}}");
    ConfigLoader loader = new ConfigLoader(List.of(dir.resolve("cfg.json").toString()));

    Map<String, Object> section = loader.getSection("server");
    assertEquals("example.com", section.get("host"));
    assertEquals(List.of("a", "static"), (List<Object>) section.get("list"));
  }

  @Test
  void mergeWithEnvConfigPrecedence(@TempDir Path dir) throws IOException {
    // config value wins for a key present in config. Env folding is exercised on real env.
    writeJsonAt(dir, "{\"testkey\": \"from_config\"}");
    ConfigLoader loader = new ConfigLoader(List.of(dir.resolve("cfg.json").toString()));

    Map<String, Object> merged = loader.mergeWithEnv("SWML_");
    assertEquals("from_config", merged.get("testkey"));
  }

  @Test
  void substituteVarsDepthGuard() {
    ConfigLoader loader = new ConfigLoader(List.of());
    Map<String, Object> nested = new LinkedHashMap<>();
    Map<String, Object> inner = new LinkedHashMap<>();
    inner.put("b", "c");
    nested.put("a", inner);

    assertThrows(IllegalArgumentException.class, () -> loader.substituteVars(nested, 1));
  }

  @Test
  void findConfigFileReturnsFirstExisting(@TempDir Path dir) throws IOException {
    Path target = writeJson(dir, "web_config.json", "{}");
    assertEquals(target.toString(), ConfigLoader.findConfigFile("web", List.of(target.toString())));
  }

  @Test
  void findConfigFileNullWhenNone() {
    assertNull(ConfigLoader.findConfigFile("unlikely-service-name-xyz-" + System.nanoTime()));
  }

  @Test
  void loadsYamlConfig(@TempDir Path dir) throws IOException {
    Path p = dir.resolve("cfg.yaml");
    Files.write(p, "a: 1\nnested:\n  x: y\n".getBytes(StandardCharsets.UTF_8));
    ConfigLoader loader = new ConfigLoader(List.of(p.toString()));

    assertTrue(loader.hasConfig());
    assertEquals("y", loader.get("nested.x"));
  }

  private void writeJsonAt(Path dir, String json) throws IOException {
    Files.write(dir.resolve("cfg.json"), json.getBytes(StandardCharsets.UTF_8));
  }
}
