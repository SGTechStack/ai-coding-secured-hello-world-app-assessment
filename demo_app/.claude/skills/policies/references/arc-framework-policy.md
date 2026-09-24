# ARC Framework — Application Code Review

**Source:** [GovTech Singapore ARC Framework](https://govtech-responsibleai.github.io/agentic-risk-capability-framework/)

## Summary of Controls

| Section | Count |
| :--- | :---: |
| Component — LLM (llm) | 8 |
| Component — Tools (tool) | 7 |
| Component — Instructions (inst) | 5 |
| Component — Memory (mem) | 3 |
| Design — Architecture (arch) | 5 |
| Design — Roles & Access (role) | 4 |
| Design — Monitoring (mon) | 4 |
| Capability — Cognitive (cog) | 10 |
| Capability — Interaction (int) | 28 |
| Capability — Operational (op) | 14 |
| **Total** | **88** |

## Component — LLM (llm)

- **CTRL-0001** [Level 0]: Use only LLMs from verified and trusted model developers.
- **CTRL-0002** [Level 0]: Obtain legally binding no-training and no-logging agreements from LLM API service providers.
- **CTRL-0003** [Level 1]: Use only established and verified model loaders in production environments.
- **CTRL-0004** [Level 2]: Review the LLM's system card to inform risk assessment and model selection.
- **CTRL-0005** [Level 0]: Conduct structured evaluation of multiple LLMs for instruction-following, performance, and safety before deployment.
- **CTRL-0006** [Level 1]: Require human approval before executing high-impact actions.
- **CTRL-0007** [Level 0]: Log all LLM inputs and outputs for regular review.
- **CTRL-0008** [Level 1]: Implement automated alerts when agent behaviour drifts from predefined thresholds.

## Component — Tools (tool)

- **CTRL-0009** [Level 0]: Use only MCP servers that implement robust authentication mechanisms in production environments.
- **CTRL-0010** [Level 1]: Use only MCP servers that validate credentials on every inbound request.
- **CTRL-0011** [Level 0]: Limit token scopes to the minimum privileges required and avoid broad or wildcard scopes.
- **CTRL-0012** [Level 2]: Use only MCP servers that integrate with authorisation servers implementing per-client consent mechanisms.
- **CTRL-0013** [Level 0]: Test all untested MCP servers in a sandboxed environment before deploying to production.
- **CTRL-0014** [Level 0]: Use only MCP servers from verified and trusted developers.
- **CTRL-0015** [Level 1]: Treat all tool metadata and outputs as untrusted input requiring validation.

## Component — Instructions (inst)

- **CTRL-0016** [Level 0]: Define clearly the agent's role, scope, and non-goals in the system prompt.
- **CTRL-0017** [Level 1]: Define clear success criteria for the agent's tasks.
- **CTRL-0018** [Level 2]: Define default behaviour when the agent encounters ambiguous situations.
- **CTRL-0019** [Level 0]: Use delimiters to enclose untrusted inputs and instruct the LLM to treat delimited content as data only.
- **CTRL-0020** [Level 2]: Use a dedicated LLM to extract required fields from inputs and filter out extraneous text or embedded instructions.

## Component — Memory (mem)

- **CTRL-0021** [Level 0]: Implement allowlists and denylists to restrict what categories of information can be written to agent memory.
- **CTRL-0022** [Level 1]: Implement content filtering on memory writes to detect and block known unsafe content patterns.
- **CTRL-0023** [Level 2]: Log all memory modifications with comprehensive source metadata for audit purposes.

## Design — Architecture (arch)

- **CTRL-0024** [Level 0]: Define formal schemas for inter-agent messages and validate all messages against these schemas before processing.
- **CTRL-0025** [Level 1]: Ensure all inter-agent communications are encrypted in transit and prohibit plaintext channels.
- **CTRL-0026** [Level 1]: Require all agents to authenticate with verifiable, cryptographically signed identities before processing requests.
- **CTRL-0027** [Level 1]: Implement circuit breakers to prevent cascading failures in multi-agent systems.
- **CTRL-0028** [Level 0]: Continuously monitor multi-agent systems for cascade failure indicators.

## Design — Roles & Access (role)

- **CTRL-0029** [Level 1]: Grant agents only the minimum permissions required for their designated tasks.
- **CTRL-0030** [Level 1]: Assign each agent a unique, verifiable identity with no shared credentials.
- **CTRL-0031** [Level 1]: Use only MCP servers that validate token provenance and prohibit unauthorised token passthrough.
- **CTRL-0032** [Level 0]: Centralise observability data collection in a unified backend system.

## Design — Monitoring (mon)

- **CTRL-0033** [Level 0]: Standardise trace attributes for agent operations using consistent semantic conventions.
- **CTRL-0034** [Level 0]: Conduct regular reviews of logs and traces to detect emergent issues in deployed agentic systems.
- **CTRL-0035** [Level 2]: Require agents to decompose user goals into explicit sub-goals and validate necessity before proceeding.
- **CTRL-0060** [Level 1]: Implement escape filtering before incorporating web content into prompts.

## Capability — Cognitive (cog)

- **CTRL-0036** [Level 1]: Regularly evaluate and test planning behaviour under representative workloads and failure scenarios.
- **CTRL-0037** [Level 1]: Require planning agents to include explicit safety constraints in all generated plans before execution.
- **CTRL-0038** [Level 0]: Conduct pre-deployment safety verification using domain-relevant stress tests and adversarial scenarios.
- **CTRL-0039** [Level 1]: Ensure each agent publishes standardised, machine-readable capability descriptors accessible to other agents.
- **CTRL-0040** [Level 0]: Limit the scope of agent actions through predefined thresholds and baselines.
- **CTRL-0041** [Level 0]: Provide comprehensive descriptions for each tool including intended use, required inputs, and potential outputs.
- **CTRL-0042** [Level 0]: Require explicit human confirmation before executing high-impact or irreversible tool actions.
- **CTRL-0043** [Level 1]: Log all tool selection decisions and invocations with comprehensive metadata.
- **CTRL-0044** [Level 1]: Implement output safety guardrails to detect and prevent generation of undesirable content.
- **CTRL-0045** [Level 0]: Implement input guardrails to detect and decline requests for specialised domain advice.

## Capability — Interaction (int)

- **CTRL-0046** [Level 0]: Implement input guardrails to detect and decline requests for controversial content that violates organisational policies.
- **CTRL-0047** [Level 0]: Implement output guardrails to detect and redact personally identifiable information.
- **CTRL-0048** [Level 2]: Implement methods to reduce hallucination rates in agent outputs.
- **CTRL-0049** [Level 0]: Implement UI/UX cues to communicate the risk of hallucination to users.
- **CTRL-0050** [Level 1]: Implement features enabling users to verify generated answers against source content.
- **CTRL-0051** [Level 0]: Implement input guardrails to detect and decline requests to generate copyrighted content.
- **CTRL-0052** [Level 2]: Declare upfront that communications are generated by an AI system.
- **CTRL-0053** [Level 0]: Require human approval for communications on sensitive matters.
- **CTRL-0054** [Level 0]: Limit agent communications to standard processes with predefined templates.
- **CTRL-0055** [Level 1]: Provide alternative channels for users to clarify communications or provide feedback.
- **CTRL-0056** [Level 1]: Require explicit user confirmation before initiating or committing any business transaction.
- **CTRL-0057** [Level 2]: Require out-of-band confirmation when transaction risk signals are elevated.
- **CTRL-0058** [Level 1]: Restrict agents to proposing transactions whilst using a separate transaction controller for execution.
- **CTRL-0059** [Level 2]: Apply fraud detection models or heuristics to agent-proposed transactions.
- **CTRL-0061** [Level 0]: Use structured retrieval APIs for web searches rather than web scraping.
- **CTRL-0062** [Level 0]: Implement input guardrails to detect prompt injection and adversarial attacks.
- **CTRL-0063** [Level 1]: Prioritise search results from verified, high-quality domains.
- **CTRL-0064** [Level 1]: Limit computer use to accessing only safe and trusted resources.
- **CTRL-0065** [Level 0]: Ensure computer use capabilities provide immediate interruptability.
- **CTRL-0066** [Level 0]: Ensure "take over" mode is activated when entering sensitive data.
- **CTRL-0067** [Level 0]: Ensure proper documentation of programmatic interfaces for agent use.

## Capability — Operational (op)

- **CTRL-0068** [Level 0]: Use code linters to screen generated code for bad practices and poor syntax.
- **CTRL-0069** [Level 0]: Run agent-generated code only in isolated compute environments with network access blocked by default.
- **CTRL-0070** [Level 0]: Review all agent-generated code before execution.
- **CTRL-0071** [Level 0]: Use static code analysers to detect security vulnerabilities and code quality issues.
- **CTRL-0072** [Level 1]: Monitor runtime and memory consumption of agent-generated code.
- **CTRL-0073** [Level 0]: Create a denylist of commands that agents are not permitted to execute.
- **CTRL-0074** [Level 0]: Conduct CVE scanning and block execution of code with High or Critical vulnerabilities.
- **CTRL-0075** [Level 1]: Do not grant write access to agents unless strictly necessary.
- **CTRL-0076** [Level 1]: Require human approval for any destructive changes to databases, tables, or files.
- **CTRL-0077** [Level 0]: Enable versioning or soft-delete for managed object stores to allow recovery from accidental modifications.
- **CTRL-0078** [Level 0]: Enforce throttling or rate limits on agent-initiated database operations.
- **CTRL-0079** [Level 2]: Validate agent-generated database queries for efficiency before execution against production databases.
- **CTRL-0080** [Level 0]: Implement caching mechanisms to reduce repetitive database queries by agents.
- **CTRL-0081** [Level 1]: Implement input guardrails to detect personally identifiable information in data accessed by agents.
- **CTRL-0082** [Level 2]: Do not grant agents access to personally identifiable or sensitive data unless strictly required.
- **CTRL-0083** [Level 0]: Disallow unknown or external files unless they have been scanned for threats.
- **CTRL-0084** [Level 0]: Set minimum and maximum limits on what agents can modify within system resources.
- **CTRL-0085** [Level 0]: Log system health metrics and implement automated alerts for abnormal conditions.
- **CTRL-0086** [Level 0]: Limit the number of concurrent queries to external systems by agents.
- **CTRL-0087** [Level 0]: Ensure logging of system health metrics and automated alerts to the developer team if any metrics are abnormal.
- **CTRL-0088** [Level 0]: Limit the number of concurrent queries to external systems from the agent.

## Control Levels

- **Level 0 (Cardinal):** MUST be implemented, cannot be waived.
- **Level 1 (Standard):** SHOULD be implemented, may be adapted with rationale.
- **Level 2 (Best Practice):** MAY be implemented, especially for high-risk systems.

## References

- Official Framework: https://govtech-responsibleai.github.io/agentic-risk-capability-framework/
- Interactive Risk Register: https://govtech-responsibleai.github.io/agentic-risk-capability-framework/arc_framework/risk-register/
- Implementation Guide: https://govtech-responsibleai.github.io/agentic-risk-capability-framework/implementation/
