Create a new ADR that analyzes **backend implementation language choice (Java vs. TypeScript vs. Python) for providing data and connectivity to the CES `acme_voice_agent`**.

**Context:**
- Existing ADRs already cover connectivity patterns:
  - `ces-agent/docs/ADR-CES-001-rest-api-vs-mcp-server.md` — REST API vs. MCP Server decision
  - `ces-agent/docs/ADR-CES-002-mcp-server-topology.md` — MCP server topology
  - `ces-agent/docs/ADR-CES-003-mcp-location-services-implementation.md` — MCP location services implementation
- CES platform architecture is documented in `ces-agent/docs/cx-agent-studio/`
- Current system uses Java Spring Boot (`bfa-service-resource`, `voice-banking-app`)
- Enterprise banking context with strict requirements for security, compliance, regulatory audit, and performance

**Requirements for the ADR:**

1. **Title:** ADR-CES-004: Backend Implementation Language for CES Agent Connectivity (Java vs. TypeScript vs. Python)

2. **Use divergent thinking from multiple stakeholder perspectives:**
   - **Security/Compliance Officer** — Authentication, authorization, audit logging, PII handling, regulatory compliance (GDPR, PSD2, DORA, BaFin)
   - **Platform Architect** — Integration with existing Java/Spring Boot ecosystem, API management, service mesh, observability
   - **Performance Engineer** — Latency, throughput, resource utilization, cold start times (Cloud Run)
   - **Developer Experience** — Maintainability, type safety, testing, debugging, team skill set
   - **Operations/SRE** — Deployment complexity, monitoring, incident response, disaster recovery
   - **Cost/Budget Owner** — Infrastructure costs, development velocity, operational overhead

3. **Evaluate each language option (Java, TypeScript, Python) across:**
   - **Integration with CES platform** — OpenAPI toolsets, MCP server support, authentication patterns
   - **Enterprise banking requirements** — Security controls, audit trails, compliance frameworks
   - **Existing infrastructure** — Spring Boot ecosystem, API management, service mesh
   - **Performance characteristics** — Latency, cold starts, memory footprint, concurrency model
   - **Operational maturity** — Monitoring, logging, error handling, deployment pipelines
   - **Team capabilities** — Existing expertise, hiring market, training requirements

4. **Consider hybrid approaches:**
   - Java for core banking operations + Python for CES-specific tooling
   - Polyglot architecture with clear boundaries
   - Shared OpenAPI contracts across implementations

5. **Reference existing patterns:**
   - `bfa-service-resource` (Java Spring Boot REST API)
   - `bfa-mcp-spike` (Java Spring AI MCP server)
   - `ai-account-balance-ts` (TypeScript reference implementation — archived)
   - CES direct Python tools (sandbox restrictions documented in `ces-agent/docs/tool-selection-guide.md`)

6. **Output format:**
   - Use the ADR template from `agents/templates/adr-template.md`
   - Set PHASE to `DESIGN_ONLY` initially
   - Include decision matrix with weighted criteria
   - Provide clear recommendation with rationale
   - Document trade-offs and risks for each option
   - Save to `ces-agent/docs/ADR-CES-004-backend-language-choice.md`

**Constraints:**
- Must integrate with existing Java Spring Boot services (`voice-banking-app`, `bfa-service-resource`)
- Must support CES authentication patterns (service agent ID tokens, OAuth, API keys)
- Must meet enterprise banking security and compliance requirements
- Must be operationally supportable by existing SRE team
- Must not introduce unnecessary technology sprawl