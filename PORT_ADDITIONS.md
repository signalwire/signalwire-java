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
signalwire.core.agent_base.AgentBase.get_dynamic_config_callback: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_global_data: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_host: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_mcp_servers: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_normalised_route: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_on_summary_callback: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_port: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.get_prompt: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
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
signalwire.core.agent_base.AgentBase.prompt_add_section: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.prompt_add_subsection: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.prompt_add_to_section: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.prompt_has_section: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.register_swaig_function: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.remove_skill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.render_swml: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.render_swml_json: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.reset_contexts: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
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
signalwire.core.agent_base.AgentBase.set_prompt_text: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.set_pronunciations: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.agent_base.AgentBase.update_global_data: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
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
signalwire.core.security.session_manager.SessionManager.create_token: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.skill_manager.SkillManager.add_skill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.skill_manager.SkillManager.cleanup: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.skill_manager.SkillManager.list_skills: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.core.skill_manager.SkillManager.remove_skill: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
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
signalwire.relay.client.RelayClient.builder: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.client.RelayClient.get_contexts: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.client.RelayClient.get_project: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.client.RelayClient.get_space: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.client.RelayClient.is_connected: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.client.RelayClient.on_event: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
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
signalwire.relay.message.Message.set_tags: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.set_to_number: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.update_from_event: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.message.Message.wait_for_completion: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.relay_client_builder.RelayClientBuilder: Java builder pattern — RelayClient.builder() is the idiomatic constructor
signalwire.relay.relay_client_builder.RelayClientBuilder.build: Java builder pattern — RelayClient.builder() is the idiomatic constructor
signalwire.relay.relay_client_builder.RelayClientBuilder.contexts: Java builder pattern — RelayClient.builder() is the idiomatic constructor
signalwire.relay.relay_client_builder.RelayClientBuilder.project: Java builder pattern — RelayClient.builder() is the idiomatic constructor
signalwire.relay.relay_client_builder.RelayClientBuilder.space: Java builder pattern — RelayClient.builder() is the idiomatic constructor
signalwire.relay.relay_client_builder.RelayClientBuilder.token: Java builder pattern — RelayClient.builder() is the idiomatic constructor
signalwire.relay.relay_event_authorization_state_event.RelayEventAuthorizationStateEvent: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.relay_event_authorization_state_event.RelayEventAuthorizationStateEvent.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.relay.relay_event_authorization_state_event.RelayEventAuthorizationStateEvent.get_authorization_state: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest._base.CrudResource.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest._base.CrudResource.get_base_path: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest._base.CrudResource.get_http_client: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.call_handler.PhoneCallHandler.__repr__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.call_handler.PhoneCallHandler.wire_value: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
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
signalwire.rest.client.RestClient.messaging: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.number_lookup: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.phone_numbers: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.project: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.pub_sub: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.queues: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.recordings: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.sip: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.streams: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.swml: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.transcriptions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.client.RestClient.video: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.billing_namespace.BillingNamespace: Java's BillingNamespace exposes the billing API one-liner; Python accesses billing via the compat namespace
signalwire.rest.namespaces.billing_namespace.BillingNamespace.__init__: Java's BillingNamespace exposes the billing API one-liner; Python accesses billing via the compat namespace
signalwire.rest.namespaces.billing_namespace.BillingNamespace.invoices: Java's BillingNamespace exposes the billing API one-liner; Python accesses billing via the compat namespace
signalwire.rest.namespaces.billing_namespace.BillingNamespace.usage: Java's BillingNamespace exposes the billing API one-liner; Python accesses billing via the compat namespace
signalwire.rest.namespaces.calling.CallingNamespace.calls: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.campaign_namespace.CampaignNamespace: Java's CampaignNamespace is the equivalent of Python's RegistryNamespace for 10DLC/TCR registration
signalwire.rest.namespaces.campaign_namespace.CampaignNamespace.__init__: Java's CampaignNamespace is the equivalent of Python's RegistryNamespace for 10DLC/TCR registration
signalwire.rest.namespaces.campaign_namespace.CampaignNamespace.assignments: Java's CampaignNamespace is the equivalent of Python's RegistryNamespace for 10DLC/TCR registration
signalwire.rest.namespaces.campaign_namespace.CampaignNamespace.brands: Java's CampaignNamespace is the equivalent of Python's RegistryNamespace for 10DLC/TCR registration
signalwire.rest.namespaces.campaign_namespace.CampaignNamespace.campaigns: Java's CampaignNamespace is the equivalent of Python's RegistryNamespace for 10DLC/TCR registration
signalwire.rest.namespaces.campaign_namespace.CampaignNamespace.orders: Java's CampaignNamespace is the equivalent of Python's RegistryNamespace for 10DLC/TCR registration
signalwire.rest.namespaces.chat_namespace.ChatNamespace: Java's ChatNamespace is the namespace-level accessor for the Chat API; Python has a flat ChatResource class
signalwire.rest.namespaces.chat_namespace.ChatNamespace.__init__: Java's ChatNamespace is the namespace-level accessor for the Chat API; Python has a flat ChatResource class
signalwire.rest.namespaces.chat_namespace.ChatNamespace.channels: Java's ChatNamespace is the namespace-level accessor for the Chat API; Python has a flat ChatResource class
signalwire.rest.namespaces.chat_namespace.ChatNamespace.members: Java's ChatNamespace is the namespace-level accessor for the Chat API; Python has a flat ChatResource class
signalwire.rest.namespaces.chat_namespace.ChatNamespace.messages: Java's ChatNamespace is the namespace-level accessor for the Chat API; Python has a flat ChatResource class
signalwire.rest.namespaces.compat.CompatNamespace.accounts: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.applications: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.calls: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.conferences: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.messages: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.queues: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.recordings: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.sip_credential_lists: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.sip_domains: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.sip_ip_access_control_lists: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compat.CompatNamespace.transcriptions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.compliance_namespace.ComplianceNamespace: Java's ComplianceNamespace exposes TCR/carrier compliance APIs; Python routes these through RegistryNamespace
signalwire.rest.namespaces.compliance_namespace.ComplianceNamespace.__init__: Java's ComplianceNamespace exposes TCR/carrier compliance APIs; Python routes these through RegistryNamespace
signalwire.rest.namespaces.compliance_namespace.ComplianceNamespace.cnam_registrations: Java's ComplianceNamespace exposes TCR/carrier compliance APIs; Python routes these through RegistryNamespace
signalwire.rest.namespaces.compliance_namespace.ComplianceNamespace.shaken_stir: Java's ComplianceNamespace exposes TCR/carrier compliance APIs; Python routes these through RegistryNamespace
signalwire.rest.namespaces.conference_namespace.ConferenceNamespace: Java's ConferenceNamespace wraps the conference API with a namespace-level accessor; Python routes through CompatConferences
signalwire.rest.namespaces.conference_namespace.ConferenceNamespace.__init__: Java's ConferenceNamespace wraps the conference API with a namespace-level accessor; Python routes through CompatConferences
signalwire.rest.namespaces.conference_namespace.ConferenceNamespace.conferences: Java's ConferenceNamespace wraps the conference API with a namespace-level accessor; Python routes through CompatConferences
signalwire.rest.namespaces.conference_namespace.ConferenceNamespace.participants: Java's ConferenceNamespace wraps the conference API with a namespace-level accessor; Python routes through CompatConferences
signalwire.rest.namespaces.datasphere.DatasphereNamespace.documents: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.datasphere.DatasphereNamespace.search: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.fabric.FabricNamespace.addresses: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.fabric.FabricNamespace.resources: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.fabric.FabricNamespace.subscribers: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.fax_namespace.FaxNamespace: Java's FaxNamespace exposes fax APIs; Python routes through CompatFaxes under the compat namespace
signalwire.rest.namespaces.fax_namespace.FaxNamespace.__init__: Java's FaxNamespace exposes fax APIs; Python routes through CompatFaxes under the compat namespace
signalwire.rest.namespaces.fax_namespace.FaxNamespace.faxes: Java's FaxNamespace exposes fax APIs; Python routes through CompatFaxes under the compat namespace
signalwire.rest.namespaces.messaging_namespace.MessagingNamespace: Java's MessagingNamespace is the namespace-level accessor for messaging; Python routes through CompatMessages
signalwire.rest.namespaces.messaging_namespace.MessagingNamespace.__init__: Java's MessagingNamespace is the namespace-level accessor for messaging; Python routes through CompatMessages
signalwire.rest.namespaces.messaging_namespace.MessagingNamespace.messages: Java's MessagingNamespace is the namespace-level accessor for messaging; Python routes through CompatMessages
signalwire.rest.namespaces.messaging_namespace.MessagingNamespace.send: Java's MessagingNamespace is the namespace-level accessor for messaging; Python routes through CompatMessages
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
signalwire.rest.namespaces.project.ProjectNamespace.update: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.pub_sub_namespace.PubSubNamespace: Java's PubSubNamespace wraps the Pub/Sub API; Python exposes a flat PubSubResource
signalwire.rest.namespaces.pub_sub_namespace.PubSubNamespace.__init__: Java's PubSubNamespace wraps the Pub/Sub API; Python exposes a flat PubSubResource
signalwire.rest.namespaces.pub_sub_namespace.PubSubNamespace.channels: Java's PubSubNamespace wraps the Pub/Sub API; Python exposes a flat PubSubResource
signalwire.rest.namespaces.pub_sub_namespace.PubSubNamespace.publish: Java's PubSubNamespace wraps the Pub/Sub API; Python exposes a flat PubSubResource
signalwire.rest.namespaces.queue_namespace.QueueNamespace: Java's QueueNamespace wraps the queues API; Python exposes a flat QueuesResource
signalwire.rest.namespaces.queue_namespace.QueueNamespace.__init__: Java's QueueNamespace wraps the queues API; Python exposes a flat QueuesResource
signalwire.rest.namespaces.queue_namespace.QueueNamespace.queues: Java's QueueNamespace wraps the queues API; Python exposes a flat QueuesResource
signalwire.rest.namespaces.recording_namespace.RecordingNamespace: Java's RecordingNamespace wraps the recordings API; Python exposes a flat RecordingsResource
signalwire.rest.namespaces.recording_namespace.RecordingNamespace.__init__: Java's RecordingNamespace wraps the recordings API; Python exposes a flat RecordingsResource
signalwire.rest.namespaces.recording_namespace.RecordingNamespace.recordings: Java's RecordingNamespace wraps the recordings API; Python exposes a flat RecordingsResource
signalwire.rest.namespaces.sip_namespace.SipNamespace: Java's SipNamespace exposes SIP-endpoint configuration; Python routes through SubscribersResource on the Fabric namespace
signalwire.rest.namespaces.sip_namespace.SipNamespace.__init__: Java's SipNamespace exposes SIP-endpoint configuration; Python routes through SubscribersResource on the Fabric namespace
signalwire.rest.namespaces.sip_namespace.SipNamespace.endpoints: Java's SipNamespace exposes SIP-endpoint configuration; Python routes through SubscribersResource on the Fabric namespace
signalwire.rest.namespaces.sip_namespace.SipNamespace.profiles: Java's SipNamespace exposes SIP-endpoint configuration; Python routes through SubscribersResource on the Fabric namespace
signalwire.rest.namespaces.stream_namespace.StreamNamespace: Java's StreamNamespace wraps the media-stream API; Python routes through the compat namespace
signalwire.rest.namespaces.stream_namespace.StreamNamespace.__init__: Java's StreamNamespace wraps the media-stream API; Python routes through the compat namespace
signalwire.rest.namespaces.stream_namespace.StreamNamespace.streams: Java's StreamNamespace wraps the media-stream API; Python routes through the compat namespace
signalwire.rest.namespaces.swml_namespace.SwmlNamespace: Java's SwmlNamespace exposes SWML-endpoint management as a dedicated namespace; Python routes through the SwmlWebhooks resource on the Fabric namespace
signalwire.rest.namespaces.swml_namespace.SwmlNamespace.__init__: Java's SwmlNamespace exposes SWML-endpoint management as a dedicated namespace; Python routes through the SwmlWebhooks resource on the Fabric namespace
signalwire.rest.namespaces.swml_namespace.SwmlNamespace.scripts: Java's SwmlNamespace exposes SWML-endpoint management as a dedicated namespace; Python routes through the SwmlWebhooks resource on the Fabric namespace
signalwire.rest.namespaces.transcription_namespace.TranscriptionNamespace: Java's TranscriptionNamespace wraps the transcription API as a dedicated namespace; Python routes through the compat namespace
signalwire.rest.namespaces.transcription_namespace.TranscriptionNamespace.__init__: Java's TranscriptionNamespace wraps the transcription API as a dedicated namespace; Python routes through the compat namespace
signalwire.rest.namespaces.transcription_namespace.TranscriptionNamespace.transcriptions: Java's TranscriptionNamespace wraps the transcription API as a dedicated namespace; Python routes through the compat namespace
signalwire.rest.namespaces.video.VideoNamespace.recordings: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.video.VideoNamespace.room_sessions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.rest.namespaces.video.VideoNamespace.rooms: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
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
signalwire.skills.claude_skills.skill.ClaudeSkillsSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.google_maps.skill.GoogleMapsSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.google_maps.skill.GoogleMapsSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.info_gatherer.skill.InfoGathererSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.info_gatherer.skill.InfoGathererSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.info_gatherer.skill.InfoGathererSkill.get_prompt_sections: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.info_gatherer.skill.InfoGathererSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.joke.skill.JokeSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.joke.skill.JokeSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.joke.skill.JokeSkill.get_swaig_functions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.math.skill.MathSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.math.skill.MathSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.math.skill.MathSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.get_swaig_functions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.registry.SkillRegistry.get: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.registry.SkillRegistry.has: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.registry.SkillRegistry.list: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.registry.SkillRegistry.register: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.registry.SkillRegistry.unregister: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.spider.skill.SpiderSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.spider.skill.SpiderSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.spider.skill.SpiderSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.weather_api.skill.WeatherApiSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.weather_api.skill.WeatherApiSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.weather_api.skill.WeatherApiSkill.get_swaig_functions: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.web_search.skill.WebSearchSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.web_search.skill.WebSearchSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.web_search.skill.WebSearchSkill.get_version: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.web_search.skill.WebSearchSkill.supports_multiple_instances: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.wikipedia_search.skill.WikipediaSearchSkill.get_description: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.skills.wikipedia_search.skill.WikipediaSearchSkill.get_name: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
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
signalwire.swml.Schema: idiomatic Java surface extension (singleton sidecar; canonical SchemaUtils ships separately at signalwire.utils.schema_utils.SchemaUtils)
signalwire.swml.Schema.get_instance: idiomatic Java surface extension (singleton accessor) not present in Python
signalwire.swml.Schema.get_verb: idiomatic Java surface extension (singleton accessor) not present in Python
signalwire.swml.Schema.get_verb_names: idiomatic Java surface extension (singleton accessor) not present in Python
signalwire.swml.Schema.is_valid_verb: idiomatic Java surface extension (singleton accessor) not present in Python
signalwire.swml.Schema.verb_count: idiomatic Java surface extension (singleton accessor) not present in Python
signalwire.utils.schema_utils.SchemaUtils.generate_method_signature: Python-source codegen helper; canonical Python signatures filter this method out (Python-only output shape)
signalwire.utils.schema_utils.SchemaUtils.generate_method_body: Python-source codegen helper; canonical Python signatures filter this method out (Python-only output shape)
signalwire.utils.schema_utils.SchemaUtils.is_full_validation_available: @property in Python (filtered as bool-returning attribute); ports expose it as an explicit method per spec
signalwire.swml.service.Service: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.__init__: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.ai: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.amazon_bedrock: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.answer: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.cond: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.connect: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.denoise: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.detect_machine: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.enter_queue: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.execute: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.get_auth_password: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.get_auth_user: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.get_document: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.goto_label: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.hangup: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.join_conference: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.join_room: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.label: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.live_transcribe: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.live_translate: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.pay: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.play: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.prompt: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.receive_fax: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.record: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.record_call: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.request: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
signalwire.swml.service.Service.return_verb: idiomatic Java surface extension (builder, getter/setter, or overload) not present in Python
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

