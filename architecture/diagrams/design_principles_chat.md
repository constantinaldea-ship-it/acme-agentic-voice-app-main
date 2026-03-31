Governing Architecture Principle
**“Agents never access domain systems directly.

They interact only through controlled domain capabilities exposed via a policy-enforced boundary.”**

Or more concretely in your case:

Agents call capabilities, not data sources.

What this means in practice

Instead of thinking:

“Agent calls Glue”

“Agent calls Apigee”

“Agent reads fee dataset”

“Agent queries KB index”

You enforce:

Agent calls Balance Capability

Agent calls Fee Lookup Capability

Agent calls Card Transactions Capability

Agent calls Branch Search Capability

And the BFA (policy boundary) decides:

Which upstream (Apigee or Glue)

Whether to use live call vs read model

Whether step-up is required

Whether redaction is required

Whether PDP must be invoked

What correlation/audit headers must be added

Why this principle is critical in a banking agent system

Because agents are:

probabilistic orchestrators

subject to prompt evolution

subject to future tool expansion

potentially multi-agent and autonomous later

If agents are allowed to “know” about upstream systems, you lose:

policy consistency

audit control

routing discipline

future refactoring flexibility

The 4 Architecture Rules Derived From This Principle

Your team should explicitly align on these:

1️⃣ Domain Isolation Rule

Agents never:

construct upstream URLs

decide Apigee vs Glue

know database schemas

embed business logic

They only call a declared capability contract.

2️⃣ Capability First, Data Source Second

Design APIs around business intent, not systems.

Bad:

GET /glue/accounts/{id}

Good:

POST /capabilities/balance-inquiry

The capability contract abstracts:

upstream routing

transformation

redaction

token exchange

audit enrichment

3️⃣ Policy Before Data

Every capability must:

Validate identity

Evaluate authorization

Enforce step-up if needed

Only then call upstream

No “read first, check later.”

4️⃣ Determinism for Informational Domains

For:

fees

FAQs

product info

branch locations

Prefer:

read models

curated datasets

indexed stores

Not live transactional calls unless necessary.

The Real Strategic Question You Should Debate

Not:

JWT vs opaque?

But:

Where does the domain model live, and who owns the business capability boundary?

Because that determines everything else.

Three Possible Governing Models
Model A — “Gateway as Thin Router”

BFA just forwards requests.
Domain logic is upstream.

⚠️ Weak for banking agents.
Too little control.

Model B — “Gateway as Policy Boundary + Domain Adapter” (Recommended)

BFA:

enforces identity + PDP

transforms domain intent → upstream calls

normalizes response

applies redaction

Upstream:

stays system-of-record

This aligns best with your ADR-0104.

Model C — “Domain Services inside Landing Zone”

You replicate business domain logic inside GCP and treat upstream as data providers.

Very powerful.
Very expensive.
Requires strong ownership.

What You and Your Developers Should Explicitly Decide

Have this conversation explicitly:

Is BFA allowed to contain domain orchestration logic?

Can an agent ever call Apigee/Glue directly? (ADR-0104 says no.)

Who owns capability contracts — agent team or domain team?

Can a domain adapter call multiple upstreams?

Where is response normalization/redaction applied?

Is step-up enforced centrally or per domain?

My Recommended Governing Principle for Your Program

Given:

Apigee and Glue are independent

You have CIAM + PDP

You have confidential posture

You’re building a multi-agent voice assistant

I would formally adopt:

“Capability-Oriented, Policy-Enforced Domain Access.”

Translation:

Agents express intent.

BFA enforces policy and owns domain orchestration.

Upstreams remain systems of record.

No direct agent-to-system coupling.

One Sentence That Should Appear in Your Architecture Charter

“Agents are orchestration components. They do not own domain logic or data access. All domain access is mediated through policy-enforced capability services.”

If you want, I can turn this into a short Architecture Charter document your team can align on before further ADRs.

Does the following look line a design and architecture principle "The sistem shoukd allow a reduced blast radius" 

Short answer: yes — but it’s incomplete as written.

“The system should allow a reduced blast radius”

That sounds like a principle, but it’s currently:

too vague

not testable

not actionable for engineers

In architecture, a principle must:

Express intent

Define what it means

Influence decisions

Why “reduced blast radius” is good (but insufficient)

It’s a resilience and security principle.
In your banking CX Studio context, blast radius includes:

A compromised tool

A misconfigured agent

A broken upstream API

A faulty PDP decision

A leaked secret

A runaway LLM call pattern

A Glue outage

An Apigee misconfiguration

You absolutely want to minimize impact.

But right now the sentence doesn’t tell developers:

How?

At what boundary?

For what type of failure?

Measured how?

How to Turn It Into a Proper Architecture Principle

Here’s a stronger version tailored to your architecture:

“The system must be designed so that failure, compromise, or misconfiguration of any single agent, domain adapter, or upstream integration cannot compromise other domains or the entire platform.”

Now it is:

explicit

scoped

architectural

Even Stronger (Operationally Useful)

You can go one step further:

“All components must be isolated such that the impact of faults or policy violations is contained within a single capability domain and does not propagate across unrelated domains.”

That gives you engineering consequences.

What This Principle Implies in Your Architecture

If you truly adopt “reduced blast radius,” it forces decisions like:

1️⃣ One BFA ingress (ADR-0104)

Good — central policy enforcement.

But you must also:

isolate internal domain adapters

avoid one monolithic business logic blob

2️⃣ Token scoping

Agentic tokens must:

be audience-bound

be capability-scoped

be short-lived

