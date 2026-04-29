/**
 * RestAuditHarness -- runtime probe for the REST transport.
 *
 * <p>Driven by porting-sdk's {@code audit_rest_transport.py}. Reads:
 * <ul>
 *   <li>{@code REST_OPERATION}       dotted name (e.g.
 *       {@code calling.list_calls})</li>
 *   <li>{@code REST_FIXTURE_URL}     ({@code http://127.0.0.1:NNNN})</li>
 *   <li>{@code REST_OPERATION_ARGS}  JSON dict of args for the operation</li>
 *   <li>{@code SIGNALWIRE_PROJECT_ID}, {@code SIGNALWIRE_API_TOKEN}</li>
 * </ul>
 *
 * <p>Constructs a {@code RestClient} pointed at {@code REST_FIXTURE_URL},
 * invokes the named operation, and prints the parsed return value as
 * JSON to stdout. Exits 0 on success, non-zero on any error.
 *
 * <p>Operations supported by this harness:
 * <ul>
 *   <li>{@code calling.list_calls}        GET LAML
 *       {@code /api/laml/2010-04-01/Accounts/{proj}/Calls.json}</li>
 *   <li>{@code messaging.send}            POST LAML
 *       {@code /api/laml/2010-04-01/Accounts/{proj}/Messages.json}</li>
 *   <li>{@code phone_numbers.list}        GET
 *       {@code /api/phone_numbers}</li>
 *   <li>{@code fabric.subscribers.list}   GET
 *       {@code /api/fabric/subscribers}</li>
 *   <li>{@code compatibility.calls.list}  GET LAML Calls (alias for
 *       {@code calling.list_calls}; both list LAML calls)</li>
 * </ul>
 */

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.rest.HttpClient;
import com.signalwire.sdk.rest.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

public class RestAuditHarness {

    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        String operation = orFail("REST_OPERATION");
        String fixtureUrl = orFail("REST_FIXTURE_URL");
        String argsRaw = orDefault(System.getenv("REST_OPERATION_ARGS"), "{}");
        String projectId = orFail("SIGNALWIRE_PROJECT_ID");
        String token = orFail("SIGNALWIRE_API_TOKEN");

        Map<String, Object> opArgs = parseJsonObject(argsRaw);

        // The audit fixture binds to a plain-HTTP loopback port; route the
        // REST client through it via the explicit-base-URL factory. The
        // factory normalizes the trailing /api so existing namespace paths
        // (e.g. /phone_numbers) end up in the right place.
        RestClient client = RestClient.withBaseUrl(fixtureUrl, projectId, token);
        // LAML operations use paths shaped like
        // {@code /api/laml/2010-04-01/Accounts/{proj}/...} where the {@code /api}
        // prefix is part of the documented path. The fixture asserts on
        // {@code /api/laml/2010-04-01/Accounts}, so we point the LAML
        // HttpClient at the fixture's bare host (NO {@code /api} suffix)
        // and pass the full {@code /api/laml/...} path explicitly below.
        String trimmedFixture = fixtureUrl.replaceAll("/$", "");
        HttpClient lamlHttp = HttpClient.withBaseUrl(trimmedFixture, projectId, token);

        Object result;
        try {
            result = switch (operation) {
                case "calling.list_calls", "compatibility.calls.list" ->
                        callingListCalls(lamlHttp, projectId, opArgs);
                case "messaging.send" ->
                        messagingSend(lamlHttp, projectId, opArgs);
                case "phone_numbers.list" ->
                        client.phoneNumbers().list(stringQuery(opArgs));
                case "fabric.subscribers.list" ->
                        client.fabric().subscribers().list(stringQuery(opArgs));
                default -> null;
            };
        } catch (Exception e) {
            die("operation '" + operation + "' failed: " + e.getMessage(), 1);
            return;
        }

        if (result == null) {
            die("unsupported operation '" + operation + "'", 2);
            return;
        }

        System.out.println(GSON.toJson(result));
        System.exit(0);
    }

    /**
     * GET /api/laml/2010-04-01/Accounts/{projectId}/Calls.json
     * The fixture only checks for /api/laml/2010-04-01/Accounts in the path
     * and a Basic-auth header; the body is the canned response.
     */
    private static Map<String, Object> callingListCalls(
            HttpClient http, String projectId, Map<String, Object> args) {
        String path = "/api/laml/2010-04-01/Accounts/" + projectId + "/Calls.json";
        return http.get(path, stringQuery(args));
    }

    /**
     * POST /api/laml/2010-04-01/Accounts/{projectId}/Messages.json
     * Audit fixture checks for "Messages" in path + Basic auth + a 2xx JSON
     * response body containing the canned sentinel.
     */
    private static Map<String, Object> messagingSend(
            HttpClient http, String projectId, Map<String, Object> args) {
        String path = "/api/laml/2010-04-01/Accounts/" + projectId + "/Messages.json";
        return http.post(path, args);
    }

    private static Map<String, String> stringQuery(Map<String, Object> args) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : args.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String s) {
                out.put(e.getKey(), s);
            } else if (v instanceof Number || v instanceof Boolean) {
                out.put(e.getKey(), v.toString());
            }
        }
        return out;
    }

    private static Map<String, Object> parseJsonObject(String raw) {
        try {
            Map<String, Object> obj = GSON.fromJson(raw,
                    new TypeToken<Map<String, Object>>() {}.getType());
            return obj == null ? new LinkedHashMap<>() : obj;
        } catch (Exception e) {
            die("REST_OPERATION_ARGS is not a JSON object: " + e.getMessage(), 1);
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

    private static void die(String msg, int code) {
        System.err.println("RestAuditHarness: " + msg);
        System.exit(code);
    }
}