# --- Audit-harness / Java-idiom additions appended 2026-04-28 ---

signalwire.core.agent_base.AgentBase.run: Java AgentBase exposes Service.run() under the AgentBase namespace — Python inherits via mixin lookup; the Java surface generator records it as an explicit override
signalwire.relay.client.RelayClient.internal_web_socket: Java's java-websocket-backed inner class surfaced as a public reflective member; Python uses an aiohttp ClientSession kept as a private attribute
signalwire.relay.client.RelayClient.on_close: Java WebSocketClient lifecycle override — required by the Java-WebSocket library and surfaced as public; Python's transport is closed via aiohttp without this method
signalwire.relay.client.RelayClient.on_error: Java WebSocketClient lifecycle override surfaced public; Python handles errors via aiohttp exception flow
signalwire.relay.client.RelayClient.on_open: Java WebSocketClient lifecycle override surfaced public; Python performs the same step as part of the async connect coroutine
signalwire.relay.client.RelayClient.send_raw: Java helper that sends a raw JSON-RPC frame on the underlying socket — used by examples/RelayAuditHarness.java to satisfy the audit fixture's method=signalwire.event filter; Python tests inject equivalent frames via internal helpers
signalwire.rest.client.RestClient.with_base_url: Java factory for pointing the REST client at an explicit base URL (loopback fixture, plain HTTP) — used by examples/RestAuditHarness.java; Python tests use httpx_mock to redirect transport instead
signalwire.swml.service.Service.define_tool: Java exposes define_tool on Service (the SWAIG host) plus on AgentBase for chained returns; Python only defines it on AgentBase
signalwire.swml.service.Service.define_tools: Java's bulk-add convenience for ToolDefinition lists — Python adds tools one at a time
signalwire.swml.service.Service.get_registered_swaig_functions: Java public accessor for raw DataMap entries — used by introspection (CLI --list-tools file mode) and audit harnesses; Python exposes this via a private attribute and helper
signalwire.swml.service.Service.get_registered_tools: Java public accessor for the tool-definition map — used by CLI --list-tools and audit harnesses; Python exposes this via a private attribute and helper
signalwire.swml.service.Service.list_tool_names: Java public accessor returning insertion-ordered tool names — used by CLI --list-tools introspection; Python returns from the registry's keys() directly
signalwire.swml.service.Service.on_function_call: Java exposes on_function_call on Service (the SWAIG host) and AgentBase; Python only defines it on AgentBase
signalwire.swml.service.Service.register_swaig_function: Java exposes register_swaig_function on Service (DataMap registration) and AgentBase; Python only defines it on AgentBase