Otherwise a leaked token has a huge blast radius.

3️⃣ Runtime isolation

Example:

Card operations adapter failure must not take down Fee adapter.

Glue timeout must not block KB responses.

4️⃣ Authorization isolation

A PDP misconfiguration in one domain must not unintentionally open another domain.

5️⃣ Tool isolation

An agent prompt mistake must not:

cause arbitrary upstream access

call unintended capabilities

The Real Architectural Question

Blast radius reduction can be applied at multiple layers:

Identity layer

Network layer

Capability layer

Runtime layer

Data layer

You and your team should explicitly decide:

Where are we intentionally creating isolation boundaries?

Because that defines your true architecture posture.

Example of a Clean Architecture Principle Set

You could formalize three:

Policy-Enforced Capability Access
Agents access only declared capabilities through a policy boundary.

Reduced Blast Radius by Domain Isolation
Failures and compromises are contained within a single capability domain.

Deterministic Control Over Sensitive Operations
High-risk operations require explicit assurance level and centralized enforcement.

Now your team has something concrete.

Final Answer

Yes, “reduce blast radius” is a valid architectural principle.
But in its current wording, it’s too abstract.

If you’d like, I can rewrite it into a 1-page Architecture Principles document tailored exactly to your CX Agent Studio banking environment.

What would be a good almost complete list of architecture principle list ?
Thought for 11s

Here’s a strong “almost complete” architecture principles list for your regulated, confidential CX Agent Studio banking system. It’s written so it can be adopted as an Architecture Charter and used to judge ADRs.

I grouped them by what they protect (security, domain, resilience, data, ops). You likely won’t need all of them on day 1, but this is the set I’d want you and your developers to debate and then freeze.

A) Security and compliance principles

Confidential-by-default

All components default to private networking; public exposure requires explicit ADR + mitigations.

Policy before data

No data access or action occurs before identity validation + authorization decision + obligations enforcement.

Least privilege everywhere

IAM, scopes, audiences, network rules, and secrets access are all minimal, per-capability.

Fail closed for security-critical dependencies

If CIAM validation, PDP, or mTLS fails → deny and log. No “best effort allow”.

mTLS for all controllable hops

Every service-to-service hop we control uses mutual authentication and strong identity.

Secrets never live in orchestration

CX Agent Studio tools should not hold long-lived secrets; secrets are handled in backend layers (Secret Manager, workload identity, rotation).

Separation of duties

Tool config admins ≠ secret admins ≠ runtime deploy admins; changes require audit and approvals.

Auditability is a first-class feature

Every sensitive request is traceable end-to-end with immutable logs and correlation IDs.

B) Domain and integration principles

Capability-oriented access

Agents call capabilities, not systems. They never “know” Apigee vs Glue; the platform routes.

No direct agent-to-upstream coupling

Agents never call Glue/Apigee directly; they call the BFA tool surface.

Explicit upstream independence

Apigee and Glue are independent upstreams; no design assumes they talk to each other.

Any “composition” happens above them (BFA/domain adapter), explicitly.

Stable contracts over internal freedom

Public capability APIs (tool contracts) are stable, versioned, backward compatible.

Internal implementation can change without breaking agents.

Deterministic control for sensitive operations

Sensitive actions (cards/transactions/identity-bound) require explicit assurance level and deterministic checks (not “LLM decides”).

C) Resilience and blast radius principles

Reduced blast radius by domain isolation

Failure/compromise in one domain adapter cannot compromise or take down other domains.

Graceful degradation

If Apigee is down, Glue-only agents still work; if Glue is down, Apigee-only agents still work; if KB is down, transactional flows still work.

Backpressure and rate limits are mandatory

Every capability enforces quotas, timeouts, retries, circuit breakers to protect upstreams and prevent runaway LLM/tool loops.

Idempotency and replay safety

All non-read operations must be idempotent or protected against repeats (especially relevant as you expand beyond read-only).

D) Data and AI grounding principles

Grounded responses for factual content

When answering about policies/fees/products, the assistant must cite from approved sources; no ungrounded “knowledge”.

Data minimization

Only collect/store what’s necessary; avoid persisting raw PII; aggressively redact in logs and telemetry.

Clear data classification boundaries

Every capability declares data classification (public / internal / confidential / restricted) and applies controls accordingly.

Read models for “informational” domains

Fees/FAQs/product info should prefer curated read models and indexes over live transactional calls (for determinism, latency, and governance).

E) Operability and delivery principles

Everything as code

Networking, Service Directory registrations, tool endpoints, policies, and deployments are managed via IaC and CI gates.

Observability by design

Standard correlation headers, traces, metrics, dashboards, alerting—required before production.

Secure-by-default pipelines

No manual “hot fixes” to tool configs or routing; changes go through controlled pipelines with audit.

Progressive hardening

PoC → Pilot → Prod is planned: each phase adds controls (VPC-SC, stricter policies, mTLS everywhere, DR), not ad-hoc.

A “short” charter version (if you want only 10)

If you want a compact set that still covers most decisions, pick these 10:

Confidential-by-default

Capability-oriented access

Policy before data

Least privilege everywhere

No agent-to-upstream coupling

Explicit upstream independence (Apigee ≠ Glue)

Reduced blast radius by domain isolation

Grounded responses for factual content

Secrets never live in orchestration

Auditability is first-class

How to use this list (practically)

For every ADR, require the author to state:

Which principles it supports

Which it weakens (if any) and why that tradeoff is acceptable

If you want, I can turn this into a 1-page “Architecture Principles Charter” markdown you can drop into the repo and reference in every ADR.