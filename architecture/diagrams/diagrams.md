# Acme Banking — Mermaid Diagrams

> These diagrams are provided as individual `.mmd` files and also embedded here for quick viewing.
> Canonical agent names/IDs/source mapping: `../agent-registry.md`
> Updated by Codex on 2026-02-28 for P0 architecture alignment.

## Context diagram
```mermaid
flowchart LR
  subgraph User[End User]
    U((Voice Caller))
  end

  subgraph GoogleManaged[Google-managed: CX Agent Studio]
    CES[CX Agent Studio\nRoot + Subagents]
  end

  subgraph Perimeter[VPC-SC Service Perimeter: acme-landingzone]
    subgraph NetProj[Net Project / Shared VPC]
      SD[Service Directory\nPrivate Network Access]
      ILB[Internal HTTP(S) LB]
      DNS[Cloud DNS Private Zones]
      IC[Cloud Interconnect\n(HA VLAN attachments)]
    end

    subgraph AppProj[App Project]
      BFA[BFA Gateway\nCloud Run (internal)]
      AUTHZ[AuthZ PDP Service\n(Cloud Run or GKE)]
      OBS[Logs/Traces/Audit Sinks]
      KB[Knowledge Index]
      FEES[Fees Read Model]
    end

    subgraph ApigeeProj[Apigee Project]
      AP[Apigee Internal\n(GCP-based)]
    end
  end

  subgraph CIAMLandingZone[GCP Landing Zone: CIAM]
    CIAM[ForgeRock CIAM]
  end

  subgraph OnPrem[On-Prem DC]
    GLUE[Glue (WSO2 API Manager)]
    CORE[Core Systems of Record]
  end

  U --> CES
  CES -->|Tool call via SD PNA| SD
  SD --> ILB --> BFA

  BFA --> AUTHZ
  BFA -->|Token exchange / introspection| CIAM
  BFA -->|Regulated/productized APIs| AP
  BFA -->|Legacy/internal APIs| GLUE

  AP -->|via Interconnect| IC --> CORE
  GLUE -->|via Interconnect| IC --> CORE
  %% NOTE: CIAM is GCP cloud-hosted — no Interconnect dependency

  BFA --> OBS
  BFA --> KB
  BFA --> FEES
  DNS --- SD
```

## Sequence: Balance inquiry (sensitive)
```mermaid
sequenceDiagram
  participant User
  participant CES as CX Agent Studio
  participant BFA as BFA Gateway (Cloud Run)
  participant CIAM as ForgeRock CIAM
  participant PDP as AuthZ PDP
  participant AP as Apigee Internal
  participant Core as Core Banking

  User->>CES: "What's my balance?"
  CES->>BFA: Tool call (Service Directory PNA)\nheaders: request_id, session_id, agent_id, tool_id, customer_id_hash, auth_level, policy_decision_id
  BFA->>BFA: Validate CES identity (ID token)\nValidate schema and mandatory headers\nClassify sensitivity
  BFA->>CIAM: Token exchange (RFC8693 or equivalent)\nMint short-lived agentic token (aud+scopes+acr)\npropagate mandatory headers
  BFA->>PDP: AuthZ decision request\n(subject/action/resource/context)\npropagate mandatory headers
  PDP-->>BFA: Permit + obligations (redaction, step-up)\nreturn policy_decision_id
  BFA->>AP: Call product API (mTLS)\nattach agentic token\npropagate mandatory headers + policy_decision_id
  AP->>Core: Private call to SoR\npropagate mandatory headers + policy_decision_id
  Core-->>AP: Balance payload
  AP-->>BFA: Normalized response + upstream ids
  BFA-->>CES: Sanitized response + correlation ids
  CES-->>User: Voice response
```

## Sequence: Branch lookup (non-sensitive)
```mermaid
sequenceDiagram
  participant User
  participant CES as CX Agent Studio
  participant BFA as BFA Gateway (Cloud Run)
  participant LOC as Location Adapter (internal)
  participant LOCRM as Location Read Model (BFA internal)
  participant Glue as Glue (WSO2) / Location API

  User->>CES: "Find the closest branch"
  CES->>BFA: Tool call (Service Directory PNA)\nheaders: request_id, session_id, agent_id, tool_id, customer_id_hash, auth_level, policy_decision_id
  BFA->>BFA: Validate CES identity\nValidate mandatory headers\nClassify as non-sensitive
  BFA->>LOC: Internal route to location domain\npropagate mandatory headers
  alt BFA read model has data
    LOC->>LOCRM: Deterministic lookup by city/postal code
    LOCRM-->>LOC: Branch list + source version
  else Live location call required
    LOC->>Glue: Query branches/ATMs (mTLS, private)\npropagate mandatory headers
    Glue-->>LOC: Branch list
  end
  LOC-->>BFA: Normalized result set (no PII)
  BFA-->>CES: Response + correlation ids
  CES-->>User: Voice response
```

