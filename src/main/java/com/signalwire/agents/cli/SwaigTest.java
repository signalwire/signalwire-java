package com.signalwire.agents.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * CLI tool for testing SWAIG functions against a running agent.
 * <p>
 * Uses only JDK classes (java.net.http.HttpClient) -- no external dependencies.
 * <p>
 * Usage:
 * <pre>
 *   swaig-test --url http://user:pass@localhost:3000 --list-tools
 *   swaig-test --url http://user:pass@localhost:3000 --dump-swml
 *   swaig-test --url http://user:pass@localhost:3000 --exec tool_name --param key=value
 *   swaig-test --url http://user:pass@localhost:3000 --exec tool_name --raw
 *   swaig-test --url http://user:pass@localhost:3000 --verbose --list-tools
 * </pre>
 */
public class SwaigTest {

    private String baseUrl;
    private String authUser;
    private String authPassword;
    private boolean verbose;
    private boolean raw;

    // Commands
    private boolean dumpSwml;
    private boolean listTools;
    private String execTool;
    private final Map<String, String> params = new LinkedHashMap<>();

    public static void main(String[] args) {
        var cli = new SwaigTest();
        try {
            cli.parseArgs(args);
            cli.run();
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (cli.verbose) {
                e.printStackTrace(System.err);
            }
            System.exit(1);
        }
    }

