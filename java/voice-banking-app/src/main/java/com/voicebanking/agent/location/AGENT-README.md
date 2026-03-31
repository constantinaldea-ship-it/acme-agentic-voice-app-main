# LocationServicesAgent

> **Agent ID:** `location-services`  
> **Package:** `com.voicebanking.agent.location`  
> **Status:** ✅ Implemented  
> **Category:** Category 2 — Voice-Enabled Context-Aware Banking (Read)  
> **Priority:** Foundation (Pre-existing)

---

## Agent Description

The **LocationServicesAgent** provides location-based banking services, primarily finding nearby branches and ATMs. It enables customers to locate physical banking touchpoints using geographic coordinates.

### Role in System

- **Primary Use:** Branch and ATM location queries
- **Interface:** I-09 Branch Directory API
- **User Intents:** "Find a branch near me", "Where's the nearest ATM?", "What are the branch hours?"

---

## Capabilities (Tools)

| Tool ID | Description | Input Parameters | Output |
|---------|-------------|------------------|--------|
| `findNearbyBranches` | Find branches/ATMs near coordinates | `latitude`, `longitude`, `radiusKm`, `limit`, `type` | List of `Branch` objects with distance |

### Tool Usage Examples

```
findNearbyBranches { 
  latitude: 50.1109, 
  longitude: 8.6821, 
  radiusKm: 5.0, 
  limit: 3,
  type: "branch" 
}
→ { branches: [{ name: "Frankfurt Main", distance: 0.5km, ... }, ...] }
```

---

## Problem Statement

### Business Problem
Customers often ask for nearby branches or ATMs, especially when traveling or needing specific services (safe deposit boxes, mortgage consultations). Voice-enabled location queries provide a hands-free way to find banking locations.

### Technical Problem
Need to:
- Accept geographic coordinates from device/user
- Search branch database by proximity
- Filter by service type (ATM-only, full-service, appointment-required)
- Return distance-sorted results optimized for voice readout

### FR Coverage
- **FR-001:** General Public Information (branch info component) ✅

---

## Solution Approach

### Architecture Pattern
```
LocationServicesAgent
         │
         ▼
    BranchService (Search Logic)
         │
         ▼
    CSV-based Branch Data
    (deutsche_bank_branches_dataset_synthetic_subset.csv)
```

### Key Design Decisions

1. **CSV Data Source:** Uses synthetic branch dataset for MVP. Production would use Branch Directory API.

2. **Haversine Distance:** Calculates great-circle distance for proximity sorting.

3. **Type Filtering:** Supports "all", "branch", "atm" filtering.

4. **Coordinate Validation:** Requires valid latitude/longitude; throws `IllegalArgumentException` if missing.

---

## Dependencies

### Internal
- `BranchService` — Branch search logic
- `Branch` — Domain model for branch/ATM

### External
- Branch Directory (CSV file, later API)

### Package Structure
```
location/
├── LocationServicesAgent.java                    # Main agent
├── deutsche_bank_branches_dataset_synthetic_subset.csv  # Mock data
├── domain/
│   └── Branch.java                               # Branch record
├── service/
│   └── BranchService.java                        # Search logic
└── tool/
    └── (Tool helpers)
```

---

## Current Gaps

| Gap | Description | Impact | Priority |
|-----|-------------|--------|----------|
| **No Real API Integration** | Uses static CSV, not live Branch Directory | High for production | P1 |
| **No ATM Tool** | Only `findNearbyBranches`, no dedicated ATM search | Medium | P2 |
| **No Opening Hours Tool** | Cannot query if branch is currently open | Medium | P2 |
| **No Service Filtering** | Cannot filter by specific services (e.g., safe deposit) | Medium | P2 |
| **No Address Geocoding** | Requires coordinates, cannot search by address/city | High | P1 |
| **No Appointment Booking** | Cannot check/book appointments at branches | Low (H2 scope) | P3 |

### Missing Tools from Architecture Plan
The master plan suggests 1 tool. Current implementation matches: ✅

---

## Alternative Approaches

### Current: Single-Tool Agent
```
LocationServicesAgent
    └── findNearbyBranches (handles branches and ATMs via type filter)
```

### Alternative 1: Multi-Tool Agent
Expand to multiple specialized tools:
```
LocationServicesAgent
    ├── findNearbyBranches
    ├── findNearbyATMs
    ├── getBranchDetails
    ├── checkBranchOpenNow
    └── getServicesAtBranch
```

**Pros:**
- Clearer intent per tool
- Better voice prompting ("Find an ATM" vs "Find a branch, type ATM")

**Cons:**
- More tools to maintain
- Some overlap (ATM is a type of branch result)

### Alternative 2: Merge into KnowledgeCompilerAgent
Location queries are a form of "knowledge lookup":
```
KnowledgeCompilerAgent
    ├── getKnowledge
    ├── getBankInfo      # Could include branch info
    └── searchFAQ
```

**Pros:**
- Fewer agents to manage
- Knowledge agent already handles bank info

**Cons:**
- Location queries need geographic logic
- Different data source (spatial vs text)
- Violates single responsibility

### Alternative 3: Skills Pattern
```
BranchFinderSkill.findNear(lat, lon, radius)
ATMFinderSkill.findNear(lat, lon)
GeocodeSkill.addressToCoordinates(address)
```

**Pros:**
- Skills can be composed
- GeocodeSkill reusable across system

**Cons:**
- Overkill for current scope
- Need skill registry infrastructure

### Recommendation
**Expand to Multi-Tool Agent** (Alternative 1) when implementing:
- Address geocoding (P1 gap)
- Branch hours/availability queries
- Keep as dedicated agent (not merged into Knowledge)

---

## Architectural Analysis

### Agent vs Skills Evaluation

| Criterion | Agent Pattern (Current) | Skills Pattern |
|-----------|------------------------|----------------|
| Spatial Logic | ✅ Centralized in agent | 🟡 Spread across skills |
| Data Access | ✅ Single data source | 🟡 Each skill needs access |
| Composability | 🟡 Agent-level only | ✅ Skills can mix |
| Complexity | ✅ Simple, focused | 🟡 More moving parts |

### Granularity Assessment

**Current State:** Potentially **too fine-grained**
- Single tool agent
- Could be merged with other agents

**Recommendation:** Keep separate because:
1. Location logic is distinct (spatial queries)
2. Different data source than other agents
3. Will grow to 3-5 tools with address geocoding, hours checking

**Future Granularity Target:** 3-5 tools
```
findNearbyBranches
findNearbyATMs
getBranchHours
getServicesAtBranch
geocodeAddress      # NEW: address → coordinates
```

---

## Testing

### Unit Tests
- `LocationServicesAgentTest.java` — Tool execution tests

### Integration Tests
- Test with CSV data source
- Verify distance calculations

### Golden Test Cases
```
1. Find 3 nearest branches to Frankfurt coordinates
2. Find ATMs within 10km radius
3. Validation error for missing coordinates
4. Empty results for remote location
5. Default radius (5km) when not specified
```

---

## Related Documents

- [Agent Interface](../Agent.java)
- [AgentRegistry](../AgentRegistry.java)
- [FR-001 General Public Information](../../../../../docs/functional-requirements/fr-001-general-public-information.md)
- [AGENT-ARCHITECTURE-MASTER](../../../../../docs/implementation-plan/AGENT-ARCHITECTURE-MASTER.md)

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-24 |  | Initial documentation |