## Sequence: Step-up authentication (ACR-driven)
```mermaid
sequenceDiagram
  participant User
  participant CES as CX Agent Studio
  participant BFA as BFA Gateway
  participant CIAM as ForgeRock CIAM
  participant PDP as AuthZ PDP
  participant AP as Apigee Internal
  participant Core as Core Banking

  User->>CES: "Show my last 10 card transactions"
  CES->>BFA: Tool call (Service Directory PNA)\nheaders: request_id, session_id, agent_id, tool_id, customer_id_hash, auth_level, policy_decision_id
  BFA->>BFA: Validate CES identity\nValidate mandatory headers\nDetect sensitive intent (cards/transactions)
  BFA->>CIAM: Validate current auth context (token/introspection)\npropagate mandatory headers
  BFA->>PDP: AuthZ request (includes auth assurance/acr)\npropagate mandatory headers
  PDP-->>BFA: Permit with obligation: step_up_required(acr=HIGH)\nreturn policy_decision_id
  BFA-->>CES: Step-up required response\n(challenge method + policy_decision_id)
  CES-->>User: Trigger step-up (out-of-band)\n"Please confirm in app / OTP"
  User-->>CIAM: Completes step-up authentication
  CIAM-->>BFA: Step-up complete / new auth context
  BFA->>CIAM: Token exchange to mint agentic token with acr=HIGH\npropagate mandatory headers
  BFA->>AP: Call cards API (mTLS)\nattach agentic token\npropagate mandatory headers + policy_decision_id
  AP->>Core: Private call to SoR\npropagate mandatory headers + policy_decision_id
  Core-->>AP: Card transaction payload
  AP-->>BFA: Normalized response
  BFA-->>CES: Sanitized response + correlation ids
  CES-->>User: Voice response
```

## Sequence: Fee lookup (deterministic)
```mermaid
sequenceDiagram
  participant User
  participant CES as CX Agent Studio
  participant BFA as BFA Gateway (Cloud Run)
  participant FEE as Fees Adapter (internal)
  participant FEERM as Fees Read Model (BFA internal)
  participant Glue as Glue (WSO2) / Fee API

  User->>CES: "What is the fee for international transfer?"
  CES->>BFA: Tool call (Service Directory PNA)\nheaders: request_id, session_id, agent_id, tool_id, customer_id_hash, auth_level, policy_decision_id
  BFA->>BFA: Validate CES identity\nValidate mandatory headers\nClassify as informational
  BFA->>FEE: Internal route to fee domain\npropagate mandatory headers
  alt Curated fee read model is current
    FEE->>FEERM: Deterministic lookup\napply effective-date rules
    FEERM-->>FEE: Fee details + conditions + source version
  else Curated model unavailable or stale
    FEE->>Glue: Query fee API (mTLS, private)\npropagate mandatory headers
    Glue-->>FEE: Fee details + conditions
  end
  FEE-->>BFA: Normalized fee response
  BFA-->>CES: Response template + correlation ids
  CES-->>User: Voice response
```

## ADR-0104 review: Connectivity (unverified state)
```mermaid
flowchart LR
  subgraph User[End User]
    U((Voice Caller))
  end

  subgraph GoogleManaged[Google-managed]
    CES[CX Agent Studio]
  end

  subgraph GCP[GCP Landing Zone]
    SD[Service Directory PNA]
    ILB[Internal HTTP(S) Load Balancer]
    BFA[BFA Gateway]
    ADP[Domain Adapters]
    PDP[AuthZ PDP]
  end

  subgraph CIAMLandingZone2[GCP Landing Zone: CIAM]
    CIAM[ForgeRock CIAM]
  end

  subgraph OnPrem[On-Premises]
    GLUE[Glue WSO2 API Manager]
  end

  subgraph ApigeeProj[Apigee Project]
    AP[Apigee Internal]
  end

  subgraph Hybrid[Hybrid Network Controls]
    IC[Cloud Interconnect]
    VPN[Cloud VPN Fallback]
    DNS[Private DNS Split-Horizon]
    FW[Firewall + Routing Policy]
    MTLS[mTLS Trust Chain]
  end

  U --> CES
  CES --> SD --> ILB --> BFA --> ADP
  BFA --> PDP
  ADP --> AP
  ADP -.->|UNVERIFIED| GLUE
  BFA -.->|UNVERIFIED| CIAM
  ADP -.->|depends on| IC
  ADP -.->|fallback| VPN
  ADP -.->|depends on| DNS
  ADP -.->|depends on| FW
  ADP -.->|depends on| MTLS
```

## ADR-0104 review: Option A (strict single BFA)
```mermaid
flowchart LR
  CES[CES Tools] -->|Single ingress| BFA[BFA Gateway]
  BFA -->|Policy + token exchange + PDP| PEP[Security Enforcement Layer]
  PEP --> ADP1[Banking Adapter]
  PEP --> ADP2[Fees Adapter]
  PEP --> ADP3[Location Adapter]
  ADP1 --> AP[Apigee Internal]
  ADP2 --> GLUE[Glue]
  ADP3 --> RM[Curated Read Models]
```

## ADR-0104 review: Option B (federated adapters)
```mermaid
flowchart LR
  CES[CES Tools] -->|Single ingress| BFA[BFA Gateway]
  BFA --> POLICY[Identity + PDP + Obligations]

  subgraph Domain1[Team A Domain]
    A1[Banking Adapter]
    C1[Domain Cache]
  end

  subgraph Domain2[Team B Domain]
    A2[Fees Adapter]
    C2[Domain Cache]
  end

  subgraph Domain3[Team C Domain]
    A3[Location Adapter]
    C3[Domain Cache]
  end

  POLICY --> A1
  POLICY --> A2
  POLICY --> A3

  A1 <--> C1
  A2 <--> C2
  A3 <--> C3

  A1 --> AP[Apigee Internal]
  A2 --> GLUE[Glue]
  A3 --> RM[Curated Read Models]
```

## ADR-0104 review: Option C (controlled Apigee exceptions)
```mermaid
flowchart LR
  CES[CES Tools]
  BFA[BFA Gateway]
  AP[Apigee Internal]
  GLUE[Glue]
  PDP[AuthZ PDP]
  CIAM[CIAM]

  CES -->|Default path| BFA
  BFA --> PDP
  BFA --> CIAM
  BFA --> AP
  BFA --> GLUE

  CES -. Exception path (approved non-sensitive reads only) .-> AP
```
