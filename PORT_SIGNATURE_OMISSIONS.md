# PORT_SIGNATURE_OMISSIONS.md

Documented signature divergences between this Java port and the Python
reference. Names-only divergences live in PORT_OMISSIONS.md /
PORT_ADDITIONS.md and are inherited automatically.

Excused divergences fall into:

1. **Idiom-level** (deliberate, not fixable without breaking Java API style):
   - Java constructors take Java-shaped Builder/Options objects, not kwargs.
   - Java methods return ``this`` for fluent chaining; Python returns None.
   - Java has no defaults — all parameters are required.

2. **Port maintenance backlog** (tracked here; will be reduced as the Java
   port catches up to Python signature parity).


## Idiom: Java constructors

signalwire.agent_server.AgentServer.__init__: Java constructor signature follows Java conventions
signalwire.cli.SwaigTest.__init__: Java constructor signature follows Java conventions
signalwire.cli.simulation.mock_env.ServerlessSimulator.__init__: Java constructor signature follows Java conventions
signalwire.core.contexts.ContextBuilder.__init__: Java constructor signature follows Java conventions
signalwire.core.contexts.GatherInfo.__init__: Java constructor signature follows Java conventions
signalwire.core.contexts.GatherQuestion.__init__: Java constructor signature follows Java conventions
signalwire.core.function_result.FunctionResult.__init__: Java constructor signature follows Java conventions
signalwire.core.security.session_manager.SessionManager.__init__: Java constructor signature follows Java conventions
signalwire.core.skill_manager.SkillManager.__init__: Java constructor signature follows Java conventions
signalwire.logging.Logger.__init__: Java constructor signature follows Java conventions
signalwire.prefabs.concierge.ConciergeAgent.__init__: Java constructor signature follows Java conventions
signalwire.prefabs.faq_bot.FAQBotAgent.__init__: Java constructor signature follows Java conventions
signalwire.prefabs.info_gatherer.InfoGathererAgent.__init__: Java constructor signature follows Java conventions
signalwire.prefabs.receptionist.ReceptionistAgent.__init__: Java constructor signature follows Java conventions
signalwire.prefabs.survey.SurveyAgent.__init__: Java constructor signature follows Java conventions
signalwire.relay.call.AIAction.__init__: Java constructor signature follows Java conventions
signalwire.relay.call.Action.__init__: Java constructor signature follows Java conventions
signalwire.relay.call.Call.__init__: Java constructor signature follows Java conventions
signalwire.relay.call.CollectAction.__init__: Java constructor signature follows Java conventions
signalwire.relay.call.DetectAction.__init__: Java constructor signature follows Java conventions
signalwire.relay.call.PayAction.__init__: Java constructor signature follows Java conventions
signalwire.relay.call.PlayAction.__init__: Java constructor signature follows Java conventions
signalwire.relay.call.RecordAction.__init__: Java constructor signature follows Java conventions
signalwire.relay.call.StreamAction.__init__: Java constructor signature follows Java conventions
signalwire.relay.call.TapAction.__init__: Java constructor signature follows Java conventions
signalwire.relay.call.TranscribeAction.__init__: Java constructor signature follows Java conventions
signalwire.relay.event.RelayEvent.__init__: Java constructor signature follows Java conventions
signalwire.relay.message.Message.__init__: Java constructor signature follows Java conventions
signalwire.rest.RestError.__init__: Java constructor signature follows Java conventions
signalwire.rest._base.HttpClient.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.BillingNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.CampaignNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.ChatNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.ComplianceNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.ConferenceNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.FaxNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.MessagingNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.NumberLookupNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.PubSubNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.QueueNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.RecordingNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.SipNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.StreamNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.SwmlNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.TranscriptionNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.calling.CallingNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.compat.CompatNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.datasphere.DatasphereNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.fabric.FabricNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.project.ProjectNamespace.__init__: Java constructor signature follows Java conventions
signalwire.rest.namespaces.video.VideoNamespace.__init__: Java constructor signature follows Java conventions
signalwire.runtime.LambdaUrlResolver.__init__: Java constructor signature follows Java conventions
signalwire.runtime.lambda.LambdaAgentHandler.__init__: Java constructor signature follows Java conventions
signalwire.runtime.lambda.LambdaResponse.__init__: Java constructor signature follows Java conventions
signalwire.search.DocumentProcessor.__init__: Java constructor signature follows Java conventions
signalwire.search.IndexBuilder.__init__: Java constructor signature follows Java conventions
signalwire.search.SearchEngine.__init__: Java constructor signature follows Java conventions
signalwire.search.SearchService.__init__: Java constructor signature follows Java conventions
signalwire.search.search_service.SearchRequest.__init__: Java constructor signature follows Java conventions
signalwire.search.search_service.SearchResponse.__init__: Java constructor signature follows Java conventions
signalwire.search.search_service.SearchResult.__init__: Java constructor signature follows Java conventions
signalwire.skills.builtin.ApiNinjaTriviaSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.builtin.CustomSkillsSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.builtin.DatasphereServerlessSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.builtin.DatasphereSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.builtin.DatetimeSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.builtin.McpGatewaySkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.builtin.SwmlTransferSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.claude_skills.skill.ClaudeSkillsSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.google_maps.skill.GoogleMapsSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.info_gatherer.skill.InfoGathererSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.joke.skill.JokeSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.math.skill.MathSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.spider.skill.SpiderSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.weather_api.skill.WeatherApiSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.web_search.skill.WebSearchSkill.__init__: Java constructor signature follows Java conventions
signalwire.skills.wikipedia_search.skill.WikipediaSearchSkill.__init__: Java constructor signature follows Java conventions
signalwire.swaig.ToolDefinition.__init__: Java constructor signature follows Java conventions
signalwire.swml.Document.__init__: Java constructor signature follows Java conventions
signalwire.swml.Service.__init__: Java constructor signature follows Java conventions