# --- mock-relay backed test helpers appended 2026-04-30 ---

signalwire.relay.call.Call.collect_digits: Java overload of collect() that takes a digits-shaped Map directly + explicit control_id — Python uses keyword arguments instead of overloads
signalwire.relay.call.Call.detect_with: Java overload of detect() that takes the detect config + explicit control_id — keyword-args translation
signalwire.relay.call.Call.record_audio: Java overload of record() that wraps the audio config as record={audio:...} — keyword-args translation
signalwire.relay.client.RelayClient.get_authorization_state: Java accessor for the SDK's stored authorization_state blob (used by reconnect-with-protocol tests); Python reads ._authorization_state directly
signalwire.relay.client.RelayClient.get_relay_protocol: Java getter for the protocol identifier issued by signalwire.connect; Python uses the relay_protocol property
signalwire.relay.client.RelayClient.set_relay_protocol: Java test-only setter to seed a stored protocol before connect (simulates reconnect-with-protocol); Python tests assign client._relay_protocol directly
signalwire.relay.message.Message.set_state: Java setter for the message state field — used internally by sendMessage() to seed initial 'queued' state; Python's __init__ sets it
signalwire.relay.relay_client_builder.RelayClientBuilder.host: Java builder alias for space() — matches the Python keyword argument name for adjacency
signalwire.relay.relay_client_builder.RelayClientBuilder.jwt_token: Java builder for the JWT-only auth path — Python takes jwt_token as a kwarg to RelayClient.__init__
