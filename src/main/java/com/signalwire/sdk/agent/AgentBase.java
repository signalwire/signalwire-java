package com.signalwire.sdk.agent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.contexts.ContextBuilder;
import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.runtime.EnvProvider;
import com.signalwire.sdk.runtime.ExecutionMode;
import com.signalwire.sdk.runtime.LambdaUrlResolver;
import com.signalwire.sdk.security.SessionManager;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.skills.SkillManager;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import com.signalwire.sdk.swaig.ToolHandler;
import com.signalwire.sdk.swml.Document;
import com.signalwire.sdk.swml.Service;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Base class for all SignalWire AI agents.
 * Composes prompt management, tool registration, AI config, HTTP serving,
 * skills integration, and SWML rendering.
 *
 * Use the builder pattern:
 * <pre>
 * var agent = AgentBase.builder()
 *     .name("my-agent")
 *     .route("/")
 *     .port(3000)
 *     .build();
 * </pre>
 */
public class AgentBase extends Service {

    private static final Logger log = Logger.getLogger(AgentBase.class);
    private static final int MAX_REQUEST_BODY_SIZE = 1_048_576; // 1 MB
    private static final Gson gson = new Gson();
    private static final Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern SIP_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,64}$");

    // --- Configuration ---
    // name, route, host, port, authUser, authPassword, document, httpServer
    // are inherited from Service. Only agent-specific fields below.
    private boolean autoAnswer;
    private int maxDuration;
    private boolean recordCall;
    private String recordFormat;
    private boolean recordStereo;

    // --- Prompt ---
    private String promptText;
    private String postPrompt;
    private String postPromptUrl;
    private boolean usePom = true;
    private final List<Map<String, Object>> pomSections = new ArrayList<>();

    // --- AI Config ---
    private final List<String> hints = new ArrayList<>();
    private final List<Map<String, Object>> languages = new ArrayList<>();
    private final List<Map<String, Object>> pronunciations = new ArrayList<>();
    private final Map<String, Object> params = new LinkedHashMap<>();
    private final Map<String, Object> promptLlmParams = new LinkedHashMap<>();
    private final Map<String, Object> postPromptLlmParams = new LinkedHashMap<>();
    private final Map<String, Object> globalData = new LinkedHashMap<>();
    private List<String> nativeFunctions;
    private final List<Map<String, Object>> internalFillers = new ArrayList<>();
    private boolean debugEventsEnabled = false;
    private final List<Map<String, Object>> functionIncludes = new ArrayList<>();

    // --- Tools ---
    // tools and registeredSwaigFunctions are now declared on Service (lifted
    // so non-agent SWMLService instances can host SWAIG functions).
    // AgentBase inherits via `extends Service`.

    // --- Verbs ---
    private final List<Map<String, Object>> preAnswerVerbs = new ArrayList<>();
    private final List<Map<String, Object>> answerVerbs = new ArrayList<>();
    private final List<Map<String, Object>> postAnswerVerbs = new ArrayList<>();
    private final List<Map<String, Object>> postAiVerbs = new ArrayList<>();

    // --- Contexts ---
    private ContextBuilder contextBuilder;

    // --- Skills ---
    private final SkillManager skillManager = new SkillManager(this);

    // --- Web ---
    private DynamicConfigCallback dynamicConfigCallback;
    private String webhookUrl;
    private String proxyUrlBase;
    private final Map<String, String> swaigQueryParams = new LinkedHashMap<>();
    private boolean debugRoutesEnabled = false;

    // --- MCP ---
    private final List<Map<String, Object>> mcpServers = new ArrayList<>();
    private boolean mcpServerEnabled = false;

    // --- SIP ---
    private boolean sipRoutingEnabled = false;
    private final Set<String> sipUsernames = new LinkedHashSet<>();

    // --- Callbacks ---
    private BiConsumer<Map<String, Object>, Map<String, Object>> onSummaryCallback;
    private Consumer<Map<String, Object>> onDebugEventCallback;

    // --- Security ---
    private final SessionManager sessionManager = new SessionManager();

    // --- HTTP Server ---
    // httpServer is now inherited from Service.

    /**
     * Package-private constructor — use the Builder. Calls Service's
     * full constructor with all configuration.
     */
    AgentBase(String name, String route, String host, int port,
              String authUser, String authPassword) {
        super(name, route, host, port, authUser, authPassword);
        this.autoAnswer = true;
        this.maxDuration = 3600;
        this.recordCall = false;
        this.recordFormat = "mp4";
        this.recordStereo = true;
    }

    // ============================================================
    // Builder
    // ============================================================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        // Collect Service-level args until build(), then call super(..) once.
        private String name = "agent";
        private String route = "/";
        private String host = "0.0.0.0";
        private Integer port = null;
        private String authUser = null;
        private String authPassword = null;

        // Agent-level config — applied to the constructed AgentBase post-super.
        private boolean autoAnswer = true;
        private int maxDuration = 3600;
        private boolean recordCall = false;
        private String recordFormat = "mp4";
        private boolean recordStereo = true;
        private EnvProvider envProvider;

        public Builder name(String name) { this.name = name; return this; }
        public Builder route(String route) {
            this.route = route.endsWith("/") && route.length() > 1
                    ? route.substring(0, route.length() - 1) : route;
            return this;
        }
        public Builder host(String host) { this.host = host; return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder autoAnswer(boolean autoAnswer) { this.autoAnswer = autoAnswer; return this; }
        public Builder maxDuration(int maxDuration) { this.maxDuration = maxDuration; return this; }
        public Builder recordCall(boolean recordCall) { this.recordCall = recordCall; return this; }
        public Builder recordFormat(String format) { this.recordFormat = format; return this; }
        public Builder recordStereo(boolean stereo) { this.recordStereo = stereo; return this; }
        public Builder authUser(String user) { this.authUser = user; return this; }
        public Builder authPassword(String password) { this.authPassword = password; return this; }

        /**
         * Supply an alternative {@link EnvProvider} for the build-time env
         * var reads ({@code SWML_BASIC_AUTH_USER}, {@code SWML_BASIC_AUTH_PASSWORD},
         * {@code SWML_PROXY_URL_BASE}).
         *
         * <p>Primarily for the {@code swaig-test --simulate-serverless}
         * harness, which needs to mask the real process env with simulated
         * values without mutating {@link System#getenv()} (which Java does
         * not support). Pass {@code null} to fall back to the real process
         * env (the default).
         *
         * @param env environment source.
         * @return this builder.
         */
        public Builder envProvider(EnvProvider env) {
            this.envProvider = env;
            return this;
        }

        public AgentBase build() {
            EnvProvider env = this.envProvider != null ? this.envProvider : EnvProvider.SYSTEM;

            // Resolve auth before constructing the agent; Service's constructor
            // does its own env-fallback if both are null, but we want builder
            // overrides + the auto-generated-password warning here.
            String resolvedUser = this.authUser;
            if (resolvedUser == null) {
                String envUser = env.get("SWML_BASIC_AUTH_USER");
                resolvedUser = (envUser != null && !envUser.isEmpty()) ? envUser : this.name;
            }
            String resolvedPass = this.authPassword;
            boolean passwordAutoGenerated = false;
            if (resolvedPass == null) {
                String envPass = env.get("SWML_BASIC_AUTH_PASSWORD");
                if (envPass != null && !envPass.isEmpty()) {
                    resolvedPass = envPass;
                } else {
                    resolvedPass = Service.generatePassword();
                    passwordAutoGenerated = true;
                }
            }

            int resolvedPort = this.port != null ? this.port : Service.resolvePort();

            AgentBase agent = new AgentBase(
                    this.name, this.route, this.host, resolvedPort,
                    resolvedUser, resolvedPass);
            agent.autoAnswer = this.autoAnswer;
            agent.maxDuration = this.maxDuration;
            agent.recordCall = this.recordCall;
            agent.recordFormat = this.recordFormat;
            agent.recordStereo = this.recordStereo;

            // Resolve proxy URL base
            String envProxy = env.get("SWML_PROXY_URL_BASE");
            if (envProxy != null && !envProxy.isEmpty()) {
                agent.proxyUrlBase = envProxy;
            }

            log.info("Agent '%s' initialized at route '%s', auth user: %s",
                    agent.name, agent.route, agent.authUser);

            // Warn loudly if the password was auto-generated. This is the
            // silent cause of every external caller hitting HTTP 401 when
            // .env wasn't loaded — the password lives only in this process
            // and changes on every restart.
            if (passwordAutoGenerated) {
                log.warn("basic_auth_password_autogenerated: username=%s. "
                        + "No SWML_BASIC_AUTH_PASSWORD found in environment "
                        + "and no password passed to the agent builder. The "
                        + "SDK generated a random password that exists only "
                        + "in this process; external callers will get HTTP 401 "
                        + "unless they read the value from this process's env. "
                        + "To fix, set SWML_BASIC_AUTH_USER and "
                        + "SWML_BASIC_AUTH_PASSWORD in your environment, or "
                        + "call .authUser(...).authPassword(...) on the agent "
                        + "builder.", agent.authUser);
            }

            return agent;
        }
    }

    // generatePassword() is provided by Service — use Service.generatePassword()
    // from this class (subclass) or via `Service` reference from Builder.

    // ============================================================
    // Prompt Methods
    // ============================================================

    public AgentBase setPromptText(String text) {
        this.promptText = text;
        this.usePom = false;
        return this;
    }

    public AgentBase setPostPrompt(String text) {
        this.postPrompt = text;
        return this;
    }

    public AgentBase promptAddSection(String title, String body, List<String> bullets) {
        this.usePom = true;
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", title);
        section.put("body", body != null ? body : "");
        if (bullets != null && !bullets.isEmpty()) {
            section.put("bullets", new ArrayList<>(bullets));
        }
        pomSections.add(section);
        return this;
    }

    public AgentBase promptAddSection(String title, String body) {
        return promptAddSection(title, body, null);
    }

    public AgentBase promptAddSubsection(String parentTitle, String title, String body) {
        for (Map<String, Object> section : pomSections) {
            if (parentTitle.equals(section.get("title"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> subs = (List<Map<String, Object>>) section
                        .computeIfAbsent("subsections", k -> new ArrayList<>());
                Map<String, Object> sub = new LinkedHashMap<>();
                sub.put("title", title);
                sub.put("body", body != null ? body : "");
                subs.add(sub);
                return this;
            }
        }
        return this;
    }

    public AgentBase promptAddToSection(String title, List<String> bullets) {
        for (Map<String, Object> section : pomSections) {
            if (title.equals(section.get("title"))) {
                @SuppressWarnings("unchecked")
                List<String> existing = (List<String>) section.computeIfAbsent("bullets", k -> new ArrayList<>());
                existing.addAll(bullets);
                return this;
            }
        }
        return this;
    }

    public boolean promptHasSection(String title) {
        for (Map<String, Object> section : pomSections) {
            if (title.equals(section.get("title"))) {
                return true;
            }
        }
        return false;
    }

    public Object getPrompt() {
        if (usePom && !pomSections.isEmpty()) {
            return Map.of("pom", new ArrayList<>(pomSections));
        } else if (promptText != null) {
            return Map.of("text", promptText);
        }
        return Map.of("text", "");
    }

    // ============================================================
    // Tool Methods
    // ============================================================

    /**
     * Register a SWAIG tool (function) that the AI can invoke during a call.
     *
     * <h3>How this becomes a tool the model sees</h3>
     *
     * <p>A SWAIG function is <b>exactly the same concept</b> as a "tool" in
     * native OpenAI / Anthropic tool calling. On every LLM turn, the SDK
     * renders each registered SWAIG function into the OpenAI tool schema:
     *
     * <pre>{@code
     * {
     *   "type": "function",
     *   "function": {
     *     "name":        "your_name_here",
     *     "description": "your description text",
     *     "parameters":  { ... your JSON schema ... }
     *   }
     * }
     * }</pre>
     *
     * <p>That schema is sent to the model as part of the same API call that
     * produces the next assistant message. The model reads:
     * <ul>
     *   <li>the function {@code description} to decide WHEN to call this tool</li>
     *   <li>each parameter {@code description} (inside parameters) to decide
     *       HOW to fill in that argument from the user's utterance</li>
     * </ul>
     *
     * <p>This means <b>descriptions are prompt engineering</b>, not developer
     * comments. A vague description is the #1 cause of "the model has the
     * right tool but doesn't call it" failures.
     *
     * <h3>Bad vs good descriptions</h3>
     * <pre>{@code
     * BAD : description: "Lookup function"
     * GOOD: description: "Look up a customer's account details by account "
     *                  + "number. Use this BEFORE quoting any account-"
     *                  + "specific info (balance, plan, status). Do not "
     *                  + "use for general product questions."
     *
     * BAD : parameters : Map.of("id", Map.of("type", "string",
     *                                        "description", "the id"))
     * GOOD: parameters : Map.of("account_number", Map.of("type", "string",
     *                    "description", "The customer's 8-digit account "
     *                    + "number, no dashes or spaces. Ask the user if "
     *                    + "they don't provide it."))
     * }</pre>
     *
     * <h3>Tool count matters</h3>
     *
     * <p>LLM tool selection accuracy degrades past ~7-8 simultaneously-active
     * tools per call. Use {@link com.signalwire.sdk.contexts.Step#setFunctions(Object)}
     * to partition tools across steps so only the relevant subset is active
     * at any moment.
     *
     * @param name the function name (snake_case verb recommended).
     * @param description LLM-facing description of when to call this tool.
     * @param parameters JSON-schema properties map with LLM-facing
     *     descriptions for each parameter.
     * @param handler the Java handler invoked when the model calls this tool.
     */
    // defineTool / registerSwaigFunction / defineTools logic now lives on
    // Service. AgentBase keeps thin covariant overrides that just return the
    // AgentBase instance, so existing fluent-chain users keep an AgentBase
    // reference (rather than the parent Service type).

    @Override
    public AgentBase defineTool(String name, String description,
                                Map<String, Object> parameters, ToolHandler handler) {
        super.defineTool(name, description, parameters, handler);
        return this;
    }

    @Override
    public AgentBase defineTool(ToolDefinition toolDef) {
        super.defineTool(toolDef);
        return this;
    }

    @Override
    public AgentBase registerSwaigFunction(Map<String, Object> swaigFunc) {
        super.registerSwaigFunction(swaigFunc);
        return this;
    }

    @Override
    public AgentBase defineTools(List<ToolDefinition> toolDefs) {
        super.defineTools(toolDefs);
        return this;
    }

    public FunctionResult onFunctionCall(String name, Map<String, Object> args, Map<String, Object> rawData) {
        ToolDefinition tool = tools.get(name);
        if (tool == null || tool.getHandler() == null) {
            return new FunctionResult("Function not found: " + name);
        }

        // Validate secure token if needed
        if (tool.isSecure()) {
            String token = (String) rawData.get("meta_data_token");
            @SuppressWarnings("unchecked")
            Map<String, Object> call = (Map<String, Object>) rawData.get("call");
            String callId = call != null ? (String) call.get("call_id") : "";
            if (token == null || !sessionManager.validateToken(token, name, callId != null ? callId : "")) {
                return new FunctionResult("Unauthorized: invalid token for function " + name);
            }
        }

        try {
            return tool.getHandler().handle(args, rawData);
        } catch (Exception e) {
            log.error("Error executing function " + name, e);
            return new FunctionResult("Error executing function: " + e.getMessage());
        }
    }

    public Map<String, ToolDefinition> getTools() {
        return Collections.unmodifiableMap(tools);
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    // ============================================================
    // AI Config Methods
    // ============================================================

    public AgentBase addHint(String hint) {
        hints.add(hint);
        return this;
    }

    public AgentBase addHints(List<String> newHints) {
        hints.addAll(newHints);
        return this;
    }

    public AgentBase addPatternHint(String pattern) {
        hints.add(pattern);
        return this;
    }

    public AgentBase addLanguage(String name, String code, String voice) {
        return addLanguage(name, code, voice, null, null, null);
    }

    public AgentBase addLanguage(String name, String code, String voice,
                                  String speechModel, String fillerWord, String engine) {
        Map<String, Object> lang = new LinkedHashMap<>();
        lang.put("name", name);
        lang.put("code", code);
        lang.put("voice", voice);
        if (speechModel != null) lang.put("speech_model", speechModel);
        if (fillerWord != null) lang.put("filler_word", fillerWord);
        if (engine != null) lang.put("engine", engine);
        languages.add(lang);
        return this;
    }

    public AgentBase setLanguages(List<Map<String, Object>> langs) {
        languages.clear();
        languages.addAll(langs);
        return this;
    }

    public AgentBase addPronunciation(String replace, String with, boolean ignoreCase) {
        Map<String, Object> pron = new LinkedHashMap<>();
        pron.put("replace", replace);
        pron.put("with", with);
        pron.put("ignore_case", ignoreCase);
        pronunciations.add(pron);
        return this;
    }

    public AgentBase setPronunciations(List<Map<String, Object>> prons) {
        pronunciations.clear();
        pronunciations.addAll(prons);
        return this;
    }

    public AgentBase setParam(String key, Object value) {
        params.put(key, value);
        return this;
    }

    public AgentBase setParams(Map<String, Object> newParams) {
        params.putAll(newParams);
        return this;
    }

    public AgentBase setGlobalData(Map<String, Object> data) {
        globalData.clear();
        globalData.putAll(data);
        return this;
    }

    public AgentBase updateGlobalData(Map<String, Object> data) {
        globalData.putAll(data);
        return this;
    }

    public Map<String, Object> getGlobalData() {
        return globalData;
    }

    public AgentBase setNativeFunctions(List<String> funcs) {
        this.nativeFunctions = new ArrayList<>(funcs);
        return this;
    }

    /**
     * The complete set of internal SWAIG function names that accept fillers,
     * matching the SWAIGInternalFiller schema definition.
     *
     * <p>Any name outside this set is silently ignored by the runtime —
     * {@link #setInternalFillersMap(java.util.Map)} and
     * {@link #addInternalFiller(String, String, java.util.List)} warn if you
     * pass an unknown name.
     *
     * <p>Notable absences: {@code change_step}, {@code gather_submit}, or
     * arbitrary user-defined SWAIG function names are NOT supported.
     */
    public static final Set<String> SUPPORTED_INTERNAL_FILLER_NAMES =
            Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                    "hangup",                   // AI is hanging up the call
                    "check_time",               // AI is checking the time
                    "wait_for_user",            // AI is waiting for user input
                    "wait_seconds",             // deliberate pause / wait period
                    "adjust_response_latency",  // AI is adjusting response timing
                    "next_step",                // transitioning between steps in prompt.contexts
                    "change_context",           // switching between contexts in prompt.contexts
                    "get_visual_input",         // processing visual input (enable_vision)
                    "get_ideal_strategy"        // thinking (enable_thinking)
            )));

    // Map<functionName, Map<languageCode, List<phrases>>> mirroring the
    // Python API. Serialized into the SWML internal_fillers block at render
    // time. Separate from the legacy internalFillers list above to preserve
    // backward compatibility.
    private final Map<String, Map<String, List<String>>> internalFillersMap = new LinkedHashMap<>();

    public AgentBase setInternalFillers(List<Map<String, Object>> fillers) {
        internalFillers.clear();
        internalFillers.addAll(fillers);
        return this;
    }

    /**
     * Set internal fillers for native SWAIG functions.
     *
     * <p>Internal fillers are short phrases the AI agent speaks (via TTS)
     * while an internal/native function is running, so the caller doesn't
     * hear dead air during transitions or background work.
     *
     * <p>Supported function names (match the SWAIGInternalFiller schema):
     * {@code hangup}, {@code check_time}, {@code wait_for_user},
     * {@code wait_seconds}, {@code adjust_response_latency},
     * {@code next_step}, {@code change_context}, {@code get_visual_input},
     * {@code get_ideal_strategy}. See
     * {@link #SUPPORTED_INTERNAL_FILLER_NAMES}.
     *
     * <p>Notably NOT supported: {@code change_step}, {@code gather_submit}, or
     * arbitrary user-defined SWAIG function names. The runtime only honors
     * fillers for the names listed above; everything else is silently
     * ignored at the SWML level. This method warns at registration time if
     * you pass an unknown name so you catch the typo early.
     *
     * @param fillers map of {@code function_name → language_code → phrases}.
     * @return this agent for chaining.
     */
    public AgentBase setInternalFillersMap(Map<String, Map<String, List<String>>> fillers) {
        if (fillers == null) return this;
        List<String> unknown = new ArrayList<>();
        for (String name : fillers.keySet()) {
            if (!SUPPORTED_INTERNAL_FILLER_NAMES.contains(name)) {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            Collections.sort(unknown);
            List<String> supported = new ArrayList<>(SUPPORTED_INTERNAL_FILLER_NAMES);
            Collections.sort(supported);
            log.warn("unknown_internal_filler_names: " + unknown
                    + ". setInternalFillersMap received names that the SWML "
                    + "schema does not recognize. Those entries will be ignored "
                    + "by the runtime. Supported names: " + supported);
        }
        internalFillersMap.clear();
        for (var entry : fillers.entrySet()) {
            Map<String, List<String>> langMap = new LinkedHashMap<>();
            if (entry.getValue() != null) {
                for (var lang : entry.getValue().entrySet()) {
                    langMap.put(lang.getKey(),
                            lang.getValue() == null ? Collections.emptyList()
                                    : new ArrayList<>(lang.getValue()));
                }
            }
            internalFillersMap.put(entry.getKey(), langMap);
        }
        return this;
    }

    public AgentBase addInternalFiller(String text, String file) {
        Map<String, Object> filler = new LinkedHashMap<>();
        if (text != null) filler.put("text", text);
        if (file != null) filler.put("file", file);
        internalFillers.add(filler);
        return this;
    }

    /**
     * Add internal fillers for a single internal function and language.
     *
     * <p>See {@link #setInternalFillersMap(java.util.Map)} for the complete
     * list of supported function names and an explanation of what fillers
     * do. Names outside the supported set log a warning and are stored, but
     * the runtime will not play them.
     *
     * @param functionName one of {@link #SUPPORTED_INTERNAL_FILLER_NAMES}.
     * @param languageCode BCP-47 language code (e.g. {@code "en-US"}).
     * @param fillers phrases to speak while the function runs.
     * @return this agent for chaining.
     */
    public AgentBase addInternalFiller(String functionName, String languageCode, List<String> fillers) {
        if (!SUPPORTED_INTERNAL_FILLER_NAMES.contains(functionName)) {
            List<String> supported = new ArrayList<>(SUPPORTED_INTERNAL_FILLER_NAMES);
            Collections.sort(supported);
            log.warn("unknown_internal_filler_name: " + functionName
                    + ". addInternalFiller received a function name the SWML "
                    + "schema does not recognize. The entry will be stored but "
                    + "the runtime will not play these fillers. Supported names: "
                    + supported);
        }
        internalFillersMap
                .computeIfAbsent(functionName, k -> new LinkedHashMap<>())
                .put(languageCode, fillers == null ? Collections.emptyList() : new ArrayList<>(fillers));
        return this;
    }

    public AgentBase enableDebugEvents() {
        this.debugEventsEnabled = true;
        return this;
    }

    public AgentBase addFunctionInclude(String url, Map<String, Object> functions) {
        Map<String, Object> include = new LinkedHashMap<>();
        include.put("url", url);
        if (functions != null) include.put("functions", functions);
        functionIncludes.add(include);
        return this;
    }

    public AgentBase setFunctionIncludes(List<Map<String, Object>> includes) {
        functionIncludes.clear();
        functionIncludes.addAll(includes);
        return this;
    }

    public AgentBase setPromptLlmParams(Map<String, Object> llmParams) {
        promptLlmParams.clear();
        promptLlmParams.putAll(llmParams);
        return this;
    }

    public AgentBase setPostPromptLlmParams(Map<String, Object> llmParams) {
        postPromptLlmParams.clear();
        postPromptLlmParams.putAll(llmParams);
        return this;
    }

    // ============================================================
    // Verb Methods (5-Phase Call Flow)
    // ============================================================

    public AgentBase addPreAnswerVerb(String verbName, Object verbData) {
        Map<String, Object> verb = new LinkedHashMap<>();
        verb.put(verbName, verbData);
        preAnswerVerbs.add(verb);
        return this;
    }

    public AgentBase addAnswerVerb(String verbName, Object verbData) {
        Map<String, Object> verb = new LinkedHashMap<>();
        verb.put(verbName, verbData);
        answerVerbs.add(verb);
        return this;
    }

    public AgentBase addPostAnswerVerb(String verbName, Object verbData) {
        Map<String, Object> verb = new LinkedHashMap<>();
        verb.put(verbName, verbData);
        postAnswerVerbs.add(verb);
        return this;
    }

    public AgentBase addPostAiVerb(String verbName, Object verbData) {
        Map<String, Object> verb = new LinkedHashMap<>();
        verb.put(verbName, verbData);
        postAiVerbs.add(verb);
        return this;
    }

    public AgentBase clearPreAnswerVerbs() { preAnswerVerbs.clear(); return this; }
    public AgentBase clearPostAnswerVerbs() { postAnswerVerbs.clear(); return this; }
    public AgentBase clearPostAiVerbs() { postAiVerbs.clear(); return this; }

    // ============================================================
    // Contexts
    // ============================================================

    /**
     * Define or return the ContextBuilder for this agent. The builder is
     * wired to report registered SWAIG tool names back so that its
     * {@code validate()} can check for collisions with reserved native tool
     * names ({@code next_step}, {@code change_context}, {@code gather_submit}).
     */
    public ContextBuilder defineContexts() {
        this.contextBuilder = new ContextBuilder();
        this.contextBuilder.attachToolNameSupplier(() -> new ArrayList<>(tools.keySet()));
        return this.contextBuilder;
    }

    public ContextBuilder contexts() {
        return this.contextBuilder;
    }

    /**
     * Remove all contexts, returning the agent to a no-contexts state.
     * This is a convenience wrapper around {@code defineContexts().reset()}.
     * Use it in a dynamic config callback when you need to rebuild
     * contexts from scratch for a specific request.
     *
     * @return this agent for chaining.
     */
    public AgentBase resetContexts() {
        if (this.contextBuilder != null) {
            this.contextBuilder.reset();
        }
        return this;
    }

    // ============================================================
    // Skills
    // ============================================================

    public AgentBase addSkill(String skillName, Map<String, Object> params) {
        skillManager.addSkill(skillName, params);
        return this;
    }

    public AgentBase removeSkill(String skillName) {
        skillManager.removeSkill(skillName);
        return this;
    }

    public List<String> listSkills() {
        return skillManager.listSkills();
    }

    public boolean hasSkill(String skillName) {
        return skillManager.hasSkill(skillName);
    }

    // ============================================================
    // Web / HTTP Config
    // ============================================================

    public AgentBase setDynamicConfigCallback(DynamicConfigCallback callback) {
        this.dynamicConfigCallback = callback;
        return this;
    }

    public AgentBase setWebHookUrl(String url) {
        this.webhookUrl = url;
        return this;
    }

    public AgentBase setPostPromptUrl(String url) {
        this.postPromptUrl = url;
        return this;
    }

    public AgentBase manualSetProxyUrl(String url) {
        this.proxyUrlBase = url;
        return this;
    }

    public AgentBase addSwaigQueryParams(Map<String, String> params) {
        swaigQueryParams.putAll(params);
        return this;
    }

    public AgentBase clearSwaigQueryParams() {
        swaigQueryParams.clear();
        return this;
    }

    public AgentBase enableDebugRoutes() {
        this.debugRoutesEnabled = true;
        return this;
    }

    // ============================================================
    // MCP Integration
    // ============================================================

    /**
     * Add an external MCP server for tool discovery and invocation.
     * Tools are discovered via MCP protocol at session start and added to SWAIG.
     *
     * @param url       MCP server HTTP endpoint URL
     * @param headers   Optional HTTP headers (e.g. Authorization)
     * @param resources Whether to fetch resources into global_data
     * @param resourceVars Variables for URI template substitution
     * @return this for chaining
     */
    public AgentBase addMcpServer(String url, Map<String, String> headers,
                                   boolean resources, Map<String, String> resourceVars) {
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("url", url);
        if (headers != null && !headers.isEmpty()) {
            server.put("headers", new LinkedHashMap<>(headers));
        }
        if (resources) {
            server.put("resources", true);
        }
        if (resourceVars != null && !resourceVars.isEmpty()) {
            server.put("resource_vars", new LinkedHashMap<>(resourceVars));
        }
        mcpServers.add(server);
        return this;
    }

    /**
     * Add an external MCP server (URL only).
     */
    public AgentBase addMcpServer(String url) {
        return addMcpServer(url, null, false, null);
    }

    /**
     * Add an external MCP server with headers.
     */
    public AgentBase addMcpServer(String url, Map<String, String> headers) {
        return addMcpServer(url, headers, false, null);
    }

    /**
     * Expose this agent's tools as an MCP server endpoint at /mcp.
     * Adds a JSON-RPC 2.0 endpoint that MCP clients can connect to.
     *
     * @return this for chaining
     */
    public AgentBase enableMcpServer() {
        this.mcpServerEnabled = true;
        return this;
    }

    /**
     * Check if MCP server endpoint is enabled.
     */
    public boolean isMcpServerEnabled() {
        return mcpServerEnabled;
    }

    /**
     * Get configured MCP servers (read-only).
     */
    public List<Map<String, Object>> getMcpServers() {
        return Collections.unmodifiableList(mcpServers);
    }

    /**
     * Build MCP tool list from registered tools.
     */
    public List<Map<String, Object>> buildMcpToolList() {
        List<Map<String, Object>> mcpTools = new ArrayList<>();
        for (Map.Entry<String, ToolDefinition> entry : tools.entrySet()) {
            ToolDefinition td = entry.getValue();
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", td.getName());
            tool.put("description", td.getDescription() != null ? td.getDescription() : td.getName());
            Map<String, Object> params = td.getParameters();
            if (params != null && !params.isEmpty()) {
                tool.put("inputSchema", params);
            } else {
                tool.put("inputSchema", Map.of("type", "object", "properties", Map.of()));
            }
            mcpTools.add(tool);
        }
        return mcpTools;
    }

    /**
     * Handle an MCP JSON-RPC 2.0 request. Returns the response map.
     */
    public Map<String, Object> handleMcpRequest(Map<String, Object> body) {
        String jsonrpc = (String) body.getOrDefault("jsonrpc", "");
        String method = (String) body.getOrDefault("method", "");
        Object reqId = body.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.getOrDefault("params", Map.of());

        if (!"2.0".equals(jsonrpc)) {
            return mcpError(reqId, -32600, "Invalid JSON-RPC version");
        }

        // Initialize handshake
        if ("initialize".equals(method)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("protocolVersion", "2025-06-18");
            result.put("capabilities", Map.of("tools", Map.of()));
            result.put("serverInfo", Map.of("name", name, "version", "1.0.0"));
            return Map.of("jsonrpc", "2.0", "id", reqId, "result", result);
        }

        // Initialized notification
        if ("notifications/initialized".equals(method)) {
            return Map.of("jsonrpc", "2.0", "id", reqId != null ? reqId : 0, "result", Map.of());
        }

        // List tools
        if ("tools/list".equals(method)) {
            return Map.of("jsonrpc", "2.0", "id", reqId, "result", Map.of("tools", buildMcpToolList()));
        }

        // Call tool
        if ("tools/call".equals(method)) {
            String toolName = (String) params.getOrDefault("name", "");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

            if (!tools.containsKey(toolName)) {
                return mcpError(reqId, -32602, "Unknown tool: " + toolName);
            }

            try {
                Map<String, Object> rawData = new LinkedHashMap<>();
                rawData.put("function", toolName);
                rawData.put("argument", Map.of("parsed", List.of(arguments)));

                FunctionResult result = onFunctionCall(toolName, arguments, rawData);
                String responseText = result.getResponse() != null ? result.getResponse() : "";

                return Map.of("jsonrpc", "2.0", "id", reqId, "result",
                        Map.of("content", List.of(Map.of("type", "text", "text", responseText)),
                                "isError", false));
            } catch (Exception e) {
                log.error("MCP tool call error: " + toolName, e);
                return Map.of("jsonrpc", "2.0", "id", reqId, "result",
                        Map.of("content", List.of(Map.of("type", "text", "text", "Error: " + e.getMessage())),
                                "isError", true));
            }
        }

        // Ping
        if ("ping".equals(method)) {
            return Map.of("jsonrpc", "2.0", "id", reqId, "result", Map.of());
        }

        return mcpError(reqId, -32601, "Method not found: " + method);
    }

    private static Map<String, Object> mcpError(Object reqId, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", reqId);
        resp.put("error", error);
        return resp;
    }

    // ============================================================
    // SIP
    // ============================================================

    public AgentBase enableSipRouting() {
        this.sipRoutingEnabled = true;
        return this;
    }

    public AgentBase registerSipUsername(String username) {
        if (username != null && SIP_USERNAME_PATTERN.matcher(username).matches()) {
            sipUsernames.add(username);
        } else {
            log.warn("Invalid SIP username rejected: %s", username);
        }
        return this;
    }

    public boolean isSipRoutingEnabled() { return sipRoutingEnabled; }
    public Set<String> getSipUsernames() { return Collections.unmodifiableSet(sipUsernames); }

    /**
     * Extract the username portion from a SIP URI.
     * <p>
     * Handles formats:
     * <ul>
     *   <li>{@code sip:user@host} -> {@code user}</li>
     *   <li>{@code sip:user@host:port} -> {@code user}</li>
     *   <li>{@code user@host} -> {@code user}</li>
     *   <li>{@code +15551234567} -> {@code +15551234567} (returned as-is)</li>
     * </ul>
     *
     * @param sipUri The SIP URI or phone number
     * @return The extracted username, or the original string if no @ is found
     */
    public static String extractSipUsername(String sipUri) {
        if (sipUri == null || sipUri.isEmpty()) {
            return "";
        }
        // Strip sip: or sips: prefix
        String work = sipUri;
        if (work.startsWith("sip:") || work.startsWith("SIP:")) {
            work = work.substring(4);
        } else if (work.startsWith("sips:") || work.startsWith("SIPS:")) {
            work = work.substring(5);
        }
        // Strip angle brackets if present: <sip:user@host> -> user@host
        if (work.startsWith("<")) {
            work = work.substring(1);
        }
        if (work.endsWith(">")) {
            work = work.substring(0, work.length() - 1);
        }
        // Extract username before @
        int atIdx = work.indexOf('@');
        if (atIdx >= 0) {
            return work.substring(0, atIdx);
        }
        // No @ found -- return as-is (could be a phone number)
        return work;
    }

    // ============================================================
    // Lifecycle Callbacks
    // ============================================================

    public AgentBase onSummary(BiConsumer<Map<String, Object>, Map<String, Object>> callback) {
        this.onSummaryCallback = callback;
        return this;
    }

    public AgentBase onDebugEvent(Consumer<Map<String, Object>> callback) {
        this.onDebugEventCallback = callback;
        return this;
    }

    // ============================================================
    // Getters
    // ============================================================

    public String getName() { return name; }
    public String getRoute() { return route; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getAuthUser() { return authUser; }
    public String getAuthPassword() { return authPassword; }

    /**
     * @return the dynamic config callback, or {@code null} if none set.
     *     Exposed primarily so alternative transports (e.g. the Lambda
     *     adapter) can invoke it outside the HTTP server path.
     */
    public DynamicConfigCallback getDynamicConfigCallback() {
        return dynamicConfigCallback;
    }

    /**
     * @return the post-prompt summary callback, or {@code null}.
     */
    public BiConsumer<Map<String, Object>, Map<String, Object>> getOnSummaryCallback() {
        return onSummaryCallback;
    }

    /**
     * Return the agent's route normalised to an empty string for the
     * root route or {@code "/<path>"} otherwise. Exposed so non-HTTP
     * transports can construct paths correctly.
     *
     * @return normalised route prefix.
     */
    public String getNormalisedRoute() {
        return normalizeRoute();
    }
    public SkillManager getSkillManager() { return skillManager; }

    // ============================================================
    // SWML Rendering — 5-Phase Pipeline
    // ============================================================

    /**
     * Render the complete SWML document.
     * 5 phases: pre-answer, answer, post-answer, AI, post-AI
     */
    public Map<String, Object> renderSwml(String baseUrl) {
        List<Map<String, Object>> mainVerbs = new ArrayList<>();

        // Phase 1: Pre-answer verbs
        mainVerbs.addAll(preAnswerVerbs);

        // Phase 2: Answer
        if (autoAnswer) {
            Map<String, Object> answerParams = new LinkedHashMap<>();
            answerParams.put("max_duration", maxDuration);
            mainVerbs.add(Map.of("answer", answerParams));

            // Record call if enabled
            if (recordCall) {
                Map<String, Object> recParams = new LinkedHashMap<>();
                recParams.put("format", recordFormat);
                recParams.put("stereo", recordStereo);
                mainVerbs.add(Map.of("record_call", recParams));
            }
        }
        mainVerbs.addAll(answerVerbs);

        // Phase 3: Post-answer verbs
        mainVerbs.addAll(postAnswerVerbs);

        // Phase 4: AI verb
        Map<String, Object> aiVerb = buildAiVerb(baseUrl);
        mainVerbs.add(Map.of("ai", aiVerb));

        // Phase 5: Post-AI verbs
        mainVerbs.addAll(postAiVerbs);

        // Build document
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("version", "1.0.0");
        doc.put("sections", Map.of("main", mainVerbs));
        return doc;
    }

    private Map<String, Object> buildAiVerb(String baseUrl) {
        Map<String, Object> ai = new LinkedHashMap<>();

        // Prompt
        ai.put("prompt", getPrompt());

        // Merge LLM params into prompt
        if (!promptLlmParams.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> promptMap = (Map<String, Object>) ai.get("prompt");
            Map<String, Object> merged = new LinkedHashMap<>(promptMap);
            for (var entry : promptLlmParams.entrySet()) {
                merged.put(entry.getKey(), entry.getValue());
            }
            ai.put("prompt", merged);
        }

        // Post prompt
        if (postPrompt != null) {
            Map<String, Object> ppMap = new LinkedHashMap<>();
            ppMap.put("text", postPrompt);
            ppMap.putAll(postPromptLlmParams);
            ai.put("post_prompt", ppMap);
        }

        // Post prompt URL
        String ppUrl = postPromptUrl;
        if (ppUrl == null && baseUrl != null) {
            ppUrl = baseUrl + normalizeRoute() + "/post_prompt";
        }
        if (ppUrl != null) {
            ai.put("post_prompt_url", ppUrl);
        }

        // Params
        if (!params.isEmpty()) {
            ai.put("params", new LinkedHashMap<>(params));
        }

        // Hints
        if (!hints.isEmpty()) {
            ai.put("hints", new ArrayList<>(hints));
        }

        // Languages
        if (!languages.isEmpty()) {
            ai.put("languages", new ArrayList<>(languages));
        }

        // Pronunciations
        if (!pronunciations.isEmpty()) {
            ai.put("pronounce", new ArrayList<>(pronunciations));
        }

        // Internal fillers
        if (!internalFillers.isEmpty()) {
            ai.put("internal_fillers", new ArrayList<>(internalFillers));
        }

        // Debug events
        if (debugEventsEnabled) {
            ai.put("debug", Map.of("events", true));
        }

        // SWAIG section
        Map<String, Object> swaig = new LinkedHashMap<>();

        // Build functions list
        List<Map<String, Object>> functions = buildSwaigFunctions(baseUrl);
        if (!functions.isEmpty()) {
            swaig.put("functions", functions);
        }

        // Function includes
        if (!functionIncludes.isEmpty()) {
            swaig.put("includes", new ArrayList<>(functionIncludes));
        }

        // Native functions
        if (nativeFunctions != null && !nativeFunctions.isEmpty()) {
            swaig.put("native_functions", new ArrayList<>(nativeFunctions));
        }

        // MCP servers
        if (!mcpServers.isEmpty()) {
            swaig.put("mcp_servers", new ArrayList<>(mcpServers));
        }

        if (!swaig.isEmpty()) {
            ai.put("SWAIG", swaig);
        }

        // Global data
        if (!globalData.isEmpty()) {
            ai.put("global_data", new LinkedHashMap<>(globalData));
        }

        // Contexts
        if (contextBuilder != null && !contextBuilder.isEmpty()) {
            ai.put("contexts", contextBuilder.toMap());
        }

        return ai;
    }

    private List<Map<String, Object>> buildSwaigFunctions(String baseUrl) {
        List<Map<String, Object>> functions = new ArrayList<>();
        String webhookBase = buildWebhookUrl(baseUrl);

        // Tools with handlers
        for (Map.Entry<String, ToolDefinition> entry : tools.entrySet()) {
            ToolDefinition tool = entry.getValue();
            String toolWebhook = tool.hasHandler() ? webhookBase : null;
            String token = tool.isSecure() ? sessionManager.createToken(tool.getName(), "") : null;
            functions.add(tool.toSwaigFunction(toolWebhook, token));
        }

        // Registered SWAIG functions (DataMap tools)
        functions.addAll(registeredSwaigFunctions);

        return functions;
    }

    private String buildWebhookUrl(String baseUrl) {
        if (webhookUrl != null) return webhookUrl;
        if (baseUrl == null) return null;

        StringBuilder url = new StringBuilder();
        url.append(baseUrl);
        url.append(normalizeRoute());
        url.append("/swaig");

        if (!swaigQueryParams.isEmpty()) {
            url.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : swaigQueryParams.entrySet()) {
                if (!first) url.append("&");
                url.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
        }

        return url.toString();
    }

    private String normalizeRoute() {
        return route.equals("/") ? "" : route;
    }

    /**
     * Render SWML as a JSON string.
     */
    public String renderSwmlJson(String baseUrl) {
        return gson.toJson(renderSwml(baseUrl));
    }

    // ============================================================
    // Dynamic Config Clone
    // ============================================================

    /**
     * Create a deep copy of this agent for per-request customization.
     */
    public AgentBase clone() {
        // Service's name/route/host/port are constructor-only; use the
        // protected constructor to seed those + auth, then copy agent-level
        // state field-by-field below.
        AgentBase copy = new AgentBase(this.name, this.route, this.host, this.port,
                this.authUser, this.authPassword);
        copy.autoAnswer = this.autoAnswer;
        copy.maxDuration = this.maxDuration;
        copy.recordCall = this.recordCall;
        copy.recordFormat = this.recordFormat;
        copy.recordStereo = this.recordStereo;
        copy.promptText = this.promptText;
        copy.postPrompt = this.postPrompt;
        copy.postPromptUrl = this.postPromptUrl;
        copy.usePom = this.usePom;
        copy.pomSections.addAll(deepCopyList(this.pomSections));
        copy.hints.addAll(this.hints);
        copy.languages.addAll(deepCopyList(this.languages));
        copy.pronunciations.addAll(deepCopyList(this.pronunciations));
        copy.params.putAll(this.params);
        copy.promptLlmParams.putAll(this.promptLlmParams);
        copy.postPromptLlmParams.putAll(this.postPromptLlmParams);
        copy.globalData.putAll(deepCopyMap(this.globalData));
        if (this.nativeFunctions != null) {
            copy.nativeFunctions = new ArrayList<>(this.nativeFunctions);
        }
        copy.internalFillers.addAll(deepCopyList(this.internalFillers));
        copy.debugEventsEnabled = this.debugEventsEnabled;
        copy.functionIncludes.addAll(deepCopyList(this.functionIncludes));
        copy.tools.putAll(this.tools);
        copy.registeredSwaigFunctions.addAll(deepCopyList(this.registeredSwaigFunctions));
        copy.preAnswerVerbs.addAll(deepCopyList(this.preAnswerVerbs));
        copy.answerVerbs.addAll(deepCopyList(this.answerVerbs));
        copy.postAnswerVerbs.addAll(deepCopyList(this.postAnswerVerbs));
        copy.postAiVerbs.addAll(deepCopyList(this.postAiVerbs));
        copy.contextBuilder = this.contextBuilder;
        copy.webhookUrl = this.webhookUrl;
        copy.proxyUrlBase = this.proxyUrlBase;
        copy.swaigQueryParams.putAll(this.swaigQueryParams);
        copy.mcpServers.addAll(deepCopyList(this.mcpServers));
        copy.mcpServerEnabled = this.mcpServerEnabled;
        copy.sipRoutingEnabled = this.sipRoutingEnabled;
        copy.sipUsernames.addAll(this.sipUsernames);
        copy.onSummaryCallback = this.onSummaryCallback;
        copy.onDebugEventCallback = this.onDebugEventCallback;
        return copy;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> deepCopyList(List<Map<String, Object>> source) {
        String json = gson.toJson(source);
        Type type = new TypeToken<List<Map<String, Object>>>() {}.getType();
        return gson.fromJson(json, type);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        String json = gson.toJson(source);
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        return gson.fromJson(json, type);
    }

    // ============================================================
    // HTTP Server
    // ============================================================

    // validateAuth, addSecurityHeaders, sendJson, sendUnauthorized,
    // sendPayloadTooLarge, and readBody are now provided by Service (parent).
    // Inherited via `extends Service`.


    private String detectBaseUrl(HttpExchange exchange) {
        if (proxyUrlBase != null) return embedCredentials(proxyUrlBase);

        // Try environment-derived bases (Lambda, etc.) before falling
        // back to request-derived ones. This covers the case where an
        // API Gateway / Function URL is fronting the Java HTTP server
        // via a container: requests come through with the public origin,
        // but X-Forwarded headers may not be set.
        String envBase = detectServerlessBaseUrl();
        if (envBase != null) return embedCredentials(envBase);

        var headers = exchange.getRequestHeaders();

        // Check X-Forwarded headers
        String proto = headers.getFirst("X-Forwarded-Proto");
        String fwdHost = headers.getFirst("X-Forwarded-Host");
        if (proto != null && fwdHost != null) {
            return embedCredentials(proto + "://" + fwdHost);
        }

        // Check X-Original-URL
        String original = headers.getFirst("X-Original-URL");
        if (original != null) return embedCredentials(original);

        // Fall back to scheme://host:port with auth credentials in URL
        String scheme = "http";
        return scheme + "://" + authUser + ":" + authPassword + "@" + host + ":" + port;
    }

    /**
     * Resolve a base URL from environment variables alone (proxy or
     * serverless platform). Returns {@code null} if no suitable env
     * vars are set.
     *
     * <p>This is used by both the HTTP server path (when no proxy is
     * manually set) and by non-HTTP transports such as the Lambda
     * adapter, so they agree on the origin to use for webhook URLs.
     *
     * <p>The returned origin is a bare scheme + host(:port) with NO
     * route appended — callers must layer their route on top via
     * {@link #buildWebhookUrl(String)} and the post-prompt URL builder.
     * This matters: it is how we guarantee that the agent's route
     * always appears in webhook URLs regardless of which source
     * produced the base.
     *
     * @return base URL, or {@code null}.
     */
    public String detectServerlessBaseUrl() {
        return detectServerlessBaseUrl(EnvProvider.SYSTEM);
    }

    /**
     * Resolve a base URL from the supplied {@link EnvProvider} (proxy or
     * serverless platform). Returns {@code null} if no suitable env vars
     * are set.
     *
     * <p>The {@code env}-aware overload is required by tools that cannot
     * mutate the real process environment (such as the {@code swaig-test
     * --simulate-serverless} harness in the CLI). Those callers build a
     * layered {@link EnvProvider} that overlays simulated values on top
     * of the real environment and pass it through here so the serverless
     * URL detection matches what would happen at runtime on the target
     * platform.
     *
     * <p>If the instance has a {@code proxyUrlBase} set (from the real
     * env at build time or an explicit {@link #manualSetProxyUrl(String)}
     * call), that wins — the injected env is only consulted for detection
     * and Lambda URL synthesis.
     *
     * @param env environment variable source.
     * @return base URL, or {@code null}.
     */
    public String detectServerlessBaseUrl(EnvProvider env) {
        if (env == null) env = EnvProvider.SYSTEM;
        if (proxyUrlBase != null) return proxyUrlBase;
        ExecutionMode mode = ExecutionMode.detect(env);
        if (mode == ExecutionMode.LAMBDA) {
            return new LambdaUrlResolver(env).resolveBaseUrl();
        }
        return null;
    }

    private String embedCredentials(String url) {
        try {
            var uri = new java.net.URI(url);
            // Already has credentials embedded
            if (uri.getUserInfo() != null) return url;
            return new java.net.URI(
                uri.getScheme(),
                authUser + ":" + authPassword,
                uri.getHost(),
                uri.getPort(),
                uri.getPath(),
                uri.getQuery(),
                uri.getFragment()
            ).toString();
        } catch (Exception e) {
            // If URL parsing fails, prepend credentials manually
            return url.replaceFirst("://", "://" + authUser + ":" + authPassword + "@");
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0) {
                String key = URLDecoder.decode(param.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(param.substring(eq + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    // serve() and the main /swaig and / handlers are now provided by Service
    // (parent). AgentBase plugs in via the two extension points below to add
    // agent-specific behavior:
    //   - renderMainSwml() — builds the SWML doc with prompts + dynamic config
    //   - registerAdditionalRoutes() — adds /post_prompt and /mcp

    @Override
    protected Map<String, Object> renderMainSwml(HttpExchange exchange) {
        String method = exchange.getRequestMethod();
        String baseUrl = detectBaseUrl(exchange);

        AgentBase renderAgent = this;
        if (dynamicConfigCallback != null) {
            renderAgent = this.clone();
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
            Map<String, Object> bodyParams = new LinkedHashMap<>();
            if ("POST".equalsIgnoreCase(method)) {
                try {
                    String body = readBody(exchange);
                    if (!body.isEmpty()) {
                        Type type = new TypeToken<Map<String, Object>>() {}.getType();
                        bodyParams = gson.fromJson(body, type);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse POST body for dynamic config");
                }
            }
            Map<String, List<String>> headerMap = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headerMap.put(k, v));
            dynamicConfigCallback.configure(queryParams, bodyParams, headerMap, renderAgent);
        }
        return renderAgent.renderSwml(baseUrl);
    }

    @Override
    protected void registerAdditionalRoutes(com.sun.net.httpserver.HttpServer server) {
        String basePath = route.equals("/") ? "" : route;

        // Post-prompt endpoint
        server.createContext(basePath + "/post_prompt", exchange -> {
            try {
                handlePostPrompt(exchange);
            } catch (Exception e) {
                log.error("Post-prompt handler error", e);
                try { exchange.sendResponseHeaders(500, -1); exchange.close(); }
                catch (Exception ignored) {}
            }
        });

        // MCP server endpoint (JSON-RPC 2.0) — opt-in
        if (mcpServerEnabled) {
            server.createContext(basePath + "/mcp", exchange -> {
                try {
                    handleMcpEndpoint(exchange);
                } catch (Exception e) {
                    log.error("MCP handler error", e);
                    try { exchange.sendResponseHeaders(500, -1); exchange.close(); }
                    catch (Exception ignored) {}
                }
            });
        }
    }

    private void handlePostPrompt(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        if (!validateAuth(exchange)) {
            sendUnauthorized(exchange);
            return;
        }
        addSecurityHeaders(exchange);

        String body;
        try {
            body = readBody(exchange);
        } catch (IOException e) {
            exchange.sendResponseHeaders(413, -1);
            exchange.close();
            return;
        }

        Map<String, Object> payload;
        try {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            payload = gson.fromJson(body, type);
        } catch (Exception e) {
            sendJson(exchange, 400, Map.of("error", "Invalid JSON"));
            return;
        }

        if (payload == null) payload = new LinkedHashMap<>();

        if (onSummaryCallback != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ppData = (Map<String, Object>) payload.get("post_prompt_data");
            Map<String, Object> parsed = null;
            if (ppData != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> p = (Map<String, Object>) ppData.get("parsed");
                parsed = p;
            }
            try {
                onSummaryCallback.accept(parsed, payload);
            } catch (Exception e) {
                log.error("Error in summary callback", e);
            }
        }

        sendJson(exchange, 200, Map.of("status", "ok"));
    }

    private void handleMcpEndpoint(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        addSecurityHeaders(exchange);

        String body;
        try {
            body = readBody(exchange);
        } catch (IOException e) {
            sendJson(exchange, 400, Map.of("jsonrpc", "2.0", "id", (Object) null,
                    "error", Map.of("code", -32700, "message", "Parse error")));
            return;
        }

        Map<String, Object> payload;
        try {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            payload = gson.fromJson(body, type);
        } catch (Exception e) {
            sendJson(exchange, 200, Map.of("jsonrpc", "2.0", "id", (Object) null,
                    "error", Map.of("code", -32700, "message", "Parse error: " + e.getMessage())));
            return;
        }

        if (payload == null) payload = new LinkedHashMap<>();
        Map<String, Object> response = handleMcpRequest(payload);
        sendJson(exchange, 200, response);
    }

    /**
     * Start the agent server. Equivalent to {@link #serve()} (inherited).
     */
    public void run() throws IOException {
        serve();
    }

    // stop() is inherited from Service.

    // ============================================================
    // Dynamic Config Callback Interface
    // ============================================================

    @FunctionalInterface
    public interface DynamicConfigCallback {
        void configure(Map<String, String> queryParams,
                       Map<String, Object> bodyParams,
                       Map<String, List<String>> headers,
                       AgentBase agent);
    }
}