## Idiom: Java fluent API returns this

signalwire.agent_server.AgentServer.get_agent: Java fluent API returns this for chaining
signalwire.agent_server.AgentServer.unregister: Java fluent API returns this for chaining
signalwire.core.contexts.ContextBuilder.get_context: Java fluent API returns this for chaining
signalwire.core.skill_base.SkillBase.register_tools: Java fluent API returns this for chaining
signalwire.skills.claude_skills.skill.ClaudeSkillsSkill.register_tools: Java fluent API returns this for chaining
signalwire.skills.google_maps.skill.GoogleMapsSkill.register_tools: Java fluent API returns this for chaining
signalwire.skills.info_gatherer.skill.InfoGathererSkill.register_tools: Java fluent API returns this for chaining
signalwire.skills.joke.skill.JokeSkill.register_tools: Java fluent API returns this for chaining
signalwire.skills.math.skill.MathSkill.register_tools: Java fluent API returns this for chaining
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.register_tools: Java fluent API returns this for chaining
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.register_tools: Java fluent API returns this for chaining
signalwire.skills.spider.skill.SpiderSkill.register_tools: Java fluent API returns this for chaining
signalwire.skills.weather_api.skill.WeatherApiSkill.register_tools: Java fluent API returns this for chaining
signalwire.skills.web_search.skill.WebSearchSkill.register_tools: Java fluent API returns this for chaining
signalwire.skills.wikipedia_search.skill.WikipediaSearchSkill.register_tools: Java fluent API returns this for chaining

## Backlog: real signature divergences (353 symbols)

