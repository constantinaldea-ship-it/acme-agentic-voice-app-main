Analyze the connectivity patterns between backend services and the CES agent (`acme_voice_agent`) documented in the following ADRs:
- `ces-agent/docs/ADR-CES-001-rest-api-vs-mcp-server.md`
- `ces-agent/docs/ADR-CES-002-mcp-server-topology.md`
- `ces-agent/docs/ADR-CES-003-mcp-location-services-implementation.md`

**Objective:** Clarify the differences between **SSE (Server-Sent Events)**, **Streamable HTTP**, and **REST API** connectivity patterns for agentic tool integration with CES/Dialogflow CX.

**Required Analysis:**

1. **Multi-Perspective Evaluation:** Analyze each connectivity pattern from at least 3 distinct viewpoints:
   - **Enterprise Architect** (governance, security, compliance, long-term maintainability)
   - **Platform Engineer** (operational complexity, observability, scalability, infrastructure requirements)
   - **Application Developer** (implementation effort, debugging experience, testing complexity)

2. **Comparative Analysis:** For each connectivity pattern (SSE, Streamable HTTP, REST API), provide:
   - **Pros** (benefits, use cases where it excels)
   - **Cons** (limitations, risks, operational challenges)
   - **CES Compatibility** (current support status, known limitations per platform-reference.md)
   - **Testing & Evaluation Support** (golden eval, scenario eval, integration testing implications)

3. **Context-Specific Recommendation:** Issue a clear recommendation for:
   - **Enterprise banking environment** (production-grade, regulated financial services)
   - **Demo service context** (specifically for `bfa-service-resource` as referenced in the codebase)
  - potential impact of audit, compliance, security and legitimation within the context of the Voice Banking App

4. **Decision Criteria:** Base recommendations on:
   - Security and compliance requirements (audit logging, authentication, data privacy)
   - Operational maturity (monitoring, alerting, incident response)
   - Developer experience (ease of implementation, debugging, testing)
   - CES platform constraints (documented limitations, supported patterns)
   - Alignment with existing architecture (Spring Boot, Cloud Run, OpenAPI toolsets)

**Deliverable Format:**
- Structured comparison table with all three perspectives
- Pros/cons analysis for each connectivity pattern
- Final recommendation with rationale tied to enterprise banking requirements and demo service constraints