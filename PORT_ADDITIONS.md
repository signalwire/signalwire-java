# PORT_ADDITIONS — Java-only public symbols with no Python equivalent

Symbols here exist in the Java SDK but have no matching entry in the Python reference. These fall into three buckets:
  1. Java-idiom necessities (builders, enums, explicit getters).
  2. Java Lambda support (the `signalwire.runtime.*` tree).
  3. Refactors where Java merged Python's split classes (`*Namespace` vs `*Resource`).

Every entry must carry a rationale. Reviewers use this file to catch accidental additions.


# Format: `<fully.qualified.symbol>: <rationale>`
# Regenerate with `python3 scripts/generate_exemptions.py` after
# a surface change.

signalwire.agent.agent_base_builder.AgentBaseBuilder: Java builder pattern — public constructor is package-private, initialization goes through AgentBase.builder()
signalwire.agent.agent_base_builder.AgentBaseBuilder.auth_password: Java builder pattern — public constructor is package-private, initialization goes through AgentBase.builder()
signalwire.agent.agent_base_builder.AgentBaseBuilder.auth_user: Java builder pattern — public constructor is package-private, initialization goes through AgentBase.builder()
signalwire.agent.agent_base_builder.AgentBaseBuilder.auto_answer: Java builder pattern — public constructor is package-private, initialization goes through AgentBase.builder()
signalwire.agent.agent_base_builder.AgentBaseBuilder.build: Java builder pattern — public constructor is package-private, initialization goes through AgentBase.builder()
signalwire.agent.agent_base_builder.AgentBaseBuilder.env_provider: Java builder pattern — public constructor is package-private, initialization goes through AgentBase.builder()
signalwire.agent.agent_base_builder.AgentBaseBuilder.host: Java builder pattern — public constructor is package-private, initialization goes through AgentBase.builder()
signalwire.agent.agent_base_builder.AgentBaseBuilder.max_duration: Java builder pattern — public constructor is package-private, initialization goes through AgentBase.builder()
signalwire.agent.agent_base_builder.AgentBaseBuilder.name: Java builder pattern — public constructor is package-private, initialization goes through AgentBase.builder()
signalwire.agent.agent_base_builder.AgentBaseBuilder.port: Java builder pattern — public constructor is package-private, initialization goes through AgentBase.builder()
signalwire.agent.agent_base_builder.AgentBaseBuilder.record_call: Java builder pattern — public constructor is package-private, initialization goes through AgentBase.builder()
signalwire.agent.agent_base_builder.AgentBaseBuilder.record_format: Java builder pattern — public constructor is package-private, initialization goes through AgentBase.builder()
signalwire.agent.agent_base_builder.AgentBaseBuilder.record_stereo: Java builder pattern — public constructor is package-private, initialization goes through AgentBase.builder()
signalwire.agent.agent_base_builder.AgentBaseBuilder.route: Java builder pattern — public constructor is package-private, initialization goes through AgentBase.builder()
signalwire.agent.agent_base_dynamic_config_callback.AgentBaseDynamicConfigCallback: Java functional interface for AgentBase.setDynamicConfigCallback (Python uses bare callables)
signalwire.agent_server.AgentServer.get_routes: Java accessor exposing the registered-agent route list for diagnostics
signalwire.agent_server.AgentServer.get_sip_route: Java accessor exposing the registered SIP route for diagnostics
signalwire.agent_server.AgentServer.register_sip_route: Java's split of Python's setup_sip_routing into explicit per-username registration
signalwire.agent_server.AgentServer.set_static_files_dir: Java setter for the static-files directory; Python configures this through AgentServer constructor arguments
signalwire.agent_server.AgentServer.stop: Java explicit server-stop method; Python's AgentServer stops when its process is signalled
signalwire.cli.simulation.mock_env.ServerlessSimulator.__init__: Java ServerlessSimulator requires an explicit public constructor for serverless-platform mock setup; Python inlines the construction in argparse-driven CLI entry points
signalwire.cli.simulation.mock_env.ServerlessSimulator.build_env_provider: Java ServerlessSimulator exposes buildEnvProvider() as a typed factory for the mock env-var provider; Python wires the same behaviour via dict-keyed os.environ patches
signalwire.cli.simulation.mock_env.ServerlessSimulator.get_platform: Java ServerlessSimulator exposes getPlatform() as a Platform-enum accessor for the active mock; Python returns a string from the same simulator
signalwire.cli.simulation.mock_env.ServerlessSimulator.parse_platform: Java helper exposed for CLI flag parsing; Python uses argparse choices directly
signalwire.cli.simulation.mock_env.ServerlessSimulator.preset_for: Java helper exposed for CLI preset lookup; Python inlines the lookup in its ServerlessSimulator constructor
signalwire.cli.simulation.serverless_simulator_platform.ServerlessSimulatorPlatform: Java enum for ServerlessSimulator.Platform; Python uses string constants
signalwire.cli.swaig_test.SwaigTest: Java swaig-test CLI entry point; Python's equivalent is the swaig-test executable in signalwire.cli
signalwire.cli.swaig_test.SwaigTest.main: Java swaig-test CLI entry point; Python's equivalent is the swaig-test executable in signalwire.cli
signalwire.cli.swaig_test.SwaigTest.run: Java swaig-test CLI entry point; Python's equivalent is the swaig-test executable in signalwire.cli
signalwire.core.agent_base.AgentBase.add_function_include: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.add_hint: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.add_hints: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.add_internal_filler: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.add_language: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.add_mcp_server: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.add_pattern_hint: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.add_pronunciation: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.add_skill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.build_mcp_tool_list: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.builder: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.clone: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.contexts: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.create_tool_token: Java AgentBase explicitly exposes createToolToken() as a public helper; Python keeps token-mint logic internal to the SecureTokenManager
signalwire.core.agent_base.AgentBase.define_contexts: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.define_tool: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.define_tools: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.detect_serverless_base_url: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.enable_debug_events: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.enable_debug_routes: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.enable_mcp_server: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.extract_sip_username: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_auth_password: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_auth_user: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_contexts: Java AgentBase exposes getContexts() as an explicit accessor; Python's reference accesses the same state via prompt_manager.contexts
signalwire.core.agent_base.AgentBase.get_dynamic_config_callback: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_global_data: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_host: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_mcp_servers: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_normalised_route: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_on_summary_callback: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_port: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_post_prompt: Java AgentBase exposes getPostPrompt() as an explicit accessor for the configured post-prompt; Python's reference exposes equivalent state via prompt_manager.get_post_prompt()
signalwire.core.agent_base.AgentBase.get_prompt: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_raw_prompt: Java AgentBase exposes getRawPrompt() as an explicit accessor; Python's reference exposes the same state via prompt_manager.get_raw_prompt()
signalwire.core.agent_base.AgentBase.get_route: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_sip_usernames: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_skill_manager: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_tools: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.handle_mcp_request: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.has_skill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.has_tool: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.is_mcp_server_enabled: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.is_sip_routing_enabled: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.list_skills: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.manual_set_proxy_url: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.on_function_call: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.pom: Java exposes the pom accessor via getPom() (renamed by surface adapter to match Python's @property pom); the entry stays here because the surface diff sees Java emitting the snake_case name from a getter
signalwire.core.agent_base.AgentBase.prompt_add_section: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.prompt_add_subsection: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.prompt_add_to_section: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.prompt_has_section: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.register_swaig_function: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.remove_skill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.render_swml: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.render_swml_json: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.reset_contexts: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.run: Java AgentBase exposes Service.run() under the AgentBase namespace — Python inherits via mixin lookup; the Java surface generator records it as an explicit override
signalwire.core.agent_base.AgentBase.set_dynamic_config_callback: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.set_function_includes: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.set_global_data: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.set_internal_fillers: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.set_internal_fillers_map: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.set_languages: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.set_native_functions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.set_param: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.set_params: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.set_post_prompt: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.set_post_prompt_llm_params: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.set_prompt_llm_params: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.set_prompt_pom: Java AgentBase exposes setPromptPom() directly as a sibling of set_prompt_text; Python's reference routes this through prompt_manager
signalwire.core.agent_base.AgentBase.set_prompt_text: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.set_pronunciations: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.update_global_data: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.validate_tool_token: Java AgentBase explicitly exposes validateToolToken() as a public helper; Python keeps token-validation logic internal to the SecureTokenManager
signalwire.core.contexts.Context.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.contexts.ContextBuilder.attach_tool_name_supplier: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.contexts.ContextBuilder.is_empty: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.contexts.GatherInfo.get_completion_action: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.contexts.GatherInfo.get_questions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.contexts.GatherQuestion.get_key: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.contexts.Step.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.data_map.DataMap.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.function_result.FunctionResult.get_actions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.function_result.FunctionResult.get_response: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.function_result.FunctionResult.is_post_process: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.function_result.FunctionResult.to_json: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.logging_config.Logger.__init__: Java Logger is a typed wrapper class with explicit construction; Python's logging.Logger is materialised via logging.getLogger() and not exposed at this canonical name
signalwire.core.logging_config.Logger.debug: Java Logger.debug() is a level-emit method on the typed wrapper; Python's reference exposes the same surface via logging.Logger.debug/warning
signalwire.core.logging_config.Logger.error: Java Logger.error() is a level-emit method on the typed wrapper; Python's reference exposes the same surface via logging.Logger.error/warning
signalwire.core.logging_config.Logger.get_global_level: Java Logger exposes getGlobalLevel() for runtime introspection of the configured threshold; Python uses logging.getLogger().getEffectiveLevel() instead
signalwire.core.logging_config.Logger.get_logger: Java Logger exposes getLogger() as a static factory; Python uses logging.getLogger as a free-function call
signalwire.core.logging_config.Logger.info: Java Logger.info() is a level-emit method on the typed wrapper; Python's reference exposes the same surface via logging.Logger.info/warning
signalwire.core.logging_config.Logger.is_enabled: Java Logger exposes isEnabled(level) as a typed predicate; Python uses logger.isEnabledFor() in the same shape but at a different canonical name
signalwire.core.logging_config.Logger.set_global_level: Java Logger exposes setGlobalLevel() as a typed setter; Python uses logging.getLogger().setLevel() instead
signalwire.core.logging_config.Logger.warn: Java Logger.warn() is a level-emit method on the typed wrapper; Python's reference exposes the same surface via logging.Logger.warn/warning
signalwire.core.security.session_manager.SessionManager.create_token: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.skill_manager.SkillManager.add_skill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.skill_manager.SkillManager.cleanup: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.skill_manager.SkillManager.list_skills: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.skill_manager.SkillManager.remove_skill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.swml_builder.Document.add_section: Java's swml.Document exposes add_section as a typed-builder helper; Python's reference Document keeps a flatter surface where the same effect is achieved via dict manipulation on the underlying SWML payload
signalwire.core.swml_builder.Document.add_verb: Java's swml.Document exposes add_verb as a typed-builder helper; Python's reference Document keeps a flatter surface where the same effect is achieved via dict manipulation on the underlying SWML payload
signalwire.core.swml_builder.Document.add_verb_to_section: Java's swml.Document exposes add_verb_to_section as a typed-builder helper; Python's reference Document keeps a flatter surface where the same effect is achieved via dict manipulation on the underlying SWML payload
signalwire.core.swml_builder.Document.get_section_verbs: Java's swml.Document exposes get_section_verbs as a typed-builder helper; Python's reference Document keeps a flatter surface where the same effect is achieved via dict manipulation on the underlying SWML payload
signalwire.core.swml_builder.Document.has_section: Java's swml.Document exposes has_section as a typed-builder helper; Python's reference Document keeps a flatter surface where the same effect is achieved via dict manipulation on the underlying SWML payload
signalwire.logging.logger.Logger: Java wraps java.util.logging with a uniform API; Python re-exports stdlib logging directly
signalwire.logging.logger.Logger.__init__: Java wraps java.util.logging with a uniform API; Python re-exports stdlib logging directly
signalwire.logging.logger.Logger.debug: Java wraps java.util.logging with a uniform API; Python re-exports stdlib logging directly
signalwire.logging.logger.Logger.error: Java wraps java.util.logging with a uniform API; Python re-exports stdlib logging directly
signalwire.logging.logger.Logger.get_global_level: Java wraps java.util.logging with a uniform API; Python re-exports stdlib logging directly
signalwire.logging.logger.Logger.get_logger: Java wraps java.util.logging with a uniform API; Python re-exports stdlib logging directly
signalwire.logging.logger.Logger.info: Java wraps java.util.logging with a uniform API; Python re-exports stdlib logging directly
signalwire.logging.logger.Logger.is_enabled: Java wraps java.util.logging with a uniform API; Python re-exports stdlib logging directly
signalwire.logging.logger.Logger.set_global_level: Java wraps java.util.logging with a uniform API; Python re-exports stdlib logging directly
signalwire.logging.logger.Logger.warn: Java wraps java.util.logging with a uniform API; Python re-exports stdlib logging directly
signalwire.logging.logger_level.LoggerLevel: Java enum for log severity; Python uses the stdlib logging module
signalwire.logging.logger_level.LoggerLevel.get_value: Java enum for log severity; Python uses the stdlib logging module
signalwire.logging.logging_level.LoggingLevel.debug: Java LoggingLevel.debug is an enum constant projecting as a zero-arg accessor in the surface dump; Python represents log levels as integer constants in the logging module
signalwire.logging.logging_level.LoggingLevel.error: Java LoggingLevel.error is an enum constant projecting as a zero-arg accessor in the surface dump; Python represents log levels as integer constants in the logging module
signalwire.logging.logging_level.LoggingLevel.info: Java LoggingLevel.info is an enum constant projecting as a zero-arg accessor in the surface dump; Python represents log levels as integer constants in the logging module
signalwire.logging.logging_level.LoggingLevel.off: Java LoggingLevel.off is an enum constant projecting as a zero-arg accessor in the surface dump; Python represents log levels as integer constants in the logging module
signalwire.logging.logging_level.LoggingLevel.value_of: Java LoggingLevel.valueOf is the standard Java enum-value-by-name lookup; Python represents log levels as integer constants and uses the logging.getLevelName() free function
signalwire.logging.logging_level.LoggingLevel.values: Java LoggingLevel.values() is the standard Java enum iterator; Python iterates log levels via logging._nameToLevel
signalwire.logging.logging_level.LoggingLevel.warn: Java LoggingLevel.warn is an enum constant projecting as a zero-arg accessor in the surface dump; Python represents log levels as integer constants in the logging module
signalwire.pom.pom.PromptObjectModel.from_json_map: Java overload accepting a parsed list-of-maps so callers can avoid re-serialising to JSON; Python's from_json accepts both (str|dict) at one signature
signalwire.pom.pom.PromptObjectModel.from_yaml_map: Java overload accepting a parsed list-of-maps so callers can avoid re-serialising to YAML; Python's from_yaml accepts both (str|dict) at one signature
signalwire.pom.pom.PromptObjectModel.get_sections: Java explicit accessor for the section list; Python exposes the equivalent state via the public sections attribute
signalwire.pom.pom.PromptObjectModel.is_debug: Java explicit accessor for the debug flag; Python exposes the equivalent state via the public debug attribute
signalwire.pom.pom.Section.get_body: Java explicit accessor for the body field; Python exposes the equivalent state via the public body attribute
signalwire.pom.pom.Section.get_bullets: Java explicit accessor for the bullets list; Python exposes the equivalent state via the public bullets attribute
signalwire.pom.pom.Section.get_numbered: Java explicit accessor for the tri-state numbered flag; Python exposes the equivalent state via the public numbered attribute
signalwire.pom.pom.Section.get_subsections: Java explicit accessor for the subsection list; Python exposes the equivalent state via the public subsections attribute
signalwire.pom.pom.Section.get_title: Java explicit accessor for the title field; Python exposes the equivalent state via the public title attribute
signalwire.pom.pom.Section.is_numbered_bullets: Java explicit accessor for the numberedBullets flag; Python exposes the equivalent state via the public numberedBullets attribute
signalwire.prefabs.concierge.ConciergeAgent.amenity: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.concierge.ConciergeAgent.get_agent: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.concierge.ConciergeAgent.run: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.concierge.ConciergeAgent.serve: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.faq_bot.FAQBotAgent.faq: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.faq_bot.FAQBotAgent.get_agent: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.faq_bot.FAQBotAgent.run: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.faq_bot.FAQBotAgent.serve: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.info_gatherer.InfoGathererAgent.get_agent: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.info_gatherer.InfoGathererAgent.question: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.info_gatherer.InfoGathererAgent.run: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.info_gatherer.InfoGathererAgent.serve: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.receptionist.ReceptionistAgent.get_agent: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.receptionist.ReceptionistAgent.phone_department: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.receptionist.ReceptionistAgent.run: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.receptionist.ReceptionistAgent.serve: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.receptionist.ReceptionistAgent.swml_department: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.survey.SurveyAgent.get_agent: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.survey.SurveyAgent.multiple_choice_question: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.survey.SurveyAgent.open_ended_question: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.survey.SurveyAgent.rating_question: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.survey.SurveyAgent.run: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.survey.SurveyAgent.serve: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.prefabs.survey.SurveyAgent.yes_no_question: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.action_play_and_collect_action.ActionPlayAndCollectAction: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.action_play_and_collect_action.ActionPlayAndCollectAction.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.action_play_and_collect_action.ActionPlayAndCollectAction.stop: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.action_play_and_collect_action.ActionPlayAndCollectAction.volume: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.action_receive_fax_action.ActionReceiveFaxAction: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.action_receive_fax_action.ActionReceiveFaxAction.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.action_receive_fax_action.ActionReceiveFaxAction.stop: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.action_send_fax_action.ActionSendFaxAction: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.action_send_fax_action.ActionSendFaxAction.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.action_send_fax_action.ActionSendFaxAction.stop: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Action.__repr__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Action.get_call: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Action.get_control_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Action.get_result: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Action.get_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Action.resolve: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Action.set_on_completed: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Action.stop: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Action.update_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Action.wait_for_completion: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.collect_digits: Java overload of collect() that takes a digits-shaped Map directly + explicit control_id — Python uses keyword arguments instead of overloads
signalwire.relay.call.Call.detect_with: Java overload of detect() that takes the detect config + explicit control_id — keyword-args translation
signalwire.relay.call.Call.dispatch_event: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.get_action: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.get_device: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.get_direction: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.get_end_reason: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.get_node_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.get_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.get_tag: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.is_ended: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.record_audio: Java overload of record() that wraps the audio config as record={audio:...} — keyword-args translation
signalwire.relay.call.Call.register_action: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.resolve_all_actions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.set_client: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.set_device: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.set_direction: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.set_end_reason: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.set_node_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.set_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.Call.set_tag: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call.RecordAction.pause_with_behavior: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.call_play_and_collect_action.CallPlayAndCollectAction.__init__: Java exposes a typed Action subclass for the corresponding relay verb; Python represents the same action as a generic class:Action returned from Call.<method>()
signalwire.relay.call_play_and_collect_action.CallPlayAndCollectAction.volume: Java CallPlayAndCollectAction exposes volume() as a typed method on the play sub-action; Python returns the same control via the Action.result attribute
signalwire.relay.call_receive_fax_action.CallReceiveFaxAction.__init__: Java exposes a typed Action subclass for the corresponding relay verb; Python represents the same action as a generic class:Action returned from Call.<method>()
signalwire.relay.call_send_fax_action.CallSendFaxAction.__init__: Java exposes a typed Action subclass for the corresponding relay verb; Python represents the same action as a generic class:Action returned from Call.<method>()
signalwire.relay.client.RelayClient.builder: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.client.RelayClient.get_authorization_state: Java accessor for the SDK's stored authorization_state blob (used by reconnect-with-protocol tests); Python reads ._authorization_state directly
signalwire.relay.client.RelayClient.get_contexts: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.client.RelayClient.get_project: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.client.RelayClient.get_relay_protocol: Java getter for the protocol identifier issued by signalwire.connect; Python uses the relay_protocol property
signalwire.relay.client.RelayClient.get_space: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.client.RelayClient.internal_web_socket: Java's java-websocket-backed inner class surfaced as a public reflective member; Python uses an aiohttp ClientSession kept as a private attribute
signalwire.relay.client.RelayClient.is_connected: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.client.RelayClient.on_close: Java WebSocketClient lifecycle override — required by the Java-WebSocket library and surfaced as public; Python's transport is closed via aiohttp without this method
signalwire.relay.client.RelayClient.on_error: Java WebSocketClient lifecycle override surfaced public; Python handles errors via aiohttp exception flow
signalwire.relay.client.RelayClient.on_event: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.client.RelayClient.on_open: Java WebSocketClient lifecycle override surfaced public; Python performs the same step as part of the async connect coroutine
signalwire.relay.client.RelayClient.send_raw: Java helper that sends a raw JSON-RPC frame on the underlying socket — used by examples/RelayAuditHarness.java to satisfy the audit fixture's method=signalwire.event filter; Python tests inject equivalent frames via internal helpers
signalwire.relay.client.RelayClient.set_relay_protocol: Java test-only setter to seed a stored protocol before connect (simulates reconnect-with-protocol); Python tests assign client._relay_protocol directly
signalwire.relay.constants.Constants: Java constants class grouping RELAY protocol constants used by RelayClient; Python uses module-level attributes
signalwire.relay.constants.Constants.is_call_gone_code: Java constants class grouping RELAY protocol constants used by RelayClient; Python uses module-level attributes
signalwire.relay.constants.Constants.is_terminal_action_state: Java constants class grouping RELAY protocol constants used by RelayClient; Python uses module-level attributes
signalwire.relay.constants.Constants.is_terminal_call_state: Java constants class grouping RELAY protocol constants used by RelayClient; Python uses module-level attributes
signalwire.relay.constants.Constants.is_terminal_message_state: Java constants class grouping RELAY protocol constants used by RelayClient; Python uses module-level attributes
signalwire.relay.event.CallReceiveEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CallReceiveEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CallReceiveEvent.get_call_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CallReceiveEvent.get_context: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CallReceiveEvent.get_device: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CallReceiveEvent.get_node_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CallStateEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CallStateEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CallStateEvent.get_call_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CallStateEvent.get_device: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CallStateEvent.get_direction: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CallStateEvent.get_end_reason: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CallStateEvent.get_node_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CallStateEvent.get_tag: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CollectEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CollectEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CollectEvent.get_control_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CollectEvent.get_result: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.CollectEvent.get_result_type: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.ConferenceEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.ConferenceEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.ConferenceEvent.get_conference_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.ConnectEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.ConnectEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.ConnectEvent.get_connect_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.DetectEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.DetectEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.DetectEvent.get_control_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.DetectEvent.get_detect: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.DetectEvent.get_detect_event: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.DialEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.DialEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.DialEvent.get_call_info: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.DialEvent.get_dial_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.DialEvent.get_node_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.DialEvent.get_tag: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.FaxEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.FaxEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.FaxEvent.get_control_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.FaxEvent.get_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageReceiveEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageReceiveEvent.get_body: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageReceiveEvent.get_context: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageReceiveEvent.get_direction: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageReceiveEvent.get_from_number: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageReceiveEvent.get_media: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageReceiveEvent.get_message_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageReceiveEvent.get_message_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageReceiveEvent.get_segments: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageReceiveEvent.get_tags: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageReceiveEvent.get_to_number: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageStateEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageStateEvent.get_body: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageStateEvent.get_context: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageStateEvent.get_direction: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageStateEvent.get_from_number: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageStateEvent.get_media: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageStateEvent.get_message_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageStateEvent.get_message_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageStateEvent.get_reason: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageStateEvent.get_segments: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageStateEvent.get_tags: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.MessageStateEvent.get_to_number: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.PayEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.PayEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.PayEvent.get_control_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.PayEvent.get_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.PlayEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.PlayEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.PlayEvent.get_control_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.PlayEvent.get_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.QueueEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.QueueEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.QueueEvent.get_queue_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.RecordEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.RecordEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.RecordEvent.get_control_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.RecordEvent.get_duration: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.RecordEvent.get_size: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.RecordEvent.get_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.RecordEvent.get_url: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.ReferEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.ReferEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.ReferEvent.get_refer_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.RelayEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.RelayEvent.__repr__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.RelayEvent.from_raw_params: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.RelayEvent.get_event_type: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.RelayEvent.get_params: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.RelayEvent.get_string_param: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.RelayEvent.get_timestamp: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.SendDigitsEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.SendDigitsEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.SendDigitsEvent.get_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.StreamEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.StreamEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.StreamEvent.get_control_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.StreamEvent.get_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.TapEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.TapEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.TapEvent.get_control_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.TapEvent.get_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.TranscribeEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.TranscribeEvent.get_call_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.TranscribeEvent.get_control_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.event.TranscribeEvent.get_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.from_receive_event: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.get_body: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.get_context: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.get_direction: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.get_from_number: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.get_media: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.get_message_id: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.get_reason: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.get_result: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.get_segments: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.get_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.get_tags: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.get_to_number: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.set_body: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.set_context: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.set_direction: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.set_from_number: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.set_media: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.set_on_completed: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.set_segments: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.set_state: Java setter for the message state field — used internally by sendMessage() to seed initial 'queued' state; Python's __init__ sets it
signalwire.relay.message.Message.set_tags: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.set_to_number: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.update_from_event: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.wait_for_completion: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.relay_client_builder.RelayClientBuilder: Java builder pattern — RelayClient.builder() is the idiomatic constructor
signalwire.relay.relay_client_builder.RelayClientBuilder.build: Java builder pattern — RelayClient.builder() is the idiomatic constructor
signalwire.relay.relay_client_builder.RelayClientBuilder.contexts: Java builder pattern — RelayClient.builder() is the idiomatic constructor
signalwire.relay.relay_client_builder.RelayClientBuilder.host: Java builder alias for space() — matches the Python keyword argument name for adjacency
signalwire.relay.relay_client_builder.RelayClientBuilder.jwt_token: Java builder for the JWT-only auth path — Python takes jwt_token as a kwarg to RelayClient.__init__
signalwire.relay.relay_client_builder.RelayClientBuilder.project: Java builder pattern — RelayClient.builder() is the idiomatic constructor
signalwire.relay.relay_client_builder.RelayClientBuilder.space: Java builder pattern — RelayClient.builder() is the idiomatic constructor
signalwire.relay.relay_client_builder.RelayClientBuilder.token: Java builder pattern — RelayClient.builder() is the idiomatic constructor
signalwire.relay.relay_constants.RelayConstants.is_call_gone_code: Java RelayConstants.is_call_gone_code is a typed predicate for state classification; Python tests the same condition inline with constant-string comparison
signalwire.relay.relay_constants.RelayConstants.is_terminal_action_state: Java RelayConstants.is_terminal_action_state is a typed predicate for state classification; Python tests the same condition inline with constant-string comparison
signalwire.relay.relay_constants.RelayConstants.is_terminal_call_state: Java RelayConstants.is_terminal_call_state is a typed predicate for state classification; Python tests the same condition inline with constant-string comparison
signalwire.relay.relay_constants.RelayConstants.is_terminal_message_state: Java RelayConstants.is_terminal_message_state is a typed predicate for state classification; Python tests the same condition inline with constant-string comparison
signalwire.relay.relay_event_authorization_state_event.RelayEventAuthorizationStateEvent: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.relay_event_authorization_state_event.RelayEventAuthorizationStateEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.relay_event_authorization_state_event.RelayEventAuthorizationStateEvent.get_authorization_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest._base.CrudResource.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest._base.CrudResource.get_base_path: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest._base.CrudResource.get_http_client: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest._base.HttpClient.with_base_url: Java HttpClient exposes withBaseUrl() as a fluent builder for re-rooting the client at a different API base; Python accepts the base URL via the RestClient constructor and does not expose a builder
signalwire.rest._base.SignalWireRestError.get_method: Java exposes structured error metadata via getter methods; Python uses public attributes on the exception
signalwire.rest._base.SignalWireRestError.get_path: Java exposes structured error metadata via getter methods; Python uses public attributes on the exception
signalwire.rest._base.SignalWireRestError.get_response_body: Java exposes structured error metadata via getter methods; Python uses public attributes on the exception
signalwire.rest._base.SignalWireRestError.get_status_code: Java exposes structured error metadata via getter methods; Python uses public attributes on the exception
signalwire.rest._base.SignalWireRestError.is_client_error: Java provides classification predicates for HTTP errors as instance methods; Python tests on response.status_code directly
signalwire.rest._base.SignalWireRestError.is_not_found: Java provides classification predicates for HTTP errors as instance methods; Python tests on response.status_code directly
signalwire.rest._base.SignalWireRestError.is_server_error: Java provides classification predicates for HTTP errors as instance methods; Python tests on response.status_code directly
signalwire.rest._base.SignalWireRestError.is_unauthorized: Java provides classification predicates for HTTP errors as instance methods; Python tests on response.status_code directly
signalwire.rest._pagination.PaginatedIterator.has_next: Java's PaginatedIterator exposes explicit Iterator-style methods (has_next/next/iterator); Python uses the generator/iter protocol implicitly
signalwire.rest._pagination.PaginatedIterator.iterator: Java's PaginatedIterator exposes explicit Iterator-style methods (has_next/next/iterator); Python uses the generator/iter protocol implicitly
signalwire.rest._pagination.PaginatedIterator.next: Java's PaginatedIterator exposes explicit Iterator-style methods (has_next/next/iterator); Python uses the generator/iter protocol implicitly
signalwire.rest.call_handler.PhoneCallHandler.__repr__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.call_handler.PhoneCallHandler.ai_agent: Java PhoneCallHandler.ai_agent is an enum constant for a phone-number routing target; Python encodes the same routing options as string constants
signalwire.rest.call_handler.PhoneCallHandler.call_flow: Java PhoneCallHandler.call_flow is an enum constant for a phone-number routing target; Python encodes the same routing options as string constants
signalwire.rest.call_handler.PhoneCallHandler.dialogflow: Java PhoneCallHandler.dialogflow is an enum constant for a phone-number routing target; Python encodes the same routing options as string constants
signalwire.rest.call_handler.PhoneCallHandler.laml_application: Java PhoneCallHandler.laml_application is an enum constant for a phone-number routing target; Python encodes the same routing options as string constants
signalwire.rest.call_handler.PhoneCallHandler.laml_webhooks: Java PhoneCallHandler.laml_webhooks is an enum constant for a phone-number routing target; Python encodes the same routing options as string constants
signalwire.rest.call_handler.PhoneCallHandler.relay_application: Java PhoneCallHandler.relay_application is an enum constant for a phone-number routing target; Python encodes the same routing options as string constants
signalwire.rest.call_handler.PhoneCallHandler.relay_connector: Java PhoneCallHandler.relay_connector is an enum constant for a phone-number routing target; Python encodes the same routing options as string constants
signalwire.rest.call_handler.PhoneCallHandler.relay_context: Java PhoneCallHandler.relay_context is an enum constant for a phone-number routing target; Python encodes the same routing options as string constants
signalwire.rest.call_handler.PhoneCallHandler.relay_script: Java PhoneCallHandler.relay_script is an enum constant for a phone-number routing target; Python encodes the same routing options as string constants
signalwire.rest.call_handler.PhoneCallHandler.relay_topic: Java PhoneCallHandler.relay_topic is an enum constant for a phone-number routing target; Python encodes the same routing options as string constants
signalwire.rest.call_handler.PhoneCallHandler.value_of: Java PhoneCallHandler.valueOf is the standard Java enum-by-name lookup; Python represents the same dispatch type as a string union
signalwire.rest.call_handler.PhoneCallHandler.values: Java PhoneCallHandler.values() is the standard Java enum iterator; Python represents the same dispatch type as a string union
signalwire.rest.call_handler.PhoneCallHandler.video_room: Java PhoneCallHandler.video_room is an enum constant for a phone-number routing target; Python encodes the same routing options as string constants
signalwire.rest.call_handler.PhoneCallHandler.wire_value: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.addresses: Java RestClient exposes the addresses namespace as a typed accessor; Python's reference RestClient does not surface this namespace directly
signalwire.rest.client.RestClient.billing: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.builder: Java builder pattern — RestClient.builder() exposes the idiomatic constructor entry point
signalwire.rest.client.RestClient.calling: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.campaign: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.chat: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.compat: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.compliance: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.conferences: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.datasphere: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.fabric: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.fax: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.get_http_client: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.get_project: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.get_space: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.imported_numbers: Java RestClient exposes the imported_numbers namespace as a typed accessor; Python's reference RestClient does not surface this namespace directly
signalwire.rest.client.RestClient.logs: Java RestClient exposes the logs namespace as a typed accessor; Python's reference RestClient does not surface this namespace directly
signalwire.rest.client.RestClient.messaging: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.mfa: Java RestClient exposes the mfa namespace as a typed accessor; Python's reference RestClient does not surface this namespace directly
signalwire.rest.client.RestClient.number_groups: Java RestClient exposes the number_groups namespace as a typed accessor; Python's reference RestClient does not surface this namespace directly
signalwire.rest.client.RestClient.number_lookup: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.phone_numbers: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.project: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.pub_sub: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.pubsub: Java RestClient exposes the pubsub namespace as a typed accessor; Python's reference RestClient does not surface this namespace directly
signalwire.rest.client.RestClient.queues: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.recordings: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.registry: Java RestClient exposes the registry namespace as a typed accessor; Python's reference RestClient does not surface this namespace directly
signalwire.rest.client.RestClient.short_codes: Java RestClient exposes the short_codes namespace as a typed accessor; Python's reference RestClient does not surface this namespace directly
signalwire.rest.client.RestClient.sip: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.sip_profile: Java RestClient exposes the sip_profile namespace as a typed accessor; Python's reference RestClient does not surface this namespace directly
signalwire.rest.client.RestClient.streams: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.swml: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.transcriptions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.video: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.with_base_url: Java factory for pointing the REST client at an explicit base URL (loopback fixture, plain HTTP) — used by examples/RestAuditHarness.java; Python tests use httpx_mock to redirect transport instead
signalwire.rest.namespaces.addresses.AddressesResource.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.billing.BillingNamespace.__init__: Java BillingNamespace requires an explicit public constructor for HttpClient injection; Python's reference handles this namespace via a polymorphic CrudResource without a typed wrapper class
signalwire.rest.namespaces.billing.BillingNamespace.invoices: Java BillingNamespace.invoices is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.billing.BillingNamespace.usage: Java BillingNamespace.usage is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.billing_namespace.BillingNamespace: Java's BillingNamespace exposes the billing API one-liner; Python accesses billing via the compat namespace
signalwire.rest.namespaces.billing_namespace.BillingNamespace.__init__: Java's BillingNamespace exposes the billing API one-liner; Python accesses billing via the compat namespace
signalwire.rest.namespaces.billing_namespace.BillingNamespace.invoices: Java's BillingNamespace exposes the billing API one-liner; Python accesses billing via the compat namespace
signalwire.rest.namespaces.billing_namespace.BillingNamespace.usage: Java's BillingNamespace exposes the billing API one-liner; Python accesses billing via the compat namespace
signalwire.rest.namespaces.calling.CallingNamespace.calls: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.campaign.CampaignNamespace.__init__: Java CampaignNamespace requires an explicit public constructor for HttpClient injection; Python's reference handles this namespace via a polymorphic CrudResource without a typed wrapper class
signalwire.rest.namespaces.campaign.CampaignNamespace.assignments: Java CampaignNamespace.assignments is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.campaign.CampaignNamespace.brands: Java CampaignNamespace.brands is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.campaign.CampaignNamespace.campaigns: Java CampaignNamespace.campaigns is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.campaign.CampaignNamespace.orders: Java CampaignNamespace.orders is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.campaign_namespace.CampaignNamespace: Java's CampaignNamespace is the equivalent of Python's RegistryNamespace for 10DLC/TCR registration
signalwire.rest.namespaces.campaign_namespace.CampaignNamespace.__init__: Java's CampaignNamespace is the equivalent of Python's RegistryNamespace for 10DLC/TCR registration
signalwire.rest.namespaces.campaign_namespace.CampaignNamespace.assignments: Java's CampaignNamespace is the equivalent of Python's RegistryNamespace for 10DLC/TCR registration
signalwire.rest.namespaces.campaign_namespace.CampaignNamespace.brands: Java's CampaignNamespace is the equivalent of Python's RegistryNamespace for 10DLC/TCR registration
signalwire.rest.namespaces.campaign_namespace.CampaignNamespace.campaigns: Java's CampaignNamespace is the equivalent of Python's RegistryNamespace for 10DLC/TCR registration
signalwire.rest.namespaces.campaign_namespace.CampaignNamespace.orders: Java's CampaignNamespace is the equivalent of Python's RegistryNamespace for 10DLC/TCR registration
signalwire.rest.namespaces.chat.ChatNamespace.__init__: Java ChatNamespace requires an explicit public constructor for HttpClient injection; Python's reference handles this namespace via a polymorphic CrudResource without a typed wrapper class
signalwire.rest.namespaces.chat.ChatNamespace.channels: Java ChatNamespace.channels is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.chat.ChatNamespace.members: Java ChatNamespace.members is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.chat.ChatNamespace.messages: Java ChatNamespace.messages is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.chat_namespace.ChatNamespace: Java's ChatNamespace is the namespace-level accessor for the Chat API; Python has a flat ChatResource class
signalwire.rest.namespaces.chat_namespace.ChatNamespace.__init__: Java's ChatNamespace is the namespace-level accessor for the Chat API; Python has a flat ChatResource class
signalwire.rest.namespaces.chat_namespace.ChatNamespace.channels: Java's ChatNamespace is the namespace-level accessor for the Chat API; Python has a flat ChatResource class
signalwire.rest.namespaces.chat_namespace.ChatNamespace.members: Java's ChatNamespace is the namespace-level accessor for the Chat API; Python has a flat ChatResource class
signalwire.rest.namespaces.chat_namespace.ChatNamespace.messages: Java's ChatNamespace is the namespace-level accessor for the Chat API; Python has a flat ChatResource class
signalwire.rest.namespaces.compat.CompatAccounts.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.compat.CompatApplications.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.compat.CompatCalls.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.compat.CompatConferences.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.compat.CompatConferences.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.compat.CompatFaxes.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.compat.CompatLamlBins.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.compat.CompatMessages.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.compat.CompatNamespace.accounts: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.applications: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.calls: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.conferences: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.faxes: Java CompatNamespace exposes the faxes sub-resource as a typed accessor; Python's reference flattens this surface differently
signalwire.rest.namespaces.compat.CompatNamespace.laml_bins: Java CompatNamespace exposes the laml_bins sub-resource as a typed accessor; Python's reference flattens this surface differently
signalwire.rest.namespaces.compat.CompatNamespace.messages: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.phone_numbers: Java CompatNamespace exposes the phone_numbers sub-resource as a typed accessor; Python's reference flattens this surface differently
signalwire.rest.namespaces.compat.CompatNamespace.queues: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.recordings: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.sip_credential_lists: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.sip_domains: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.sip_ip_access_control_lists: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.tokens: Java CompatNamespace exposes the tokens sub-resource as a typed accessor; Python's reference flattens this surface differently
signalwire.rest.namespaces.compat.CompatNamespace.transcriptions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatPhoneNumbers.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.compat.CompatQueues.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.compat.CompatRecordings.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.compat.CompatRecordings.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.compat.CompatTokens.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.compat.CompatTokens.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.compat.CompatTranscriptions.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.compat.CompatTranscriptions.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.compliance.ComplianceNamespace.__init__: Java ComplianceNamespace requires an explicit public constructor for HttpClient injection; Python's reference handles this namespace via a polymorphic CrudResource without a typed wrapper class
signalwire.rest.namespaces.compliance.ComplianceNamespace.cnam_registrations: Java ComplianceNamespace.cnam_registrations is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.compliance.ComplianceNamespace.shaken_stir: Java ComplianceNamespace.shaken_stir is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.compliance_namespace.ComplianceNamespace: Java's ComplianceNamespace exposes TCR/carrier compliance APIs; Python routes these through RegistryNamespace
signalwire.rest.namespaces.compliance_namespace.ComplianceNamespace.__init__: Java's ComplianceNamespace exposes TCR/carrier compliance APIs; Python routes these through RegistryNamespace
signalwire.rest.namespaces.compliance_namespace.ComplianceNamespace.cnam_registrations: Java's ComplianceNamespace exposes TCR/carrier compliance APIs; Python routes these through RegistryNamespace
signalwire.rest.namespaces.compliance_namespace.ComplianceNamespace.shaken_stir: Java's ComplianceNamespace exposes TCR/carrier compliance APIs; Python routes these through RegistryNamespace
signalwire.rest.namespaces.conference.ConferenceNamespace.__init__: Java ConferenceNamespace requires an explicit public constructor for HttpClient injection; Python's reference handles this namespace via a polymorphic CrudResource without a typed wrapper class
signalwire.rest.namespaces.conference.ConferenceNamespace.conferences: Java ConferenceNamespace.conferences is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.conference.ConferenceNamespace.participants: Java ConferenceNamespace.participants is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.conference_namespace.ConferenceNamespace: Java's ConferenceNamespace wraps the conference API with a namespace-level accessor; Python routes through CompatConferences
signalwire.rest.namespaces.conference_namespace.ConferenceNamespace.__init__: Java's ConferenceNamespace wraps the conference API with a namespace-level accessor; Python routes through CompatConferences
signalwire.rest.namespaces.conference_namespace.ConferenceNamespace.conferences: Java's ConferenceNamespace wraps the conference API with a namespace-level accessor; Python routes through CompatConferences
signalwire.rest.namespaces.conference_namespace.ConferenceNamespace.participants: Java's ConferenceNamespace wraps the conference API with a namespace-level accessor; Python routes through CompatConferences
signalwire.rest.namespaces.datasphere.DatasphereNamespace.documents: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.datasphere.DatasphereNamespace.search: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.fabric.CallFlowsResource.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.fabric.CallFlowsResource.update: Java fabric resource exposes the update CRUD verb explicitly; Python's reference omits update on this fabric resource (PUT support is type-erased through the polymorphic CrudResource base)
signalwire.rest.namespaces.fabric.ConferenceRoomsResource.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.fabric.ConferenceRoomsResource.update: Java fabric resource exposes the update CRUD verb explicitly; Python's reference omits update on this fabric resource (PUT support is type-erased through the polymorphic CrudResource base)
signalwire.rest.namespaces.fabric.CxmlApplicationsResource.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.fabric.CxmlApplicationsResource.update: Java fabric resource exposes the update CRUD verb explicitly; Python's reference omits update on this fabric resource (PUT support is type-erased through the polymorphic CrudResource base)
signalwire.rest.namespaces.fabric.FabricAddresses.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.fabric.FabricAddresses.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.fabric.FabricNamespace.addresses: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.fabric.FabricNamespace.ai_agents: Java FabricNamespace exposes the ai_agents sub-resource as a typed accessor; Python's reference handles fabric resources via a polymorphic CrudResource
signalwire.rest.namespaces.fabric.FabricNamespace.call_flows: Java FabricNamespace exposes the call_flows sub-resource as a typed accessor; Python's reference handles fabric resources via a polymorphic CrudResource
signalwire.rest.namespaces.fabric.FabricNamespace.conference_rooms: Java FabricNamespace exposes the conference_rooms sub-resource as a typed accessor; Python's reference handles fabric resources via a polymorphic CrudResource
signalwire.rest.namespaces.fabric.FabricNamespace.cxml_applications: Java FabricNamespace exposes the cxml_applications sub-resource as a typed accessor; Python's reference handles fabric resources via a polymorphic CrudResource
signalwire.rest.namespaces.fabric.FabricNamespace.cxml_scripts: Java FabricNamespace exposes the cxml_scripts sub-resource as a typed accessor; Python's reference handles fabric resources via a polymorphic CrudResource
signalwire.rest.namespaces.fabric.FabricNamespace.cxml_webhooks: Java FabricNamespace exposes the cxml_webhooks sub-resource as a typed accessor; Python's reference handles fabric resources via a polymorphic CrudResource
signalwire.rest.namespaces.fabric.FabricNamespace.freeswitch_connectors: Java FabricNamespace exposes the freeswitch_connectors sub-resource as a typed accessor; Python's reference handles fabric resources via a polymorphic CrudResource
signalwire.rest.namespaces.fabric.FabricNamespace.relay_applications: Java FabricNamespace exposes the relay_applications sub-resource as a typed accessor; Python's reference handles fabric resources via a polymorphic CrudResource
signalwire.rest.namespaces.fabric.FabricNamespace.resources: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.fabric.FabricNamespace.sip_endpoints: Java FabricNamespace exposes the sip_endpoints sub-resource as a typed accessor; Python's reference handles fabric resources via a polymorphic CrudResource
signalwire.rest.namespaces.fabric.FabricNamespace.sip_gateways: Java FabricNamespace exposes the sip_gateways sub-resource as a typed accessor; Python's reference handles fabric resources via a polymorphic CrudResource
signalwire.rest.namespaces.fabric.FabricNamespace.subscribers: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.fabric.FabricNamespace.swml_scripts: Java FabricNamespace exposes the swml_scripts sub-resource as a typed accessor; Python's reference handles fabric resources via a polymorphic CrudResource
signalwire.rest.namespaces.fabric.FabricNamespace.swml_webhooks: Java FabricNamespace exposes the swml_webhooks sub-resource as a typed accessor; Python's reference handles fabric resources via a polymorphic CrudResource
signalwire.rest.namespaces.fabric.FabricNamespace.tokens: Java FabricNamespace exposes the tokens sub-resource as a typed accessor; Python's reference handles fabric resources via a polymorphic CrudResource
signalwire.rest.namespaces.fabric.GenericResources.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.fabric.GenericResources.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.fabric.SubscribersResource.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.fabric.SubscribersResource.list_addresses: Java SubscribersResource exposes list_addresses as a typed pagination accessor; Python's reference scopes the addresses listing to AddressesResource
signalwire.rest.namespaces.fabric.SubscribersResource.update: Java fabric resource exposes the update CRUD verb explicitly; Python's reference omits update on this fabric resource (PUT support is type-erased through the polymorphic CrudResource base)
signalwire.rest.namespaces.fax.FaxNamespace.__init__: Java FaxNamespace requires an explicit public constructor for HttpClient injection; Python's reference handles this namespace via a polymorphic CrudResource without a typed wrapper class
signalwire.rest.namespaces.fax.FaxNamespace.faxes: Java FaxNamespace.faxes is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.fax_namespace.FaxNamespace: Java's FaxNamespace exposes fax APIs; Python routes through CompatFaxes under the compat namespace
signalwire.rest.namespaces.fax_namespace.FaxNamespace.__init__: Java's FaxNamespace exposes fax APIs; Python routes through CompatFaxes under the compat namespace
signalwire.rest.namespaces.fax_namespace.FaxNamespace.faxes: Java's FaxNamespace exposes fax APIs; Python routes through CompatFaxes under the compat namespace
signalwire.rest.namespaces.imported_numbers.ImportedNumbersResource.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.logs.ConferenceLogs.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.logs.ConferenceLogs.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.logs.FaxLogs.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.logs.FaxLogs.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.logs.LogsNamespace.conferences: Java LogsNamespace exposes the conferences sub-resource as a typed accessor; Python's reference handles logs resources via a polymorphic CrudResource
signalwire.rest.namespaces.logs.LogsNamespace.fax: Java LogsNamespace exposes the fax sub-resource as a typed accessor; Python's reference handles logs resources via a polymorphic CrudResource
signalwire.rest.namespaces.logs.LogsNamespace.messages: Java LogsNamespace exposes the messages sub-resource as a typed accessor; Python's reference handles logs resources via a polymorphic CrudResource
signalwire.rest.namespaces.logs.LogsNamespace.voice: Java LogsNamespace exposes the voice sub-resource as a typed accessor; Python's reference handles logs resources via a polymorphic CrudResource
signalwire.rest.namespaces.logs.MessageLogs.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.logs.MessageLogs.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.logs.VoiceLogs.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.logs.VoiceLogs.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.messaging.MessagingNamespace.__init__: Java MessagingNamespace requires an explicit public constructor for HttpClient injection; Python's reference handles this namespace via a polymorphic CrudResource without a typed wrapper class
signalwire.rest.namespaces.messaging.MessagingNamespace.messages: Java MessagingNamespace.messages is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.messaging.MessagingNamespace.send: Java MessagingNamespace.send is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.messaging_namespace.MessagingNamespace: Java's MessagingNamespace is the namespace-level accessor for messaging; Python routes through CompatMessages
signalwire.rest.namespaces.messaging_namespace.MessagingNamespace.__init__: Java's MessagingNamespace is the namespace-level accessor for messaging; Python routes through CompatMessages
signalwire.rest.namespaces.messaging_namespace.MessagingNamespace.messages: Java's MessagingNamespace is the namespace-level accessor for messaging; Python routes through CompatMessages
signalwire.rest.namespaces.messaging_namespace.MessagingNamespace.send: Java's MessagingNamespace is the namespace-level accessor for messaging; Python routes through CompatMessages
signalwire.rest.namespaces.mfa.MfaResource.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.number_groups.NumberGroupsResource.create: Java NumberGroupsResource ships with the full CRUD surface; Python's reference handles number-groups membership only and exposes the CRUD methods via its base resource indirectly
signalwire.rest.namespaces.number_groups.NumberGroupsResource.delete: Java NumberGroupsResource ships with the full CRUD surface; Python's reference handles number-groups membership only and exposes the CRUD methods via its base resource indirectly
signalwire.rest.namespaces.number_groups.NumberGroupsResource.get: Java NumberGroupsResource ships with the full CRUD surface; Python's reference handles number-groups membership only and exposes the CRUD methods via its base resource indirectly
signalwire.rest.namespaces.number_groups.NumberGroupsResource.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.number_groups.NumberGroupsResource.list: Java NumberGroupsResource ships with the full CRUD surface; Python's reference handles number-groups membership only and exposes the CRUD methods via its base resource indirectly
signalwire.rest.namespaces.number_groups.NumberGroupsResource.update: Java NumberGroupsResource ships with the full CRUD surface; Python's reference handles number-groups membership only and exposes the CRUD methods via its base resource indirectly
signalwire.rest.namespaces.number_lookup.NumberLookupNamespace.__init__: Java NumberLookupNamespace requires an explicit public constructor for HttpClient injection; Python's reference handles this namespace via a polymorphic CrudResource without a typed wrapper class
signalwire.rest.namespaces.number_lookup.NumberLookupNamespace.lookup: Java NumberLookupNamespace.lookup is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.number_lookup_namespace.NumberLookupNamespace: Java's NumberLookupNamespace wraps the number-lookup API; Python exposes a flat LookupResource
signalwire.rest.namespaces.number_lookup_namespace.NumberLookupNamespace.__init__: Java's NumberLookupNamespace wraps the number-lookup API; Python exposes a flat LookupResource
signalwire.rest.namespaces.number_lookup_namespace.NumberLookupNamespace.lookup: Java's NumberLookupNamespace wraps the number-lookup API; Python exposes a flat LookupResource
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.create: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.delete: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.get: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.get_resource: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.list: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.update: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.project.ProjectNamespace.create_token: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.project.ProjectNamespace.get: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.project.ProjectNamespace.list_tokens: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.project.ProjectNamespace.tokens: Java ProjectNamespace exposes the tokens sub-resource as a typed accessor; Python's reference uses indexed dispatch on the project namespace
signalwire.rest.namespaces.project.ProjectNamespace.update: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.project.ProjectTokens.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.pub_sub.PubSubNamespace.__init__: Java PubSubNamespace requires an explicit public constructor for HttpClient injection; Python's reference handles this namespace via a polymorphic CrudResource without a typed wrapper class
signalwire.rest.namespaces.pub_sub.PubSubNamespace.channels: Java PubSubNamespace.channels is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.pub_sub.PubSubNamespace.publish: Java PubSubNamespace.publish is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.pub_sub_namespace.PubSubNamespace: Java's PubSubNamespace wraps the Pub/Sub API; Python exposes a flat PubSubResource
signalwire.rest.namespaces.pub_sub_namespace.PubSubNamespace.__init__: Java's PubSubNamespace wraps the Pub/Sub API; Python exposes a flat PubSubResource
signalwire.rest.namespaces.pub_sub_namespace.PubSubNamespace.channels: Java's PubSubNamespace wraps the Pub/Sub API; Python exposes a flat PubSubResource
signalwire.rest.namespaces.pub_sub_namespace.PubSubNamespace.publish: Java's PubSubNamespace wraps the Pub/Sub API; Python exposes a flat PubSubResource
signalwire.rest.namespaces.queue_namespace.QueueNamespace: Java's QueueNamespace wraps the queues API; Python exposes a flat QueuesResource
signalwire.rest.namespaces.queue_namespace.QueueNamespace.__init__: Java's QueueNamespace wraps the queues API; Python exposes a flat QueuesResource
signalwire.rest.namespaces.queue_namespace.QueueNamespace.queues: Java's QueueNamespace wraps the queues API; Python exposes a flat QueuesResource
signalwire.rest.namespaces.queues.QueuesResource.create: Java QueuesResource exposes the create CRUD method directly on the resource; Python's reference scopes the surface to per-call helpers
signalwire.rest.namespaces.queues.QueuesResource.delete: Java QueuesResource exposes the delete CRUD method directly on the resource; Python's reference scopes the surface to per-call helpers
signalwire.rest.namespaces.queues.QueuesResource.get: Java QueuesResource exposes the get CRUD method directly on the resource; Python's reference scopes the surface to per-call helpers
signalwire.rest.namespaces.queues.QueuesResource.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.queues.QueuesResource.list: Java QueuesResource exposes the list CRUD method directly on the resource; Python's reference scopes the surface to per-call helpers
signalwire.rest.namespaces.queues.QueuesResource.queues: Java QueuesResource exposes the queues CRUD method directly on the resource; Python's reference scopes the surface to per-call helpers
signalwire.rest.namespaces.queues.QueuesResource.update: Java QueuesResource exposes the update CRUD method directly on the resource; Python's reference scopes the surface to per-call helpers
signalwire.rest.namespaces.recording_namespace.RecordingNamespace: Java's RecordingNamespace wraps the recordings API; Python exposes a flat RecordingsResource
signalwire.rest.namespaces.recording_namespace.RecordingNamespace.__init__: Java's RecordingNamespace wraps the recordings API; Python exposes a flat RecordingsResource
signalwire.rest.namespaces.recording_namespace.RecordingNamespace.recordings: Java's RecordingNamespace wraps the recordings API; Python exposes a flat RecordingsResource
signalwire.rest.namespaces.recordings.RecordingsResource.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.recordings.RecordingsResource.recordings: Java RecordingsResource exposes a recordings() typed accessor for nested-resource navigation; Python's reference does not expose this accessor
signalwire.rest.namespaces.registry.RegistryBrands.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.registry.RegistryBrands.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.registry.RegistryCampaigns.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.registry.RegistryCampaigns.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.registry.RegistryNamespace.brands: Java RegistryNamespace exposes the brands sub-resource as a typed accessor; Python's reference handles registry resources differently
signalwire.rest.namespaces.registry.RegistryNamespace.campaigns: Java RegistryNamespace exposes the campaigns sub-resource as a typed accessor; Python's reference handles registry resources differently
signalwire.rest.namespaces.registry.RegistryNamespace.numbers: Java RegistryNamespace exposes the numbers sub-resource as a typed accessor; Python's reference handles registry resources differently
signalwire.rest.namespaces.registry.RegistryNamespace.orders: Java RegistryNamespace exposes the orders sub-resource as a typed accessor; Python's reference handles registry resources differently
signalwire.rest.namespaces.registry.RegistryNumbers.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.registry.RegistryNumbers.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.registry.RegistryOrders.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.registry.RegistryOrders.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.short_codes.ShortCodesResource.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.sip.SipNamespace.__init__: Java SipNamespace requires an explicit public constructor for HttpClient injection; Python's reference handles this namespace via a polymorphic CrudResource without a typed wrapper class
signalwire.rest.namespaces.sip.SipNamespace.endpoints: Java SipNamespace.endpoints is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.sip.SipNamespace.profiles: Java SipNamespace.profiles is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.sip_namespace.SipNamespace: Java's SipNamespace exposes SIP-endpoint configuration; Python routes through SubscribersResource on the Fabric namespace
signalwire.rest.namespaces.sip_namespace.SipNamespace.__init__: Java's SipNamespace exposes SIP-endpoint configuration; Python routes through SubscribersResource on the Fabric namespace
signalwire.rest.namespaces.sip_namespace.SipNamespace.endpoints: Java's SipNamespace exposes SIP-endpoint configuration; Python routes through SubscribersResource on the Fabric namespace
signalwire.rest.namespaces.sip_namespace.SipNamespace.profiles: Java's SipNamespace exposes SIP-endpoint configuration; Python routes through SubscribersResource on the Fabric namespace
signalwire.rest.namespaces.sip_profile.SipProfileResource.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.stream.StreamNamespace.__init__: Java StreamNamespace requires an explicit public constructor for HttpClient injection; Python's reference handles this namespace via a polymorphic CrudResource without a typed wrapper class
signalwire.rest.namespaces.stream.StreamNamespace.streams: Java StreamNamespace.streams is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.stream_namespace.StreamNamespace: Java's StreamNamespace wraps the media-stream API; Python routes through the compat namespace
signalwire.rest.namespaces.stream_namespace.StreamNamespace.__init__: Java's StreamNamespace wraps the media-stream API; Python routes through the compat namespace
signalwire.rest.namespaces.stream_namespace.StreamNamespace.streams: Java's StreamNamespace wraps the media-stream API; Python routes through the compat namespace
signalwire.rest.namespaces.swml.SwmlNamespace.__init__: Java SwmlNamespace requires an explicit public constructor for HttpClient injection; Python's reference handles this namespace via a polymorphic CrudResource without a typed wrapper class
signalwire.rest.namespaces.swml.SwmlNamespace.scripts: Java SwmlNamespace.scripts is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.swml_namespace.SwmlNamespace: Java's SwmlNamespace exposes SWML-endpoint management as a dedicated namespace; Python routes through the SwmlWebhooks resource on the Fabric namespace
signalwire.rest.namespaces.swml_namespace.SwmlNamespace.__init__: Java's SwmlNamespace exposes SWML-endpoint management as a dedicated namespace; Python routes through the SwmlWebhooks resource on the Fabric namespace
signalwire.rest.namespaces.swml_namespace.SwmlNamespace.scripts: Java's SwmlNamespace exposes SWML-endpoint management as a dedicated namespace; Python routes through the SwmlWebhooks resource on the Fabric namespace
signalwire.rest.namespaces.transcription.TranscriptionNamespace.__init__: Java TranscriptionNamespace requires an explicit public constructor for HttpClient injection; Python's reference handles this namespace via a polymorphic CrudResource without a typed wrapper class
signalwire.rest.namespaces.transcription.TranscriptionNamespace.transcriptions: Java TranscriptionNamespace.transcriptions is a typed sub-resource accessor for the namespace; Python's reference handles this surface differently via indexed CrudResource dispatch
signalwire.rest.namespaces.transcription_namespace.TranscriptionNamespace: Java's TranscriptionNamespace wraps the transcription API as a dedicated namespace; Python routes through the compat namespace
signalwire.rest.namespaces.transcription_namespace.TranscriptionNamespace.__init__: Java's TranscriptionNamespace wraps the transcription API as a dedicated namespace; Python routes through the compat namespace
signalwire.rest.namespaces.transcription_namespace.TranscriptionNamespace.transcriptions: Java's TranscriptionNamespace wraps the transcription API as a dedicated namespace; Python routes through the compat namespace
signalwire.rest.namespaces.video.VideoConferenceTokens.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.video.VideoConferenceTokens.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.video.VideoConferences.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.video.VideoConferences.update: Java video resource exposes the update CRUD verb explicitly; Python's reference omits update on this video resource
signalwire.rest.namespaces.video.VideoNamespace.conference_tokens: Java VideoNamespace exposes the conference_tokens sub-resource as a typed accessor; Python's reference exposes the surface via a different routing
signalwire.rest.namespaces.video.VideoNamespace.conferences: Java VideoNamespace exposes the conferences sub-resource as a typed accessor; Python's reference exposes the surface via a different routing
signalwire.rest.namespaces.video.VideoNamespace.recordings: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.video.VideoNamespace.room_recordings: Java VideoNamespace exposes the room_recordings sub-resource as a typed accessor; Python's reference exposes the surface via a different routing
signalwire.rest.namespaces.video.VideoNamespace.room_sessions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.video.VideoNamespace.room_tokens: Java VideoNamespace exposes the room_tokens sub-resource as a typed accessor; Python's reference exposes the surface via a different routing
signalwire.rest.namespaces.video.VideoNamespace.rooms: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.video.VideoNamespace.streams: Java VideoNamespace exposes the streams sub-resource as a typed accessor; Python's reference exposes the surface via a different routing
signalwire.rest.namespaces.video.VideoRoomRecordings.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.video.VideoRoomRecordings.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.video.VideoRoomSessions.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.video.VideoRoomSessions.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.namespaces.video.VideoRooms.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.video.VideoRooms.update: Java video resource exposes the update CRUD verb explicitly; Python's reference omits update on this video resource
signalwire.rest.namespaces.video.VideoStreams.__init__: Java requires an explicit public constructor on every CRUD resource (HttpClient is injected); Python materialises the resource implicitly via class instantiation, so the constructor has no Python __init__ counterpart
signalwire.rest.namespaces.video.VideoStreams.get_base_path: Java exposes the resource base path via getBasePath() for runtime URL building; Python encodes it as a class attribute so the getter has no Python counterpart
signalwire.rest.rest_client_builder.RestClientBuilder: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.rest_client_builder.RestClientBuilder.build: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.rest_client_builder.RestClientBuilder.project: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.rest_client_builder.RestClientBuilder.space: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.rest_client_builder.RestClientBuilder.token: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.rest_error.RestError: Java exception class — Python exposes SignalWireRestError under signalwire.rest._base; Java names it RestError for idiomatic reasons
signalwire.rest.rest_error.RestError.__init__: Java exception class — Python exposes SignalWireRestError under signalwire.rest._base; Java names it RestError for idiomatic reasons
signalwire.rest.rest_error.RestError.get_method: Java exception class — Python exposes SignalWireRestError under signalwire.rest._base; Java names it RestError for idiomatic reasons
signalwire.rest.rest_error.RestError.get_path: Java exception class — Python exposes SignalWireRestError under signalwire.rest._base; Java names it RestError for idiomatic reasons
signalwire.rest.rest_error.RestError.get_response_body: Java exception class — Python exposes SignalWireRestError under signalwire.rest._base; Java names it RestError for idiomatic reasons
signalwire.rest.rest_error.RestError.get_status_code: Java exception class — Python exposes SignalWireRestError under signalwire.rest._base; Java names it RestError for idiomatic reasons
signalwire.rest.rest_error.RestError.is_client_error: Java exception class — Python exposes SignalWireRestError under signalwire.rest._base; Java names it RestError for idiomatic reasons
signalwire.rest.rest_error.RestError.is_not_found: Java exception class — Python exposes SignalWireRestError under signalwire.rest._base; Java names it RestError for idiomatic reasons
signalwire.rest.rest_error.RestError.is_server_error: Java exception class — Python exposes SignalWireRestError under signalwire.rest._base; Java names it RestError for idiomatic reasons
signalwire.rest.rest_error.RestError.is_unauthorized: Java exception class — Python exposes SignalWireRestError under signalwire.rest._base; Java names it RestError for idiomatic reasons
signalwire.runtime.env_provider.EnvProvider: Java Lambda support — injectable env-var abstraction required because Java can't mutate System.getenv() at runtime
signalwire.runtime.execution_mode.ExecutionMode: Java Lambda support — distinguishes HTTP-server vs Lambda handler execution so AgentBase can skip HTTP bootstrap in serverless mode
signalwire.runtime.execution_mode.ExecutionMode.detect: Java Lambda support — distinguishes HTTP-server vs Lambda handler execution so AgentBase can skip HTTP bootstrap in serverless mode
signalwire.runtime.execution_mode.ExecutionMode.get_execution_mode: Java ExecutionMode exposes getExecutionMode() as a static helper on the enum; the canonical signalwire.core.logging_config.get_execution_mode is also projected as a free function via FREE_FUNCTION_PROJECTIONS, so the dual exposure is intentional
signalwire.runtime.execution_mode.ExecutionMode.is_serverless_mode: Java ExecutionMode exposes isServerlessMode() as a static helper on the enum; the canonical signalwire.utils.is_serverless_mode is also projected as a free function via FREE_FUNCTION_PROJECTIONS, so the dual exposure is intentional
signalwire.runtime.lambda.lambda_agent_handler.LambdaAgentHandler: Java Lambda support — AWS Lambda RequestStreamHandler adapter; Python ships a thin WSGI-level adapter instead
signalwire.runtime.lambda.lambda_agent_handler.LambdaAgentHandler.__init__: Java Lambda support — AWS Lambda RequestStreamHandler adapter; Python ships a thin WSGI-level adapter instead
signalwire.runtime.lambda.lambda_agent_handler.LambdaAgentHandler.handle: Java Lambda support — AWS Lambda RequestStreamHandler adapter; Python ships a thin WSGI-level adapter instead
signalwire.runtime.lambda.lambda_response.LambdaResponse: Java Lambda support — AWS Lambda RequestStreamHandler adapter; Python ships a thin WSGI-level adapter instead
signalwire.runtime.lambda.lambda_response.LambdaResponse.__init__: Java Lambda support — AWS Lambda RequestStreamHandler adapter; Python ships a thin WSGI-level adapter instead
signalwire.runtime.lambda.lambda_response.LambdaResponse.get_body: Java Lambda support — AWS Lambda RequestStreamHandler adapter; Python ships a thin WSGI-level adapter instead
signalwire.runtime.lambda.lambda_response.LambdaResponse.get_headers: Java Lambda support — AWS Lambda RequestStreamHandler adapter; Python ships a thin WSGI-level adapter instead
signalwire.runtime.lambda.lambda_response.LambdaResponse.get_status_code: Java Lambda support — AWS Lambda RequestStreamHandler adapter; Python ships a thin WSGI-level adapter instead
signalwire.runtime.lambda.lambda_response.LambdaResponse.is_base64_encoded: Java Lambda support — AWS Lambda RequestStreamHandler adapter; Python ships a thin WSGI-level adapter instead
signalwire.runtime.lambda.lambda_response.LambdaResponse.json: Java Lambda support — AWS Lambda RequestStreamHandler adapter; Python ships a thin WSGI-level adapter instead
signalwire.runtime.lambda.lambda_response.LambdaResponse.to_dict: Java Lambda support — AWS Lambda RequestStreamHandler adapter; Python ships a thin WSGI-level adapter instead
signalwire.runtime.lambda_url_resolver.LambdaUrlResolver: Java Lambda support — resolves the Lambda Function URL at runtime to populate AgentBase's self-URL (Python uses env vars directly)
signalwire.runtime.lambda_url_resolver.LambdaUrlResolver.__init__: Java Lambda support — resolves the Lambda Function URL at runtime to populate AgentBase's self-URL (Python uses env vars directly)
signalwire.runtime.lambda_url_resolver.LambdaUrlResolver.resolve_base_url: Java Lambda support — resolves the Lambda Function URL at runtime to populate AgentBase's self-URL (Python uses env vars directly)
signalwire.signalwire.Signalwire: Java-side holder class for the top-level signalwire.* free-function helpers; surface projects each static method as the canonical signalwire.<func> entry, the holder class itself remains a Java idiom
signalwire.signalwire.Signalwire.add_skill_directory: static method on the Java-side Signalwire holder class; surface projection emits the canonical signalwire.<func> entry, so the class-method form is a Java-idiom artifact
signalwire.signalwire.Signalwire.list_skills_with_params: static method on the Java-side Signalwire holder class; surface projection emits the canonical signalwire.<func> entry, so the class-method form is a Java-idiom artifact
signalwire.signalwire.Signalwire.register_skill: static method on the Java-side Signalwire holder class; surface projection emits the canonical signalwire.<func> entry, so the class-method form is a Java-idiom artifact
signalwire.signalwire.Signalwire.rest_client: static method on the Java-side Signalwire holder class; surface projection emits the canonical signalwire.<func> entry, so the class-method form is a Java-idiom artifact
signalwire.skills.builtin.api_ninja_trivia_skill.ApiNinjaTriviaSkill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.api_ninja_trivia_skill.ApiNinjaTriviaSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.api_ninja_trivia_skill.ApiNinjaTriviaSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.api_ninja_trivia_skill.ApiNinjaTriviaSkill.get_swaig_functions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.api_ninja_trivia_skill.ApiNinjaTriviaSkill.register_tools: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.api_ninja_trivia_skill.ApiNinjaTriviaSkill.setup: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.api_ninja_trivia_skill.ApiNinjaTriviaSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.custom_skills_skill.CustomSkillsSkill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.custom_skills_skill.CustomSkillsSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.custom_skills_skill.CustomSkillsSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.custom_skills_skill.CustomSkillsSkill.register_tools: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.custom_skills_skill.CustomSkillsSkill.setup: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.custom_skills_skill.CustomSkillsSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_serverless_skill.DatasphereServerlessSkill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_serverless_skill.DatasphereServerlessSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_serverless_skill.DatasphereServerlessSkill.get_global_data: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_serverless_skill.DatasphereServerlessSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_serverless_skill.DatasphereServerlessSkill.get_prompt_sections: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_serverless_skill.DatasphereServerlessSkill.get_swaig_functions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_serverless_skill.DatasphereServerlessSkill.register_tools: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_serverless_skill.DatasphereServerlessSkill.setup: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_serverless_skill.DatasphereServerlessSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_skill.DatasphereSkill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_skill.DatasphereSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_skill.DatasphereSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_skill.DatasphereSkill.register_tools: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_skill.DatasphereSkill.setup: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datasphere_skill.DatasphereSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datetime_skill.DatetimeSkill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datetime_skill.DatetimeSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datetime_skill.DatetimeSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datetime_skill.DatetimeSkill.get_prompt_sections: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datetime_skill.DatetimeSkill.register_tools: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datetime_skill.DatetimeSkill.setup: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.datetime_skill.DatetimeSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.mcp_gateway_skill.McpGatewaySkill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.mcp_gateway_skill.McpGatewaySkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.mcp_gateway_skill.McpGatewaySkill.get_global_data: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.mcp_gateway_skill.McpGatewaySkill.get_hints: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.mcp_gateway_skill.McpGatewaySkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.mcp_gateway_skill.McpGatewaySkill.get_prompt_sections: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.mcp_gateway_skill.McpGatewaySkill.register_tools: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.mcp_gateway_skill.McpGatewaySkill.setup: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.swml_transfer_skill.SwmlTransferSkill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.swml_transfer_skill.SwmlTransferSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.swml_transfer_skill.SwmlTransferSkill.get_hints: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.swml_transfer_skill.SwmlTransferSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.swml_transfer_skill.SwmlTransferSkill.get_prompt_sections: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.swml_transfer_skill.SwmlTransferSkill.get_swaig_functions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.swml_transfer_skill.SwmlTransferSkill.register_tools: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.swml_transfer_skill.SwmlTransferSkill.setup: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.builtin.swml_transfer_skill.SwmlTransferSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.claude_skills.skill.ClaudeSkillsSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.claude_skills.skill.ClaudeSkillsSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.claude_skills.skill.ClaudeSkillsSkill.get_prompt_sections: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.claude_skills.skill.ClaudeSkillsSkill.register_tools: Java skill exposes registerTools() as the typed entry point its SkillBase parent invokes; Python's SkillBase delegates this through a callable hook with a different name in the reference
signalwire.skills.claude_skills.skill.ClaudeSkillsSkill.setup: Java skill exposes setup() as the typed configure-on-attach hook on its SkillBase parent; Python's SkillBase calls a different lifecycle hook in the reference (register_tools is invoked once per attach instead)
signalwire.skills.claude_skills.skill.ClaudeSkillsSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.google_maps.skill.GoogleMapsSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.google_maps.skill.GoogleMapsSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.google_maps.skill.GoogleMapsSkill.register_tools: Java skill exposes registerTools() as the typed entry point its SkillBase parent invokes; Python's SkillBase delegates this through a callable hook with a different name in the reference
signalwire.skills.google_maps.skill.GoogleMapsSkill.setup: Java skill exposes setup() as the typed configure-on-attach hook on its SkillBase parent; Python's SkillBase calls a different lifecycle hook in the reference (register_tools is invoked once per attach instead)
signalwire.skills.info_gatherer.skill.InfoGathererSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.info_gatherer.skill.InfoGathererSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.info_gatherer.skill.InfoGathererSkill.get_prompt_sections: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.info_gatherer.skill.InfoGathererSkill.register_tools: Java skill exposes registerTools() as the typed entry point its SkillBase parent invokes; Python's SkillBase delegates this through a callable hook with a different name in the reference
signalwire.skills.info_gatherer.skill.InfoGathererSkill.setup: Java skill exposes setup() as the typed configure-on-attach hook on its SkillBase parent; Python's SkillBase calls a different lifecycle hook in the reference (register_tools is invoked once per attach instead)
signalwire.skills.info_gatherer.skill.InfoGathererSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.joke.skill.JokeSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.joke.skill.JokeSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.joke.skill.JokeSkill.get_swaig_functions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.joke.skill.JokeSkill.register_tools: Java skill exposes registerTools() as the typed entry point its SkillBase parent invokes; Python's SkillBase delegates this through a callable hook with a different name in the reference
signalwire.skills.joke.skill.JokeSkill.setup: Java skill exposes setup() as the typed configure-on-attach hook on its SkillBase parent; Python's SkillBase calls a different lifecycle hook in the reference (register_tools is invoked once per attach instead)
signalwire.skills.math.skill.MathSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.math.skill.MathSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.math.skill.MathSkill.register_tools: Java skill exposes registerTools() as the typed entry point its SkillBase parent invokes; Python's SkillBase delegates this through a callable hook with a different name in the reference
signalwire.skills.math.skill.MathSkill.setup: Java skill exposes setup() as the typed configure-on-attach hook on its SkillBase parent; Python's SkillBase calls a different lifecycle hook in the reference (register_tools is invoked once per attach instead)
signalwire.skills.math.skill.MathSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.register_tools: Java skill exposes registerTools() as the typed entry point its SkillBase parent invokes; Python's SkillBase delegates this through a callable hook with a different name in the reference
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.setup: Java skill exposes setup() as the typed configure-on-attach hook on its SkillBase parent; Python's SkillBase calls a different lifecycle hook in the reference (register_tools is invoked once per attach instead)
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.get_swaig_functions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.register_tools: Java skill exposes registerTools() as the typed entry point its SkillBase parent invokes; Python's SkillBase delegates this through a callable hook with a different name in the reference
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.setup: Java skill exposes setup() as the typed configure-on-attach hook on its SkillBase parent; Python's SkillBase calls a different lifecycle hook in the reference (register_tools is invoked once per attach instead)
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.registry.CustomSkillsSkill.register_tools: Java CustomSkillsSkill is the registry-wired pseudo-skill that materialises skill-directory entries; Python represents the same logic in skills.registry.SkillRegistry without exposing a Skill subclass
signalwire.skills.registry.CustomSkillsSkill.setup: Java CustomSkillsSkill is the registry-wired pseudo-skill that materialises skill-directory entries; Python represents the same logic in skills.registry.SkillRegistry without exposing a Skill subclass
signalwire.skills.registry.SkillRegistry.get: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.registry.SkillRegistry.get_external_paths: Java SkillRegistry exposes getExternalPaths() as a diagnostics accessor for the registry's loaded paths; Python tracks the same state internally without exposing it
signalwire.skills.registry.SkillRegistry.has: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.registry.SkillRegistry.list: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.registry.SkillRegistry.register: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.registry.SkillRegistry.unregister: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.spider.skill.SpiderSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.spider.skill.SpiderSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.spider.skill.SpiderSkill.register_tools: Java skill exposes registerTools() as the typed entry point its SkillBase parent invokes; Python's SkillBase delegates this through a callable hook with a different name in the reference
signalwire.skills.spider.skill.SpiderSkill.setup: Java skill exposes setup() as the typed configure-on-attach hook on its SkillBase parent; Python's SkillBase calls a different lifecycle hook in the reference (register_tools is invoked once per attach instead)
signalwire.skills.spider.skill.SpiderSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.weather_api.skill.WeatherApiSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.weather_api.skill.WeatherApiSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.weather_api.skill.WeatherApiSkill.get_swaig_functions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.weather_api.skill.WeatherApiSkill.register_tools: Java skill exposes registerTools() as the typed entry point its SkillBase parent invokes; Python's SkillBase delegates this through a callable hook with a different name in the reference
signalwire.skills.weather_api.skill.WeatherApiSkill.setup: Java skill exposes setup() as the typed configure-on-attach hook on its SkillBase parent; Python's SkillBase calls a different lifecycle hook in the reference (register_tools is invoked once per attach instead)
signalwire.skills.web_search.skill.WebSearchSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.web_search.skill.WebSearchSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.web_search.skill.WebSearchSkill.get_version: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.web_search.skill.WebSearchSkill.register_tools: Java skill exposes registerTools() as the typed entry point its SkillBase parent invokes; Python's SkillBase delegates this through a callable hook with a different name in the reference
signalwire.skills.web_search.skill.WebSearchSkill.setup: Java skill exposes setup() as the typed configure-on-attach hook on its SkillBase parent; Python's SkillBase calls a different lifecycle hook in the reference (register_tools is invoked once per attach instead)
signalwire.skills.web_search.skill.WebSearchSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.wikipedia_search.skill.WikipediaSearchSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.wikipedia_search.skill.WikipediaSearchSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.wikipedia_search.skill.WikipediaSearchSkill.register_tools: Java skill exposes registerTools() as the typed entry point its SkillBase parent invokes; Python's SkillBase delegates this through a callable hook with a different name in the reference
signalwire.skills.wikipedia_search.skill.WikipediaSearchSkill.setup: Java skill exposes setup() as the typed configure-on-attach hook on its SkillBase parent; Python's SkillBase calls a different lifecycle hook in the reference (register_tools is invoked once per attach instead)
signalwire.swaig.tool_definition.ToolDefinition: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swaig.tool_definition.ToolDefinition.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swaig.tool_definition.ToolDefinition.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swaig.tool_definition.ToolDefinition.get_extra_fields: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swaig.tool_definition.ToolDefinition.get_handler: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swaig.tool_definition.ToolDefinition.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swaig.tool_definition.ToolDefinition.get_parameters: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swaig.tool_definition.ToolDefinition.has_handler: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swaig.tool_definition.ToolDefinition.is_secure: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swaig.tool_definition.ToolDefinition.set_extra_fields: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swaig.tool_definition.ToolDefinition.set_secure: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swaig.tool_definition.ToolDefinition.to_swaig_function: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swaig.tool_handler.ToolHandler: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.Schema: idiomatic Java surface extension (singleton sidecar; canonical SchemaUtils ships separately at signalwire.utils.schema_utils.SchemaUtils)
signalwire.swml.Schema.get_instance: idiomatic Java surface extension (singleton accessor) not present in Python
signalwire.swml.Schema.get_verb: idiomatic Java surface extension (singleton accessor) not present in Python
signalwire.swml.Schema.get_verb_names: idiomatic Java surface extension (singleton accessor) not present in Python
signalwire.swml.Schema.is_valid_verb: idiomatic Java surface extension (singleton accessor) not present in Python
signalwire.swml.Schema.verb_count: idiomatic Java surface extension (singleton accessor) not present in Python
signalwire.swml.document.Document: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.document.Document.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.document.Document.add_section: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.document.Document.add_verb: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.document.Document.add_verb_to_section: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.document.Document.get_section_verbs: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.document.Document.get_verbs: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.document.Document.has_section: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.document.Document.render: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.document.Document.render_pretty: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.document.Document.reset: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.document.Document.to_dict: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.schema.Schema: Java's swml.Schema is a singleton sidecar with verb-introspection helpers (getInstance/getVerb/etc.); Python's reference puts the equivalent surface under utils.schema_utils.SchemaUtils
signalwire.swml.schema.Schema.get_instance: Java's swml.Schema is a singleton sidecar with verb-introspection helpers (getInstance/getVerb/etc.); Python's reference puts the equivalent surface under utils.schema_utils.SchemaUtils
signalwire.swml.schema.Schema.get_verb: Java's swml.Schema is a singleton sidecar with verb-introspection helpers (getInstance/getVerb/etc.); Python's reference puts the equivalent surface under utils.schema_utils.SchemaUtils
signalwire.swml.schema.Schema.get_verb_names: Java's swml.Schema is a singleton sidecar with verb-introspection helpers (getInstance/getVerb/etc.); Python's reference puts the equivalent surface under utils.schema_utils.SchemaUtils
signalwire.swml.schema.Schema.is_valid_verb: Java's swml.Schema is a singleton sidecar with verb-introspection helpers (getInstance/getVerb/etc.); Python's reference puts the equivalent surface under utils.schema_utils.SchemaUtils
signalwire.swml.schema.Schema.verb_count: Java's swml.Schema is a singleton sidecar with verb-introspection helpers (getInstance/getVerb/etc.); Python's reference puts the equivalent surface under utils.schema_utils.SchemaUtils
signalwire.swml.schema_utils_verb_info.SchemaUtilsVerbInfo: Java's SchemaUtilsVerbInfo is a typed value-class that wraps verb-introspection results; Python represents the same shape as a tuple/dict with no class wrapper
signalwire.swml.service.Service: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.ai: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.amazon_bedrock: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.answer: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.cond: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.connect: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.define_tool: Java exposes define_tool on Service (the SWAIG host) plus on AgentBase for chained returns; Python only defines it on AgentBase
signalwire.swml.service.Service.define_tools: Java's bulk-add convenience for ToolDefinition lists — Python adds tools one at a time
signalwire.swml.service.Service.denoise: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.detect_machine: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.enter_queue: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.execute: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.get_all_functions: Java's swml.Service base class exposes function-registry and request-handler methods (getAllFunctions/onSwmlRequest/etc.); Python's reference distributes the same surface across SWMLService + ToolRegistry + WebMixin
signalwire.swml.service.Service.get_auth_password: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.get_auth_user: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.get_basic_auth_credentials: Java's swml.Service base class exposes function-registry and request-handler methods (getAllFunctions/onSwmlRequest/etc.); Python's reference distributes the same surface across SWMLService + ToolRegistry + WebMixin
signalwire.swml.service.Service.get_basic_auth_credentials_with_source: Java's swml.Service base class exposes function-registry and request-handler methods (getAllFunctions/onSwmlRequest/etc.); Python's reference distributes the same surface across SWMLService + ToolRegistry + WebMixin
signalwire.swml.service.Service.get_document: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.get_function: Java's swml.Service base class exposes function-registry and request-handler methods (getAllFunctions/onSwmlRequest/etc.); Python's reference distributes the same surface across SWMLService + ToolRegistry + WebMixin
signalwire.swml.service.Service.get_registered_swaig_functions: Java public accessor for raw DataMap entries — used by introspection (CLI --list-tools file mode) and audit harnesses; Python exposes this via a private attribute and helper
signalwire.swml.service.Service.get_registered_tools: Java public accessor for the tool-definition map — used by CLI --list-tools and audit harnesses; Python exposes this via a private attribute and helper
signalwire.swml.service.Service.goto_label: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.hangup: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.has_function: Java's swml.Service base class exposes function-registry and request-handler methods (getAllFunctions/onSwmlRequest/etc.); Python's reference distributes the same surface across SWMLService + ToolRegistry + WebMixin
signalwire.swml.service.Service.join_conference: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.join_room: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.label: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.list_tool_names: Java public accessor returning insertion-ordered tool names — used by CLI --list-tools introspection; Python returns from the registry's keys() directly
signalwire.swml.service.Service.live_transcribe: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.live_translate: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.on_function_call: Java exposes on_function_call on Service (the SWAIG host) and AgentBase; Python only defines it on AgentBase
signalwire.swml.service.Service.on_request: Java's swml.Service base class exposes function-registry and request-handler methods (getAllFunctions/onSwmlRequest/etc.); Python's reference distributes the same surface across SWMLService + ToolRegistry + WebMixin
signalwire.swml.service.Service.on_swml_request: Java's swml.Service base class exposes function-registry and request-handler methods (getAllFunctions/onSwmlRequest/etc.); Python's reference distributes the same surface across SWMLService + ToolRegistry + WebMixin
signalwire.swml.service.Service.pay: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.play: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.prompt: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.receive_fax: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.record: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.record_call: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.register_swaig_function: Java exposes register_swaig_function on Service (DataMap registration) and AgentBase; Python only defines it on AgentBase
signalwire.swml.service.Service.remove_function: Java's swml.Service base class exposes function-registry and request-handler methods (getAllFunctions/onSwmlRequest/etc.); Python's reference distributes the same surface across SWMLService + ToolRegistry + WebMixin
signalwire.swml.service.Service.request: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.return_verb: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.schema_utils: Java's swml.Service base class exposes function-registry and request-handler methods (getAllFunctions/onSwmlRequest/etc.); Python's reference distributes the same surface across SWMLService + ToolRegistry + WebMixin
signalwire.swml.service.Service.send_digits: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.send_fax: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.send_sms: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.serve: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.set: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.sip_refer: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.sleep: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.stop: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.stop_denoise: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.stop_record_call: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.stop_tap: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.switch_verb: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.tap: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.transfer: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.unset: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.user_event: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.validate_basic_auth: Java's swml.Service base class exposes function-registry and request-handler methods (getAllFunctions/onSwmlRequest/etc.); Python's reference distributes the same surface across SWMLService + ToolRegistry + WebMixin
signalwire.utils.schema_utils.SchemaUtils.generate_method_body: Python-source codegen helper; canonical Python signatures filter this method out (Python-only output shape)
signalwire.utils.schema_utils.SchemaUtils.generate_method_signature: Python-source codegen helper; canonical Python signatures filter this method out (Python-only output shape)
signalwire.utils.schema_utils.SchemaUtils.is_full_validation_available: @property in Python (filtered as bool-returning attribute); ports expose it as an explicit method per spec
signalwire.utils.schema_utils.SchemaValidationError.get_errors: Java SchemaValidationError exposes structured error data via typed getters; Python's reference uses public attribute access on the exception
signalwire.utils.schema_utils.SchemaValidationError.get_verb_name: Java SchemaValidationError exposes structured error data via typed getters; Python's reference uses public attribute access on the exception
signalwire.utils.url_validator.UrlValidator: Java exposes URL validation via a UrlValidator class for namespace cohesion; Python uses a free function. The free-function form is projected into the same module via FREE_FUNCTION_PROJECTIONS
signalwire.utils.url_validator.UrlValidator.validate_url: Java's UrlValidator.validateUrl is the static class form; the canonical free function is also projected via FREE_FUNCTION_PROJECTIONS, so the dual exposure is intentional