    private void parseArgs(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("No arguments provided");
        }

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--url" -> {
                    if (++i >= args.length) throw new IllegalArgumentException("--url requires a value");
                    parseUrl(args[i]);
                }
                case "--dump-swml" -> dumpSwml = true;
                case "--list-tools" -> listTools = true;
                case "--exec" -> {
                    if (++i >= args.length) throw new IllegalArgumentException("--exec requires a tool name");
                    execTool = args[i];
                }
                case "--param" -> {
                    if (++i >= args.length) throw new IllegalArgumentException("--param requires key=value");
                    String kv = args[i];
                    int eq = kv.indexOf('=');
                    if (eq <= 0) throw new IllegalArgumentException("--param must be key=value, got: " + kv);
                    params.put(kv.substring(0, eq), kv.substring(eq + 1));
                }
                case "--raw" -> raw = true;
                case "--verbose" -> verbose = true;
                case "--help", "-h" -> {
                    printUsage();
                    System.exit(0);
                }
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }

        if (baseUrl == null) {
            throw new IllegalArgumentException("--url is required");
        }
        if (!dumpSwml && !listTools && execTool == null) {
            throw new IllegalArgumentException("One of --dump-swml, --list-tools, or --exec is required");
        }
    }

    /**
     * Parse URL, extracting embedded auth credentials if present.
     * Supports: http://user:pass@host:port/path
     */
    private void parseUrl(String urlStr) {
        try {
            // Extract auth from URL if present
            if (urlStr.contains("@")) {
                int schemeEnd = urlStr.indexOf("://");
                if (schemeEnd >= 0) {
                    String scheme = urlStr.substring(0, schemeEnd + 3);
                    String rest = urlStr.substring(schemeEnd + 3);
                    int atIdx = rest.indexOf('@');
                    if (atIdx >= 0) {
                        String authPart = rest.substring(0, atIdx);
                        String hostPart = rest.substring(atIdx + 1);
                        int colonIdx = authPart.indexOf(':');
                        if (colonIdx >= 0) {
                            authUser = authPart.substring(0, colonIdx);
                            authPassword = authPart.substring(colonIdx + 1);
                        } else {
                            authUser = authPart;
                            authPassword = "";
                        }
                        urlStr = scheme + hostPart;
                    }
                }
            }

            // Remove trailing slash
            if (urlStr.endsWith("/") && urlStr.length() > 1) {
                urlStr = urlStr.substring(0, urlStr.length() - 1);
            }
            this.baseUrl = urlStr;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL: " + urlStr);
        }
    }

    private void run() throws Exception {
        if (dumpSwml) {
            doDumpSwml();
        } else if (listTools) {
            doListTools();
        } else if (execTool != null) {
            doExecTool();
        }
    }

    private void doDumpSwml() throws Exception {
        if (verbose) System.err.println("[verbose] GET " + baseUrl);

        String response = httpGet(baseUrl);
        if (raw) {
            System.out.println(response);
        } else {
            System.out.println(prettyPrintJson(response));
        }
    }

    private void doListTools() throws Exception {
        if (verbose) System.err.println("[verbose] GET " + baseUrl + " (fetching SWML to extract tools)");

        String response = httpGet(baseUrl);

        // Extract function names from SWML JSON
        // Look for "functions" array, each with "function" key
        List<String> tools = extractToolNames(response);
        if (tools.isEmpty()) {
            System.out.println("No tools found.");
        } else {
            System.out.println("Tools (" + tools.size() + "):");
            for (String tool : tools) {
                System.out.println("  - " + tool);
            }
        }
    }

    private void doExecTool() throws Exception {
        String swaigUrl = baseUrl + "/swaig";
        if (verbose) System.err.println("[verbose] POST " + swaigUrl + " (exec: " + execTool + ")");

        // Build SWAIG request payload
        StringBuilder json = new StringBuilder();
        json.append("{\"function\":\"").append(escapeJson(execTool)).append("\"");

        if (!params.isEmpty()) {
            json.append(",\"argument\":{\"parsed\":[{");
            boolean first = true;
            for (var entry : params.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
                    .append(escapeJson(entry.getValue())).append("\"");
                first = false;
            }
            json.append("}]}");
        }
        json.append("}");

        if (verbose) System.err.println("[verbose] Payload: " + json);

        String response = httpPost(swaigUrl, json.toString());
        if (raw) {
            System.out.println(response);
        } else {
            System.out.println(prettyPrintJson(response));
        }
    }

    // ── HTTP helpers using java.net.http ──────────────────────────────

    private String httpGet(String url) throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET();
        addAuthHeader(builder);
        var req = builder.build();
        var resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (verbose) System.err.println("[verbose] Response status: " + resp.statusCode());
        if (resp.statusCode() >= 400) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }

    private String httpPost(String url, String body) throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        addAuthHeader(builder);
        var req = builder.build();
        var resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (verbose) System.err.println("[verbose] Response status: " + resp.statusCode());
        if (resp.statusCode() >= 400) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }

    private void addAuthHeader(HttpRequest.Builder builder) {
        if (authUser != null) {
            String creds = authUser + ":" + (authPassword != null ? authPassword : "");
            String encoded = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        }
    }

    // ── JSON helpers (no external deps) ──────────────────────────────

    /**
     * Extract tool names from SWML JSON response.
     * Looks for "function":"name" patterns within the functions array.
     */
    private List<String> extractToolNames(String json) {
        List<String> names = new ArrayList<>();
        // Simple pattern matching: find "function":"value" patterns
        String marker = "\"function\":\"";
        int idx = 0;
        while ((idx = json.indexOf(marker, idx)) >= 0) {
            int start = idx + marker.length();
            int end = json.indexOf("\"", start);
            if (end > start) {
                String name = json.substring(start, end);
                // Skip "function" key that's part of SWML version/sections structure
                if (!name.isEmpty() && !name.contains("{") && !name.contains("}")) {
                    names.add(name);
                }
            }
            idx = start;
        }
        return names;
    }

    /**
     * Minimal JSON pretty-printer (no dependencies).
     */
    private String prettyPrintJson(String json) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                sb.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }

            if (inString) {
                sb.append(c);
                continue;
            }

            switch (c) {
                case '{', '[' -> {
                    sb.append(c);
                    indent += 2;
                    sb.append('\n');
                    sb.append(" ".repeat(indent));
                }
                case '}', ']' -> {
                    indent -= 2;
                    sb.append('\n');
                    sb.append(" ".repeat(Math.max(0, indent)));
                    sb.append(c);
                }
                case ',' -> {
                    sb.append(c);
                    sb.append('\n');
                    sb.append(" ".repeat(indent));
                }
                case ':' -> sb.append(": ");
                case ' ', '\t', '\n', '\r' -> {
                    // skip whitespace
                }
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void printUsage() {
        System.err.println("""
                Usage: swaig-test [options]

                Options:
                  --url URL           Agent URL (e.g. http://user:pass@localhost:3000)
                  --dump-swml         Fetch and display the SWML document
                  --list-tools        List all registered SWAIG tools
                  --exec NAME         Execute a SWAIG tool by name
                  --param key=value   Pass a parameter to the tool (repeatable)
                  --raw               Output raw JSON without pretty-printing
                  --verbose           Show verbose debug output
                  --help, -h          Show this help message

                Examples:
                  swaig-test --url http://user:pass@localhost:3000 --dump-swml
                  swaig-test --url http://user:pass@localhost:3000 --list-tools
                  swaig-test --url http://user:pass@localhost:3000 --exec get_weather --param city=Austin
                """);
    }
}
