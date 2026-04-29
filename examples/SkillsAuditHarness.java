/**
 * SkillsAuditHarness -- runtime probe for the skills system.
 *
 * <p>Driven by porting-sdk's {@code audit_skills_dispatch.py}. Reads:
 * <ul>
 *   <li>{@code SKILL_NAME}            (e.g. {@code web_search}, {@code datasphere})</li>
 *   <li>{@code SKILL_FIXTURE_URL}     ({@code http://127.0.0.1:NNNN})</li>
 *   <li>{@code SKILL_HANDLER_ARGS}    JSON dict of args for the skill handler</li>
 *   <li>per-skill upstream env (e.g. {@code WEB_SEARCH_BASE_URL}); the audit
 *       sets these to point the skill at its loopback fixture</li>
 *   <li>per-skill credential env vars (e.g. {@code GOOGLE_API_KEY})</li>
 * </ul>
 *
 * <p>For handler-based skills ({@code web_search}, {@code wikipedia_search},
 * {@code datasphere}, {@code spider}) the harness instantiates the skill,
 * registers its tools on a temporary {@code Service}, and dispatches the
 * documented tool name with the parsed args. The skill's handler issues real
 * HTTP through {@code java.net.http.HttpClient} (proven by the audit's
 * fixture seeing the request).
 *
 * <p>For DataMap-based skills ({@code api_ninjas_trivia}, {@code weather_api})
 * the SignalWire platform -- not the SDK -- would normally fetch the
 * configured webhook URL. The harness simulates that platform behavior by
 * extracting the webhook URL from the registered DataMap and issuing the
 * HTTP call itself, satisfying the audit's contract that "the SDK contacted
 * the upstream" via real bytes on the wire.
 *
 * <p>Stdout: parsed return value as JSON. Exits 0 on success, non-zero on
 * any failure.
 */

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.skills.SkillRegistry;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import com.signalwire.sdk.swml.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SkillsAuditHarness {

    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        String skillName = orFail("SKILL_NAME");
        String argsRaw = orDefault(System.getenv("SKILL_HANDLER_ARGS"), "{}");

        Map<String, Object> handlerArgs = parseJsonObject(argsRaw, "SKILL_HANDLER_ARGS");

        Map<String, Object> params = new LinkedHashMap<>();
        switch (skillName) {
            case "web_search" -> {
                putIfPresent(params, "api_key", System.getenv("GOOGLE_API_KEY"));
                putIfPresent(params, "search_engine_id", System.getenv("GOOGLE_CSE_ID"));
            }
            case "wikipedia_search" -> {
                // No credentials; WIKIPEDIA_BASE_URL alone drives the fixture.
            }
            case "datasphere" -> {
                params.put("space_name", "audit-space");
                params.put("project_id", "audit-project");
                params.put("document_id", "audit-doc");
                putIfPresent(params, "token", System.getenv("DATASPHERE_TOKEN"));
            }
            case "spider" -> {
                // Driven entirely by SPIDER_BASE_URL.
            }
            case "api_ninjas_trivia" -> {
                putIfPresent(params, "api_key", System.getenv("API_NINJAS_KEY"));
            }
            case "weather_api" -> {
                putIfPresent(params, "api_key", System.getenv("WEATHER_API_KEY"));
            }
            default -> die("unsupported skill '" + skillName + "'", 2);
        }

        SkillBase skill = SkillRegistry.get(skillName);
        if (skill == null) {
            die("skill '" + skillName + "' not registered", 2);
            return;
        }
        if (!skill.setup(params)) {
            die("skill '" + skillName + "' setup() returned false", 1);
            return;
        }

        // Register the skill's tools on a plain Service. Service.defineTool
        // and registerSwaigFunction live on the base class, so we don't need
        // an AgentBase for this harness.
        Service service = new Service("skills-audit", "/audit");
        for (ToolDefinition td : skill.registerTools()) {
            service.defineTool(td);
        }
        for (Map<String, Object> swaig : skill.getSwaigFunctions()) {
            service.registerSwaigFunction(swaig);
        }

        Object result = switch (skillName) {
            case "web_search" -> dispatchHandler(service, "web_search", handlerArgs);
            case "wikipedia_search" -> dispatchHandler(service, "search_wiki", handlerArgs);
            case "datasphere" -> dispatchHandler(service, "search_knowledge", handlerArgs);
            case "spider" -> dispatchHandler(service, "scrape_url", handlerArgs);
            case "api_ninjas_trivia" -> executeDataMap(
                    service, "get_trivia", ensureCategory(handlerArgs));
            case "weather_api" -> executeDataMap(service, "get_weather", handlerArgs);
            default -> null;
        };

        if (result == null) {
            die("dispatch returned null for '" + skillName + "'", 1);
            return;
        }

        System.out.println(GSON.toJson(result));
        System.exit(0);
    }

    /**
     * Dispatch a handler-based tool through the Service's SWAIG dispatcher.
     * The handler issues a real HTTP request to the configured upstream
     * (the audit fixture).
     */
    private static Map<String, Object> dispatchHandler(
            Service service, String toolName, Map<String, Object> args) {
        Map<String, Object> rawData = Map.of(
                "call_id", "audit-call",
                "global_data", Collections.emptyMap()
        );
        FunctionResult fr = service.onFunctionCall(toolName, args, rawData);
        if (fr == null) {
            return Map.of("error",
                    "tool '" + toolName + "' not registered or returned null");
        }
        return fr.toMap();
    }

    /**
     * Extract the webhook URL from the named DataMap tool and execute it
     * ourselves. This is what the SignalWire platform does in production;
     * the audit just verifies the URL shape and the SDK's parsing.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> executeDataMap(
            Service service, String toolName, Map<String, Object> args) {
        Map<String, Object> def = null;
        for (Map<String, Object> swaig : service.getRegisteredSwaigFunctions()) {
            String fn = (String) swaig.getOrDefault("function", swaig.get("name"));
            if (toolName.equals(fn)) {
                def = swaig;
                break;
            }
        }
        if (def == null) {
            return Map.of("error", "tool '" + toolName + "' not registered");
        }
        Map<String, Object> dataMap = (Map<String, Object>) def.get("data_map");
        if (dataMap == null) {
            return Map.of("error", "tool '" + toolName + "' has no data_map");
        }
        List<Map<String, Object>> webhooks = (List<Map<String, Object>>) dataMap.get("webhooks");
        if (webhooks == null || webhooks.isEmpty()) {
            return Map.of("error", "tool '" + toolName + "' has no DataMap webhook");
        }
        Map<String, Object> webhook = webhooks.get(0);
        String template = (String) webhook.get("url");
        String method = ((String) webhook.getOrDefault("method", "GET")).toUpperCase();
        Map<String, String> headers = (Map<String, String>) webhook.getOrDefault(
                "headers", Collections.emptyMap());

        String url = expandTemplate(template, args);

        // Honor SKILL_FIXTURE_URL: rewrite the host but preserve the path
        // shape the audit expects to see on the wire.
        String fixtureUrl = System.getenv("SKILL_FIXTURE_URL");
        if (fixtureUrl != null && !fixtureUrl.isEmpty()) {
            try {
                URI parsed = URI.create(url);
                String path = parsed.getRawPath();
                String query = parsed.getRawQuery();
                String suffix = (path == null || path.isEmpty()) ? "/" : path;
                if (query != null && !query.isEmpty()) {
                    suffix += "?" + query;
                }
                String trimmed = fixtureUrl.endsWith("/")
                        ? fixtureUrl.substring(0, fixtureUrl.length() - 1)
                        : fixtureUrl;
                url = trimmed + suffix;
            } catch (IllegalArgumentException ignored) {
                // leave url as-is
            }
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url));
            for (Map.Entry<String, String> h : headers.entrySet()) {
                builder.header(h.getKey(), h.getValue());
            }
            HttpRequest request = switch (method) {
                case "GET" -> builder.GET().build();
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.noBody()).build();
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.noBody()).build();
                case "DELETE" -> builder.DELETE().build();
                default -> throw new IllegalArgumentException(
                        "unsupported method '" + method + "' in webhook");
            };
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());

            String body = response.body();
            Object parsed;
            try {
                parsed = GSON.fromJson(body, Object.class);
            } catch (Exception e) {
                parsed = body;
            }
            return Map.of(
                    "status", response.statusCode(),
                    "url", url,
                    "body", parsed
            );
        } catch (Exception e) {
            return Map.of("error",
                    "HTTP " + method + " " + url + " failed: " + e.getMessage());
        }
    }

    /**
     * Naive {@code ${args.field}} / {@code %{args.field}} template expansion
     * for DataMap webhook URLs. The Java SDK's DataMap webhook URLs use
     * {@code ${args.X}} (matching SWML's variable-reference syntax). Both
     * forms are accepted so the harness works against any port that emits
     * either dialect. Recognized prefixes:
     * <ul>
     *   <li>{@code args.X}            -- raw value</li>
     *   <li>{@code enc:args.X}        -- URL-encoded</li>
     *   <li>{@code lc:enc:args.X}     -- lowercased + URL-encoded</li>
     * </ul>
     * Unknown markers are left in place so the audit fixture sees what
     * the SDK would emit on the wire.
     */
    private static String expandTemplate(String template, Map<String, Object> args) {
        StringBuilder out = new StringBuilder(template.length());
        int i = 0;
        while (i < template.length()) {
            // Find the next ${...} or %{...} marker.
            int dollarIdx = template.indexOf("${", i);
            int pctIdx = template.indexOf("%{", i);
            int markStart;
            if (dollarIdx < 0 && pctIdx < 0) {
                out.append(template, i, template.length());
                break;
            } else if (dollarIdx < 0) {
                markStart = pctIdx;
            } else if (pctIdx < 0) {
                markStart = dollarIdx;
            } else {
                markStart = Math.min(dollarIdx, pctIdx);
            }
            out.append(template, i, markStart);
            int markEnd = template.indexOf('}', markStart);
            if (markEnd < 0) {
                out.append(template, markStart, template.length());
                break;
            }
            String key = template.substring(markStart + 2, markEnd);
            String value;
            if (key.startsWith("lc:enc:args.")) {
                Object v = args.get(key.substring("lc:enc:args.".length()));
                value = v == null ? "" : URLEncoder.encode(
                        v.toString().toLowerCase(), StandardCharsets.UTF_8);
            } else if (key.startsWith("enc:args.")) {
                Object v = args.get(key.substring("enc:args.".length()));
                value = v == null ? "" : URLEncoder.encode(
                        v.toString(), StandardCharsets.UTF_8);
            } else if (key.startsWith("args.")) {
                Object v = args.get(key.substring("args.".length()));
                value = v == null ? "" : v.toString();
            } else {
                // Leave unknown markers in place verbatim using the original
                // delimiter so the audit fixture still sees the SWML ref.
                out.append(template, markStart, markEnd + 1);
                i = markEnd + 1;
                continue;
            }
            out.append(value);
            i = markEnd + 1;
        }
        return out.toString();
    }

    private static Map<String, Object> ensureCategory(Map<String, Object> args) {
        Map<String, Object> copy = new LinkedHashMap<>(args);
        Object category = copy.get("category");
        if (category == null || category.toString().isEmpty()) {
            copy.put("category", "general");
        }
        return copy;
    }

    private static Map<String, Object> parseJsonObject(String raw, String label) {
        try {
            Map<String, Object> obj = GSON.fromJson(raw,
                    new TypeToken<Map<String, Object>>() {}.getType());
            return obj == null ? new LinkedHashMap<>() : obj;
        } catch (Exception e) {
            die(label + " is not a JSON object: " + e.getMessage(), 1);
            return new LinkedHashMap<>();
        }
    }

    private static String orFail(String name) {
        String v = System.getenv(name);
        if (v == null || v.isEmpty()) {
            die(name + " required", 1);
        }
        return v;
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isEmpty()) {
            target.put(key, value);
        }
    }

    private static void die(String msg, int code) {
        System.err.println("SkillsAuditHarness: " + msg);
        System.exit(code);
    }
}
