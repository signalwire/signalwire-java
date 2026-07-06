package com.signalwire.sdk.skills.builtin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Native vector search skill - supports remote mode only (network API). Local .swsearch file
 * support is skipped per porting guide.
 */
public class NativeVectorSearchSkill implements SkillBase {

  private static final Logger log = Logger.getLogger(NativeVectorSearchSkill.class);

  private String remoteUrl;
  // Base URL the /search endpoint is appended to (auth stripped from the URL,
  // path preserved) — mirrors Python's self.remote_base_url.
  private String remoteBaseUrl;
  // "user:pass" Basic-auth credentials parsed out of the remote_url userinfo,
  // or null. Mirrors Python's self.remote_auth = (username, password).
  private String remoteAuth;
  private String indexName;
  private String toolName = "search_knowledge";
  private String description = "Search the local knowledge base for information";
  private int count = 3;
  private List<String> customHints = new ArrayList<>();
  // Python parity: get_instance_key defaults index_file to "default"
  // (native_vector_search/skill.py).
  private String indexFile = "default";

  @Override
  public String getName() {
    return "native_vector_search";
  }

  @Override
  public String getDescription() {
    return "Search document indexes using vector similarity and keyword search (local or remote)";
  }

  @Override
  public boolean supportsMultipleInstances() {
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean setup(Map<String, Object> params) {
    this.remoteUrl = (String) params.get("remote_url");
    this.indexName = (String) params.get("index_name");
    if (params.containsKey("tool_name")) this.toolName = (String) params.get("tool_name");
    if (params.containsKey("description")) this.description = (String) params.get("description");
    if (params.containsKey("count")) this.count = ((Number) params.get("count")).intValue();
    if (params.containsKey("hints")) this.customHints = (List<String>) params.get("hints");
    if (params.containsKey("index_file")) this.indexFile = (String) params.get("index_file");
    // Parse userinfo (user:pass@) out of the remote_url for Basic auth and keep
    // the auth-free base URL that the "/search" endpoint is appended to.
    // Mirrors Python native_vector_search/skill.py setup().
    this.remoteBaseUrl = remoteUrl;
    this.remoteAuth = null;
    if (remoteUrl != null && !remoteUrl.isEmpty()) {
      try {
        URI parsed = URI.create(remoteUrl);
        String userInfo = parsed.getUserInfo();
        if (userInfo != null && userInfo.contains(":")) {
          this.remoteAuth = userInfo;
          StringBuilder base = new StringBuilder();
          base.append(parsed.getScheme()).append("://").append(parsed.getHost());
          if (parsed.getPort() != -1) base.append(':').append(parsed.getPort());
          if (parsed.getPath() != null) base.append(parsed.getPath());
          this.remoteBaseUrl = base.toString();
        }
      } catch (IllegalArgumentException e) {
        // Leave remoteBaseUrl = remoteUrl; the request will fail loudly at send time.
        log.warn("Could not parse remote_url for auth extraction: %s", remoteUrl);
      }
    }
    return remoteUrl != null && !remoteUrl.isEmpty();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ToolDefinition> registerTools() {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("type", "object");
    parameters.put(
        "properties",
        Map.of(
            "query", Map.of("type", "string", "description", "Search query"),
            "count", Map.of("type", "integer", "description", "Number of results to return")));
    parameters.put("required", List.of("query"));

    return List.of(
        new ToolDefinition(
            toolName,
            description,
            parameters,
            (args, raw) -> {
              String query = (String) args.get("query");
              int resultCount =
                  args.containsKey("count") ? ((Number) args.get("count")).intValue() : count;

              try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("query", query);
                body.put("index_name", indexName);
                body.put("count", resultCount);

                // POST to <remote_base_url>/search — matches Python
                // native_vector_search/skill.py _search_remote (NOT the bare
                // remote_url). remote_base_url has any user:pass@ stripped.
                String searchUrl = remoteBaseUrl + "/search";
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest.Builder builder =
                    HttpRequest.newBuilder()
                        .uri(URI.create(searchUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)));
                if (remoteAuth != null) {
                  builder.header(
                      "Authorization",
                      "Basic "
                          + Base64.getEncoder()
                              .encodeToString(remoteAuth.getBytes(StandardCharsets.UTF_8)));
                }
                HttpRequest request = builder.build();
                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                  return new FunctionResult("Search failed: " + response.statusCode());
                }

                Map<String, Object> result =
                    new Gson()
                        .fromJson(
                            response.body(), new TypeToken<Map<String, Object>>() {}.getType());
                List<Map<String, Object>> results =
                    (List<Map<String, Object>>) result.get("results");

                if (results == null || results.isEmpty()) {
                  return new FunctionResult("No results found for: " + query);
                }

                StringBuilder sb = new StringBuilder("Search results:\n\n");
                for (Map<String, Object> r : results) {
                  sb.append(r.getOrDefault("text", r.getOrDefault("content", ""))).append("\n\n");
                }
                return new FunctionResult(sb.toString());
              } catch (Exception e) {
                log.error("Vector search error", e);
                return new FunctionResult("Error performing search: " + e.getMessage());
              }
            }));
  }

  @Override
  public List<String> getHints() {
    List<String> hints =
        new ArrayList<>(List.of("search", "find", "look up", "documentation", "knowledge base"));
    hints.addAll(customHints);
    return hints;
  }

  /**
   * Python parity: get_global_data returns {@code {}} unless the local search engine can supply
   * stats (native_vector_search/skill.py). This port has no local search engine to introspect, so
   * it faithfully mirrors the base case — an empty map.
   */
  @Override
  public Map<String, Object> getGlobalData() {
    return new LinkedHashMap<>();
  }

  /**
   * Python parity: get_prompt_sections returns {@code []}; the section is added in register_tools
   * once the agent is set (native_vector_search/skill.py).
   */
  @Override
  public List<Map<String, Object>> getPromptSections() {
    return Collections.emptyList();
  }

  /**
   * Python parity: get_instance_key uses the tool name (default {@code "search_knowledge"}) and the
   * index file (default {@code "default"}) — {@code f"{SKILL_NAME}_{tool_name}_{index_file}"}
   * (native_vector_search/skill.py).
   */
  @Override
  public String getInstanceKey() {
    return getName() + "_" + toolName + "_" + indexFile;
  }

  /**
   * Python parity: cleanup best-effort removes any temp dirs created while indexing
   * (native_vector_search/skill.py). This port runs in remote mode only and creates no temp dirs,
   * so there is nothing to remove; it is a safe no-op that logs.
   */
  @Override
  public void cleanup() {
    log.debug("Native vector search skill cleaned up");
  }

  /**
   * Python parity: get_parameter_schema (native_vector_search/skill.py) — base params plus custom
   * fields.
   */
  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = SkillParams.base(true, getName());

    schema.put(
        "index_file",
        required(
            "string",
            "Path to .swsearch index file (SQLite backend only). Use this for local"
                + " file-based search"));
    schema.put("build_index", entry("boolean", "Whether to build index from source files", false));
    schema.put(
        "source_dir",
        required(
            "string", "Directory containing documents to index (required if build_index=True)"));
    schema.put(
        "remote_url",
        required(
            "string",
            "URL of remote search server for network mode (e.g., http://localhost:8001)."
                + " Use this instead of index_file or pgvector for centralized search"));
    schema.put(
        "index_name",
        entry(
            "string",
            "Name of index on remote server (network mode only, used with remote_url)",
            "default"));

    Map<String, Object> count = entry("integer", "Number of search results to return", 5);
    count.put("minimum", 1);
    count.put("maximum", 20);
    schema.put("count", count);

    Map<String, Object> similarity =
        entry(
            "number",
            "Minimum similarity score for results (0.0 = no limit, 1.0 = exact match)",
            0.0);
    similarity.put("minimum", 0.0);
    similarity.put("maximum", 1.0);
    schema.put("similarity_threshold", similarity);

    schema.put("tags", arrayEntry("Tags to filter search results", new ArrayList<>(), "string"));
    schema.put(
        "global_tags",
        arrayEntry("Tags to apply to all indexed documents", new ArrayList<>(), "string"));
    schema.put(
        "file_types",
        arrayEntry(
            "File extensions to include when building index",
            new ArrayList<>(List.of("md", "txt", "pdf", "docx", "html")),
            "string"));
    schema.put(
        "exclude_patterns",
        arrayEntry(
            "Patterns to exclude when building index",
            new ArrayList<>(
                List.of("**/node_modules/**", "**/.git/**", "**/dist/**", "**/build/**")),
            "string"));

    schema.put(
        "no_results_message",
        entry("string", "Message when no results are found", "No information found for '{query}'"));
    schema.put("response_prefix", entry("string", "Prefix to add to search results", ""));
    schema.put("response_postfix", entry("string", "Postfix to add to search results", ""));

    Map<String, Object> maxContent =
        entry(
            "integer",
            "Maximum total response size in characters (distributed across all results)",
            32768);
    maxContent.put("minimum", 1000);
    schema.put("max_content_length", maxContent);

    schema.put(
        "response_format_callback",
        required(
            "callable",
            "Optional callback function to format/transform the response. Called with"
                + " (response, agent, query, results, args). Must return a string."));

    schema.put(
        "description",
        entry("string", "Tool description", "Search the knowledge base for information"));
    schema.put("hints", arrayEntry("Speech recognition hints", new ArrayList<>(), "string"));

    Map<String, Object> nlpBackend = entry("string", "NLP backend for query processing", "basic");
    nlpBackend.put("enum", List.of("basic", "spacy", "nltk"));
    schema.put("nlp_backend", nlpBackend);

    Map<String, Object> queryNlp = required("string", "NLP backend for query expansion");
    queryNlp.put("enum", List.of("basic", "spacy", "nltk"));
    schema.put("query_nlp_backend", queryNlp);

    Map<String, Object> indexNlp = required("string", "NLP backend for indexing");
    indexNlp.put("enum", List.of("basic", "spacy", "nltk"));
    schema.put("index_nlp_backend", indexNlp);

    Map<String, Object> backend =
        entry(
            "string",
            "Storage backend for local database mode: 'sqlite' for file-based or 'pgvector'"
                + " for PostgreSQL. Ignored if remote_url is set",
            "sqlite");
    backend.put("enum", List.of("sqlite", "pgvector"));
    schema.put("backend", backend);

    schema.put(
        "connection_string",
        required(
            "string",
            "PostgreSQL connection string (pgvector backend only, e.g.,"
                + " 'postgresql://user:pass@localhost:5432/dbname'). Required when"
                + " backend='pgvector'"));
    schema.put(
        "collection_name",
        required(
            "string",
            "Collection/table name in PostgreSQL (pgvector backend only). Required when"
                + " backend='pgvector'"));
    schema.put("verbose", entry("boolean", "Enable verbose logging", false));

    Map<String, Object> keywordWeight = new LinkedHashMap<>();
    keywordWeight.put("type", "number");
    keywordWeight.put(
        "description", "Manual keyword weight (0.0-1.0). Overrides automatic weight detection");
    keywordWeight.put("default", null);
    keywordWeight.put("required", false);
    keywordWeight.put("minimum", 0.0);
    keywordWeight.put("maximum", 1.0);
    schema.put("keyword_weight", keywordWeight);

    schema.put(
        "model_name",
        entry(
            "string",
            "Embedding model to use. Options: 'mini' (fastest, 384 dims), 'base' (balanced,"
                + " 768 dims), 'large' (same as base). Or specify full model name like"
                + " 'sentence-transformers/all-MiniLM-L6-v2'",
            "mini"));
    schema.put(
        "overwrite",
        entry(
            "boolean",
            "Overwrite existing pgvector collection when building index (pgvector backend"
                + " only)",
            false));

    return schema;
  }

  private static Map<String, Object> entry(String type, String description, Object dflt) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", type);
    m.put("description", description);
    m.put("default", dflt);
    m.put("required", false);
    return m;
  }

  private static Map<String, Object> required(String type, String description) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", type);
    m.put("description", description);
    m.put("required", false);
    return m;
  }

  private static Map<String, Object> arrayEntry(String description, Object dflt, String itemType) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", "array");
    m.put("description", description);
    m.put("default", dflt);
    m.put("required", false);
    m.put("items", Map.of("type", itemType));
    return m;
  }
}
