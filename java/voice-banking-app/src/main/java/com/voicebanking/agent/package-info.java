/**
 * Agent Framework
 * 
 * Multi-agent architecture for AI Banking Voice system.
 * 
 * <h2>Architecture Overview</h2>
 * This package implements the multi-agent architecture described in Acme Bank's
 * AI Banking Voice design (Component E: AI Functional Agents).
 * 
 * <h2>Implementation Status (as of 2026-01-25)</h2>
 * <ul>
 *   <li><b>7 of 12 agents implemented</b> (58%)</li>
 *   <li><b>31 of ~45 tools implemented</b> (69%)</li>
 *   <li><b>All P0 Essential agents complete</b></li>
 *   <li><b>P1 Required: MobileAppAssistanceAgent complete</b></li>
 * </ul>
 * 
 * <h2>Implemented Agents</h2>
 * <ul>
 *   <li><b>BankingOperationsAgent</b> (banking/) - 3 tools: balance, accounts, transactions</li>
 *   <li><b>LocationServicesAgent</b> (location/) - 1 tool: branch finder</li>
 *   <li><b>PolicyGuardrailsAgent</b> (policy/) - 6 tools: policy enforcement, refusal</li>
 *   <li><b>HumanHandoverAgent</b> (handover/) - 6 tools: escalation to human agents</li>
 *   <li><b>KnowledgeCompilerAgent</b> (knowledge/) - 4 tools: FAQ, bank info, app guidance</li>
 *   <li><b>TextGeneratorAgent</b> (text/) - 5 tools: voice formatting, templates</li>
 *   <li><b>MobileAppAssistanceAgent</b> (mobileapp/) - 6 tools: app guides, navigation, troubleshooting</li>
 * </ul>
 * 
 * <h2>Key Components</h2>
 * <ul>
 *   <li><b>Agent</b> - Base interface for all functional agents</li>
 *   <li><b>AgentRegistry</b> - Discovers, registers, and routes to agents</li>
 * </ul>
 * 
 * <h2>Agent Communication Flow</h2>
 * <pre>
 * OrchestratorService → AgentRegistry → [Functional Agent] → Tool Execution
 * </pre>
 * 
 * <h2>Documentation</h2>
 * Each agent package contains an AGENT-README.md with:
 * <ul>
 *   <li>Agent description and capabilities</li>
 *   <li>Problem statement and solution approach</li>
 *   <li>Current gaps and alternatives</li>
 *   <li>Architectural analysis</li>
 * </ul>
 * See also: ARCHITECTURE-ANALYSIS.md for cross-agent analysis.
 * 
 * <h2>Adding New Agents</h2>
 * <ol>
 *   <li>Implement the {@link com.voicebanking.agent.Agent} interface</li>
 *   <li>Annotate with {@code @Component} for Spring discovery</li>
 *   <li>Define tool IDs and implement executeTool()</li>
 *   <li>AgentRegistry will auto-discover and register the agent</li>
 *   <li>Create AGENT-README.md in the package</li>
 * </ol>
 * 
 * @since 2026-01-22
 * @see com.voicebanking.agent.Agent
 * @see com.voicebanking.agent.AgentRegistry
 */
package com.voicebanking.agent;
