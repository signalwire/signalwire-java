# PORT_OMISSIONS — Python symbols the Java SDK does not implement

Every symbol listed here is a public class, method or function present in the Python reference (`porting-sdk/python_surface.json`) that this Java port deliberately does not expose. Each entry records a one-line rationale; the Phase 13 surface audit in CI will reject any Python symbol missing from the Java SDK that is also missing from this file.

Entries marked `not_yet_implemented:` are honest — a future PR will close the gap. Everything else is intentional divergence with a design reason.


# Format: `<fully.qualified.symbol>: <rationale>`
# Regenerate with `python3 scripts/generate_exemptions.py` after
# a surface change.

signalwire.agent_server.AgentServer.get_agents: Java's AgentServer exposes getRoutes() and getAgent(route); a bulk getAgents() listing is redundant
signalwire.agent_server.AgentServer.register_global_routing_callback: Python global-routing callback hook; Java apps install equivalent behaviour via AgentServer.register() with a routed AgentBase
signalwire.agent_server.AgentServer.register_sip_username: SIP routing by username is configured on AgentBase directly in Java (registerSipUsername), not at the AgentServer level
signalwire.agent_server.AgentServer.setup_sip_routing: Java's AgentServer.registerSipRoute covers the SIP setup in one method; setup_sip_routing is a Python-specific split
signalwire.agents.bedrock.BedrockAgent: Bedrock agent is not ported — Java ships AgentBase + SWML only; Bedrock integration is Python-specific
signalwire.agents.bedrock.BedrockAgent.__init__: Bedrock agent is not ported — Java ships AgentBase + SWML only; Bedrock integration is Python-specific
signalwire.agents.bedrock.BedrockAgent.__repr__: Bedrock agent is not ported — Java ships AgentBase + SWML only; Bedrock integration is Python-specific
signalwire.agents.bedrock.BedrockAgent.set_inference_params: Bedrock agent is not ported — Java ships AgentBase + SWML only; Bedrock integration is Python-specific
signalwire.agents.bedrock.BedrockAgent.set_llm_model: Bedrock agent is not ported — Java ships AgentBase + SWML only; Bedrock integration is Python-specific
signalwire.agents.bedrock.BedrockAgent.set_llm_temperature: Bedrock agent is not ported — Java ships AgentBase + SWML only; Bedrock integration is Python-specific
signalwire.agents.bedrock.BedrockAgent.set_post_prompt_llm_params: Bedrock agent is not ported — Java ships AgentBase + SWML only; Bedrock integration is Python-specific
signalwire.agents.bedrock.BedrockAgent.set_prompt_llm_params: Bedrock agent is not ported — Java ships AgentBase + SWML only; Bedrock integration is Python-specific
signalwire.agents.bedrock.BedrockAgent.set_voice: Bedrock agent is not ported — Java ships AgentBase + SWML only; Bedrock integration is Python-specific
signalwire.cli.build_search.console_entry_point: builds search-index artifacts for the Python search subsystem — not applicable (see signalwire.search omission)
signalwire.cli.build_search.main: builds search-index artifacts for the Python search subsystem — not applicable (see signalwire.search omission)
signalwire.cli.build_search.migrate_command: builds search-index artifacts for the Python search subsystem — not applicable (see signalwire.search omission)
signalwire.cli.build_search.remote_command: builds search-index artifacts for the Python search subsystem — not applicable (see signalwire.search omission)
signalwire.cli.build_search.search_command: builds search-index artifacts for the Python search subsystem — not applicable (see signalwire.search omission)
signalwire.cli.build_search.validate_command: builds search-index artifacts for the Python search subsystem — not applicable (see signalwire.search omission)
signalwire.cli.core.agent_loader.discover_agents_in_file: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.agent_loader.discover_services_in_file: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.agent_loader.load_agent_from_file: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.agent_loader.load_service_from_file: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.argparse_helpers.CustomArgumentParser: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.argparse_helpers.CustomArgumentParser.__init__: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.argparse_helpers.CustomArgumentParser.error: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.argparse_helpers.CustomArgumentParser.parse_args: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.argparse_helpers.CustomArgumentParser.print_usage: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.argparse_helpers.parse_function_arguments: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.dynamic_config.apply_dynamic_config: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.service_loader.ServiceCapture: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.service_loader.ServiceCapture.__init__: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.service_loader.ServiceCapture.capture: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.service_loader.discover_agents_in_file: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.service_loader.load_agent_from_file: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.service_loader.load_and_simulate_service: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.core.service_loader.simulate_request_to_service: Python CLI loader internals — the Java swaig-test CLI is self-contained and doesn't need these modules
signalwire.cli.dokku.Colors: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.DokkuProjectGenerator: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.DokkuProjectGenerator.__init__: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.DokkuProjectGenerator.generate: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.cmd_config: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.cmd_deploy: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.cmd_init: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.cmd_logs: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.cmd_scale: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.generate_password: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.main: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.print_error: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.print_header: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.print_step: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.print_success: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.print_warning: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.prompt: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.dokku.prompt_yes_no: Dokku deploy helper is a Python shell-wrapper; Java deploys via standard JVM tooling
signalwire.cli.execution.datamap_exec.execute_datamap_function: Python CLI executors for DataMap / webhook simulation — Java's SwaigTest covers the execution cases in-process
signalwire.cli.execution.datamap_exec.simple_template_expand: Python CLI executors for DataMap / webhook simulation — Java's SwaigTest covers the execution cases in-process
signalwire.cli.execution.webhook_exec.execute_external_webhook_function: Python CLI executors for DataMap / webhook simulation — Java's SwaigTest covers the execution cases in-process
signalwire.cli.init_project.Colors: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.ProjectGenerator: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.ProjectGenerator.__init__: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.ProjectGenerator.generate: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.generate_password: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.get_agent_template: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.get_app_template: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.get_env_credentials: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.get_readme_template: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.get_test_template: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.get_web_index_template: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.main: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.mask_token: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.print_error: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.print_step: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.print_success: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.print_warning: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.prompt: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.prompt_multiselect: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.prompt_select: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.prompt_yes_no: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.run_interactive: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.init_project.run_quick: Python project scaffolder; Java users initialize with Gradle/Maven
signalwire.cli.output.output_formatter.display_agent_tools: CLI pretty-print helpers; Java's SwaigTest prints via the standard logger
signalwire.cli.output.output_formatter.format_result: CLI pretty-print helpers; Java's SwaigTest prints via the standard logger
signalwire.cli.output.swml_dump.handle_dump_swml: CLI pretty-print helpers; Java's SwaigTest prints via the standard logger
signalwire.cli.output.swml_dump.setup_output_suppression: CLI pretty-print helpers; Java's SwaigTest prints via the standard logger
signalwire.cli.simulation.data_generation.adapt_for_call_type: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.data_generation.generate_comprehensive_post_data: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.data_generation.generate_fake_node_id: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.data_generation.generate_fake_sip_from: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.data_generation.generate_fake_sip_to: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.data_generation.generate_fake_swml_post_data: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.data_generation.generate_fake_uuid: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.data_generation.generate_minimal_post_data: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.data_overrides.apply_convenience_mappings: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.data_overrides.apply_overrides: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.data_overrides.parse_value: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.data_overrides.set_nested_value: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockHeaders: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockHeaders.__contains__: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockHeaders.__getitem__: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockHeaders.__init__: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockHeaders.get: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockHeaders.items: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockHeaders.keys: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockHeaders.values: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockQueryParams: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockQueryParams.__contains__: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockQueryParams.__getitem__: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockQueryParams.__init__: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockQueryParams.get: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockQueryParams.items: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockQueryParams.keys: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockQueryParams.values: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockRequest: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockRequest.__init__: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockRequest.body: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockRequest.client: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockRequest.json: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockURL: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockURL.__init__: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.MockURL.__str__: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.ServerlessSimulator.activate: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.ServerlessSimulator.add_override: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.ServerlessSimulator.deactivate: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.ServerlessSimulator.get_current_env: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.create_mock_request: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.simulation.mock_env.load_env_file: Python CLI simulation scaffolding; Java's ServerlessSimulator covers the subset the SwaigTest CLI drives
signalwire.cli.swaig_test_wrapper.main: Python-only CLI helper not mirrored in Java's SwaigTest
signalwire.cli.test_swaig.console_entry_point: Python-only CLI helper not mirrored in Java's SwaigTest
signalwire.cli.test_swaig.main: Python-only CLI helper not mirrored in Java's SwaigTest
signalwire.cli.test_swaig.print_help_examples: Python-only CLI helper not mirrored in Java's SwaigTest
signalwire.cli.test_swaig.print_help_platforms: Python-only CLI helper not mirrored in Java's SwaigTest
signalwire.cli.types.AgentInfo: CLI-internal typed dict shims; not exposed in any runtime API
signalwire.cli.types.CallData: CLI-internal typed dict shims; not exposed in any runtime API
signalwire.cli.types.DataMapConfig: CLI-internal typed dict shims; not exposed in any runtime API
signalwire.cli.types.FunctionInfo: CLI-internal typed dict shims; not exposed in any runtime API
signalwire.cli.types.PostData: CLI-internal typed dict shims; not exposed in any runtime API
signalwire.cli.types.VarsData: CLI-internal typed dict shims; not exposed in any runtime API
signalwire.core.agent.prompt.manager.PromptManager: Python's PromptManager is embedded in Java's AgentBase prompt APIs
signalwire.core.agent.prompt.manager.PromptManager.__init__: Python's PromptManager is embedded in Java's AgentBase prompt APIs
signalwire.core.agent.prompt.manager.PromptManager.define_contexts: Python's PromptManager is embedded in Java's AgentBase prompt APIs
signalwire.core.agent.prompt.manager.PromptManager.get_contexts: Python's PromptManager is embedded in Java's AgentBase prompt APIs
signalwire.core.agent.prompt.manager.PromptManager.get_post_prompt: Python's PromptManager is embedded in Java's AgentBase prompt APIs
signalwire.core.agent.prompt.manager.PromptManager.get_prompt: Python's PromptManager is embedded in Java's AgentBase prompt APIs
signalwire.core.agent.prompt.manager.PromptManager.get_raw_prompt: Python's PromptManager is embedded in Java's AgentBase prompt APIs
signalwire.core.agent.prompt.manager.PromptManager.prompt_add_section: Python's PromptManager is embedded in Java's AgentBase prompt APIs
signalwire.core.agent.prompt.manager.PromptManager.prompt_add_subsection: Python's PromptManager is embedded in Java's AgentBase prompt APIs
signalwire.core.agent.prompt.manager.PromptManager.prompt_add_to_section: Python's PromptManager is embedded in Java's AgentBase prompt APIs
signalwire.core.agent.prompt.manager.PromptManager.prompt_has_section: Python's PromptManager is embedded in Java's AgentBase prompt APIs
signalwire.core.agent.prompt.manager.PromptManager.set_post_prompt: Python's PromptManager is embedded in Java's AgentBase prompt APIs
signalwire.core.agent.prompt.manager.PromptManager.set_prompt_pom: Python's PromptManager is embedded in Java's AgentBase prompt APIs
signalwire.core.agent.prompt.manager.PromptManager.set_prompt_text: Python's PromptManager is embedded in Java's AgentBase prompt APIs
signalwire.core.agent.tools.decorator.ToolDecorator: Python function-decorator mechanism; Java uses AgentBase.defineTool with a lambda/method reference instead
signalwire.core.agent.tools.decorator.ToolDecorator.create_class_decorator: Python function-decorator mechanism; Java uses AgentBase.defineTool with a lambda/method reference instead
signalwire.core.agent.tools.decorator.ToolDecorator.create_instance_decorator: Python function-decorator mechanism; Java uses AgentBase.defineTool with a lambda/method reference instead
signalwire.core.agent.tools.registry.ToolRegistry: Python's ToolRegistry is an internal detail of its decorator system; Java registers tools via AgentBase.defineTool directly
signalwire.core.agent.tools.registry.ToolRegistry.__init__: Python's ToolRegistry is an internal detail of its decorator system; Java registers tools via AgentBase.defineTool directly
signalwire.core.agent.tools.registry.ToolRegistry.define_tool: Python's ToolRegistry is an internal detail of its decorator system; Java registers tools via AgentBase.defineTool directly
signalwire.core.agent.tools.registry.ToolRegistry.get_all_functions: Python's ToolRegistry is an internal detail of its decorator system; Java registers tools via AgentBase.defineTool directly
signalwire.core.agent.tools.registry.ToolRegistry.get_function: Python's ToolRegistry is an internal detail of its decorator system; Java registers tools via AgentBase.defineTool directly
signalwire.core.agent.tools.registry.ToolRegistry.has_function: Python's ToolRegistry is an internal detail of its decorator system; Java registers tools via AgentBase.defineTool directly
signalwire.core.agent.tools.registry.ToolRegistry.register_class_decorated_tools: Python's ToolRegistry is an internal detail of its decorator system; Java registers tools via AgentBase.defineTool directly
signalwire.core.agent.tools.registry.ToolRegistry.register_swaig_function: Python's ToolRegistry is an internal detail of its decorator system; Java registers tools via AgentBase.defineTool directly
signalwire.core.agent.tools.registry.ToolRegistry.remove_function: Python's ToolRegistry is an internal detail of its decorator system; Java registers tools via AgentBase.defineTool directly
signalwire.core.agent.tools.type_inference.create_typed_handler_wrapper: Python's runtime type-inference from function annotations is not applicable — Java's defineTool requires an explicit ToolDefinition with parameter types
signalwire.core.agent.tools.type_inference.infer_schema: Python's runtime type-inference from function annotations is not applicable — Java's defineTool requires an explicit ToolDefinition with parameter types
signalwire.core.agent_base.AgentBase.__init__: constructor is package-private in Java — public initialization goes through AgentBase.builder() to enforce required fields
signalwire.core.agent_base.AgentBase.auto_map_sip_usernames: Python convenience that auto-registers all public methods as SIP usernames; Java's static typing makes that unsafe — users call registerSipUsername explicitly
signalwire.core.agent_base.AgentBase.get_full_url: Python constructs the full-URL string for self-referencing webhooks; Java's AgentBase exposes host/port/route accessors so users assemble the URL as needed
signalwire.core.auth_handler.AuthHandler: Python AuthHandler class; Java folds auth into AgentBase's HTTP handler (basic auth via authUser/authPassword)
signalwire.core.auth_handler.AuthHandler.__init__: Python AuthHandler class; Java folds auth into AgentBase's HTTP handler (basic auth via authUser/authPassword)
signalwire.core.auth_handler.AuthHandler.flask_decorator: Python AuthHandler class; Java folds auth into AgentBase's HTTP handler (basic auth via authUser/authPassword)
signalwire.core.auth_handler.AuthHandler.get_auth_info: Python AuthHandler class; Java folds auth into AgentBase's HTTP handler (basic auth via authUser/authPassword)
signalwire.core.auth_handler.AuthHandler.get_fastapi_dependency: Python AuthHandler class; Java folds auth into AgentBase's HTTP handler (basic auth via authUser/authPassword)
signalwire.core.auth_handler.AuthHandler.verify_api_key: Python AuthHandler class; Java folds auth into AgentBase's HTTP handler (basic auth via authUser/authPassword)
signalwire.core.auth_handler.AuthHandler.verify_basic_auth: Python AuthHandler class; Java folds auth into AgentBase's HTTP handler (basic auth via authUser/authPassword)
signalwire.core.auth_handler.AuthHandler.verify_bearer_token: Python AuthHandler class; Java folds auth into AgentBase's HTTP handler (basic auth via authUser/authPassword)
signalwire.core.config_loader.ConfigLoader: Python YAML/env config loader; Java uses standard Properties / application.yml patterns via the user's build tool
signalwire.core.config_loader.ConfigLoader.__init__: Python YAML/env config loader; Java uses standard Properties / application.yml patterns via the user's build tool
signalwire.core.config_loader.ConfigLoader.find_config_file: Python YAML/env config loader; Java uses standard Properties / application.yml patterns via the user's build tool
signalwire.core.config_loader.ConfigLoader.get: Python YAML/env config loader; Java uses standard Properties / application.yml patterns via the user's build tool
signalwire.core.config_loader.ConfigLoader.get_config: Python YAML/env config loader; Java uses standard Properties / application.yml patterns via the user's build tool
signalwire.core.config_loader.ConfigLoader.get_config_file: Python YAML/env config loader; Java uses standard Properties / application.yml patterns via the user's build tool
signalwire.core.config_loader.ConfigLoader.get_section: Python YAML/env config loader; Java uses standard Properties / application.yml patterns via the user's build tool
signalwire.core.config_loader.ConfigLoader.has_config: Python YAML/env config loader; Java uses standard Properties / application.yml patterns via the user's build tool
signalwire.core.config_loader.ConfigLoader.merge_with_env: Python YAML/env config loader; Java uses standard Properties / application.yml patterns via the user's build tool
signalwire.core.config_loader.ConfigLoader.substitute_vars: Python YAML/env config loader; Java uses standard Properties / application.yml patterns via the user's build tool
signalwire.core.contexts.Context.add_system_bullets: Python helper; Java's Context.addBullets covers both prompt and system cases via explicit flags on Step
signalwire.core.contexts.Context.add_system_section: Python helper; Java's Context.addSection covers both prompt and system cases via explicit flags on Step
signalwire.core.contexts.create_simple_context: convenience factory present only in Python; Java users call new Context(name).addStep(...) directly
signalwire.core.data_map.DataMap.webhook_expressions: Python ergonomic wrapper over addWebhook + addExpression; Java's DataMap.addWebhook / addExpression cover the same ground
signalwire.core.data_map.create_expression_tool: Python factory function; Java users instantiate DataMap and call addExpression directly
signalwire.core.data_map.create_simple_api_tool: Python factory function; Java users instantiate DataMap and call addWebhook directly
signalwire.core.logging_config.configure_logging: Python logging bootstrap helpers; Java uses java.util.logging configured via standard logging.properties — equivalent surface is exposed through signalwire.sdk.logging.Logger
signalwire.core.logging_config.get_execution_mode: Python logging bootstrap helpers; Java uses java.util.logging configured via standard logging.properties — equivalent surface is exposed through signalwire.sdk.logging.Logger
signalwire.core.logging_config.get_logger: Python logging bootstrap helpers; Java uses java.util.logging configured via standard logging.properties — equivalent surface is exposed through signalwire.sdk.logging.Logger
signalwire.core.logging_config.reset_logging_configuration: Python logging bootstrap helpers; Java uses java.util.logging configured via standard logging.properties — equivalent surface is exposed through signalwire.sdk.logging.Logger
signalwire.core.logging_config.strip_control_chars: Python logging bootstrap helpers; Java uses java.util.logging configured via standard logging.properties — equivalent surface is exposed through signalwire.sdk.logging.Logger
signalwire.core.mixins.ai_config_mixin.AIConfigMixin: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.add_function_include: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.add_hint: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.add_hints: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.add_internal_filler: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.add_language: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.add_mcp_server: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.add_pattern_hint: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.add_pronunciation: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.enable_debug_events: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.enable_mcp_server: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.set_function_includes: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.set_global_data: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.set_internal_fillers: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.set_languages: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.set_native_functions: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.set_param: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.set_params: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.set_post_prompt_llm_params: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.set_prompt_llm_params: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.set_pronunciations: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.ai_config_mixin.AIConfigMixin.update_global_data: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.auth_mixin.AuthMixin: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.auth_mixin.AuthMixin.get_basic_auth_credentials: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.auth_mixin.AuthMixin.validate_basic_auth: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.mcp_server_mixin.MCPServerMixin: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.prompt_mixin.PromptMixin: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.prompt_mixin.PromptMixin.contexts: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.prompt_mixin.PromptMixin.define_contexts: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.prompt_mixin.PromptMixin.get_post_prompt: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.prompt_mixin.PromptMixin.get_prompt: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.prompt_mixin.PromptMixin.prompt_add_section: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.prompt_mixin.PromptMixin.prompt_add_subsection: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.prompt_mixin.PromptMixin.prompt_add_to_section: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.prompt_mixin.PromptMixin.prompt_has_section: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.prompt_mixin.PromptMixin.reset_contexts: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.prompt_mixin.PromptMixin.set_post_prompt: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.prompt_mixin.PromptMixin.set_prompt_pom: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.prompt_mixin.PromptMixin.set_prompt_text: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.serverless_mixin.ServerlessMixin: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.serverless_mixin.ServerlessMixin.handle_serverless_request: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.skill_mixin.SkillMixin: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.skill_mixin.SkillMixin.add_skill: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.skill_mixin.SkillMixin.has_skill: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.skill_mixin.SkillMixin.list_skills: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.skill_mixin.SkillMixin.remove_skill: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.state_mixin.StateMixin: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.state_mixin.StateMixin.validate_tool_token: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.tool_mixin.ToolMixin: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.tool_mixin.ToolMixin.define_tool: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.tool_mixin.ToolMixin.define_tools: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.tool_mixin.ToolMixin.on_function_call: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.tool_mixin.ToolMixin.register_swaig_function: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.tool_mixin.ToolMixin.tool: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.web_mixin.WebMixin: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.web_mixin.WebMixin.as_router: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.web_mixin.WebMixin.enable_debug_routes: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.web_mixin.WebMixin.get_app: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.web_mixin.WebMixin.manual_set_proxy_url: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.web_mixin.WebMixin.on_request: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.web_mixin.WebMixin.on_swml_request: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.web_mixin.WebMixin.register_routing_callback: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.web_mixin.WebMixin.run: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.web_mixin.WebMixin.serve: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.web_mixin.WebMixin.set_dynamic_config_callback: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.mixins.web_mixin.WebMixin.setup_graceful_shutdown: Python composes AgentBase from mixins; Java uses a single flat AgentBase class — the mixin methods are folded into AgentBase
signalwire.core.pom_builder.PomBuilder: PomBuilder helper merged into AgentBase's prompt APIs in Java
signalwire.core.pom_builder.PomBuilder.__init__: PomBuilder helper merged into AgentBase's prompt APIs in Java
signalwire.core.pom_builder.PomBuilder.add_section: PomBuilder helper merged into AgentBase's prompt APIs in Java
signalwire.core.pom_builder.PomBuilder.add_subsection: PomBuilder helper merged into AgentBase's prompt APIs in Java
signalwire.core.pom_builder.PomBuilder.add_to_section: PomBuilder helper merged into AgentBase's prompt APIs in Java
signalwire.core.pom_builder.PomBuilder.from_sections: PomBuilder helper merged into AgentBase's prompt APIs in Java
signalwire.core.pom_builder.PomBuilder.get_section: PomBuilder helper merged into AgentBase's prompt APIs in Java
signalwire.core.pom_builder.PomBuilder.has_section: PomBuilder helper merged into AgentBase's prompt APIs in Java
signalwire.core.pom_builder.PomBuilder.render_markdown: PomBuilder helper merged into AgentBase's prompt APIs in Java
signalwire.core.pom_builder.PomBuilder.render_xml: PomBuilder helper merged into AgentBase's prompt APIs in Java
signalwire.core.pom_builder.PomBuilder.to_dict: PomBuilder helper merged into AgentBase's prompt APIs in Java
signalwire.core.pom_builder.PomBuilder.to_json: PomBuilder helper merged into AgentBase's prompt APIs in Java
signalwire.core.security.session_manager.SessionManager.activate_session: Sub-package alias for signalwire.core.security_config in Python; Java exposes SessionManager at signalwire.security.SessionManager
signalwire.core.security.session_manager.SessionManager.create_session: Sub-package alias for signalwire.core.security_config in Python; Java exposes SessionManager at signalwire.security.SessionManager
signalwire.core.security.session_manager.SessionManager.create_tool_token: Sub-package alias for signalwire.core.security_config in Python; Java exposes SessionManager at signalwire.security.SessionManager
signalwire.core.security.session_manager.SessionManager.debug_token: Sub-package alias for signalwire.core.security_config in Python; Java exposes SessionManager at signalwire.security.SessionManager
signalwire.core.security.session_manager.SessionManager.end_session: Sub-package alias for signalwire.core.security_config in Python; Java exposes SessionManager at signalwire.security.SessionManager
signalwire.core.security.session_manager.SessionManager.generate_token: Sub-package alias for signalwire.core.security_config in Python; Java exposes SessionManager at signalwire.security.SessionManager
signalwire.core.security.session_manager.SessionManager.get_session_metadata: Sub-package alias for signalwire.core.security_config in Python; Java exposes SessionManager at signalwire.security.SessionManager
signalwire.core.security.session_manager.SessionManager.set_session_metadata: Sub-package alias for signalwire.core.security_config in Python; Java exposes SessionManager at signalwire.security.SessionManager
signalwire.core.security.session_manager.SessionManager.validate_tool_token: Sub-package alias for signalwire.core.security_config in Python; Java exposes SessionManager at signalwire.security.SessionManager
signalwire.core.security.webhook_middleware.make_webhook_validation_dependency: Python's FastAPI-specific dependency factory; Java ships an idiomatic Servlet Filter (signalwire.security.WebhookFilter) and an HttpExchange-based hook on AgentBase that cover the same flow per porting-sdk/webhooks.md
signalwire.core.security_config.SecurityConfig: Python security-config dataclass; Java exposes equivalent configuration via AgentBase builder methods
signalwire.core.security_config.SecurityConfig.__init__: Python security-config dataclass; Java exposes equivalent configuration via AgentBase builder methods
signalwire.core.security_config.SecurityConfig.get_basic_auth: Python security-config dataclass; Java exposes equivalent configuration via AgentBase builder methods
signalwire.core.security_config.SecurityConfig.get_cors_config: Python security-config dataclass; Java exposes equivalent configuration via AgentBase builder methods
signalwire.core.security_config.SecurityConfig.get_security_headers: Python security-config dataclass; Java exposes equivalent configuration via AgentBase builder methods
signalwire.core.security_config.SecurityConfig.get_ssl_context_kwargs: Python security-config dataclass; Java exposes equivalent configuration via AgentBase builder methods
signalwire.core.security_config.SecurityConfig.get_url_scheme: Python security-config dataclass; Java exposes equivalent configuration via AgentBase builder methods
signalwire.core.security_config.SecurityConfig.load_from_env: Python security-config dataclass; Java exposes equivalent configuration via AgentBase builder methods
signalwire.core.security_config.SecurityConfig.log_config: Python security-config dataclass; Java exposes equivalent configuration via AgentBase builder methods
signalwire.core.security_config.SecurityConfig.should_allow_host: Python security-config dataclass; Java exposes equivalent configuration via AgentBase builder methods
signalwire.core.security_config.SecurityConfig.validate_ssl_config: Python security-config dataclass; Java exposes equivalent configuration via AgentBase builder methods
signalwire.core.skill_base.SkillBase.__init__: Python SkillBase supports plugin-discovery patterns and async helpers not applicable to Java; Java's SkillBase is a leaner abstract class
signalwire.core.skill_base.SkillBase.cleanup: Python SkillBase supports plugin-discovery patterns and async helpers not applicable to Java; Java's SkillBase is a leaner abstract class
signalwire.core.skill_base.SkillBase.define_tool: Python SkillBase supports plugin-discovery patterns and async helpers not applicable to Java; Java's SkillBase is a leaner abstract class
signalwire.core.skill_base.SkillBase.get_global_data: Python SkillBase supports plugin-discovery patterns and async helpers not applicable to Java; Java's SkillBase is a leaner abstract class
signalwire.core.skill_base.SkillBase.get_hints: Python SkillBase supports plugin-discovery patterns and async helpers not applicable to Java; Java's SkillBase is a leaner abstract class
signalwire.core.skill_base.SkillBase.get_instance_key: Python SkillBase supports plugin-discovery patterns and async helpers not applicable to Java; Java's SkillBase is a leaner abstract class
signalwire.core.skill_base.SkillBase.get_parameter_schema: Python SkillBase supports plugin-discovery patterns and async helpers not applicable to Java; Java's SkillBase is a leaner abstract class
signalwire.core.skill_base.SkillBase.get_prompt_sections: Python SkillBase supports plugin-discovery patterns and async helpers not applicable to Java; Java's SkillBase is a leaner abstract class
signalwire.core.skill_base.SkillBase.get_skill_data: Python SkillBase supports plugin-discovery patterns and async helpers not applicable to Java; Java's SkillBase is a leaner abstract class
signalwire.core.skill_base.SkillBase.register_tools: Python SkillBase supports plugin-discovery patterns and async helpers not applicable to Java; Java's SkillBase is a leaner abstract class
signalwire.core.skill_base.SkillBase.setup: Python SkillBase supports plugin-discovery patterns and async helpers not applicable to Java; Java's SkillBase is a leaner abstract class
signalwire.core.skill_base.SkillBase.update_skill_data: Python SkillBase supports plugin-discovery patterns and async helpers not applicable to Java; Java's SkillBase is a leaner abstract class
signalwire.core.skill_base.SkillBase.validate_env_vars: Python SkillBase supports plugin-discovery patterns and async helpers not applicable to Java; Java's SkillBase is a leaner abstract class
signalwire.core.skill_base.SkillBase.validate_packages: Python SkillBase supports plugin-discovery patterns and async helpers not applicable to Java; Java's SkillBase is a leaner abstract class
signalwire.core.skill_manager.SkillManager.get_skill: Java's SkillManager mirrors Python's public surface minus the plugin-discovery API (which relies on Python's entry_points)
signalwire.core.skill_manager.SkillManager.list_loaded_skills: Java's SkillManager mirrors Python's public surface minus the plugin-discovery API (which relies on Python's entry_points)
signalwire.core.skill_manager.SkillManager.load_skill: Java's SkillManager mirrors Python's public surface minus the plugin-discovery API (which relies on Python's entry_points)
signalwire.core.skill_manager.SkillManager.unload_skill: Java's SkillManager mirrors Python's public surface minus the plugin-discovery API (which relies on Python's entry_points)
signalwire.core.swaig_function.SWAIGFunction: Python SWAIGFunction DTO; Java uses ToolDefinition/ToolHandler as the equivalent pair
signalwire.core.swaig_function.SWAIGFunction.__call__: Python SWAIGFunction DTO; Java uses ToolDefinition/ToolHandler as the equivalent pair
signalwire.core.swaig_function.SWAIGFunction.__init__: Python SWAIGFunction DTO; Java uses ToolDefinition/ToolHandler as the equivalent pair
signalwire.core.swaig_function.SWAIGFunction.execute: Python SWAIGFunction DTO; Java uses ToolDefinition/ToolHandler as the equivalent pair
signalwire.core.swaig_function.SWAIGFunction.to_swaig: Python SWAIGFunction DTO; Java uses ToolDefinition/ToolHandler as the equivalent pair
signalwire.core.swaig_function.SWAIGFunction.validate_args: Python SWAIGFunction DTO; Java uses ToolDefinition/ToolHandler as the equivalent pair
signalwire.core.swml_builder.SWMLBuilder: SWMLBuilder methods are consumed by AgentBase internally in Java; users build SWML via the Document/Service helpers
signalwire.core.swml_builder.SWMLBuilder.__getattr__: SWMLBuilder methods are consumed by AgentBase internally in Java; users build SWML via the Document/Service helpers
signalwire.core.swml_builder.SWMLBuilder.__init__: SWMLBuilder methods are consumed by AgentBase internally in Java; users build SWML via the Document/Service helpers
signalwire.core.swml_builder.SWMLBuilder.add_section: SWMLBuilder methods are consumed by AgentBase internally in Java; users build SWML via the Document/Service helpers
signalwire.core.swml_builder.SWMLBuilder.ai: SWMLBuilder methods are consumed by AgentBase internally in Java; users build SWML via the Document/Service helpers
signalwire.core.swml_builder.SWMLBuilder.answer: SWMLBuilder methods are consumed by AgentBase internally in Java; users build SWML via the Document/Service helpers
signalwire.core.swml_builder.SWMLBuilder.build: SWMLBuilder methods are consumed by AgentBase internally in Java; users build SWML via the Document/Service helpers
signalwire.core.swml_builder.SWMLBuilder.hangup: SWMLBuilder methods are consumed by AgentBase internally in Java; users build SWML via the Document/Service helpers
signalwire.core.swml_builder.SWMLBuilder.play: SWMLBuilder methods are consumed by AgentBase internally in Java; users build SWML via the Document/Service helpers
signalwire.core.swml_builder.SWMLBuilder.render: SWMLBuilder methods are consumed by AgentBase internally in Java; users build SWML via the Document/Service helpers
signalwire.core.swml_builder.SWMLBuilder.reset: SWMLBuilder methods are consumed by AgentBase internally in Java; users build SWML via the Document/Service helpers
signalwire.core.swml_builder.SWMLBuilder.say: SWMLBuilder methods are consumed by AgentBase internally in Java; users build SWML via the Document/Service helpers
signalwire.core.swml_handler.AIVerbHandler: Python's verb-handler registry is folded into Java's Schema and Document helpers
signalwire.core.swml_handler.AIVerbHandler.build_config: Python's verb-handler registry is folded into Java's Schema and Document helpers
signalwire.core.swml_handler.AIVerbHandler.get_verb_name: Python's verb-handler registry is folded into Java's Schema and Document helpers
signalwire.core.swml_handler.AIVerbHandler.validate_config: Python's verb-handler registry is folded into Java's Schema and Document helpers
signalwire.core.swml_handler.SWMLVerbHandler: Python's verb-handler registry is folded into Java's Schema and Document helpers
signalwire.core.swml_handler.SWMLVerbHandler.build_config: Python's verb-handler registry is folded into Java's Schema and Document helpers
signalwire.core.swml_handler.SWMLVerbHandler.get_verb_name: Python's verb-handler registry is folded into Java's Schema and Document helpers
signalwire.core.swml_handler.SWMLVerbHandler.validate_config: Python's verb-handler registry is folded into Java's Schema and Document helpers
signalwire.core.swml_handler.VerbHandlerRegistry: Python's verb-handler registry is folded into Java's Schema and Document helpers
signalwire.core.swml_handler.VerbHandlerRegistry.__init__: Python's verb-handler registry is folded into Java's Schema and Document helpers
signalwire.core.swml_handler.VerbHandlerRegistry.get_handler: Python's verb-handler registry is folded into Java's Schema and Document helpers
signalwire.core.swml_handler.VerbHandlerRegistry.has_handler: Python's verb-handler registry is folded into Java's Schema and Document helpers
signalwire.core.swml_handler.VerbHandlerRegistry.register_handler: Python's verb-handler registry is folded into Java's Schema and Document helpers
signalwire.core.swml_renderer.SwmlRenderer: SWML rendering is owned by Java's Document class directly
signalwire.core.swml_renderer.SwmlRenderer.render_function_response_swml: SWML rendering is owned by Java's Document class directly
signalwire.core.swml_renderer.SwmlRenderer.render_swml: SWML rendering is owned by Java's Document class directly
signalwire.core.swml_service.SWMLService: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.__getattr__: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.__init__: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.add_section: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.add_verb: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.add_verb_to_section: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.as_router: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.extract_sip_username: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.full_validation_enabled: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.get_basic_auth_credentials: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.get_document: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.manual_set_proxy_url: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.on_request: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.register_routing_callback: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.register_verb_handler: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.render_document: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.reset_document: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.serve: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.core.swml_service.SWMLService.stop: Python's SWMLService wraps a stand-alone SWML endpoint; Java's AgentBase embeds the SWML-serving path directly
signalwire.list_skills: Python top-level helper; Java exposes SkillRegistry.list()
signalwire.livewire.Agent: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.Agent.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.Agent.llm_node: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.Agent.on_enter: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.Agent.on_exit: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.Agent.on_user_turn_completed: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.Agent.session: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.Agent.stt_node: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.Agent.tts_node: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.Agent.update_instructions: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.Agent.update_tools: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.AgentHandoff: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.AgentHandoff.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.AgentServer: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.AgentServer.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.AgentServer.rtc_session: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.AgentSession: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.AgentSession.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.AgentSession.generate_reply: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.AgentSession.history: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.AgentSession.interrupt: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.AgentSession.say: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.AgentSession.start: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.AgentSession.update_agent: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.AgentSession.userdata: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.ChatContext: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.ChatContext.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.ChatContext.append: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.InferenceLLM: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.InferenceLLM.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.InferenceSTT: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.InferenceSTT.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.InferenceTTS: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.InferenceTTS.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.JobContext: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.JobContext.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.JobContext.connect: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.JobContext.wait_for_participant: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.JobProcess: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.JobProcess.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.Room: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.RunContext: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.RunContext.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.RunContext.userdata: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.StopResponse: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.ToolError: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.function_tool: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.plugins.CartesiaTTS: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.plugins.CartesiaTTS.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.plugins.DeepgramSTT: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.plugins.DeepgramSTT.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.plugins.ElevenLabsTTS: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.plugins.ElevenLabsTTS.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.plugins.OpenAILLM: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.plugins.OpenAILLM.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.plugins.SileroVAD: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.plugins.SileroVAD.__init__: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.plugins.SileroVAD.load: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.livewire.run_app: LiveKit compatibility shim is Python-specific; Java apps interop with realtime systems directly via the SignalWire REST/RELAY APIs
signalwire.mcp_gateway.gateway_service.MCPGateway: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.gateway_service.MCPGateway.__init__: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.gateway_service.MCPGateway.run: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.gateway_service.MCPGateway.shutdown: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.gateway_service.main: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPClient: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPClient.__init__: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPClient.call_method: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPClient.call_tool: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPClient.get_tools: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPClient.start: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPClient.stop: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPManager: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPManager.__init__: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPManager.create_client: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPManager.get_service: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPManager.get_service_tools: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPManager.list_services: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPManager.shutdown: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPManager.validate_services: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPService: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPService.__hash__: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.mcp_manager.MCPService.__post_init__: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.session_manager.Session: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.session_manager.Session.is_alive: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.session_manager.Session.is_expired: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.session_manager.Session.touch: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.session_manager.SessionManager: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.session_manager.SessionManager.__init__: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.session_manager.SessionManager.close_session: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.session_manager.SessionManager.create_session: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.session_manager.SessionManager.get_service_session_count: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.session_manager.SessionManager.get_session: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.session_manager.SessionManager.list_sessions: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.mcp_gateway.session_manager.SessionManager.shutdown: MCP gateway service is not ported; Java AgentBase exposes MCP client-side integration only
signalwire.pom.pom_tool.detect_file_format: Python CLI for rendering POM files; Java embeds POM rendering directly in AgentBase's prompt builder with no separate CLI
signalwire.pom.pom_tool.load_pom: Python CLI for rendering POM files; Java embeds POM rendering directly in AgentBase's prompt builder with no separate CLI
signalwire.pom.pom_tool.main: Python CLI for rendering POM files; Java embeds POM rendering directly in AgentBase's prompt builder with no separate CLI
signalwire.pom.pom_tool.render_pom: Python CLI for rendering POM files; Java embeds POM rendering directly in AgentBase's prompt builder with no separate CLI
signalwire.prefabs.concierge.ConciergeAgent.check_availability: Python prefab exposes additional internal helpers not needed in Java's equivalent prefab class
signalwire.prefabs.concierge.ConciergeAgent.get_directions: Python prefab exposes additional internal helpers not needed in Java's equivalent prefab class
signalwire.prefabs.concierge.ConciergeAgent.on_summary: Python prefab exposes additional internal helpers not needed in Java's equivalent prefab class
signalwire.prefabs.faq_bot.FAQBotAgent.on_summary: Python prefab exposes additional internal helpers not needed in Java's equivalent prefab class
signalwire.prefabs.faq_bot.FAQBotAgent.search_faqs: Python prefab exposes additional internal helpers not needed in Java's equivalent prefab class
signalwire.prefabs.info_gatherer.InfoGathererAgent.on_swml_request: Python prefab exposes additional internal helpers not needed in Java's equivalent prefab class
signalwire.prefabs.info_gatherer.InfoGathererAgent.set_question_callback: Python prefab exposes additional internal helpers not needed in Java's equivalent prefab class
signalwire.prefabs.info_gatherer.InfoGathererAgent.start_questions: Python prefab exposes additional internal helpers not needed in Java's equivalent prefab class
signalwire.prefabs.info_gatherer.InfoGathererAgent.submit_answer: Python prefab exposes additional internal helpers not needed in Java's equivalent prefab class
signalwire.prefabs.receptionist.ReceptionistAgent.on_summary: Python prefab exposes additional internal helpers not needed in Java's equivalent prefab class
signalwire.prefabs.survey.SurveyAgent.log_response: Python prefab exposes additional internal helpers not needed in Java's equivalent prefab class
signalwire.prefabs.survey.SurveyAgent.on_summary: Python prefab exposes additional internal helpers not needed in Java's equivalent prefab class
signalwire.prefabs.survey.SurveyAgent.validate_response: Python prefab exposes additional internal helpers not needed in Java's equivalent prefab class
signalwire.relay.call.Action.wait: Java's Call class exposes the equivalent surface; the listed Python method is an internal helper or uses a Python-specific signature (e.g. kwargs) that has no direct Java analog
signalwire.relay.call.Call.wait_for: Java's Call class exposes the equivalent surface; the listed Python method is an internal helper or uses a Python-specific signature (e.g. kwargs) that has no direct Java analog
signalwire.relay.call.Call.wait_for_ended: Java's Call class exposes the equivalent surface; the listed Python method is an internal helper or uses a Python-specific signature (e.g. kwargs) that has no direct Java analog
signalwire.relay.call.CollectAction.volume: Java's Call class exposes the equivalent surface; the listed Python method is an internal helper or uses a Python-specific signature (e.g. kwargs) that has no direct Java analog
signalwire.relay.call.FaxAction: Java's Call class exposes the equivalent surface; the listed Python method is an internal helper or uses a Python-specific signature (e.g. kwargs) that has no direct Java analog
signalwire.relay.call.FaxAction.__init__: Java's Call class exposes the equivalent surface; the listed Python method is an internal helper or uses a Python-specific signature (e.g. kwargs) that has no direct Java analog
signalwire.relay.call.FaxAction.stop: Java's Call class exposes the equivalent surface; the listed Python method is an internal helper or uses a Python-specific signature (e.g. kwargs) that has no direct Java analog
signalwire.relay.call.StandaloneCollectAction: Java's Call class exposes the equivalent surface; the listed Python method is an internal helper or uses a Python-specific signature (e.g. kwargs) that has no direct Java analog
signalwire.relay.call.StandaloneCollectAction.__init__: Java's Call class exposes the equivalent surface; the listed Python method is an internal helper or uses a Python-specific signature (e.g. kwargs) that has no direct Java analog
signalwire.relay.call.StandaloneCollectAction.start_input_timers: Java's Call class exposes the equivalent surface; the listed Python method is an internal helper or uses a Python-specific signature (e.g. kwargs) that has no direct Java analog
signalwire.relay.call.StandaloneCollectAction.stop: Java's Call class exposes the equivalent surface; the listed Python method is an internal helper or uses a Python-specific signature (e.g. kwargs) that has no direct Java analog
signalwire.relay.client.RelayClient.__aenter__: Java's RelayClient builder provides equivalent configuration; Python exposes additional internal helpers
signalwire.relay.client.RelayClient.__aexit__: Java's RelayClient builder provides equivalent configuration; Python exposes additional internal helpers
signalwire.relay.client.RelayClient.__del__: Java's RelayClient builder provides equivalent configuration; Python exposes additional internal helpers
signalwire.relay.client.RelayClient.__init__: Java's RelayClient builder provides equivalent configuration; Python exposes additional internal helpers
signalwire.relay.client.RelayClient.connect: Java's RelayClient builder provides equivalent configuration; Python exposes additional internal helpers
signalwire.relay.client.RelayClient.relay_protocol: Java's RelayClient builder provides equivalent configuration; Python exposes additional internal helpers
signalwire.relay.client.RelayError: Java's RelayClient builder provides equivalent configuration; Python exposes additional internal helpers
signalwire.relay.client.RelayError.__init__: Java's RelayClient builder provides equivalent configuration; Python exposes additional internal helpers
signalwire.relay.event.CallReceiveEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.CallStateEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.CallingErrorEvent: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.CallingErrorEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.CollectEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.ConferenceEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.ConnectEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.DenoiseEvent: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.DenoiseEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.DetectEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.DialEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.EchoEvent: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.EchoEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.FaxEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.HoldEvent: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.HoldEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.MessageReceiveEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.MessageStateEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.PayEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.PlayEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.QueueEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.RecordEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.ReferEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.RelayEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.SendDigitsEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.StreamEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.TapEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.TranscribeEvent.from_payload: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.event.parse_event: Java's RelayEvent family has the equivalent static-class hierarchy; the Python method is an internal accessor
signalwire.relay.message.Message.result: Python Message exposes additional internal helpers; Java's Message sticks to the public send/reply API
signalwire.relay.message.Message.wait: Python Message exposes additional internal helpers; Java's Message sticks to the public send/reply API
signalwire.rest._base.BaseResource: Python splits base resource classes across _base.py; Java exposes the equivalent via CrudResource / HttpClient / RestError
signalwire.rest._base.BaseResource.__init__: Python splits base resource classes across _base.py; Java exposes the equivalent via CrudResource / HttpClient / RestError
signalwire.rest._base.CrudWithAddresses: Python splits base resource classes across _base.py; Java exposes the equivalent via CrudResource / HttpClient / RestError
signalwire.rest._base.CrudWithAddresses.list_addresses: Python splits base resource classes across _base.py; Java exposes the equivalent via CrudResource / HttpClient / RestError
signalwire.rest._base.HttpClient.delete: Python splits base resource classes across _base.py; Java exposes the equivalent via CrudResource / HttpClient / RestError
signalwire.rest._base.HttpClient.get: Python splits base resource classes across _base.py; Java exposes the equivalent via CrudResource / HttpClient / RestError
signalwire.rest._base.HttpClient.patch: Python splits base resource classes across _base.py; Java exposes the equivalent via CrudResource / HttpClient / RestError
signalwire.rest._base.HttpClient.post: Python splits base resource classes across _base.py; Java exposes the equivalent via CrudResource / HttpClient / RestError
signalwire.rest._base.HttpClient.put: Python splits base resource classes across _base.py; Java exposes the equivalent via CrudResource / HttpClient / RestError
signalwire.rest._base.SignalWireRestError: Python splits base resource classes across _base.py; Java exposes the equivalent via CrudResource / HttpClient / RestError
signalwire.rest._base.SignalWireRestError.__init__: Python splits base resource classes across _base.py; Java exposes the equivalent via CrudResource / HttpClient / RestError
signalwire.rest._pagination.PaginatedIterator: Python pagination iterator; Java returns raw Maps and users drive pagination via query params on CrudResource.list()
signalwire.rest._pagination.PaginatedIterator.__init__: Python pagination iterator; Java returns raw Maps and users drive pagination via query params on CrudResource.list()
signalwire.rest._pagination.PaginatedIterator.__iter__: Python pagination iterator; Java returns raw Maps and users drive pagination via query params on CrudResource.list()
signalwire.rest._pagination.PaginatedIterator.__next__: Python pagination iterator; Java returns raw Maps and users drive pagination via query params on CrudResource.list()
signalwire.rest.client.RestClient.__init__: Java's RestClient constructor is package-private — public initialization goes through RestClient.builder()
signalwire.rest.namespaces.addresses.AddressesResource: Java's FabricNamespace.addresses() returns a CrudResource that covers AddressesResource's surface
signalwire.rest.namespaces.addresses.AddressesResource.__init__: Java's FabricNamespace.addresses() returns a CrudResource that covers AddressesResource's surface
signalwire.rest.namespaces.addresses.AddressesResource.create: Java's FabricNamespace.addresses() returns a CrudResource that covers AddressesResource's surface
signalwire.rest.namespaces.addresses.AddressesResource.delete: Java's FabricNamespace.addresses() returns a CrudResource that covers AddressesResource's surface
signalwire.rest.namespaces.addresses.AddressesResource.get: Java's FabricNamespace.addresses() returns a CrudResource that covers AddressesResource's surface
signalwire.rest.namespaces.addresses.AddressesResource.list: Java's FabricNamespace.addresses() returns a CrudResource that covers AddressesResource's surface
signalwire.rest.namespaces.calling.CallingNamespace.ai_hold: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.ai_message: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.ai_stop: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.ai_unhold: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.collect: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.collect_start_input_timers: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.collect_stop: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.denoise: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.denoise_stop: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.detect: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.detect_stop: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.dial: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.disconnect: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.end: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.live_transcribe: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.live_translate: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.play: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.play_pause: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.play_resume: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.play_stop: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.play_volume: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.receive_fax_stop: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.record: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.record_pause: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.record_resume: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.record_stop: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.refer: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.send_fax_stop: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.stream: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.stream_stop: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.tap: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.tap_stop: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.transcribe: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.transcribe_stop: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.transfer: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.update: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.calling.CallingNamespace.user_event: Java exposes the Calling API through a flat CallingNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.chat.ChatResource: Java's ChatNamespace exposes the equivalent operations via CrudResource accessors
signalwire.rest.namespaces.chat.ChatResource.__init__: Java's ChatNamespace exposes the equivalent operations via CrudResource accessors
signalwire.rest.namespaces.chat.ChatResource.create_token: Java's ChatNamespace exposes the equivalent operations via CrudResource accessors
signalwire.rest.namespaces.compat.CompatAccounts: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatAccounts.__init__: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatAccounts.create: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatAccounts.get: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatAccounts.list: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatAccounts.update: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatApplications: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatApplications.update: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatCalls: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatCalls.start_recording: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatCalls.start_stream: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatCalls.stop_stream: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatCalls.update: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatCalls.update_recording: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatConferences: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatConferences.delete_recording: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatConferences.get: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatConferences.get_participant: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatConferences.get_recording: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatConferences.list: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatConferences.list_participants: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatConferences.list_recordings: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatConferences.remove_participant: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatConferences.start_stream: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatConferences.stop_stream: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatConferences.update: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatConferences.update_participant: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatConferences.update_recording: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatFaxes: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatFaxes.delete_media: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatFaxes.get_media: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatFaxes.list_media: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatFaxes.update: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatLamlBins: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatLamlBins.update: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatMessages: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatMessages.delete_media: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatMessages.get_media: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatMessages.list_media: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatMessages.update: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatPhoneNumbers: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatPhoneNumbers.__init__: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatPhoneNumbers.delete: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatPhoneNumbers.get: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatPhoneNumbers.import_number: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatPhoneNumbers.list: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatPhoneNumbers.list_available_countries: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatPhoneNumbers.purchase: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatPhoneNumbers.search_local: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatPhoneNumbers.search_toll_free: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatPhoneNumbers.update: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatQueues: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatQueues.dequeue_member: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatQueues.get_member: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatQueues.list_members: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatQueues.update: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatRecordings: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatRecordings.delete: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatRecordings.get: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatRecordings.list: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatTokens: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatTokens.create: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatTokens.delete: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatTokens.update: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatTranscriptions: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatTranscriptions.delete: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatTranscriptions.get: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.compat.CompatTranscriptions.list: Java exposes the Compat API through a flat CompatNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.datasphere.DatasphereDocuments: Java exposes the Datasphere API through DatasphereNamespace + CrudResource accessors
signalwire.rest.namespaces.datasphere.DatasphereDocuments.__init__: Java exposes the Datasphere API through DatasphereNamespace + CrudResource accessors
signalwire.rest.namespaces.datasphere.DatasphereDocuments.delete_chunk: Java exposes the Datasphere API through DatasphereNamespace + CrudResource accessors
signalwire.rest.namespaces.datasphere.DatasphereDocuments.get_chunk: Java exposes the Datasphere API through DatasphereNamespace + CrudResource accessors
signalwire.rest.namespaces.datasphere.DatasphereDocuments.list_chunks: Java exposes the Datasphere API through DatasphereNamespace + CrudResource accessors
signalwire.rest.namespaces.datasphere.DatasphereDocuments.search: Java exposes the Datasphere API through DatasphereNamespace + CrudResource accessors
signalwire.rest.namespaces.fabric.AutoMaterializedWebhook: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.AutoMaterializedWebhook.create: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.CallFlowsResource: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.CallFlowsResource.deploy_version: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.CallFlowsResource.list_addresses: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.CallFlowsResource.list_versions: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.ConferenceRoomsResource: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.ConferenceRoomsResource.list_addresses: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.CxmlApplicationsResource: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.CxmlApplicationsResource.create: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.CxmlWebhooksResource: auto-materialized by phone_numbers.set_cxml_webhook; Java doesn't expose the CxmlWebhooksResource class to avoid the trap of hand-materializing webhooks out of band — per porting-sdk/phone-binding.md
signalwire.rest.namespaces.fabric.FabricAddresses: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.FabricAddresses.get: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.FabricAddresses.list: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.FabricResource: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.FabricResourcePUT: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.FabricTokens: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.FabricTokens.__init__: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.FabricTokens.create_embed_token: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.FabricTokens.create_guest_token: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.FabricTokens.create_invite_token: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.FabricTokens.create_subscriber_token: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.FabricTokens.refresh_subscriber_token: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.GenericResources: Java routes generic Fabric operations through CrudResource accessors on FabricNamespace; the per-phone-route bind is handled by phone_numbers.set_* helpers per porting-sdk/phone-binding.md
signalwire.rest.namespaces.fabric.GenericResources.assign_domain_application: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.GenericResources.assign_phone_route: narrow-use legacy API; Java ships only the good path via phone_numbers.set_* helpers, per porting-sdk/phone-binding.md
signalwire.rest.namespaces.fabric.GenericResources.delete: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.GenericResources.get: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.GenericResources.list: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.GenericResources.list_addresses: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.SubscribersResource: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.SubscribersResource.create_sip_endpoint: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.SubscribersResource.delete_sip_endpoint: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.SubscribersResource.get_sip_endpoint: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.SubscribersResource.list_sip_endpoints: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.SubscribersResource.update_sip_endpoint: Java's FabricNamespace exposes subresources through CrudResource accessors instead of Python's per-subresource class; see rest/docs/fabric.md for the mapping
signalwire.rest.namespaces.fabric.SwmlWebhooksResource: auto-materialized by phone_numbers.set_swml_webhook; Java doesn't expose the SwmlWebhooksResource class to avoid the trap of hand-materializing webhooks out of band — per porting-sdk/phone-binding.md
signalwire.rest.namespaces.imported_numbers.ImportedNumbersResource: Java's PhoneNumbersNamespace covers imported numbers via the standard list/create flow
signalwire.rest.namespaces.imported_numbers.ImportedNumbersResource.__init__: Java's PhoneNumbersNamespace covers imported numbers via the standard list/create flow
signalwire.rest.namespaces.imported_numbers.ImportedNumbersResource.create: Java's PhoneNumbersNamespace covers imported numbers via the standard list/create flow
signalwire.rest.namespaces.logs.ConferenceLogs: Java ships a flat LogsNamespace that routes to the voice/message/fax/conference logs; Python splits into one class per log family
signalwire.rest.namespaces.logs.ConferenceLogs.list: Java ships a flat LogsNamespace that routes to the voice/message/fax/conference logs; Python splits into one class per log family
signalwire.rest.namespaces.logs.FaxLogs: Java ships a flat LogsNamespace that routes to the voice/message/fax/conference logs; Python splits into one class per log family
signalwire.rest.namespaces.logs.FaxLogs.get: Java ships a flat LogsNamespace that routes to the voice/message/fax/conference logs; Python splits into one class per log family
signalwire.rest.namespaces.logs.FaxLogs.list: Java ships a flat LogsNamespace that routes to the voice/message/fax/conference logs; Python splits into one class per log family
signalwire.rest.namespaces.logs.LogsNamespace: Java ships a flat LogsNamespace that routes to the voice/message/fax/conference logs; Python splits into one class per log family
signalwire.rest.namespaces.logs.LogsNamespace.__init__: Java ships a flat LogsNamespace that routes to the voice/message/fax/conference logs; Python splits into one class per log family
signalwire.rest.namespaces.logs.MessageLogs: Java ships a flat LogsNamespace that routes to the voice/message/fax/conference logs; Python splits into one class per log family
signalwire.rest.namespaces.logs.MessageLogs.get: Java ships a flat LogsNamespace that routes to the voice/message/fax/conference logs; Python splits into one class per log family
signalwire.rest.namespaces.logs.MessageLogs.list: Java ships a flat LogsNamespace that routes to the voice/message/fax/conference logs; Python splits into one class per log family
signalwire.rest.namespaces.logs.VoiceLogs: Java ships a flat LogsNamespace that routes to the voice/message/fax/conference logs; Python splits into one class per log family
signalwire.rest.namespaces.logs.VoiceLogs.get: Java ships a flat LogsNamespace that routes to the voice/message/fax/conference logs; Python splits into one class per log family
signalwire.rest.namespaces.logs.VoiceLogs.list: Java ships a flat LogsNamespace that routes to the voice/message/fax/conference logs; Python splits into one class per log family
signalwire.rest.namespaces.logs.VoiceLogs.list_events: Java ships a flat LogsNamespace that routes to the voice/message/fax/conference logs; Python splits into one class per log family
signalwire.rest.namespaces.lookup.LookupResource: Java's NumberLookupNamespace exposes the equivalent operations
signalwire.rest.namespaces.lookup.LookupResource.__init__: Java's NumberLookupNamespace exposes the equivalent operations
signalwire.rest.namespaces.lookup.LookupResource.phone_number: Java's NumberLookupNamespace exposes the equivalent operations
signalwire.rest.namespaces.mfa.MfaResource: MFA API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.mfa.MfaResource.__init__: MFA API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.mfa.MfaResource.call: MFA API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.mfa.MfaResource.sms: MFA API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.mfa.MfaResource.verify: MFA API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.number_groups.NumberGroupsResource: Number-groups API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.number_groups.NumberGroupsResource.__init__: Number-groups API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.number_groups.NumberGroupsResource.add_membership: Number-groups API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.number_groups.NumberGroupsResource.delete_membership: Number-groups API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.number_groups.NumberGroupsResource.get_membership: Number-groups API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.number_groups.NumberGroupsResource.list_memberships: Number-groups API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.project.ProjectTokens: Java merges Project tokens/config into a flat ProjectNamespace
signalwire.rest.namespaces.project.ProjectTokens.__init__: Java merges Project tokens/config into a flat ProjectNamespace
signalwire.rest.namespaces.project.ProjectTokens.create: Java merges Project tokens/config into a flat ProjectNamespace
signalwire.rest.namespaces.project.ProjectTokens.delete: Java merges Project tokens/config into a flat ProjectNamespace
signalwire.rest.namespaces.project.ProjectTokens.update: Java merges Project tokens/config into a flat ProjectNamespace
signalwire.rest.namespaces.pubsub.PubSubResource: Java exposes Pub/Sub through PubSubNamespace with list/create operations on a CrudResource
signalwire.rest.namespaces.pubsub.PubSubResource.__init__: Java exposes Pub/Sub through PubSubNamespace with list/create operations on a CrudResource
signalwire.rest.namespaces.pubsub.PubSubResource.create_token: Java exposes Pub/Sub through PubSubNamespace with list/create operations on a CrudResource
signalwire.rest.namespaces.queues.QueuesResource: Java's QueueNamespace exposes the queues API via CrudResource accessors
signalwire.rest.namespaces.queues.QueuesResource.__init__: Java's QueueNamespace exposes the queues API via CrudResource accessors
signalwire.rest.namespaces.queues.QueuesResource.get_member: Java's QueueNamespace exposes the queues API via CrudResource accessors
signalwire.rest.namespaces.queues.QueuesResource.get_next_member: Java's QueueNamespace exposes the queues API via CrudResource accessors
signalwire.rest.namespaces.queues.QueuesResource.list_members: Java's QueueNamespace exposes the queues API via CrudResource accessors
signalwire.rest.namespaces.recordings.RecordingsResource: Java's RecordingNamespace exposes the recordings API via CrudResource accessors
signalwire.rest.namespaces.recordings.RecordingsResource.__init__: Java's RecordingNamespace exposes the recordings API via CrudResource accessors
signalwire.rest.namespaces.recordings.RecordingsResource.delete: Java's RecordingNamespace exposes the recordings API via CrudResource accessors
signalwire.rest.namespaces.recordings.RecordingsResource.get: Java's RecordingNamespace exposes the recordings API via CrudResource accessors
signalwire.rest.namespaces.recordings.RecordingsResource.list: Java's RecordingNamespace exposes the recordings API via CrudResource accessors
signalwire.rest.namespaces.registry.RegistryBrands: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryBrands.create: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryBrands.create_campaign: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryBrands.get: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryBrands.list: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryBrands.list_campaigns: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryCampaigns: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryCampaigns.create_order: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryCampaigns.get: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryCampaigns.list_numbers: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryCampaigns.list_orders: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryCampaigns.update: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryNamespace: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryNamespace.__init__: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryNumbers: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryNumbers.delete: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryOrders: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.registry.RegistryOrders.get: Java exposes the 10DLC/TCR registry through a flat CampaignNamespace + CrudResource accessors; Python splits into one class per subresource
signalwire.rest.namespaces.short_codes.ShortCodesResource: Short codes not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.short_codes.ShortCodesResource.__init__: Short codes not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.short_codes.ShortCodesResource.get: Short codes not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.short_codes.ShortCodesResource.list: Short codes not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.short_codes.ShortCodesResource.update: Short codes not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.sip_profile.SipProfileResource: SIP profile API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.sip_profile.SipProfileResource.__init__: SIP profile API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.sip_profile.SipProfileResource.get: SIP profile API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.sip_profile.SipProfileResource.update: SIP profile API not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.verified_callers.VerifiedCallersResource: Verified callers not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.verified_callers.VerifiedCallersResource.__init__: Verified callers not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.verified_callers.VerifiedCallersResource.redial_verification: Verified callers not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.verified_callers.VerifiedCallersResource.submit_verification: Verified callers not yet exposed as a first-class namespace in Java — available via RestClient HTTP primitives
signalwire.rest.namespaces.video.VideoConferenceTokens: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoConferenceTokens.get: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoConferenceTokens.reset: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoConferences: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoConferences.create_stream: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoConferences.list_conference_tokens: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoConferences.list_streams: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRoomRecordings: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRoomRecordings.delete: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRoomRecordings.get: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRoomRecordings.list: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRoomRecordings.list_events: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRoomSessions: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRoomSessions.get: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRoomSessions.list: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRoomSessions.list_events: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRoomSessions.list_members: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRoomSessions.list_recordings: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRoomTokens: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRoomTokens.create: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRooms: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRooms.create_stream: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoRooms.list_streams: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoStreams: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoStreams.delete: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoStreams.get: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.rest.namespaces.video.VideoStreams.update: Java exposes the Video API through a flat VideoNamespace + CrudResource accessors; Python splits into one class per resource
signalwire.run_agent: Python top-level helper; Java users call AgentBase.run() on their built agent
signalwire.search.document_processor.DocumentProcessor: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.document_processor.DocumentProcessor.__init__: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.document_processor.DocumentProcessor.create_chunks: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.index_builder.IndexBuilder: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.index_builder.IndexBuilder.__init__: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.index_builder.IndexBuilder.build_index: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.index_builder.IndexBuilder.build_index_from_sources: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.index_builder.IndexBuilder.validate_index: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.migration.SearchIndexMigrator: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.migration.SearchIndexMigrator.__init__: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.migration.SearchIndexMigrator.get_index_info: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.migration.SearchIndexMigrator.migrate_pgvector_to_sqlite: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.migration.SearchIndexMigrator.migrate_sqlite_to_pgvector: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.models.resolve_model_alias: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.pgvector_backend.PgVectorBackend: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.pgvector_backend.PgVectorBackend.__init__: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.pgvector_backend.PgVectorBackend.close: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.pgvector_backend.PgVectorBackend.create_schema: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.pgvector_backend.PgVectorBackend.delete_collection: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.pgvector_backend.PgVectorBackend.get_stats: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.pgvector_backend.PgVectorBackend.list_collections: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.pgvector_backend.PgVectorBackend.store_chunks: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.pgvector_backend.PgVectorSearchBackend: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.pgvector_backend.PgVectorSearchBackend.__init__: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.pgvector_backend.PgVectorSearchBackend.close: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.pgvector_backend.PgVectorSearchBackend.fetch_candidates: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.pgvector_backend.PgVectorSearchBackend.get_stats: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.pgvector_backend.PgVectorSearchBackend.search: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.query_processor.detect_language: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.query_processor.ensure_nltk_resources: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.query_processor.get_synonyms: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.query_processor.get_wordnet_pos: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.query_processor.load_spacy_model: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.query_processor.preprocess_document_content: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.query_processor.preprocess_query: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.query_processor.remove_duplicate_words: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.query_processor.set_global_model: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.query_processor.vectorize_query: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.search_engine.SearchEngine: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.search_engine.SearchEngine.__init__: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.search_engine.SearchEngine.get_stats: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.search_engine.SearchEngine.search: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.search_service.SearchService: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.search_service.SearchService.__init__: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.search_service.SearchService.search_direct: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.search_service.SearchService.start: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.search.search_service.SearchService.stop: search subsystem (local/pgvector document search) not ported — Java apps delegate search to managed services or an external Elasticsearch/Postgres setup
signalwire.skills.api_ninjas_trivia.skill.ApiNinjasTriviaSkill: Java ships ApiNinjaTriviaSkill with equivalent one-liner surface
signalwire.skills.api_ninjas_trivia.skill.ApiNinjasTriviaSkill.__init__: Java ships ApiNinjaTriviaSkill with equivalent one-liner surface
signalwire.skills.api_ninjas_trivia.skill.ApiNinjasTriviaSkill.get_instance_key: Java ships ApiNinjaTriviaSkill with equivalent one-liner surface
signalwire.skills.api_ninjas_trivia.skill.ApiNinjasTriviaSkill.get_parameter_schema: Java ships ApiNinjaTriviaSkill with equivalent one-liner surface
signalwire.skills.api_ninjas_trivia.skill.ApiNinjasTriviaSkill.get_tools: Java ships ApiNinjaTriviaSkill with equivalent one-liner surface
signalwire.skills.api_ninjas_trivia.skill.ApiNinjasTriviaSkill.register_tools: Java ships ApiNinjaTriviaSkill with equivalent one-liner surface
signalwire.skills.api_ninjas_trivia.skill.ApiNinjasTriviaSkill.setup: Java ships ApiNinjaTriviaSkill with equivalent one-liner surface
signalwire.skills.claude_skills.skill.ClaudeSkillsSkill.get_instance_key: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.claude_skills.skill.ClaudeSkillsSkill.get_parameter_schema: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.datasphere.skill.DataSphereSkill: Java ships DatasphereSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere.skill.DataSphereSkill.cleanup: Java ships DatasphereSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere.skill.DataSphereSkill.get_global_data: Java ships DatasphereSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere.skill.DataSphereSkill.get_hints: Java ships DatasphereSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere.skill.DataSphereSkill.get_instance_key: Java ships DatasphereSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere.skill.DataSphereSkill.get_parameter_schema: Java ships DatasphereSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere.skill.DataSphereSkill.get_prompt_sections: Java ships DatasphereSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere.skill.DataSphereSkill.register_tools: Java ships DatasphereSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere.skill.DataSphereSkill.setup: Java ships DatasphereSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere_serverless.skill.DataSphereServerlessSkill: Java ships DatasphereServerlessSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere_serverless.skill.DataSphereServerlessSkill.get_global_data: Java ships DatasphereServerlessSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere_serverless.skill.DataSphereServerlessSkill.get_hints: Java ships DatasphereServerlessSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere_serverless.skill.DataSphereServerlessSkill.get_instance_key: Java ships DatasphereServerlessSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere_serverless.skill.DataSphereServerlessSkill.get_parameter_schema: Java ships DatasphereServerlessSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere_serverless.skill.DataSphereServerlessSkill.get_prompt_sections: Java ships DatasphereServerlessSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere_serverless.skill.DataSphereServerlessSkill.register_tools: Java ships DatasphereServerlessSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datasphere_serverless.skill.DataSphereServerlessSkill.setup: Java ships DatasphereServerlessSkill with equivalent one-liner surface; Python exposes additional internal helpers
signalwire.skills.datetime.skill.DateTimeSkill: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.datetime.skill.DateTimeSkill.get_hints: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.datetime.skill.DateTimeSkill.get_parameter_schema: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.datetime.skill.DateTimeSkill.get_prompt_sections: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.datetime.skill.DateTimeSkill.register_tools: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.datetime.skill.DateTimeSkill.setup: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.google_maps.skill.GoogleMapsClient: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.google_maps.skill.GoogleMapsClient.__init__: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.google_maps.skill.GoogleMapsClient.compute_route: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.google_maps.skill.GoogleMapsClient.validate_address: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.google_maps.skill.GoogleMapsSkill.get_parameter_schema: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.info_gatherer.skill.InfoGathererSkill.get_instance_key: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.info_gatherer.skill.InfoGathererSkill.get_parameter_schema: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.joke.skill.JokeSkill.get_global_data: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.joke.skill.JokeSkill.get_hints: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.joke.skill.JokeSkill.get_parameter_schema: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.joke.skill.JokeSkill.get_prompt_sections: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.math.skill.MathSkill.get_hints: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.math.skill.MathSkill.get_parameter_schema: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.mcp_gateway.skill.MCPGatewaySkill: Java's McpGatewaySkill proxies to a running gateway — no gateway code is bundled, consistent with signalwire.mcp_gateway omission
signalwire.skills.mcp_gateway.skill.MCPGatewaySkill.get_global_data: Java's McpGatewaySkill proxies to a running gateway — no gateway code is bundled, consistent with signalwire.mcp_gateway omission
signalwire.skills.mcp_gateway.skill.MCPGatewaySkill.get_hints: Java's McpGatewaySkill proxies to a running gateway — no gateway code is bundled, consistent with signalwire.mcp_gateway omission
signalwire.skills.mcp_gateway.skill.MCPGatewaySkill.get_parameter_schema: Java's McpGatewaySkill proxies to a running gateway — no gateway code is bundled, consistent with signalwire.mcp_gateway omission
signalwire.skills.mcp_gateway.skill.MCPGatewaySkill.get_prompt_sections: Java's McpGatewaySkill proxies to a running gateway — no gateway code is bundled, consistent with signalwire.mcp_gateway omission
signalwire.skills.mcp_gateway.skill.MCPGatewaySkill.register_tools: Java's McpGatewaySkill proxies to a running gateway — no gateway code is bundled, consistent with signalwire.mcp_gateway omission
signalwire.skills.mcp_gateway.skill.MCPGatewaySkill.setup: Java's McpGatewaySkill proxies to a running gateway — no gateway code is bundled, consistent with signalwire.mcp_gateway omission
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.cleanup: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.get_global_data: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.get_instance_key: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.get_parameter_schema: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.native_vector_search.skill.NativeVectorSearchSkill.get_prompt_sections: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.__init__: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.get_instance_key: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.get_parameter_schema: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.get_tools: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.registry.SkillRegistry.__init__: Java's SkillRegistry mirrors the Python registry but exposes a narrower public surface (register/list only)
signalwire.skills.registry.SkillRegistry.add_skill_directory: Java's SkillRegistry mirrors the Python registry but exposes a narrower public surface (register/list only)
signalwire.skills.registry.SkillRegistry.discover_skills: Java's SkillRegistry mirrors the Python registry but exposes a narrower public surface (register/list only)
signalwire.skills.registry.SkillRegistry.get_all_skills_schema: Java's SkillRegistry mirrors the Python registry but exposes a narrower public surface (register/list only)
signalwire.skills.registry.SkillRegistry.get_skill_class: Java's SkillRegistry mirrors the Python registry but exposes a narrower public surface (register/list only)
signalwire.skills.registry.SkillRegistry.list_all_skill_sources: Java's SkillRegistry mirrors the Python registry but exposes a narrower public surface (register/list only)
signalwire.skills.registry.SkillRegistry.list_skills: Java's SkillRegistry mirrors the Python registry but exposes a narrower public surface (register/list only)
signalwire.skills.registry.SkillRegistry.register_skill: Java's SkillRegistry mirrors the Python registry but exposes a narrower public surface (register/list only)
signalwire.skills.spider.skill.SpiderSkill.__init__: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.spider.skill.SpiderSkill.cleanup: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.spider.skill.SpiderSkill.get_instance_key: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.spider.skill.SpiderSkill.get_parameter_schema: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.swml_transfer.skill.SWMLTransferSkill: Java's SwmlTransferSkill exposes the transfer helper with a simplified public surface
signalwire.skills.swml_transfer.skill.SWMLTransferSkill.get_hints: Java's SwmlTransferSkill exposes the transfer helper with a simplified public surface
signalwire.skills.swml_transfer.skill.SWMLTransferSkill.get_instance_key: Java's SwmlTransferSkill exposes the transfer helper with a simplified public surface
signalwire.skills.swml_transfer.skill.SWMLTransferSkill.get_parameter_schema: Java's SwmlTransferSkill exposes the transfer helper with a simplified public surface
signalwire.skills.swml_transfer.skill.SWMLTransferSkill.get_prompt_sections: Java's SwmlTransferSkill exposes the transfer helper with a simplified public surface
signalwire.skills.swml_transfer.skill.SWMLTransferSkill.register_tools: Java's SwmlTransferSkill exposes the transfer helper with a simplified public surface
signalwire.skills.swml_transfer.skill.SWMLTransferSkill.setup: Java's SwmlTransferSkill exposes the transfer helper with a simplified public surface
signalwire.skills.weather_api.skill.WeatherApiSkill.__init__: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.weather_api.skill.WeatherApiSkill.get_parameter_schema: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.weather_api.skill.WeatherApiSkill.get_tools: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.web_search.skill.GoogleSearchScraper: Java ships a lighter web-search skill; Python's scraper variant classes are Python-specific
signalwire.skills.web_search.skill.GoogleSearchScraper.__init__: Java ships a lighter web-search skill; Python's scraper variant classes are Python-specific
signalwire.skills.web_search.skill.GoogleSearchScraper.extract_html_content: Java ships a lighter web-search skill; Python's scraper variant classes are Python-specific
signalwire.skills.web_search.skill.GoogleSearchScraper.extract_reddit_content: Java ships a lighter web-search skill; Python's scraper variant classes are Python-specific
signalwire.skills.web_search.skill.GoogleSearchScraper.extract_text_from_url: Java ships a lighter web-search skill; Python's scraper variant classes are Python-specific
signalwire.skills.web_search.skill.GoogleSearchScraper.is_reddit_url: Java ships a lighter web-search skill; Python's scraper variant classes are Python-specific
signalwire.skills.web_search.skill.GoogleSearchScraper.search_and_scrape: Java ships a lighter web-search skill; Python's scraper variant classes are Python-specific
signalwire.skills.web_search.skill.GoogleSearchScraper.search_and_scrape_best: Java ships a lighter web-search skill; Python's scraper variant classes are Python-specific
signalwire.skills.web_search.skill.GoogleSearchScraper.search_google: Java ships a lighter web-search skill; Python's scraper variant classes are Python-specific
signalwire.skills.web_search.skill.WebSearchSkill.get_hints: Java ships a lighter web-search skill; Python's scraper variant classes are Python-specific
signalwire.skills.web_search.skill.WebSearchSkill.get_instance_key: Java ships a lighter web-search skill; Python's scraper variant classes are Python-specific
signalwire.skills.web_search.skill.WebSearchSkill.get_parameter_schema: Java ships a lighter web-search skill; Python's scraper variant classes are Python-specific
signalwire.skills.web_search.skill_improved.GoogleSearchScraper: Python internal experiment; not a public API
signalwire.skills.web_search.skill_improved.GoogleSearchScraper.__init__: Python internal experiment; not a public API
signalwire.skills.web_search.skill_improved.GoogleSearchScraper.extract_text_from_url: Python internal experiment; not a public API
signalwire.skills.web_search.skill_improved.GoogleSearchScraper.search_and_scrape: Python internal experiment; not a public API
signalwire.skills.web_search.skill_improved.GoogleSearchScraper.search_and_scrape_best: Python internal experiment; not a public API
signalwire.skills.web_search.skill_improved.GoogleSearchScraper.search_google: Python internal experiment; not a public API
signalwire.skills.web_search.skill_improved.WebSearchSkill: Python internal experiment; not a public API
signalwire.skills.web_search.skill_improved.WebSearchSkill.get_global_data: Python internal experiment; not a public API
signalwire.skills.web_search.skill_improved.WebSearchSkill.get_hints: Python internal experiment; not a public API
signalwire.skills.web_search.skill_improved.WebSearchSkill.get_instance_key: Python internal experiment; not a public API
signalwire.skills.web_search.skill_improved.WebSearchSkill.get_parameter_schema: Python internal experiment; not a public API
signalwire.skills.web_search.skill_improved.WebSearchSkill.get_prompt_sections: Python internal experiment; not a public API
signalwire.skills.web_search.skill_improved.WebSearchSkill.register_tools: Python internal experiment; not a public API
signalwire.skills.web_search.skill_improved.WebSearchSkill.setup: Python internal experiment; not a public API
signalwire.skills.web_search.skill_original.GoogleSearchScraper: Python legacy version; not a public API
signalwire.skills.web_search.skill_original.GoogleSearchScraper.__init__: Python legacy version; not a public API
signalwire.skills.web_search.skill_original.GoogleSearchScraper.extract_text_from_url: Python legacy version; not a public API
signalwire.skills.web_search.skill_original.GoogleSearchScraper.search_and_scrape: Python legacy version; not a public API
signalwire.skills.web_search.skill_original.GoogleSearchScraper.search_google: Python legacy version; not a public API
signalwire.skills.web_search.skill_original.WebSearchSkill: Python legacy version; not a public API
signalwire.skills.web_search.skill_original.WebSearchSkill.get_global_data: Python legacy version; not a public API
signalwire.skills.web_search.skill_original.WebSearchSkill.get_hints: Python legacy version; not a public API
signalwire.skills.web_search.skill_original.WebSearchSkill.get_instance_key: Python legacy version; not a public API
signalwire.skills.web_search.skill_original.WebSearchSkill.get_parameter_schema: Python legacy version; not a public API
signalwire.skills.web_search.skill_original.WebSearchSkill.get_prompt_sections: Python legacy version; not a public API
signalwire.skills.web_search.skill_original.WebSearchSkill.register_tools: Python legacy version; not a public API
signalwire.skills.web_search.skill_original.WebSearchSkill.setup: Python legacy version; not a public API
signalwire.skills.wikipedia_search.skill.WikipediaSearchSkill.get_hints: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.wikipedia_search.skill.WikipediaSearchSkill.get_parameter_schema: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.wikipedia_search.skill.WikipediaSearchSkill.get_prompt_sections: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.skills.wikipedia_search.skill.WikipediaSearchSkill.search_wiki: Java ships a leaner skill class with the same public surface; helper methods from Python's skill modules are inlined
signalwire.start_agent: Python top-level helper; Java users call AgentBase.run() or AgentServer.register() + run()
signalwire.utils.is_serverless_mode: Python helper; Java uses ExecutionMode.isServerless() on the injected ExecutionMode instance
signalwire.utils.schema_utils.SchemaUtils: Python's schema utils include load/validate helpers used by the SWMLService; Java does schema loading inline in Document/Schema
signalwire.utils.schema_utils.SchemaUtils.__init__: Python's schema utils include load/validate helpers used by the SWMLService; Java does schema loading inline in Document/Schema
signalwire.utils.schema_utils.SchemaUtils.full_validation_available: Python's schema utils include load/validate helpers used by the SWMLService; Java does schema loading inline in Document/Schema
signalwire.utils.schema_utils.SchemaUtils.generate_method_body: Python's schema utils include load/validate helpers used by the SWMLService; Java does schema loading inline in Document/Schema
signalwire.utils.schema_utils.SchemaUtils.generate_method_signature: Python's schema utils include load/validate helpers used by the SWMLService; Java does schema loading inline in Document/Schema
signalwire.utils.schema_utils.SchemaUtils.get_all_verb_names: Python's schema utils include load/validate helpers used by the SWMLService; Java does schema loading inline in Document/Schema
signalwire.utils.schema_utils.SchemaUtils.get_verb_parameters: Python's schema utils include load/validate helpers used by the SWMLService; Java does schema loading inline in Document/Schema
signalwire.utils.schema_utils.SchemaUtils.get_verb_properties: Python's schema utils include load/validate helpers used by the SWMLService; Java does schema loading inline in Document/Schema
signalwire.utils.schema_utils.SchemaUtils.get_verb_required_properties: Python's schema utils include load/validate helpers used by the SWMLService; Java does schema loading inline in Document/Schema
signalwire.utils.schema_utils.SchemaUtils.load_schema: Python's schema utils include load/validate helpers used by the SWMLService; Java does schema loading inline in Document/Schema
signalwire.utils.schema_utils.SchemaUtils.validate_document: Python's schema utils include load/validate helpers used by the SWMLService; Java does schema loading inline in Document/Schema
signalwire.utils.schema_utils.SchemaUtils.validate_verb: Python's schema utils include load/validate helpers used by the SWMLService; Java does schema loading inline in Document/Schema
signalwire.utils.schema_utils.SchemaValidationError: Python's schema utils include load/validate helpers used by the SWMLService; Java does schema loading inline in Document/Schema
signalwire.utils.schema_utils.SchemaValidationError.__init__: Python's schema utils include load/validate helpers used by the SWMLService; Java does schema loading inline in Document/Schema
signalwire.utils.url_validator.validate_url: Python URL-validation helper used by SWMLService; Java's Document.addExternal / addWebhook validate URLs at call time
signalwire.web.web_service.WebService: Python's WebService abstraction is not a Java idiom — Java uses AgentBase's built-in HTTP server or the user's chosen framework
signalwire.web.web_service.WebService.__init__: Python's WebService abstraction is not a Java idiom — Java uses AgentBase's built-in HTTP server or the user's chosen framework
signalwire.web.web_service.WebService.add_directory: Python's WebService abstraction is not a Java idiom — Java uses AgentBase's built-in HTTP server or the user's chosen framework
signalwire.web.web_service.WebService.remove_directory: Python's WebService abstraction is not a Java idiom — Java uses AgentBase's built-in HTTP server or the user's chosen framework
signalwire.web.web_service.WebService.start: Python's WebService abstraction is not a Java idiom — Java uses AgentBase's built-in HTTP server or the user's chosen framework
signalwire.web.web_service.WebService.stop: Python's WebService abstraction is not a Java idiom — Java uses AgentBase's built-in HTTP server or the user's chosen framework

## POM internal collections

signalwire.pom.pom.PromptObjectModel.sections: java-bean-accessor — Java exposes the section list via getSections() (a public accessor in PORT_ADDITIONS) rather than direct attribute access; Python exposes public `sections` attribute
signalwire.pom.pom.Section.subsections: java-bean-accessor — Java exposes the subsection list via getSubsections() (a public accessor in PORT_ADDITIONS) rather than direct attribute access; Python exposes public `subsections` attribute
