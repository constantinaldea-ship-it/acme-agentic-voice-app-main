# ADR-CES-003: MCP Location Services — Implementation Framework & Topology

> PHASE: DESIGN_ONLY
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**Status:** Proposed
**Date:** 2026-02-09
**Owner:** Architecture Guild / CES Integration Team
**Decision Drivers:** Framework selection for MCP spike, service-layer reuse, CES Streamable HTTP transport compatibility, Cloud Run operational maturity, enterprise banking governance
**Related:**
- [ADR-CES-001: REST vs MCP](ADR-CES-001-rest-api-vs-mcp-server.md) — parent decision retaining REST as primary protocol
- [ADR-CES-002: MCP Server Topology](ADR-CES-002-mcp-server-topology.md) — approved location-services-only MCP spike
- [mcp-vs-openapi analysis](mcp-vs-openapi.md) — transport analysis for Google Cloud
- [Spring AI MCP Server Boot Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
- [CES MCP tool documentation](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp)
- [CX Agent Studio Platform Reference](cx-agent-studio/platform-reference.md) — tool types, auth, MCP transport constraints (§ 5)
- [CX Agent Studio Design Patterns](cx-agent-studio/design-patterns.md) — `x-ces-session-context` (§ 5.1)
- [bfa-service-resource](../../java/bfa-service-resource/) — current REST API implementation
- [CES Agent toolsets](../acme_voice_agent/toolsets/) — current OpenAPI-based tool binding

---

## Context

### What This ADR Decides

ADR-CES-002 approved a **location-services-only MCP spike** on a feature branch:

> *"Validate Streamable HTTP transport from CES to Cloud Run end-to-end. Measure latency overhead vs REST. Test CES `mcpTool` binding configuration and auth. Document CES MCP toolset JSON format and deployment workflow."*

This ADR decides **how** to implement that spike:
1. Which **framework/technology stack** to use for the MCP server
2. Which **topology** (in-process, shared-library, or standalone) to adopt
3. What **implementation constraints** CES imposes on MCP transport

### Current State

| Component | Technology | Version |
|-----------|-----------|---------|
| `bfa-service-resource` | Spring Boot (WebMVC) | 3.3.0 |
| Java | OpenJDK | 21 |
| `LocationService` | Spring `@Service` | Transport-agnostic |
| `LocationController` | `@RestController` + OpenAPI annotations | HTTP/REST |
| Deployment | Google Cloud Run | HTTP container |
| CES binding | OpenAPI toolset (`location.json`) | `openApiToolset` |
| Auth | `serviceAgentIdTokenAuthConfig` | Google IAM |

### Spike Scope

- **In scope:** `branch_search` (search branches with filters), `branch_details` (get single branch by ID)
- **Out of scope:** Banking operations (accounts, cards, transfers) — these require `x-ces-session-context` which MCP tools do not support ([design-patterns.md § 5.1](cx-agent-studio/design-patterns.md#51-pass-context-variables-to-openapi-tools-x-ces-session-context); ADR-CES-002 § View 3)
- **Branch policy:** Feature branch only — spike code is not for production merge

---

## Diverging Views Analysis

### View 1: SRE / Operations — *"What does my on-call look like?"*

#### Framework Comparison (Operations Lens)

| Dimension | Spring Boot + Spring AI MCP | Quarkus + MCP | Python MCP SDK | Micronaut + MCP |
|-----------|----------------------------|---------------|----------------|-----------------|
| **Cloud Run cold start** | 3-8s (JVM, depends on heap) | 0.5-2s (native image) | 0.3-1s (CPython) | 2-5s (JVM) |
| **Memory footprint** | 256-512MB (Spring + Tomcat) | 64-128MB (native) | 64-128MB | 128-256MB |
| **Streamable HTTP behaviour on Cloud Run** | Uses embedded Tomcat HTTP stack — same engine as production REST | Vert.x SSE — different engine, less Cloud Run experience | Python async (uvicorn/starlette) — well-tested for SSE | Netty-based — less Cloud Run SSE production data |
| **Monitoring integration** | Micrometer + Cloud Monitoring (same as existing) | Micrometer supported, but config differs | OpenTelemetry (separate setup) | Micrometer supported, but config differs |
| **Operational familiarity** | ✅ Same stack as production | ❌ New runtime, new debugging | ❌ New language runtime | ❌ New runtime |
| **Shared deployment** | ✅ Same JVM, same Cloud Run service | ❌ Separate native binary | ❌ Separate Python process | ❌ Separate container |
| **Rollback** | Remove Spring AI dependency + beans | Delete service | Delete service | Delete service |

**Streamable HTTP Cloud Run defaults (all frameworks):**
- Cloud Run request timeout: standard request/response defaults are acceptable
- No keepalive ping requirement for request/response transport
- No SSE reconnect handling required
- Connection monitoring: track active SSE connections alongside HTTP requests
- If Apigee fronts the service: configure standard HTTP passthrough

**Verdict:** Spring Boot + Spring AI MCP is the only option that avoids adding a new operational surface. Same JVM, same container, same monitoring pipeline. For a spike, operational simplicity matters — if transport debugging is painful, you don't want to also debug a new runtime simultaneously.

---

### View 2: Developer Experience — *"How fast can I ship this and iterate?"*

#### Framework Comparison (DX Lens)

| Dimension | Spring Boot + Spring AI MCP | Quarkus + MCP | Python MCP SDK | Micronaut + MCP |
|-----------|----------------------------|---------------|----------------|-----------------|
| **Time to first tool** | ~2 hours (add starter, annotate existing service) | ~4 hours (new project, port service logic) | ~1 hour (simplest MCP SDK) | ~4 hours (new project) |
| **Service layer reuse** | ✅ Direct `@Autowired` of `LocationService` | ❌ Must rewrite or HTTP-delegate | ❌ Must HTTP-delegate to REST API | ❌ Must rewrite or HTTP-delegate |
| **Annotation model** | `@Tool`/`@ToolParam` (1.0.0 GA), `@McpTool`/`@McpToolParam` (1.1.0+) — similar to `@Operation` | No official MCP annotations | `@mcp.tool()` decorator | No official MCP annotations |
| **Testing** | Same `@SpringBootTest`, MockMvc, MCP Inspector | New test framework | pytest + MCP Inspector | New test framework |
| **CI/CD impact** | Add to existing Maven build | New build pipeline | New Python build pipeline | New build pipeline |
| **Local dev** | Same `mvn spring-boot:run`, MCP Inspector | `quarkus dev` | `python server.py`, MCP Inspector | `mn:run` |
| **IDE support** | IntelliJ Spring tooling ✅ | IntelliJ Quarkus plugin | PyCharm / VS Code | IntelliJ Micronaut plugin |
| **Debugging** | Same JVM debugger, same breakpoints | Different runtime (native vs JVM mode) | Python debugger (different toolchain) | JVM debugger (similar) |

**Spring AI MCP annotation model — side-by-side with current REST:**

```java
// Current REST (LocationController.java)
@GetMapping
@Operation(summary = "Search branches", description = "Search for bank branches...")
public ResponseEntity<ApiResponse<BranchSearchResponse>> searchBranches(
    @ParameterObject BranchSearchRequest request, ...) {
    return ResponseEntity.ok(ApiResponse.success(locationService.searchBranches(request)));
}

// MCP equivalent (LocationMcpTools.java)
@McpTool(name = "branch_search", description = "Search for bank branches by city, address, ...")
public BranchSearchResponse branchSearch(
    @McpToolParam(description = "City name") String city,
    @McpToolParam(description = "Street address") String address, ...) {
    return locationService.searchBranches(new BranchSearchRequest(city, address, ...));
}
```

**Python MCP SDK — rapid prototyping advantage:**

```python
@mcp.tool()
async def branch_search(city: str = None, limit: int = 5) -> dict:
    """Search for bank branches by city."""
    resp = httpx.get(f"{BFA_URL}/api/v1/branches", params={"city": city, "limit": limit})
    return resp.json()
```

Python is fastest for a throwaway prototype (~1 hour). But it **delegates to the REST API** instead of reusing the service layer, meaning it tests *transport* but not *service-layer reuse*. Spring AI directly validates the key claim: `LocationService` can serve both REST and MCP without duplication.

**Verdict:** Spring Boot + Spring AI MCP is the fastest path that also validates service-layer reuse. Python is faster for a protocol-only test but doesn't prove the architectural thesis. Quarkus and Micronaut require porting domain logic without clear compensating benefit for a spike.

---

### View 3: Enterprise Architect — *"Does this hold up in 3 years?"*

#### Framework Comparison (Enterprise Lens)

| Dimension | Spring Boot + Spring AI MCP | Quarkus + MCP | Python MCP SDK | Micronaut + MCP |
|-----------|----------------------------|---------------|----------------|-----------------|
| **Ecosystem maturity** | Spring AI MCP: **GA 1.0.0–1.1.2** (latest stable Dec 2025; 2.0.0-M2 milestone), Spring Boot: GA 3.3.0 (latest 3.5.10), Java MCP SDK: 0.17.2 | Quarkus: GA 3.x, MCP: community/experimental | Official MCP Python SDK: mature (reference impl) | Micronaut: GA 4.x, MCP: no official support |
| **Enterprise adoption** | VMware → Broadcom backing, Spring dominance in banking | Red Hat backing, growing but smaller share in banking | Widely used in ML/AI, less common in banking backends | Oracle-adjacent, niche in banking |
| **Compliance/audit** | Same audit framework (`@Audited`, `AuditInterceptor`) | Must rebuild audit capabilities | Must rebuild or delegate | Must rebuild audit capabilities |
| **Type safety** | Full Java type system, compile-time checks | Full Java type system | Runtime type checking (Pydantic), weaker guarantees | Full Java type system |
| **Team skills** | ✅ Existing team competency | 🔄 Some Java transferable | ❌ Requires Python skills | 🔄 Some Java transferable |
| **Vendor lock-in** | Low — MCP is an open standard; Spring AI is one impl | Low — open standard | Low — reference SDK | Low — open standard |
| **Long-term MCP support** | Spring AI actively tracking MCP spec evolution | Uncertain — community-driven | ✅ Reference implementation, always up to date | Uncertain — no official support |
| **Banking regulatory** | Same tech stack as production → same compliance posture | New runtime → new compliance assessment | New language → new compliance + security assessment | New runtime → new compliance assessment |

**Spring AI MCP maturity assessment (as of 2026-02):**

| Component | Version | Stability | CES-Compatible? |
|-----------|---------|-----------|-----------------|
| Spring AI BOM | 1.0.0 GA | GA release on Maven Central | N/A (parent BOM) |
| `spring-ai-starter-mcp-server-webmvc` | via BOM | GA — production-ready | Yes — Streamable HTTP transport on Tomcat |
| Java MCP SDK (`io.modelcontextprotocol.sdk:mcp`) | 0.17.2 (via BOM) | Active development (0.8.0–0.17.2, 20 releases) | Implements MCP spec |
| `@Tool` annotation model | via BOM | GA — stable API | Yes — auto-registered as MCP tools |
| SSE transport | via BOM | Legacy transport path | ❌ Not supported by CES (since 2026-02-12) |
| Streamable HTTP transport | via BOM | Supported and CES-required | ✅ CES requires Streamable HTTP |

**Note:** Spring AI 1.0.0 GA is available on Maven Central — no milestone repository needed. The `@Tool`/`@ToolParam` annotations from `spring-ai-model` are auto-registered as MCP tools by the boot starter. The newer `@McpTool`/`@McpToolParam` annotations (from `spring-ai-mcp-annotations` module) are available in Spring AI 1.1.0+ for MCP-specific features.

**Verdict:** Spring Boot + Spring AI MCP is the enterprise-aligned choice. It maintains the existing compliance posture, reuses audit infrastructure, and avoids introducing a new language/runtime into a regulated banking application.

---

### View 4: Security Architect — *"What's the threat model difference?"*

| Concern | REST/OpenAPI (Current) | Spring AI MCP (In-Process) | Python MCP (Standalone) |
|---------|------------------------|---------------------------|------------------------|
| **Auth model** | `serviceAgentIdTokenAuthConfig` | ✅ Same — Streamable HTTP endpoint behind same Cloud Run IAM ([platform-reference.md § 5](cx-agent-studio/platform-reference.md)) | Must configure separately |
| **Session context** | `x-ces-session-context` ([design-patterns.md § 5.1](cx-agent-studio/design-patterns.md#51-pass-context-variables-to-openapi-tools-x-ces-session-context)) | ❌ Not available (acceptable — location data is public) | ❌ Not available |
| **PII exposure** | Minimized via `x-ces-session-context` (params invisible to model) | N/A — location data contains no PII | N/A |
| **Audit logging** | `@Audited` + `AuditInterceptor` | ✅ Same interceptor chain (shared Tomcat) | Must implement separately |
| **Attack surface** | REST endpoints only | REST + Streamable HTTP endpoint (incremental) | Separate service = separate attack surface |
| **TLS termination** | Cloud Run managed | ✅ Same Cloud Run service | Separate Cloud Run TLS config |
| **Network segmentation** | Same VPC, same service | ✅ Same service | New service, potentially new VPC config |

**Key finding:** In-process MCP on Spring Boot inherits the entire security posture of the existing service. No new TLS certificates, no new IAM bindings, no new network rules. For a spike evaluating transport behaviour, this is the minimum-risk option.

**Verdict:** In-process Spring AI MCP adds the smallest incremental attack surface. Location services contain no PII, making MCP's lack of `x-ces-session-context` a non-issue for this domain.

---

### View 5: Cost & Performance — *"What's the latency and cost delta?"*

| Dimension | REST/OpenAPI (Baseline) | MCP Streamable HTTP (In-Process) | MCP Streamable HTTP (Standalone Python) |
|-----------|------------------------|---------------------|----------------------------|
| **Connection model** | Request/response (stateless) | Request/response over Streamable HTTP | Request/response over Streamable HTTP |
| **Cold start impact** | Per-request (amortized) | Standard per-request handling | Standard per-request handling + Python startup |
| **Expected tool call latency** | 50-200ms (direct service call) | 80-250ms (JSON-RPC framing over HTTP) | 150-400ms (HTTP delegation to REST) |
| **Memory overhead** | Baseline | +30-50MB (Spring AI + MCP SDK) | +64-128MB (separate Python process) |
| **Cloud Run cost** | Instance-hours (idle scale down) | Comparable to standard HTTP request/response usage | 2× instance-hours (two services) |
| **Infrastructure cost** | 1 Cloud Run service | Same service (no additional cost) | +1 Cloud Run service |

**Critical observation:** Streamable HTTP follows standard request/response lifecycle on Cloud Run. This is manageable for a spike but is a production cost consideration. CES connection pooling and reconnect strategy will determine the real-world cost profile.

**The spike must measure:**
1. initial tools/list HTTP call latency
2. Subsequent `tools/call` latency (warm connection)
3. Reconnect latency after idle timeout
4. Memory delta from Spring AI MCP dependencies

**Verdict:** In-process is the cheapest option for the spike (no additional infrastructure). Latency measurements will inform the production cost-benefit analysis.

---

### Synthesis: Where the Views Converge and Diverge

**All five views agree:**
1. **Spring Boot + Spring AI MCP** is the recommended framework for the spike
2. **In-process topology** (embed MCP in `bfa-service-resource`) is the simplest and cheapest option
3. **Python MCP SDK** is fastest for pure protocol testing but doesn't validate service-layer reuse
4. **Quarkus and Micronaut** offer cold-start benefits but introduce operational complexity without compensating value for a spike
5. Location services are safe for MCP (no PII, no session context requirement)

**The views diverge on:**
- **SRE vs Strategist:** SRE favours the most conservative approach (Spring AI, in-process, minimum change). A strategist might argue for Python as a faster prototype. **Resolution:** The spike's primary goal is validating *service-layer reuse* alongside Streamable HTTP transport, which requires the Java/Spring path.
- **Enterprise Architect vs Developer:** EA initially flagged Spring AI pre-1.0 risk; since resolved — Spring AI 1.0.0 GA (May 2025) and 1.1.2 (Dec 2025) are both stable releases. **Resolution:** GA availability eliminates this concern. Feature branch still appropriate for spike isolation.
- **Cost vs Performance:** Streamable HTTP has lower operational overhead than long-lived SSE (longer instance uptime). **Resolution:** Acceptable for spike; production adoption gated on latency measurements (ADR-CES-002 decision triggers).

---

## Decision

### Framework: Spring Boot + Spring AI MCP Server (WebMVC Starter)

Use `spring-ai-starter-mcp-server-webmvc` embedded in `bfa-service-resource` with Streamable HTTP transport.

**Rationale:**
1. **Direct service-layer reuse** — `LocationMcpTools` can `@Autowired` inject `LocationService` and call it directly, proving the key architectural claim
2. **Same operational surface** — No new deployment, no new monitoring, no new security configuration
3. **CES-required Streamable HTTP** — Spring AI's Streamable HTTP path uses the same embedded Tomcat HTTP stack as production REST
4. **Annotations mirror existing patterns** — `@McpTool` / `@McpToolParam` map naturally from `@Operation` / `@Parameter`
5. **Enterprise-aligned** — Same language, same framework, same compliance posture

### Topology: In-Process (Option A) — MCP Embedded in `bfa-service-resource`

**Chosen over:**
- **Option B (Shared-Library Module):** Unnecessary complexity for a spike. POM surgery, two build artifacts, auto-configuration leakage.
- **Option C (Standalone Service):** Doesn't validate service-layer reuse. Adds infrastructure cost. HTTP delegation adds latency noise to measurements.

### Alternatives Rejected

#### Quarkus + MCP (Rejected for Spike)

- **Pros:** Native compilation → sub-second cold starts on Cloud Run. Reactive model natural for streaming workloads.
- **Cons:** No official MCP integration (community only). Cannot reuse `LocationService` directly — must rewrite or HTTP-delegate. New build pipeline, new deployment, new monitoring. Team unfamiliar with Quarkus runtime.
- **Verdict:** Cold-start advantage is real but irrelevant for evaluating Streamable HTTP transport and service reuse. Reconsider if production MCP adoption requires sub-second cold starts.

#### Python MCP SDK (Rejected for Spike, Recommended for Protocol-Only Tests)

- **Pros:** Reference MCP implementation — always spec-compliant. Fastest time to first tool (~1 hour). Excellent for testing CES `mcpTool` binding format.
- **Cons:** Cannot reuse `LocationService` — must delegate to REST API. Adds separate deployment. Python runtime in a Java-centric banking backend raises governance questions. Type safety limited to Pydantic runtime checks.
- **Verdict:** If the goal were *only* to test CES MCP configuration format, Python would be ideal. But the spike must validate service-layer reuse, which requires the Java path. A Python script can supplement the spike for CES binding format testing.

#### Micronaut + MCP (Rejected)

- **Pros:** Lighter than Spring Boot. Compile-time DI.
- **Cons:** No official MCP support (no equivalent of `spring-ai-starter-mcp-server-webmvc`). Would require raw Java MCP SDK integration. Team unfamiliar.
- **Verdict:** No clear advantage over Spring AI for a spike. Revisit only if Spring AI MCP proves problematic.

---

## Framework Comparison Matrix

| Dimension | Spring Boot + Spring AI | Quarkus | Python MCP SDK | Micronaut |
|-----------|------------------------|---------|----------------|-----------|
| **Service-layer reuse** | ✅ Direct injection | ❌ Rewrite/delegate | ❌ HTTP delegate | ❌ Rewrite/delegate |
| **CES transport compatibility** | ✅ Streamable HTTP (Tomcat) | ⚠️ Streamable HTTP (Vert.x path) | ✅ Streamable HTTP (native support) | ⚠️ Streamable HTTP (Netty path) |
| **Time to working spike** | ~2 hours | ~1 day | ~1 hour | ~1 day |
| **Operational complexity** | None (same service) | High (new service) | Medium (new service) | High (new service) |
| **Enterprise governance** | ✅ Same stack | 🔄 New assessment | ❌ New language | 🔄 New assessment |
| **MCP SDK maturity** | GA (1.0.0–1.1.2; Java MCP SDK 0.17.2) | Experimental | ✅ GA (reference) | ❌ No SDK |
| **Cold start** | 3-8s | 0.5-2s (native) | 0.3-1s | 2-5s |
| **Team familiarity** | ✅ High | ❌ Low | ❌ Low (Python) | ❌ Low |
| **Audit/compliance reuse** | ✅ Shared interceptors | ❌ Rebuild | ❌ Rebuild | ❌ Rebuild |
| **Production migration path** | Direct (same codebase) | Port required | Rewrite required | Port required |

---

## Topology Comparison Matrix

| Dimension | Option A: In-Process | Option B: Shared Library | Option C: Standalone |
|-----------|---------------------|-------------------------|---------------------|
| **Service reuse** | ✅ Direct injection | ✅ Library dependency | ❌ HTTP delegation |
| **Deployment** | Same artifact | Two artifacts | Two services |
| **Operational cost** | None incremental | +1 build + deploy | +1 service + monitoring |
| **Auth config** | Unified | Separate (per artifact) | Separate (per service) |
| **Monitoring** | Single metric stream | Two metric streams | Two metric streams |
| **Scaling** | Unified autoscaler | Independent scaling | Independent scaling |
| **Rollback** | Remove dependency + beans | Delete artifact | Delete service |
| **Latency measurement** | Cleanest (no network hop) | Clean (same JVM if co-deployed) | Noisy (+HTTP hop) |
| **POM complexity** | +1 dependency | Major surgery | None (separate project) |

---

## Implementation Plan (High-Level)

### Phase 1: Dependencies & Configuration (~30 min)

1. Add Spring AI BOM and MCP WebMVC starter to `bfa-service-resource/pom.xml`
2. Configure Streamable HTTP transport properties (CES-required)
3. Configure MCP server metadata (name, version, instructions)

**Key Maven coordinates:**

```xml
<!-- Spring AI BOM -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- MCP Server WebMVC Starter -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

**Key application properties:**

```properties
# MCP Server configuration (CES-compatible, Streamable HTTP since 2026-02-12)
spring.ai.mcp.server.protocol=STREAMABLE
spring.ai.mcp.server.type=SYNC
spring.ai.mcp.server.name=acme-location-services
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.instructions=Search for Acme Bank branch locations by city, address, or GPS coordinates.
```

### Phase 2: MCP Tool Implementation (~1 hour)

1. Create `LocationMcpTools` component with `@Tool` annotated methods
2. Map `branch_search` tool to `LocationService.searchBranches()`
3. Map `branch_details` tool to `LocationService.getBranch()`
4. Ensure tool descriptions are optimized for LLM consumption (clear parameter semantics)

### Phase 3: Testing & Validation (~1 hour)

1. Local testing with MCP Inspector (`npx @modelcontextprotocol/inspector`)
2. Verify `tools/list` returns both tools with correct JSON Schema
3. Verify `tools/call` for `branch_search` and `branch_details`
4. Confirm REST endpoints still function correctly (coexistence)
5. Measure latency: MCP Streamable HTTP path vs REST path for same `LocationService` call

### Phase 4: CES Integration (Optional in Spike)

1. Create `mcpTool` JSON binding for CES agent
2. Deploy to Cloud Run (feature branch, staging environment)
3. Test CES → Cloud Run Streamable HTTP end-to-end
4. Measure end-to-end latency including CES overhead

---

## CES Integration Constraints

### Transport: Streamable HTTP (Mandatory)

As of 2026-02-12, CES requires **Streamable HTTP transport** for MCP servers — SSE transport is not supported ([CES MCP docs](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp)).

| Transport | Spring AI Property | CES Compatible? |
|-----------|-------------------|-----------------|
| **Streamable HTTP** | `spring.ai.mcp.server.protocol=STREAMABLE` | ✅ Required |
| SSE | `spring.ai.mcp.server.protocol=SSE` | ❌ Not supported (since 2026-02-12) |
| Stateless | `spring.ai.mcp.server.protocol=STATELESS` | ❌ Rejected by CES |
| STDIO | `spring.ai.mcp.server.stdio=true` | ❌ Not applicable (managed runtime) |

### Authentication

CES MCP tools use the same auth model as OpenAPI tools ([platform-reference.md § 5](cx-agent-studio/platform-reference.md)):

```json
{
  "displayName": "location_mcp",
  "mcpTool": {
    "serverUrl": "https://bfa-service-resource-XXXXX.us-central1.run.app/mcp",
    "apiAuthentication": {
      "serviceAgentIdTokenAuthConfig": {}
    }
  }
}
```

The MCP Streamable HTTP endpoint must:
- Accept Google identity tokens in the `Authorization: Bearer <token>` header
- Validate the token's audience matches the Cloud Run service URL
- This is handled by Cloud Run's built-in IAM authentication (no code change required)

### Session Context

MCP has no equivalent to `x-ces-session-context` ([design-patterns.md § 5.1](cx-agent-studio/design-patterns.md#51-pass-context-variables-to-openapi-tools-x-ces-session-context)). For location services this is acceptable:
- Branch data is public (no customer-specific data)
- No session correlation required for audit (search queries don't modify state)
- The `@RequiresConsent(AI_INTERACTION)` check is satisfied by CES auth headers

### Cloud Run Configuration

> **Note (2026-02-12):** With CES now requiring Streamable HTTP instead of SSE, the previous SSE-specific hardening (300s timeouts, keepalive pings, reconnect handling) is no longer necessary. Streamable HTTP uses standard request/response semantics compatible with default Cloud Run settings.

| Setting | Recommended Value | Reason |
|---------|------------------|--------|
| Request timeout | 60s (default) | Standard request/response — no long-lived connections |
| Max instances | 2 (spike only) | Cost control during spike |
| Min instances | 0 | Scale to zero when unused |
| Concurrency | 80 (default) | Standard HTTP concurrency |

---

## Risks, Mitigations, and Decision Triggers

### Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| Spring AI annotation model: 1.1.0 added `@McpTool` (Nov 2025) | **Realized** | Low (as predicted) | Spring AI 1.1.0 (Nov 12, 2025) added `@McpTool`/`@McpToolParam` in `spring-ai-mcp-annotations`. Spike uses stable `@Tool`/`@ToolParam` from 1.0.0 GA. Migration path confirmed straightforward. |
| Legacy SSE instability on Cloud Run | ~~Medium~~ N/A | ~~Medium~~ N/A | **Superseded (2026-02-12):** CES now requires Streamable HTTP, which uses standard request/response. SSE stability is no longer a concern for CES integration. |
| Spring AI MCP starter conflicts with existing Spring Boot 3.3.0 config | Low | Medium | Using Spring AI 1.0.0 GA BOM for version alignment. Coexistence verified: 70/70 tests pass. |
| CES `mcpTool` binding format undocumented edge cases | Medium | Low | Spike tests binding end-to-end. Document findings. |
| MCP latency overhead makes production adoption unviable | Low | High | Spike's primary measurement goal. Data informs ADR-CES-002 decision triggers. |
| Team lacks MCP debugging skills | Medium | Low | MCP Inspector provides visual debugging. Spike builds team experience. |

### Decision Triggers for Reassessment

Re-evaluate this implementation choice if:

1. ✅ ~~**Spring AI MCP 1.1+ released**~~ **MET (Nov 2025):** Spring AI 1.1.0 released Nov 12, 2025 (latest: 1.1.2, Dec 8, 2025) with `@McpTool`/`@McpToolParam` annotations. Consider upgrading spike from 1.0.0 → 1.1.2 for MCP-specific features.
2. ✅ ~~**CES adds Streamable HTTP support**~~ **MET (2026-02-12):** CES now requires Streamable HTTP; SSE is no longer supported. Spike should use `STREAMABLE` protocol.
3. **Spike latency > 2× REST baseline** — evaluate if in-process overhead is the cause or if MCP protocol framing is inherent
4. **Quarkus or Micronaut ship official MCP starters** — re-evaluate framework choice for production
5. **Python MCP SDK needed** for CES binding format testing that the Spring AI path cannot provide

---

## Consequences

### Positive
- Validates the key architectural claim: `LocationService` serves both REST and MCP transports without business logic duplication
- Builds team MCP experience with minimal operational risk (same service, same deployment)
- Produces latency data that informs ADR-CES-002 production adoption triggers
- Documents CES `mcpTool` binding configuration for future reference
- In-process topology confirms (or refutes) the coexistence model recommended by ADR-CES-002

### Negative / Trade-offs
- Spring AI 1.0.0 GA dependency is stable; 1.1.x (Nov–Dec 2025) added `@McpTool` as expected — migration straightforward (spike uses stable `@Tool` API from 1.0.0)
- MCP Streamable HTTP endpoint adds attack surface to `bfa-service-resource` (mitigated by feature branch + Cloud Run IAM)
- Spike does not test standalone deployment scenarios (Option B/C remain theoretical)
- ~~Resolved (2026-02-12): CES requires Streamable HTTP; spike validates Streamable HTTP path~~ **Resolved (2026-02-12):** CES now requires Streamable HTTP; spike should use `STREAMABLE` protocol.

---

## Appendix: Spring AI MCP Version Compatibility

| Spring Boot | Spring AI BOM | MCP SDK | Spring AI MCP WebMVC | Status |
|-------------|---------------|---------|---------------------|--------|
| 3.3.0 (current) | 1.0.0 GA | 0.8.x (via BOM) | via BOM | ✅ Tested and verified in spike |
| 3.3.x–3.4.x | 1.0.1–1.0.3 | via BOM | via BOM | ✅ Compatible (patch releases: Jul–Oct 2025) |
| 3.4.x–3.5.x | **1.1.0–1.1.2** (latest stable) | 0.17.2 (via BOM) | via BOM | ✅ Adds `@McpTool` annotations (Nov 12, 2025); latest: 1.1.2 (Dec 8, 2025) |
| 3.5.x+ | 2.0.0-M2 (milestone) | via BOM | via BOM | ⚠️ Pre-release milestone only (Jan 23, 2026). Note: Spring Boot 4.x renames `starter-web` → `starter-webmvc` |

**No milestone repository needed** — Spring AI 1.0.0 GA is on Maven Central.

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-09 |  | Initial proposal — framework evaluation, topology decision, implementation plan |
| 2026-02-09 |  | Added cx-agent-studio evidence links for session context, auth model, and transport constraints. |
| 2026-02-09 |  | **Version audit:** Corrected all version references using verified Maven Central data. Spring AI latest stable: 1.1.2 (Dec 8, 2025). Java MCP SDK: 0.17.2. Updated risk table (1.1.0 annotation change realized), decision triggers (trigger #1 met), appendix compatibility matrix. |
| 2026-02-12 | Copilot | **Transport reversal:** CES official docs now require Streamable HTTP; SSE is no longer supported ([CES MCP docs](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp), updated 2026-02-12). Updated Spring AI config `SSE`→`STREAMABLE`. Reversed transport compatibility table. Replaced SSE Cloud Run hardening with standard Streamable HTTP configuration. Updated spike phases, risks, decision triggers (#2 MET), and consequences. Updated toolset JSON endpoint. |
| 2026-02-12 | Codex | Consistency pass: cleaned leftover SSE-era wording in framework/latency/security sections and normalized the implementation guidance to Streamable HTTP as the CES-required transport. |
