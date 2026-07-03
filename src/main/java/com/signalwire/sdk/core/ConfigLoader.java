/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.logging.Logger;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * Configuration loader with environment variable substitution.
 *
 * <p>Java port of the Python reference {@code signalwire.core.config_loader.ConfigLoader}. Supports
 * {@code ${VAR|default}} syntax for referencing environment variables within JSON (or YAML)
 * configuration files. The first existing, parseable file in the search paths wins.
 *
 * <p>Idiom mapping: JSON is parsed with Gson and YAML with SnakeYAML (both already repo
 * dependencies). After substitution, string values that look like booleans/integers/floats are
 * coerced to those native types (Python parity).
 */
public class ConfigLoader {

  private static final Logger LOG = Logger.getLogger("config_loader");

  /** Pattern matching {@code ${VAR}} or {@code ${VAR|default}}. */
  private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}|]+)(?:\\|([^}]*))?\\}");

  private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
  private static final Gson GSON = new Gson();

  private final List<String> configPaths;
  private Map<String, Object> config;
  private String configFile;

  /** Initialize with the default search paths. */
  public ConfigLoader() {
    this(null);
  }

  /**
   * Initialize the config loader.
   *
   * @param configPaths optional list of config file paths to check; when {@code null} the default
   *     search paths are used. The first existing, parseable file wins.
   */
  public ConfigLoader(List<String> configPaths) {
    this.configPaths = configPaths != null ? configPaths : defaultPaths();
    this.config = null;
    this.configFile = null;
    loadConfig();
  }

  private static List<String> defaultPaths() {
    List<String> paths = new ArrayList<>();
    paths.add("config.json");
    paths.add("agent_config.json");
    paths.add("swml_config.json");
    paths.add(".swml/config.json");
    paths.add(expandHome("~/.swml/config.json"));
    paths.add("/etc/swml/config.json");
    return paths;
  }

  private static String expandHome(String path) {
    if (path.startsWith("~")) {
      return System.getProperty("user.home") + path.substring(1);
    }
    return path;
  }

  /** Check if a configuration was loaded. */
  public boolean hasConfig() {
    return config != null;
  }

  /** Get the path of the loaded config file, or {@code null}. */
  public String getConfigFile() {
    return configFile;
  }

  /** Get the raw configuration (before substitution) as a map. */
  public Map<String, Object> getConfig() {
    return config != null ? config : new LinkedHashMap<>();
  }

  /**
   * Recursively substitute environment variables in configuration values. Supports {@code
   * ${VAR|default}} syntax.
   *
   * @throws IllegalArgumentException when {@code maxDepth} is exhausted
   */
  public Object substituteVars(Object value, int maxDepth) {
    if (maxDepth <= 0) {
      throw new IllegalArgumentException("Maximum variable substitution depth exceeded");
    }
    if (value instanceof String) {
      return substituteString((String) value);
    }
    if (value instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) value;
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<String, Object> e : map.entrySet()) {
        out.put(e.getKey(), substituteVars(e.getValue(), maxDepth - 1));
      }
      return out;
    }
    if (value instanceof List) {
      List<?> list = (List<?>) value;
      List<Object> out = new ArrayList<>(list.size());
      for (Object item : list) {
        out.add(substituteVars(item, maxDepth - 1));
      }
      return out;
    }
    return value;
  }

  /** Convenience: {@code substituteVars(value, 10)}. */
  public Object substituteVars(Object value) {
    return substituteVars(value, 10);
  }

  /**
   * Get a configuration value by dot-notation path (e.g. {@code "security.ssl_enabled"}), with
   * variables substituted. Returns {@code default} when the path is not found.
   */
  public Object get(String keyPath, Object defaultValue) {
    if (config == null) {
      return defaultValue;
    }
    Object value = config;
    for (String key : keyPath.split("\\.")) {
      if (value instanceof Map && ((Map<?, ?>) value).containsKey(key)) {
        value = ((Map<?, ?>) value).get(key);
      } else {
        return defaultValue;
      }
    }
    return substituteVars(value);
  }

  /** Convenience: {@code get(keyPath, null)}. */
  public Object get(String keyPath) {
    return get(keyPath, null);
  }

  /**
   * Get an entire configuration section with all variables substituted. Returns an empty map when
   * the section is absent.
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> getSection(String section) {
    if (config == null || !config.containsKey(section)) {
      return new LinkedHashMap<>();
    }
    Object substituted = substituteVars(config.get(section));
    if (substituted instanceof Map) {
      return (Map<String, Object>) substituted;
    }
    return new LinkedHashMap<>();
  }

  /**
   * Merge configuration with environment variables. The config file takes precedence. Env vars
   * beginning with {@code envPrefix} (default {@code "SWML_"}) are lowercased, the prefix stripped,
   * and folded into the result on underscore boundaries -- only when not already present.
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> mergeWithEnv(String envPrefix) {
    Map<String, Object> result =
        config != null
            ? (Map<String, Object>) substituteVars(config)
            : new LinkedHashMap<String, Object>();
    for (Map.Entry<String, String> e : System.getenv().entrySet()) {
      String key = e.getKey();
      if (!key.startsWith(envPrefix)) {
        continue;
      }
      String configKey = key.substring(envPrefix.length()).toLowerCase(Locale.ROOT);
      if (!hasNestedKey(result, configKey)) {
        setNestedKey(result, configKey, e.getValue());
      }
    }
    return result;
  }

  /** Convenience: {@code mergeWithEnv("SWML_")}. */
  public Map<String, Object> mergeWithEnv() {
    return mergeWithEnv("SWML_");
  }

  /**
   * Find a config file for a service. Returns the first existing file, or {@code null}.
   *
   * @param serviceName optional service name seeding service-specific config file names
   * @param additionalPaths optional paths checked after the service-specific names
   */
  public static String findConfigFile(String serviceName, List<String> additionalPaths) {
    List<String> paths = new ArrayList<>();
    if (serviceName != null) {
      paths.add(serviceName + "_config.json");
      paths.add(".swml/" + serviceName + "_config.json");
    }
    if (additionalPaths != null) {
      paths.addAll(additionalPaths);
    }
    paths.add("config.json");
    paths.add("agent_config.json");
    paths.add(".swml/config.json");
    paths.add(expandHome("~/.swml/config.json"));
    paths.add("/etc/swml/config.json");
    for (String path : paths) {
      if (Files.exists(Paths.get(path))) {
        return path;
      }
    }
    return null;
  }

  /** Convenience: {@code findConfigFile(serviceName, null)}. */
  public static String findConfigFile(String serviceName) {
    return findConfigFile(serviceName, null);
  }

  // ------------------------------------------------------------------
  // Internal.
  // ------------------------------------------------------------------

  private void loadConfig() {
    for (String path : configPaths) {
      Path p = Paths.get(path);
      if (!Files.exists(p)) {
        continue;
      }
      try {
        this.config = parseFile(path);
        this.configFile = path;
        LOG.info("config_loaded path=%s", path);
        break;
      } catch (Exception e) {
        LOG.error("config_load_error path=" + path + " error=" + e.getMessage());
      }
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseFile(String path) throws IOException {
    String contents =
        new String(Files.readAllBytes(Paths.get(path)), java.nio.charset.StandardCharsets.UTF_8);
    if (path.endsWith(".yaml") || path.endsWith(".yml")) {
      Object loaded = new Yaml().load(contents);
      if (loaded == null) {
        return new LinkedHashMap<>();
      }
      if (!(loaded instanceof Map)) {
        throw new IllegalArgumentException("YAML root must be a mapping");
      }
      return (Map<String, Object>) loaded;
    }
    return GSON.fromJson(contents, MAP_TYPE);
  }

  private Object substituteString(String value) {
    Matcher m = VAR_PATTERN.matcher(value);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String varName = m.group(1);
      String def = m.group(2) != null ? m.group(2) : "";
      String env = System.getenv(varName);
      String replacement = env != null ? env : def;
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb);
    return coerceScalar(sb.toString());
  }

  private Object coerceScalar(String result) {
    String lowered = result.toLowerCase(Locale.ROOT);
    if ("true".equals(lowered)) {
      return Boolean.TRUE;
    }
    if ("false".equals(lowered)) {
      return Boolean.FALSE;
    }
    if (result.matches("\\d+")) {
      try {
        return Long.parseLong(result);
      } catch (NumberFormatException ignored) {
        return result;
      }
    }
    // matches Python's result.replace(".", "", 1).isdigit() -> single dot float
    if (result.indexOf('.') >= 0 && result.replaceFirst("\\.", "").matches("\\d+")) {
      try {
        return Double.parseDouble(result);
      } catch (NumberFormatException ignored) {
        return result;
      }
    }
    return result;
  }

  private boolean hasNestedKey(Map<String, Object> data, String keyPath) {
    Object current = data;
    for (String key : keyPath.split("_")) {
      if (current instanceof Map && ((Map<?, ?>) current).containsKey(key)) {
        current = ((Map<?, ?>) current).get(key);
      } else {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  private void setNestedKey(Map<String, Object> data, String keyPath, Object value) {
    String[] keys = keyPath.split("_");
    Map<String, Object> current = data;
    for (int i = 0; i < keys.length - 1; i++) {
      Object next = current.get(keys[i]);
      if (!(next instanceof Map)) {
        next = new LinkedHashMap<String, Object>();
        current.put(keys[i], next);
      }
      current = (Map<String, Object>) next;
    }
    current.put(keys[keys.length - 1], value);
  }
}