signalwire.agent_server.AgentServer.register: BACKLOG / param-count-mismatch/ reference has 3 param(s), port has 2/ reference=['self', 'agent', 'route'] port=; return-mismatch/
signalwire.agent_server.AgentServer.run: BACKLOG / param-count-mismatch/ reference has 5 param(s), port has 1/ reference=['self', 'event', 'context', 'ho; return-mismatch/
signalwire.agent_server.AgentServer.serve_static_files: BACKLOG / param-mismatch/ param[2] (route)/ required False vs True; default '/' vs '<absent>'; return-mismatch/ returns 'void' vs 
signalwire.cli.SwaigTest.main: BACKLOG / missing-reference/ in port, not in reference
signalwire.cli.SwaigTest.run: BACKLOG / missing-reference/ in port, not in reference
signalwire.cli.simulation.mock_env.ServerlessSimulator.build_env_provider: BACKLOG / missing-reference/ in port, not in reference
signalwire.cli.simulation.mock_env.ServerlessSimulator.get_masked_keys: BACKLOG / missing-reference/ in port, not in reference
signalwire.cli.simulation.mock_env.ServerlessSimulator.get_platform: BACKLOG / missing-reference/ in port, not in reference
signalwire.cli.simulation.mock_env.ServerlessSimulator.get_simulated_env: BACKLOG / missing-reference/ in port, not in reference
signalwire.cli.simulation.mock_env.ServerlessSimulator.proxy_url_base_masked_from_real_env: BACKLOG / missing-reference/ in port, not in reference
signalwire.core.agent_base.AgentBase.add_answer_verb: BACKLOG / param-count-mismatch/ reference has 2 param(s), port has 3/ reference=['self', 'config'] port=['self',
signalwire.core.agent_base.AgentBase.add_post_ai_verb: BACKLOG / param-mismatch/ param[2] (config)/ name 'config' vs 'verb_data'; type 'dict<string,any>' vs 'any
signalwire.core.agent_base.AgentBase.add_post_answer_verb: BACKLOG / param-mismatch/ param[2] (config)/ name 'config' vs 'verb_data'; type 'dict<string,any>' vs 'any
signalwire.core.agent_base.AgentBase.add_pre_answer_verb: BACKLOG / param-mismatch/ param[2] (config)/ name 'config' vs 'verb_data'; type 'dict<string,any>' vs 'any
signalwire.core.agent_base.AgentBase.enable_sip_routing: BACKLOG / param-count-mismatch/ reference has 3 param(s), port has 1/ reference=['self', 'auto_map', 'path'] por
signalwire.core.agent_base.AgentBase.on_debug_event: BACKLOG / param-mismatch/ param[1] (handler)/ name 'handler' vs 'callback'; type 'class/Callable' vs 'call; return-mismatch/ retur
signalwire.core.agent_base.AgentBase.on_summary: BACKLOG / param-count-mismatch/ reference has 3 param(s), port has 2/ reference=['self', 'summary', 'raw_data'] ; return-mismatch/
signalwire.core.agent_base.AgentBase.register_sip_username: BACKLOG / param-mismatch/ param[1] (sip_username)/ name 'sip_username' vs 'username'
signalwire.core.contexts.Context.add_step: BACKLOG / param-count-mismatch/ reference has 7 param(s), port has 2/ reference=['self', 'name', 'task', 'bullet
signalwire.core.contexts.Context.get_step: BACKLOG / param-mismatch/ param[1] (name)/ name 'name' vs 'step_name'; return-mismatch/ returns 'optional<class/signalwire.core.co
signalwire.core.contexts.Context.move_step: BACKLOG / param-mismatch/ param[1] (name)/ name 'name' vs 'step_name'
signalwire.core.contexts.Context.remove_step: BACKLOG / param-mismatch/ param[1] (name)/ name 'name' vs 'step_name'
signalwire.core.contexts.Context.set_enter_fillers: BACKLOG / param-mismatch/ param[1] (enter_fillers)/ name 'enter_fillers' vs 'fillers'
signalwire.core.contexts.Context.set_exit_fillers: BACKLOG / param-mismatch/ param[1] (exit_fillers)/ name 'exit_fillers' vs 'fillers'
signalwire.core.contexts.GatherInfo.add_question: BACKLOG / param-count-mismatch/ reference has 4 param(s), port has 3/ reference=['self', 'key', 'question', 'kwa
signalwire.core.contexts.Step.add_gather_question: BACKLOG / param-count-mismatch/ reference has 7 param(s), port has 3/ reference=['self', 'key', 'question', 'typ
signalwire.core.contexts.Step.set_functions: BACKLOG / param-mismatch/ param[1] (functions)/ type 'union<list<string>,string>' vs 'any'
signalwire.core.contexts.Step.set_gather_info: BACKLOG / param-mismatch/ param[1] (output_key)/ type 'optional<string>' vs 'string'; required False vs Tr; param-mismatch/ param[
signalwire.core.data_map.DataMap.expression: BACKLOG / param-count-mismatch/ reference has 5 param(s), port has 4/ reference=['self', 'test_value', 'pattern'
signalwire.core.data_map.DataMap.parameter: BACKLOG / param-count-mismatch/ reference has 6 param(s), port has 4/ reference=['self', 'name', 'param_type', '
signalwire.core.data_map.DataMap.webhook: BACKLOG / param-count-mismatch/ reference has 7 param(s), port has 3/ reference=['self', 'method', 'url', 'heade
signalwire.core.function_result.FunctionResult.add_actions: BACKLOG / param-mismatch/ param[1] (actions)/ name 'actions' vs 'action_list'
signalwire.core.function_result.FunctionResult.add_dynamic_hints: BACKLOG / param-mismatch/ param[1] (hints)/ type 'list<union<dict<string,any>,string>>' vs 'list<any>'
signalwire.core.function_result.FunctionResult.connect: BACKLOG / param-count-mismatch/ reference has 4 param(s), port has 3/ reference=['self', 'destination', 'final',
signalwire.core.function_result.FunctionResult.create_payment_prompt: BACKLOG / param-mismatch/ param[1] (actions)/ name 'actions' vs 'pay_actions'; param-mismatch/ param[2] (card_type)/ type 'optiona
signalwire.core.function_result.FunctionResult.enable_extensive_data: BACKLOG / param-mismatch/ param[1] (enabled)/ required False vs True; default True vs '<absent>'
signalwire.core.function_result.FunctionResult.enable_functions_on_timeout: BACKLOG / param-mismatch/ param[1] (enabled)/ required False vs True; default True vs '<absent>'
signalwire.core.function_result.FunctionResult.execute_rpc: BACKLOG / param-count-mismatch/ reference has 5 param(s), port has 3/ reference=['self', 'method', 'params', 'ca
signalwire.core.function_result.FunctionResult.execute_swml: BACKLOG / param-count-mismatch/ reference has 3 param(s), port has 2/ reference=['self', 'swml_content', 'transf
signalwire.core.function_result.FunctionResult.hold: BACKLOG / param-mismatch/ param[1] (timeout)/ required False vs True; default 300 vs '<absent>'
signalwire.core.function_result.FunctionResult.join_conference: BACKLOG / param-count-mismatch/ reference has 19 param(s), port has 2/ reference=['self', 'name', 'muted', 'beep
signalwire.core.function_result.FunctionResult.pay: BACKLOG / param-count-mismatch/ reference has 20 param(s), port has 6/ reference=['self', 'payment_connector_url
signalwire.core.function_result.FunctionResult.play_background_file: BACKLOG / param-count-mismatch/ reference has 3 param(s), port has 2/ reference=['self', 'filename', 'wait'] por
signalwire.core.function_result.FunctionResult.record_call: BACKLOG / param-count-mismatch/ reference has 12 param(s), port has 1/ reference=['self', 'control_id', 'stereo'
signalwire.core.function_result.FunctionResult.remove_global_data: BACKLOG / param-mismatch/ param[1] (keys)/ type 'union<list<string>,string>' vs 'any'
signalwire.core.function_result.FunctionResult.remove_metadata: BACKLOG / param-mismatch/ param[1] (keys)/ type 'union<list<string>,string>' vs 'any'
signalwire.core.function_result.FunctionResult.replace_in_history: BACKLOG / param-mismatch/ param[1] (text)/ name 'text' vs 'summary'; type 'union<bool,string>' vs 'bool'; 
signalwire.core.function_result.FunctionResult.rpc_ai_message: BACKLOG / param-count-mismatch/ reference has 4 param(s), port has 3/ reference=['self', 'call_id', 'message_tex
signalwire.core.function_result.FunctionResult.rpc_dial: BACKLOG / param-count-mismatch/ reference has 5 param(s), port has 4/ reference=['self', 'to_number', 'from_numb
signalwire.core.function_result.FunctionResult.send_sms: BACKLOG / param-count-mismatch/ reference has 7 param(s), port has 6/ reference=['self', 'to_number', 'from_numb
signalwire.core.function_result.FunctionResult.stop_record_call: BACKLOG / param-count-mismatch/ reference has 2 param(s), port has 1/ reference=['self', 'control_id'] port=['se
signalwire.core.function_result.FunctionResult.stop_tap: BACKLOG / param-count-mismatch/ reference has 2 param(s), port has 1/ reference=['self', 'control_id'] port=['se
signalwire.core.function_result.FunctionResult.switch_context: BACKLOG / param-count-mismatch/ reference has 5 param(s), port has 2/ reference=['self', 'system_prompt', 'user_
signalwire.core.function_result.FunctionResult.swml_transfer: BACKLOG / param-mismatch/ param[3] (final)/ name 'final' vs 'is_final'; required False vs True; default Tr
signalwire.core.function_result.FunctionResult.tap: BACKLOG / param-count-mismatch/ reference has 7 param(s), port has 5/ reference=['self', 'uri', 'control_id', 'd
signalwire.core.function_result.FunctionResult.toggle_functions: BACKLOG / param-mismatch/ param[1] (function_toggles)/ name 'function_toggles' vs 'toggles'
signalwire.core.function_result.FunctionResult.wait_for_user: BACKLOG / param-count-mismatch/ reference has 4 param(s), port has 1/ reference=['self', 'enabled', 'timeout', '
signalwire.core.security.session_manager.SessionManager.validate_token: BACKLOG / param-mismatch/ param[1] (call_id)/ name 'call_id' vs 'token'; param-mismatch/ param[3] (token)/ name 'token' vs 'call_i
signalwire.core.skill_base.SkillBase.get_description: BACKLOG / missing-reference/ in port, not in reference
signalwire.core.skill_base.SkillBase.get_extra_fields: BACKLOG / missing-reference/ in port, not in reference
signalwire.core.skill_base.SkillBase.get_name: BACKLOG / missing-reference/ in port, not in reference
signalwire.core.skill_base.SkillBase.get_parameter_schema: BACKLOG / param-mismatch/ param[0] (cls)/ name 'cls' vs 'self'; kind 'cls' vs 'self'; return-mismatch/ returns 'dict<string,dict<s
signalwire.core.skill_base.SkillBase.get_required_env_vars: BACKLOG / missing-reference/ in port, not in reference
signalwire.core.skill_base.SkillBase.get_required_packages: BACKLOG / missing-reference/ in port, not in reference
signalwire.core.skill_base.SkillBase.get_swaig_functions: BACKLOG / missing-reference/ in port, not in reference
signalwire.core.skill_base.SkillBase.get_version: BACKLOG / missing-reference/ in port, not in reference
signalwire.core.skill_base.SkillBase.setup: BACKLOG / param-count-mismatch/ reference has 1 param(s), port has 2/ reference=['self'] port=['self', 'params']
signalwire.core.skill_base.SkillBase.supports_multiple_instances: BACKLOG / missing-reference/ in port, not in reference
signalwire.core.skill_manager.SkillManager.has_skill: BACKLOG / param-mismatch/ param[1] (skill_identifier)/ name 'skill_identifier' vs 'skill_name'
signalwire.logging.Logger.debug: BACKLOG / missing-reference/ in port, not in reference
signalwire.logging.Logger.error: BACKLOG / missing-reference/ in port, not in reference
signalwire.logging.Logger.get_global_level: BACKLOG / missing-reference/ in port, not in reference
signalwire.logging.Logger.get_logger: BACKLOG / missing-reference/ in port, not in reference
signalwire.logging.Logger.info: BACKLOG / missing-reference/ in port, not in reference
signalwire.logging.Logger.is_enabled: BACKLOG / missing-reference/ in port, not in reference
signalwire.logging.Logger.set_global_level: BACKLOG / missing-reference/ in port, not in reference
signalwire.logging.Logger.warn: BACKLOG / missing-reference/ in port, not in reference
signalwire.relay.Constants.is_call_gone_code: BACKLOG / missing-reference/ in port, not in reference
signalwire.relay.Constants.is_terminal_action_state: BACKLOG / missing-reference/ in port, not in reference
signalwire.relay.Constants.is_terminal_call_state: BACKLOG / missing-reference/ in port, not in reference
signalwire.relay.Constants.is_terminal_message_state: BACKLOG / missing-reference/ in port, not in reference
signalwire.relay.call.AIAction.stop: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.Action.is_done: BACKLOG / missing-reference/ in port, not in reference
signalwire.relay.call.Action.to_string: BACKLOG / missing-reference/ in port, not in reference
signalwire.relay.call.Call.ai: BACKLOG / param-count-mismatch/ reference has 16 param(s), port has 2/ reference=['self', 'control_id', 'agent',; return-mismatch/
signalwire.relay.call.Call.ai_hold: BACKLOG / param-count-mismatch/ reference has 4 param(s), port has 2/ reference=['self', 'timeout', 'prompt', 'k; return-mismatch/
signalwire.relay.call.Call.ai_message: BACKLOG / param-count-mismatch/ reference has 6 param(s), port has 2/ reference=['self', 'message_text', 'role',; return-mismatch/
signalwire.relay.call.Call.ai_unhold: BACKLOG / param-count-mismatch/ reference has 3 param(s), port has 2/ reference=['self', 'prompt', 'kwargs'] por; return-mismatch/
signalwire.relay.call.Call.amazon_bedrock: BACKLOG / param-count-mismatch/ reference has 8 param(s), port has 2/ reference=['self', 'prompt', 'SWAIG', 'ai_; return-mismatch/
signalwire.relay.call.Call.answer: BACKLOG / param-count-mismatch/ reference has 2 param(s), port has 1/ reference=['self', 'kwargs'] port=['self']; return-mismatch/
signalwire.relay.call.Call.bind_digit: BACKLOG / param-count-mismatch/ reference has 7 param(s), port has 4/ reference=['self', 'digits', 'bind_method'; return-mismatch/
signalwire.relay.call.Call.clear_digit_bindings: BACKLOG / param-count-mismatch/ reference has 3 param(s), port has 1/ reference=['self', 'realm', 'kwargs'] port; return-mismatch/
signalwire.relay.call.Call.collect: BACKLOG / param-count-mismatch/ reference has 11 param(s), port has 3/ reference=['self', 'digits', 'speech', 'i; return-mismatch/
signalwire.relay.call.Call.connect: BACKLOG / param-count-mismatch/ reference has 8 param(s), port has 3/ reference=['self', 'devices', 'ringback', ; return-mismatch/
signalwire.relay.call.Call.denoise: BACKLOG / return-mismatch/ returns 'dict<any,any>' vs 'dict<string,any>'
signalwire.relay.call.Call.denoise_stop: BACKLOG / return-mismatch/ returns 'dict<any,any>' vs 'dict<string,any>'
signalwire.relay.call.Call.detect: BACKLOG / param-count-mismatch/ reference has 6 param(s), port has 3/ reference=['self', 'detect', 'timeout', 'c; return-mismatch/
signalwire.relay.call.Call.disconnect: BACKLOG / return-mismatch/ returns 'dict<any,any>' vs 'dict<string,any>'
signalwire.relay.call.Call.echo: BACKLOG / param-count-mismatch/ reference has 4 param(s), port has 2/ reference=['self', 'timeout', 'status_url'; return-mismatch/
signalwire.relay.call.Call.hangup: BACKLOG / param-count-mismatch/ reference has 2 param(s), port has 1/ reference=['self', 'reason'] port=['self']; return-mismatch/
signalwire.relay.call.Call.hold: BACKLOG / return-mismatch/ returns 'dict<any,any>' vs 'dict<string,any>'
signalwire.relay.call.Call.join_conference: BACKLOG / param-count-mismatch/ reference has 22 param(s), port has 3/ reference=['self', 'name', 'muted', 'beep; return-mismatch/
signalwire.relay.call.Call.join_room: BACKLOG / param-count-mismatch/ reference has 4 param(s), port has 3/ reference=['self', 'name', 'status_url', '; return-mismatch/
signalwire.relay.call.Call.leave_conference: BACKLOG / param-count-mismatch/ reference has 3 param(s), port has 2/ reference=['self', 'conference_id', 'kwarg; return-mismatch/
signalwire.relay.call.Call.leave_room: BACKLOG / param-count-mismatch/ reference has 2 param(s), port has 1/ reference=['self', 'kwargs'] port=['self']; return-mismatch/
signalwire.relay.call.Call.live_transcribe: BACKLOG / param-count-mismatch/ reference has 3 param(s), port has 2/ reference=['self', 'action', 'kwargs'] por; return-mismatch/
signalwire.relay.call.Call.live_translate: BACKLOG / param-count-mismatch/ reference has 4 param(s), port has 3/ reference=['self', 'action', 'status_url',; return-mismatch/
signalwire.relay.call.Call.on: BACKLOG / param-count-mismatch/ reference has 3 param(s), port has 2/ reference=['self', 'event_type', 'handler'
signalwire.relay.call.Call.pass_: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.Call.pay: BACKLOG / param-count-mismatch/ reference has 22 param(s), port has 3/ reference=['self', 'payment_connector_url; return-mismatch/
signalwire.relay.call.Call.play: BACKLOG / param-count-mismatch/ reference has 8 param(s), port has 2/ reference=['self', 'media', 'volume', 'dir; return-mismatch/
signalwire.relay.call.Call.play_and_collect: BACKLOG / param-count-mismatch/ reference has 7 param(s), port has 4/ reference=['self', 'media', 'collect', 'vo; return-mismatch/
signalwire.relay.call.Call.queue_enter: BACKLOG / param-count-mismatch/ reference has 5 param(s), port has 3/ reference=['self', 'queue_name', 'control_; return-mismatch/
signalwire.relay.call.Call.queue_leave: BACKLOG / param-count-mismatch/ reference has 6 param(s), port has 3/ reference=['self', 'queue_name', 'control_; return-mismatch/
signalwire.relay.call.Call.receive_fax: BACKLOG / param-count-mismatch/ reference has 4 param(s), port has 2/ reference=['self', 'control_id', 'on_compl; return-mismatch/
signalwire.relay.call.Call.record: BACKLOG / param-count-mismatch/ reference has 5 param(s), port has 3/ reference=['self', 'audio', 'control_id', ; return-mismatch/
signalwire.relay.call.Call.refer: BACKLOG / param-count-mismatch/ reference has 4 param(s), port has 3/ reference=['self', 'device', 'status_url',; return-mismatch/
signalwire.relay.call.Call.send_digits: BACKLOG / param-count-mismatch/ reference has 3 param(s), port has 2/ reference=['self', 'digits', 'control_id']; return-mismatch/
signalwire.relay.call.Call.send_fax: BACKLOG / param-count-mismatch/ reference has 7 param(s), port has 3/ reference=['self', 'document', 'identity',; return-mismatch/
signalwire.relay.call.Call.stream: BACKLOG / param-count-mismatch/ reference has 12 param(s), port has 3/ reference=['self', 'url', 'name', 'codec'; return-mismatch/
signalwire.relay.call.Call.tap: BACKLOG / param-count-mismatch/ reference has 6 param(s), port has 4/ reference=['self', 'tap', 'device', 'contr; return-mismatch/
signalwire.relay.call.Call.to_string: BACKLOG / missing-reference/ in port, not in reference
signalwire.relay.call.Call.transcribe: BACKLOG / param-count-mismatch/ reference has 5 param(s), port has 2/ reference=['self', 'control_id', 'status_u; return-mismatch/
signalwire.relay.call.Call.transfer: BACKLOG / param-count-mismatch/ reference has 3 param(s), port has 2/ reference=['self', 'dest', 'kwargs'] port=; return-mismatch/
signalwire.relay.call.Call.unhold: BACKLOG / return-mismatch/ returns 'dict<any,any>' vs 'dict<string,any>'
signalwire.relay.call.Call.user_event: BACKLOG / param-count-mismatch/ reference has 3 param(s), port has 2/ reference=['self', 'event', 'kwargs'] port; return-mismatch/
signalwire.relay.call.CollectAction.start_input_timers: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.CollectAction.stop: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.DetectAction.stop: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.PayAction.stop: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.PlayAction.pause: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.PlayAction.resume: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.PlayAction.stop: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.PlayAction.volume: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.RecordAction.pause: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.RecordAction.resume: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.RecordAction.stop: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.StreamAction.stop: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.TapAction.stop: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.call.TranscribeAction.stop: BACKLOG / missing-port/ in reference, not in port
signalwire.relay.client.RelayClient.dial: BACKLOG / param-count-mismatch/ reference has 5 param(s), port has 2/ reference=['self', 'devices', 'tag', 'max_
signalwire.relay.client.RelayClient.execute: BACKLOG / return-mismatch/ returns 'dict<any,any>' vs 'dict<string,any>'
signalwire.relay.client.RelayClient.on_call: BACKLOG / param-mismatch/ param[1] (handler)/ type 'class/signalwire.relay.client.CallHandler' vs 'callabl; return-mismatch/ retur
signalwire.relay.client.RelayClient.on_message: BACKLOG / param-mismatch/ param[1] (handler)/ type 'class/signalwire.relay.client.MessageHandler' vs 'call; return-mismatch/ retur
signalwire.relay.client.RelayClient.receive: BACKLOG / param-mismatch/ param[1] (contexts)/ name 'contexts' vs 'new_contexts'; return-mismatch/ returns 'void' vs 'dict<string,
signalwire.relay.client.RelayClient.send_message: BACKLOG / param-count-mismatch/ reference has 9 param(s), port has 6/ reference=['self', 'to_number', 'from_numb
signalwire.relay.client.RelayClient.unreceive: BACKLOG / param-mismatch/ param[1] (contexts)/ name 'contexts' vs 'remove_contexts'; return-mismatch/ returns 'void' vs 'dict<stri
signalwire.relay.event.RelayEvent.to_string: BACKLOG / missing-reference/ in port, not in reference
signalwire.relay.message.Message.is_done: BACKLOG / missing-reference/ in port, not in reference
signalwire.relay.message.Message.on: BACKLOG / param-mismatch/ param[1] (handler)/ name 'handler' vs 'listener'; type 'class/Callable' vs 'call
signalwire.relay.message.Message.to_string: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.RestError.get_method: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.RestError.get_path: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.RestError.get_response_body: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.RestError.get_status_code: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.RestError.is_client_error: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.RestError.is_not_found: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.RestError.is_server_error: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.RestError.is_unauthorized: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest._base.CrudResource.create: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest._base.CrudResource.delete: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest._base.CrudResource.get: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest._base.CrudResource.list: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest._base.CrudResource.update: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest._base.HttpClient.get_base_url: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest._base.HttpClient.with_base_url: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.call_handler.PhoneCallHandler.to_string: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.call_handler.PhoneCallHandler.value_of: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.call_handler.PhoneCallHandler.values: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.BillingNamespace.invoices: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.BillingNamespace.usage: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.CampaignNamespace.assignments: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.CampaignNamespace.brands: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.CampaignNamespace.campaigns: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.CampaignNamespace.orders: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.ChatNamespace.channels: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.ChatNamespace.members: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.ChatNamespace.messages: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.ComplianceNamespace.cnam_registrations: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.ComplianceNamespace.shaken_stir: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.ConferenceNamespace.conferences: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.ConferenceNamespace.participants: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.FaxNamespace.faxes: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.MessagingNamespace.messages: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.MessagingNamespace.send: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.NumberLookupNamespace.lookup: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.PubSubNamespace.channels: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.PubSubNamespace.publish: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.QueueNamespace.queues: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.RecordingNamespace.recordings: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.SipNamespace.endpoints: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.SipNamespace.profiles: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.StreamNamespace.streams: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.SwmlNamespace.scripts: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.TranscriptionNamespace.transcriptions: BACKLOG / missing-reference/ in port, not in reference
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.search: BACKLOG / param-mismatch/ param[1] (params)/ name 'params' vs 'query_params'; kind 'var_keyword' vs 'posit; return-mismatch/ retur
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.set_ai_agent: BACKLOG / param-count-mismatch/ reference has 4 param(s), port has 3/ reference=['self', 'resource_id', 'agent_i; return-mismatch/
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.set_call_flow: BACKLOG / param-count-mismatch/ reference has 5 param(s), port has 3/ reference=['self', 'resource_id', 'flow_id; return-mismatch/
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.set_cxml_application: BACKLOG / param-count-mismatch/ reference has 4 param(s), port has 3/ reference=['self', 'resource_id', 'applica; return-mismatch/
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.set_cxml_webhook: BACKLOG / param-count-mismatch/ reference has 6 param(s), port has 3/ reference=['self', 'resource_id', 'url', '; return-mismatch/
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.set_relay_application: BACKLOG / param-count-mismatch/ reference has 4 param(s), port has 3/ reference=['self', 'resource_id', 'name', ; return-mismatch/
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.set_relay_topic: BACKLOG / param-count-mismatch/ reference has 5 param(s), port has 3/ reference=['self', 'resource_id', 'topic',; return-mismatch/
signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.set_swml_webhook: BACKLOG / param-count-mismatch/ reference has 4 param(s), port has 3/ reference=['self', 'resource_id', 'url', '; return-mismatch/
signalwire.runtime.EnvProvider.get: BACKLOG / missing-reference/ in port, not in reference
signalwire.runtime.EnvProvider.is_set: BACKLOG / missing-reference/ in port, not in reference
signalwire.runtime.ExecutionMode.detect: BACKLOG / missing-reference/ in port, not in reference
signalwire.runtime.ExecutionMode.value_of: BACKLOG / missing-reference/ in port, not in reference
signalwire.runtime.ExecutionMode.values: BACKLOG / missing-reference/ in port, not in reference
signalwire.runtime.LambdaUrlResolver.resolve_base_url: BACKLOG / missing-reference/ in port, not in reference
signalwire.runtime.lambda.LambdaAgentHandler.handle: BACKLOG / missing-reference/ in port, not in reference
signalwire.runtime.lambda.LambdaResponse.get_body: BACKLOG / missing-reference/ in port, not in reference
signalwire.runtime.lambda.LambdaResponse.get_headers: BACKLOG / missing-reference/ in port, not in reference
signalwire.runtime.lambda.LambdaResponse.get_status_code: BACKLOG / missing-reference/ in port, not in reference
signalwire.runtime.lambda.LambdaResponse.is_base64_encoded: BACKLOG / missing-reference/ in port, not in reference
signalwire.runtime.lambda.LambdaResponse.json: BACKLOG / missing-reference/ in port, not in reference
signalwire.runtime.lambda.LambdaResponse.to_dict: BACKLOG / missing-reference/ in port, not in reference
signalwire.search.preprocess_document_content: BACKLOG / missing-port/ in reference, not in port
signalwire.search.preprocess_query: BACKLOG / missing-port/ in reference, not in port
signalwire.skills.builtin.ApiNinjaTriviaSkill.get_description: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.ApiNinjaTriviaSkill.get_name: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.ApiNinjaTriviaSkill.get_swaig_functions: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.ApiNinjaTriviaSkill.register_tools: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.ApiNinjaTriviaSkill.setup: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.ApiNinjaTriviaSkill.supports_multiple_instances: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.CustomSkillsSkill.get_description: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.CustomSkillsSkill.get_name: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.CustomSkillsSkill.register_tools: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.CustomSkillsSkill.setup: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.CustomSkillsSkill.supports_multiple_instances: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereServerlessSkill.get_description: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereServerlessSkill.get_global_data: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereServerlessSkill.get_name: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereServerlessSkill.get_prompt_sections: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereServerlessSkill.get_swaig_functions: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereServerlessSkill.register_tools: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereServerlessSkill.setup: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereServerlessSkill.supports_multiple_instances: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereSkill.get_description: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereSkill.get_global_data: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereSkill.get_name: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereSkill.get_prompt_sections: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereSkill.register_tools: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereSkill.setup: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatasphereSkill.supports_multiple_instances: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatetimeSkill.get_description: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatetimeSkill.get_name: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatetimeSkill.get_prompt_sections: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatetimeSkill.register_tools: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatetimeSkill.setup: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.DatetimeSkill.supports_multiple_instances: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.McpGatewaySkill.get_description: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.McpGatewaySkill.get_global_data: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.McpGatewaySkill.get_hints: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.McpGatewaySkill.get_name: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.McpGatewaySkill.get_prompt_sections: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.McpGatewaySkill.register_tools: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.McpGatewaySkill.setup: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.SwmlTransferSkill.get_description: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.SwmlTransferSkill.get_hints: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.SwmlTransferSkill.get_name: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.SwmlTransferSkill.get_prompt_sections: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.SwmlTransferSkill.get_swaig_functions: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.SwmlTransferSkill.register_tools: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.SwmlTransferSkill.setup: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.builtin.SwmlTransferSkill.supports_multiple_instances: BACKLOG / missing-reference/ in port, not in reference
signalwire.skills.claude_skills.skill.ClaudeSkillsSkill.setup: BACKLOG / param-count-mismatch/ reference has 1 param(s), port has 2/ reference=['self'] port=['self', 'params']
signalwire.skills.google_maps.skill.GoogleMapsSkill.setup: BACKLOG / param-count-mismatch/ reference has 1 param(s), port has 2/ reference=['self'] port=['self', 'params']
signalwire.skills.info_gatherer.skill.InfoGathererSkill.setup: BACKLOG / param-count-mismatch/ reference has 1 param(s), port has 2/ reference=['self'] port=['self', 'params']
signalwire.skills.joke.skill.JokeSkill.setup: BACKLOG / param-count-mismatch/ reference has 1 param(s), port has 2/ reference=['self'] port=['self', 'params']
signalwire.skills.math.skill.MathSkill.setup: BACKLOG / param-count-mismatch/ reference has 1 param(s), port has 2/ reference=['self'] port=['self', 'params']
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.setup: BACKLOG / param-count-mismatch/ reference has 1 param(s), port has 2/ reference=['self'] port=['self', 'params']
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.setup: BACKLOG / param-count-mismatch/ reference has 1 param(s), port has 2/ reference=['self'] port=['self', 'params']
signalwire.skills.spider.skill.SpiderSkill.setup: BACKLOG / param-count-mismatch/ reference has 1 param(s), port has 2/ reference=['self'] port=['self', 'params']
signalwire.skills.weather_api.skill.WeatherApiSkill.setup: BACKLOG / param-count-mismatch/ reference has 1 param(s), port has 2/ reference=['self'] port=['self', 'params']
signalwire.skills.web_search.skill.WebSearchSkill.setup: BACKLOG / param-count-mismatch/ reference has 1 param(s), port has 2/ reference=['self'] port=['self', 'params']
signalwire.skills.wikipedia_search.skill.WikipediaSearchSkill.get_prompt_sections: BACKLOG / return-mismatch/ returns 'list<any>' vs 'list<dict<string,any>>'
signalwire.skills.wikipedia_search.skill.WikipediaSearchSkill.setup: BACKLOG / param-count-mismatch/ reference has 1 param(s), port has 2/ reference=['self'] port=['self', 'params']
signalwire.swaig.ToolDefinition.get_description: BACKLOG / missing-reference/ in port, not in reference
signalwire.swaig.ToolDefinition.get_extra_fields: BACKLOG / missing-reference/ in port, not in reference
signalwire.swaig.ToolDefinition.get_handler: BACKLOG / missing-reference/ in port, not in reference
signalwire.swaig.ToolDefinition.get_name: BACKLOG / missing-reference/ in port, not in reference
signalwire.swaig.ToolDefinition.get_parameters: BACKLOG / missing-reference/ in port, not in reference
signalwire.swaig.ToolDefinition.has_handler: BACKLOG / missing-reference/ in port, not in reference
signalwire.swaig.ToolDefinition.is_secure: BACKLOG / missing-reference/ in port, not in reference
signalwire.swaig.ToolDefinition.set_extra_fields: BACKLOG / missing-reference/ in port, not in reference
signalwire.swaig.ToolDefinition.set_secure: BACKLOG / missing-reference/ in port, not in reference
signalwire.swaig.ToolDefinition.to_swaig_function: BACKLOG / missing-reference/ in port, not in reference
signalwire.swaig.ToolHandler.handle: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Document.add_section: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Document.add_verb: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Document.add_verb_to_section: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Document.get_section_verbs: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Document.get_verbs: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Document.has_section: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Document.render: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Document.render_pretty: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Document.reset: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Document.to_dict: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Schema.get_instance: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Schema.get_verb: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Schema.get_verb_names: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Schema.is_valid_verb: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Schema.verb_count: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.ai: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.amazon_bedrock: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.answer: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.cond: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.connect: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.define_tool: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.define_tools: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.denoise: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.detect_machine: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.enter_queue: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.execute: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.get_auth_password: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.get_auth_user: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.get_document: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.get_registered_swaig_functions: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.get_registered_tools: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.goto_label: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.hangup: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.join_conference: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.join_room: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.label: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.list_tool_names: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.live_transcribe: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.live_translate: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.on_function_call: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.pay: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.play: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.prompt: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.receive_fax: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.record: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.record_call: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.register_swaig_function: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.request: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.return_verb: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.send_digits: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.send_fax: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.send_sms: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.serve: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.set: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.sip_refer: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.sleep: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.stop: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.stop_denoise: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.stop_record_call: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.stop_tap: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.switch_verb: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.tap: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.transfer: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.unset: BACKLOG / missing-reference/ in port, not in reference
signalwire.swml.Service.user_event: BACKLOG / missing-reference/ in port, not in reference
