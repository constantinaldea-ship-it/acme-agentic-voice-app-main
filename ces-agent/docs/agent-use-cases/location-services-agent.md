# Use Case Spec: LocationServicesAgent — Branch & ATM Finder

> **Agent ID:** `location_services_agent`  
> **Document Type:** Persona-Driven Use Case Specification  
> **Version:** 2.0  
> **Date:** 2026-02-09  
> **Author:**   
> **Status:** ✅ Revised — Aligned with bfa-service-resource implementation  
> **Related FR:** [FR-001 General Public Information](../functional-requirements/fr-001-general-public-information.md)  
> **Related Implementation Plan:** [AGENT-ARCHITECTURE-MASTER](../implementation-plan/AGENT-ARCHITECTURE-MASTER.md)  
> **Agent Source (CES):** `ces-agent/acme_voice_agent/agents/location_services_agent/`  
> **Backend Source:** `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/location/`  
> **OpenAPI Spec:** `ces-agent/acme_voice_agent/toolsets/location/open_api_toolset/open_api_schema.yaml`

---

## Table of Contents

1. [Persona Profile](#1-persona-profile)
2. [Current Implementation Summary](#2-current-implementation-summary)
3. [Use Cases](#3-use-cases)
4. [Dependencies](#4-dependencies)
5. [Intent Impact Analysis](#5-intent-impact-analysis)
6. [Conversational Agent Design Foundation](#6-conversational-agent-design-foundation)
7. [Accessibility & Inclusion Recommendations](#7-accessibility--inclusion-recommendations)
8. [Remaining Implementation Gaps](#8-remaining-implementation-gaps)
9. [Evaluation Test Matrix](#9-evaluation-test-matrix)

---

## 1. Persona Profile

### Brigitte — Primary Persona

| Attribute | Detail |
|-----------|--------|
| **Age** | 60 |
| **Former bank** | Postbank (acquired by Deutsche Bank) |
| **Digital literacy** | Low — prefers in-person banking, avoids apps |
| **Primary language** | German (possibly regional dialect) |
| **Key frustration** | Feels abandoned by digitalization; misses her familiar Postbank branch |
| **Core need** | Find a branch where she can speak to a real person, face-to-face |
| **Accessibility** | May need wheelchair access or elevator; finds stairs difficult |
| **Technology comfort** | Uses a landline phone or basic smartphone; no location sharing |

### Multi-Brand Context

> **⚠️ CRITICAL CONSTRAINT:** The voice assistant serves a **multi-brand customer base.** The branch dataset contains **two brands**:
>
> | Brand | Count in Dataset | Customer Profile | Likely Caller Behaviour |
> |-------|-----------------|------------------|------------------------|
> | **Deutsche Bank** | 223 branches | Core brand, full-service | Default assumption; full branchServices available |
> | **Postbank** | 170 branches | Mass-market retail, often older, lower digital literacy | References "meine Postbank", confused by branch closures; 170 of 170 are **self-service only** (empty `branchServices`) |
>
> **Key data insight:** Every Postbank entry in the dataset has `branchServices: []` — meaning Postbank locations are **self-service terminals only** (ATMs, Kontoauszugsdrucker, etc.) with no counter advisory. This is critical for UC-BF-04 and UC-BF-06.
>
> The agent should also handle mentions of **Norisbank** and **Fyrst** (other Deutsche Bank Group subsidiaries), even though they are not in the branch dataset. These customers can use any Deutsche Bank branch.

### Why This Persona?

Brigitte represents the **hardest-to-serve user segment** for a voice branch finder. If the agent works for Brigitte, it works for everyone. She:

- Thinks in city names, street names, and landmarks — not coordinates
- May reference legacy brand names ("Postbank", "Norisbank", "Fyrst")
- Needs accessibility information proactively (not as an afterthought)
- Requires patience, repetition, and slow delivery
- Will escalate to a human agent if the system frustrates her

---

## 2. Current Implementation Summary

### Architecture

```
CES Agent (Google CX Agent Studio)
    └── location_services_agent
            │
            ├── searchBranches  → GET /api/v1/branches  (city, address, postalCode, lat/lon, brand, accessible, limit)
            └── getBranch       → GET /api/v1/branches/{branchId}
                    │
                    ▼
            BFA Service Resource (Spring Boot)
                ├── LocationController  → REST endpoints
                ├── LocationService     → Filtering, Haversine distance, city centroid
                └── BranchRepository    → 393 branches from branches.json (Deutsche Bank + Postbank)
```

### Data Model — `Branch` Record

Sourced from `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/location/Branch.java` and `branches.json`:

| Field | Type | Example | Notes |
|-------|------|---------|-------|
| `branchId` | String | `"20173148"` | Unique identifier from Filialfinder API |
| `name` | String | `"Deutsche Bank Alexanderstraße 5, Berlin"` | Descriptive name including city |
| `brand` | String | `"Deutsche Bank"` or `"Postbank"` | Bank brand — only two values in dataset |
| `address` | String | `"Alexanderstraße 5"` | Street + house number |
| `city` | String | `"Berlin"` | City name (German, 112 unique cities) |
| `postalCode` | String | `"10178"` | German PLZ |
| `latitude` | double | `52.52312` | GPS latitude |
| `longitude` | double | `13.41558` | GPS longitude |
| `phone` | String (nullable) | `"+49 30 3407 0"` | Often null for Postbank |
| `openingHours` | String (nullable) | `"Mo 10:00-12:30, 14:00-18:00; Di 10:00-12:30"` | Raw German format; nullable |
| `wheelchairAccessible` | boolean | `true` | Barrier-free access |
| `selfServices` | List\<String\> | `["Bargeldauszahlung", "Kontoauszüge drucken"]` | Self-service terminal offerings |
| `branchServices` | List\<String\> | `["Wertschließfächer", "Fremde Währungen"]` | In-branch counter services (**empty for all 170 Postbank entries**) |
| `transitInfo` | String (nullable) | `"S-Bahn,U-Bahn,Straßenbahn (Tram),Bus"` | Public transport info |
| `parkingInfo` | String (nullable) | `"Parkhaus Q-Park am Alexanderplatz"` | Parking details |

**Response DTO** (`BranchDto`) adds one computed field:

| Field | Type | Notes |
|-------|------|-------|
| `distanceKm` | Double (nullable) | Haversine distance from reference point; null if no geographic context |

### Dataset Statistics

| Metric | Value |
|--------|-------|
| Total branches | **393** |
| Deutsche Bank | 223 (full-service: 221, self-service only: 2) |
| Postbank | 170 (all self-service only — `branchServices: []`) |
| Unique cities | 112 |
| Saturday-open branches | 55 |
| Wheelchair accessible | varies by city (e.g., Berlin: 11 of 26) |
| With Wertschließfächer | 130 |
| With Devisenhandel (currency exchange) | 103 |
| With Bargeldauszahlung (ATM cash withdrawal) | 352 |

### API Capabilities (What's Implemented)

| Capability | Status | API Parameter | Notes |
|------------|--------|---------------|-------|
| Search by city | ✅ Implemented | `city` (partial match) | Case-insensitive; city centroid used for distance sorting |
| Search by address/landmark | ✅ Implemented | `address` (partial match) | Also searches branch names |
| Search by postal code | ✅ Implemented | `postalCode` (prefix match) | Matches start of PLZ |
| Search by GPS coordinates | ✅ Implemented | `latitude` + `longitude` | Haversine distance + radius filter |
| Filter by brand | ✅ Implemented | `brand` | "Deutsche Bank" or "Postbank" |
| Filter by wheelchair access | ✅ Implemented | `accessible=true` | Boolean filter |
| Get single branch details | ✅ Implemented | `GET /branches/{branchId}` | Full details including services |
| Distance sorting | ✅ Implemented | automatic | Uses provided coords, city centroid, postal centroid, or data centroid |
| Radius filtering | ✅ Implemented | `radiusKm` (default: 50) | Only when lat/lon provided |
| Pagination / limit | ✅ Implemented | `limit` (default: 10, max: 50) | — |

### Self-Service Vocabulary (ATM Capabilities)

These are the actual `selfServices` values in the dataset — **this is how ATM capabilities are represented**:

| Self-Service String | English Meaning | Count |
|--------------------|-----------------|-------|
| `Bargeldauszahlung` | Cash withdrawal (ATM) | 352 |
| `Bargeldeinzahlung` | Cash deposit | 340 |
| `Kontoauszüge drucken` | Print account statements | 348 |
| `Überweisungsaufträge erteilen` | Transfer orders | 339 |
| `SEPA-Überweisungen erteilen` | SEPA transfers | 336 |
| `Daueraufträge einrichten/ändern/löschen` | Standing orders | 340 |
| `Depotübersicht anzeigen/drucken` | Securities overview | 330 |
| `Überweisungsaufträge in Papierform einscannen und erteilen` | Scan paper transfers | 338 |

### Branch-Services Vocabulary (Counter Services)

These are the actual `branchServices` values — only available at Deutsche Bank branches (Postbank = always empty):

| Branch-Service String | English Meaning | Count |
|----------------------|-----------------|-------|
| `Wertschließfächer` | Safe deposit boxes | 130 |
| `Fremde Währungen und Edelmetalle kaufen/verkaufen` | Currency exchange / precious metals | 103 |
| `Überweisungsaufträge erteilen` | Transfer orders (at counter) | 163 |
| `Daueraufträge einrichten/ändern/löschen` | Standing orders (at counter) | 163 |
| `Bargeld nur im Selbstbedienungsbereich` | Cash only via self-service | 58 |
| `Beratung durch selbstständige Finanzberater` | Advice from independent advisors | 5 |
| `Für Ihre Serviceanliegen wenden Sie sich bitte an eine Deutsche Bank Filiale.` | "Please visit a Deutsche Bank branch" (Postbank referral) | 0 (in branchServices) |

---

## 3. Use Cases

### UC-BF-01: Find Branch by City Name

| Field | Detail |
|-------|--------|
| **ID** | UC-BF-01 |
| **Title** | Find Branch by City Name |
| **Persona Need** | Brigitte thinks in city names and neighbourhoods, not coordinates. |
| **API Call** | `searchBranches(city="Köln")` |
| **Voice Interaction (DE)** | **Brigitte:** *"Wo ist die nächste Bankfiliale in Köln?"*<br>**Agent:** "Ich habe 18 Filialen in Köln gefunden. Hier sind die nächsten drei: 1. Postbank Breite Str. 6-26, 2. Deutsche Bank An den Dominikanern 11-27, 3. Deutsche Bank Richmodstraße 4-8. Soll ich Ihnen weitere anzeigen?" |
| **Voice Interaction (EN)** | **Brigitte:** *"Where is the nearest bank branch in Cologne?"*<br>**Agent:** "I found 18 branches in Cologne. Here are the nearest three: 1. Postbank Breite Str. 6-26, 2. Deutsche Bank An den Dominikanern 11-27, 3. Deutsche Bank Richmodstraße 4-8. Would you like to see more?" |
| **Success Criteria** | Branches found from city name alone (no coordinates). Results distance-sorted from city centroid. `totalMatches` used for count (not `count`). |
| **Evaluation Ref** | `branch_search_munich`, `german_branch_search` |

---

### UC-BF-02: Postbank Brand Search — "Where Is My Postbank?"

| Field | Detail |
|-------|--------|
| **ID** | UC-BF-02 |
| **Title** | Multi-Brand Search with Postbank |
| **Persona Need** | Brigitte's old Postbank branch may have changed. She wants to find Postbank locations specifically, or understand that Postbank branches are now self-service and that full advisory is at Deutsche Bank. |
| **API Call** | `searchBranches(city="Berlin", brand="Postbank")` |
| **Voice Interaction (DE)** | **Brigitte:** *"Wo ist die nächste Postbank in Berlin?"*<br>**Agent:** "Ich habe 12 Postbank-Standorte in Berlin gefunden. Bitte beachten Sie: Postbank-Filialen bieten Selbstbedienungsservice — Geldautomaten, Kontoauszüge und Überweisungen. Für persönliche Beratung empfehle ich eine Deutsche Bank Filiale. Soll ich Postbank-Standorte anzeigen oder eine Deutsche Bank Filiale mit Beratung suchen?"<br>**Brigitte:** *"Zeigen Sie mir die Postbank-Standorte."*<br>**Agent:** "Die nächsten Postbank-Standorte in Berlin sind: 1. Postbank Rathausstr. 5, 2. Postbank Rankestr. 5-6, 3. Postbank Hardenbergstr. 27. Soll ich Details zu einem davon anzeigen?" |
| **Voice Interaction — Norisbank variant (DE)** | **Caller:** *"Ich bin bei der Norisbank. Wo ist die nächste Filiale?"*<br>**Agent:** "Norisbank ist eine Direktbank der Deutsche Bank Gruppe. Norisbank-Filialen sind leider nicht in unserem Verzeichnis, aber als Norisbank-Kunde können Sie jede Deutsche Bank Filiale für persönliche Anliegen nutzen. In welcher Stadt suchen Sie?" |
| **Voice Interaction — Fyrst variant (DE)** | **Caller:** *"Ich bin Fyrst-Kunde. Gibt es eine Filiale, wo ich hingehen kann?"*<br>**Agent:** "Fyrst ist die digitale Geschäftsbank der Deutsche Bank Gruppe. Als Fyrst-Kunde können Sie für persönliche Anliegen jede Deutsche Bank Filiale aufsuchen. In welcher Stadt sind Sie?" |
| **Success Criteria** | Agent handles `brand="Postbank"` filter. Agent proactively informs that Postbank = self-service only. Agent recognises Norisbank/Fyrst and redirects to Deutsche Bank branches. |
| **Evaluation Ref** | *(new evaluation needed — `postbank_brand_search`)* |

---

### UC-BF-03: Wheelchair Accessibility Filter

| Field | Detail |
|-------|--------|
| **ID** | UC-BF-03 |
| **Title** | Branch Accessibility Inquiry |
| **Persona Need** | Brigitte has mobility issues and needs to know if the branch is wheelchair accessible before she travels. |
| **API Call** | `searchBranches(city="Berlin", accessible=true)` |
| **Voice Interaction (DE)** | **Brigitte:** *"Ist die Filiale in Berlin barrierefrei? Ich komme mit dem Rollator."*<br>**Agent:** "In Berlin gibt es 11 barrierefreie Filialen von insgesamt 26. Die nächste ist die Deutsche Bank Alexanderstraße 5. Dort gibt es auch Parkplätze: Parkhaus Q-Park am Alexanderplatz. Soll ich Ihnen mehr Details geben?"<br>**Brigitte:** *"Ja, wie komme ich mit Bus dahin?"*<br>**Agent:** "Die Filiale ist erreichbar mit S-Bahn, U-Bahn, Straßenbahn und Bus." |
| **Voice Interaction (EN)** | **Brigitte:** *"Is the branch in Berlin wheelchair accessible? I use a walker."*<br>**Agent:** "Berlin has 11 accessible branches out of 26 total. The nearest is Deutsche Bank Alexanderstraße 5. Parking is available at Q-Park am Alexanderplatz. Shall I give you more details?"<br>**Brigitte:** *"Yes, how do I get there by bus?"*<br>**Agent:** "The branch is reachable by S-Bahn, U-Bahn, tram, and bus." |
| **Success Criteria** | `accessible=true` filter applied. `wheelchairAccessible`, `parkingInfo`, and `transitInfo` fields surfaced proactively. |
| **Evaluation Ref** | `german_branch_search` (includes accessibility follow-up) |

---

### UC-BF-04: Branch with Personal Advisory (Counter Services)

| Field | Detail |
|-------|--------|
| **ID** | UC-BF-04 |
| **Title** | Find Branch with Human Advisor (Counter Services) |
| **Persona Need** | Brigitte wants a branch with an actual human advisor, not just a self-service terminal. |
| **API Call** | `searchBranches(city="München", brand="Deutsche Bank")` then agent checks `branchServices` is non-empty |
| **Voice Interaction (DE)** | **Brigitte:** *"Ich brauche eine Filiale, wo ich mit jemandem persönlich sprechen kann. Nicht so ein Automat."*<br>**Agent:** "Ich suche für Sie eine Filiale mit persönlicher Beratung. In welcher Stadt sind Sie?"<br>**Brigitte:** *"In München."*<br>**Agent:** "In München gibt es die Deutsche Bank in der Arabellastraße 11 — dort gibt es persönliche Beratung, Schließfächer, Devisenhandel und weitere Schalterservices. Die Filiale ist barrierefrei. Geöffnet Montag 10:00-12:30, Dienstag bis Donnerstag 9:30-12:30 und 14:00-16:00, Freitag 9:30-12:30. Soll ich Ihnen die Telefonnummer geben?" |
| **Success Criteria** | Agent distinguishes self-service-only branches (Postbank, or Deutsche Bank with `branchServices: ["Bargeld nur im Selbstbedienungsbereich"]`) from full-service branches (non-empty `branchServices` with advisory). Agent filters or explains the difference. |
| **Evaluation Ref** | *(new evaluation needed — `advisory_branch_search`)* |

---

### UC-BF-05: Opening Hours for Today (Natural Time Reference)

| Field | Detail |
|-------|--------|
| **ID** | UC-BF-05 |
| **Title** | "Is the Branch Open Right Now?" |
| **Persona Need** | Brigitte wants to know if she can go to the branch *right now* — not the full weekly schedule. |
| **API Call** | `getBranch(branchId="20173148")` → agent parses `openingHours` string for today |
| **Voice Interaction (DE)** | **Brigitte:** *"Hat die Filiale in der Alexanderstraße in Berlin heute noch auf?"*<br>**Agent:** "Die Deutsche Bank Alexanderstraße 5, Berlin hat folgende Öffnungszeiten: Montag 10:00-12:30 und 14:00-18:00, Dienstag 10:00-12:30 und 14:00-18:00, Mittwoch 10:00-14:00, Donnerstag 10:00-12:30 und 14:00-18:00, Freitag 10:00-14:00. Heute ist Sonntag — die Filiale ist heute leider geschlossen." |
| **Success Criteria** | Agent parses `openingHours` string and resolves "heute" (today) to actual day. LLM interprets the raw German format directly (as designed — see BranchDto schema annotation). |
| **Evaluation Ref** | *(new evaluation needed — `opening_hours_query`)* |

---

### UC-BF-06: Finding a Cash Machine (ATM / Geldautomat)

| Field | Detail |
|-------|--------|
| **ID** | UC-BF-06 |
| **Title** | ATM / Cash Withdrawal Locator |
| **Persona Need** | Brigitte needs cash urgently and wants the nearest location with a cash machine (Geldautomat). |
| **API Call** | `searchBranches(city="Berlin")` → agent filters results by `selfServices` containing `"Bargeldauszahlung"` (352 of 393 branches have this) |
| **Data Reality** | The dataset does **not** contain standalone ATM entries. ATM capability = a branch having `"Bargeldauszahlung"` in its `selfServices` array. Most branches (352/393) offer this. Postbank self-service-only locations are the closest to "ATM-only" in the dataset. |
| **Voice Interaction (DE)** | **Brigitte:** *"Ich brauche dringend einen Geldautomaten. Gibt es einen in meiner Nähe?"*<br>**Agent:** "Können Sie mir Ihre Stadt oder Ihren Stadtteil nennen?"<br>**Brigitte:** *"Ich bin am Alexanderplatz in Berlin."*<br>**Agent:** "In der Nähe gibt es die Deutsche Bank in der Alexanderstraße 5 — dort gibt es Geldautomaten für Bargeldauszahlung und Bargeldeinzahlung. Die Filiale ist barrierefrei und erreichbar mit S-Bahn, U-Bahn, Straßenbahn und Bus. Soll ich Ihnen die Öffnungszeiten sagen?" |
| **Voice Interaction (EN)** | **Brigitte:** *"I urgently need an ATM. Is there one nearby?"*<br>**Agent:** "Can you tell me your city or neighbourhood?"<br>**Brigitte:** *"I'm at Berlin Alexanderplatz."*<br>**Agent:** "Nearby there is a Deutsche Bank at Alexanderstraße 5 — it has ATMs for cash withdrawal and deposit. The branch is wheelchair accessible and reachable by S-Bahn, U-Bahn, tram, and bus." |
| **Success Criteria** | Agent searches by city + address and identifies branches with `Bargeldauszahlung` in `selfServices`. Agent does NOT fabricate standalone ATM locations that don't exist in the dataset. Agent mentions accessibility and transit proactively. |
| **Evaluation Ref** | *(new evaluation needed — `atm_cash_machine_search`)* |

---

### UC-BF-07: Frustration Escalation — "I Want to Talk to a Person"

| Field | Detail |
|-------|--------|
| **ID** | UC-BF-07 |
| **Title** | Human Handover on Frustration |
| **Persona Need** | Brigitte becomes frustrated with the voice system and wants a human agent. |
| **API Call** | `end_session(session_escalated=true, reason="customer_requested_live_agent")` |
| **Voice Interaction (DE)** | **Brigitte:** *"Ich verstehe das alles nicht. Können Sie mich nicht einfach mit jemandem verbinden?"*<br>**Agent:** "Natürlich, das verstehe ich. Ich verbinde Sie jetzt mit einem Mitarbeiter, der Ihnen persönlich weiterhelfen kann. Einen Moment bitte." |
| **Voice Interaction (EN)** | **Brigitte:** *"I don't understand any of this. Can't you just connect me to someone?"*<br>**Agent:** "Of course, I understand. I'm connecting you now with a representative who can help you personally. One moment please." |
| **Success Criteria** | Frustration detected. `end_session` tool called with `session_escalated=true`. Verbal farewell given before tool call. |
| **Evaluation Ref** | `session_end_live_agent` |

---

### UC-BF-08: Saturday Opening Hours

| Field | Detail |
|-------|--------|
| **ID** | UC-BF-08 |
| **Title** | Find Branch Open on Saturday |
| **Persona Need** | Brigitte works part-time and can only visit on Saturdays. |
| **API Call** | `searchBranches(city="Berlin")` → agent filters results where `openingHours` contains `"Sa"` |
| **Data Reality** | 55 branches in the dataset have Saturday hours (identified by `"Sa"` in `openingHours`). The LLM parses the raw string directly. |
| **Voice Interaction (DE)** | **Brigitte:** *"Gibt es eine Filiale in Berlin, die auch samstags geöffnet hat?"*<br>**Agent:** "Ja! In Berlin gibt es samstags geöffnete Filialen. Die Deutsche Bank Friedrichstraße 181 ist samstags von 10:00 bis 18:00 geöffnet. Auch die Postbank Rathausstr. 5 hat samstags von 9:00 bis 14:00 geöffnet. Soll ich Ihnen mehr Details geben?" |
| **Success Criteria** | Agent identifies Saturday-open branches from `openingHours` field. Only presents branches where `openingHours` actually contains Saturday hours. |
| **Evaluation Ref** | *(new evaluation needed — `saturday_hours_search`)* |

---

### UC-BF-09: Repeat and Slow Down

| Field | Detail |
|-------|--------|
| **ID** | UC-BF-09 |
| **Title** | Repeat Response Slowly |
| **Persona Need** | Brigitte couldn't catch the address or phone number and needs it repeated more slowly. |
| **Voice Interaction (DE)** | **Brigitte:** *"Das war zu schnell. Können Sie die Telefonnummer nochmal langsam sagen?"*<br>**Agent:** "Natürlich! Die Telefonnummer ist... null... drei... null... drei... vier... null... sieben... null. Soll ich sie nochmal wiederholen?" |
| **Voice Interaction (EN)** | **Brigitte:** *"That was too fast. Can you say the phone number again slowly?"*<br>**Agent:** "Of course! The phone number is... zero... three... zero... three... four... zero... seven... zero. Shall I repeat it again?" |
| **Success Criteria** | Agent recognises "zu schnell" / "nochmal" / "langsam" as repeat-and-slow-down requests. Phone numbers spoken digit-by-digit. |
| **Evaluation Ref** | *(conversation design — embedded in agent instruction)* |

---

### UC-BF-10: Multi-Turn Refinement — "Not That One, a Closer One"

| Field | Detail |
|-------|--------|
| **ID** | UC-BF-10 |
| **Title** | Refine Results Without Restarting |
| **Persona Need** | Brigitte gets a suggestion but wants alternatives. She shouldn't have to start over. |
| **Voice Interaction (DE)** | **Brigitte:** *"Die ist mir zu weit weg. Gibt es nicht eine näher dran?"*<br>**Agent:** "Die nächstgelegene Alternative ist... [presents next result from the search]. Passt das besser?" |
| **Success Criteria** | Conversational context retained — agent remembers the city/area from the previous turn. Next-closest result served without re-asking for location. Agent can call `searchBranches` with `limit=20` for more results. |
| **Evaluation Ref** | *(new evaluation needed — `multi_turn_refinement`)* |

---

### UC-BF-11: Branch Services Query — "Can I Do X There?"

| Field | Detail |
|-------|--------|
| **ID** | UC-BF-11 |
| **Title** | Find Branch by Available Service |
| **Persona Need** | Brigitte wants to know *before* she travels whether a specific branch offers the service she needs. |
| **API Call** | `getBranch(branchId=...)` → agent checks `selfServices` and `branchServices` arrays |
| **Data Reality** | Services are **free-text German strings** in two arrays: `selfServices` (machine services) and `branchServices` (counter services). There is no enum/taxonomy — the LLM must match user intent to the actual string values. |
| **Voice Interaction — Cash Deposit (DE)** | **Brigitte:** *"Ich muss Bargeld einzahlen. Kann ich das in der Filiale in der Alexanderstraße?"*<br>**Agent:** "Ja, die Deutsche Bank Alexanderstraße 5 in Berlin bietet Bargeldeinzahlung als Selbstbedienungsservice an. Die Filiale hat auch Bargeldauszahlung, Kontoauszüge drucken und weitere Automatenservices. Geöffnet Montag 10:00-12:30 und 14:00-18:00." |
| **Voice Interaction — Safe Deposit Box (DE)** | **Brigitte:** *"Ich brauche ein Schließfach. Welche Filiale in München hat Schließfächer?"*<br>**Agent:** "Die Deutsche Bank in der Arabellastraße 11 in München bietet Wertschließfächer an. Soll ich Ihnen die Telefonnummer geben: +49 89 923335 0?" |
| **Voice Interaction — Foreign Exchange (DE)** | **Brigitte:** *"Ich fliege nächste Woche nach London. Kann ich irgendwo Pfund bekommen?"*<br>**Agent:** "Die Deutsche Bank Arabellastraße 11 in München bietet 'Fremde Währungen und Edelmetalle kaufen/verkaufen' an. Insgesamt 103 Filialen deutschlandweit bieten Devisenhandel. Soll ich eine in Ihrer Nähe suchen?" |
| **Voice Interaction — Service Not Available (DE)** | **Brigitte:** *"Kann ich in der Postbank am Rathausplatz Schließfächer mieten?"*<br>**Agent:** "Postbank-Standorte bieten leider nur Selbstbedienungsservice an — Geldautomaten, Kontoauszüge und Überweisungen. Für Wertschließfächer empfehle ich eine Deutsche Bank Filiale. Soll ich die nächste mit Schließfächern in Ihrer Stadt suchen?" |
| **Success Criteria** | Agent queries `selfServices` and `branchServices` arrays. Handles Postbank limitation gracefully (no counter services). Suggests alternatives when service is unavailable at a location. |
| **Evaluation Ref** | *(new evaluation needed — `service_specific_search`)* |

#### Actual Service Strings Reference

> The LLM must match user natural language to these exact strings in the dataset:

| User Intent (DE) | User Intent (EN) | Matches `selfServices` | Matches `branchServices` |
|-------------------|-------------------|------------------------|--------------------------|
| "Geld abheben", "Geldautomat", "ATM" | "Withdraw cash", "ATM" | `Bargeldauszahlung` | — |
| "Geld einzahlen", "Bargeld einzahlen" | "Deposit cash" | `Bargeldeinzahlung` | — |
| "Kontoauszug", "Kontoauszüge" | "Bank statement", "print statement" | `Kontoauszüge drucken` | — |
| "Überweisung", "Geld überweisen" | "Transfer money", "wire transfer" | `Überweisungsaufträge erteilen` | `Überweisungsaufträge erteilen` |
| "Dauerauftrag" | "Standing order" | `Daueraufträge einrichten/ändern/löschen` | `Daueraufträge einrichten/ändern/löschen` |
| "Schließfach", "Safe" | "Safe deposit box", "locker" | — | `Wertschließfächer` |
| "Geld wechseln", "Fremdwährung", "Dollar/Pfund kaufen" | "Exchange money", "foreign currency" | — | `Fremde Währungen und Edelmetalle kaufen/verkaufen` |
| "Depot", "Wertpapiere" | "Securities", "portfolio" | `Depotübersicht anzeigen/drucken` | — |

---

### UC-BF-12: Parking and Transit Info

| Field | Detail |
|-------|--------|
| **ID** | UC-BF-12 |
| **Title** | How Do I Get There? (Parking & Transit) |
| **Persona Need** | Brigitte's son drives her, so she needs parking info. Or she takes public transport and needs transit directions. |
| **API Call** | `getBranch(branchId=...)` → return `parkingInfo` and `transitInfo` |
| **Voice Interaction (DE)** | **Brigitte:** *"Gibt es Parkplätze bei der Filiale am Alexanderplatz?"*<br>**Agent:** "Ja, bei der Deutschen Bank Alexanderstraße 5 in Berlin gibt es das Parkhaus Q-Park am Alexanderplatz — kostenpflichtig. Soll ich Ihnen auch die Öffnungszeiten sagen?" |
| **Voice Interaction (EN)** | **Brigitte:** *"How do I get to the branch at Alexanderplatz by public transport?"*<br>**Agent:** "The Deutsche Bank at Alexanderstraße 5 is reachable by S-Bahn, U-Bahn, tram, and bus. Would you like the opening hours?" |
| **Success Criteria** | `parkingInfo` and `transitInfo` surfaced from branch data. Agent does not fabricate transit details — uses only what's in the data (nullable fields handled gracefully). |
| **Evaluation Ref** | *(new evaluation needed — `parking_transit_query`)* |

---

### UC-BF-13: Edge Case — No Results Found

| Field | Detail |
|-------|--------|
| **ID** | UC-BF-13 |
| **Title** | No Branches Found in Location |
| **Persona Need** | Brigitte asks for a branch in a small village or misspelled city. |
| **API Call** | `searchBranches(city="Kleinkleckersdorf")` → `totalMatches: 0` |
| **Voice Interaction (DE)** | **Brigitte:** *"Gibt es eine Filiale in Hinterwald?"*<br>**Agent:** "Leider habe ich keine Filialen in Hinterwald gefunden. Soll ich in einer nahegelegenen Stadt suchen, oder Sie mit einem Mitarbeiter verbinden?" |
| **Critical Rule** | Agent MUST NOT claim the city "doesn't exist" or "is not real". The tool only reports whether branches exist there — it says nothing about geography. |
| **Success Criteria** | Agent states no branches found. Does NOT judge city validity. Suggests alternatives (nearby city or live agent). |
| **Evaluation Ref** | `edge_case_no_results` |

---

## 4. Dependencies

### 4.1 Data Coverage Analysis

| Data Field | Available | Coverage | Notes |
|------------|-----------|----------|-------|
| `branchId` | ✅ | 393/393 | Real Filialfinder IDs |
| `name` | ✅ | 393/393 | Descriptive, includes city |
| `brand` | ✅ | 393/393 | "Deutsche Bank" (223) or "Postbank" (170) |
| `address` | ✅ | 393/393 | Street + house number |
| `city` | ✅ | 393/393 | 112 unique cities |
| `postalCode` | ✅ | 393/393 | German PLZ |
| `latitude` / `longitude` | ✅ | 393/393 | GPS coordinates |
| `phone` | ⚠️ Partial | ~223/393 | Often null for Postbank |
| `openingHours` | ⚠️ Partial | ~280/393 | Nullable; some branches unknown |
| `wheelchairAccessible` | ✅ | 393/393 | Boolean |
| `selfServices` | ✅ | ~350/393 | Can be empty array |
| `branchServices` | ✅ | 221/393 | **All 170 Postbank entries = empty** |
| `transitInfo` | ⚠️ Partial | ~180/393 | Nullable |
| `parkingInfo` | ⚠️ Partial | ~160/393 | Nullable |

### 4.2 What's NOT in the Data

| Missing Capability | Impact | Workaround |
|-------------------|--------|------------|
| **Standalone ATM entries** | No ATM-only locations; ATM = branch with `Bargeldauszahlung` in `selfServices` | Agent searches branches, filters by selfServices |
| **Structured opening hours** | Hours are raw strings; LLM must parse `"Mo 10:00-12:30; Di..."` | LLM parses directly (designed intentionally — see BranchDto schema) |
| **Saturday filter parameter** | No `openOnSaturday` API param | LLM searches by city, then parses `openingHours` for `"Sa"` |
| **Service-specific filter parameter** | No `service=Wertschließfächer` API param | LLM searches by city, calls `getBranch`, checks `selfServices`/`branchServices` |
| **Norisbank / Fyrst branches** | Not in dataset | Agent provides verbal guidance; redirects to Deutsche Bank |
| **Language spoken** | Not in data | Not available — potential enhancement |
| **Appointment booking** | Not in data | Agent provides phone number instead |
| **Real-time closure info** | Not in data | Static data only |

### 4.3 Agent Dependencies

| Agent | Coordination Needed | Use Case |
|-------|-------------------|----------|
| **voice_banking_agent** | Transfers TO location_services_agent for branch queries; receives transfer BACK for non-location topics | UC-BF-07 (escalation), all entry/exit |
| **end_session tool** | Called with `session_escalated=true/false` and `reason` string | UC-BF-07, goodbye flows |

### 4.4 Compliance Dependencies

| Requirement | Regulation | Impact |
|-------------|-----------|--------|
| **No location tracking without consent** | GDPR Art. 6, 7 | Agent must not request GPS; use city/address input only |
| **Accessible service obligation** | BGG (Behindertengleichstellungsgesetz) | Must proactively disclose accessibility info |
| **Financial information accuracy** | BaFin MaRisk | Branch hours and service info must be kept current |
| **Senior-appropriate UX** | EU Accessibility Act 2025 | Longer timeouts, simple language, repeat capability |

---

## 5. Intent Impact Analysis

### 5.1 Intents Supported by Current Implementation

| Intent | Trigger Phrases (DE) | Trigger Phrases (EN) | API Call | Status |
|--------|----------------------|----------------------|----------|--------|
| `branch_find_by_city` | "Filiale in Köln", "Bank in München" | "Branch in Cologne", "Bank in Munich" | `searchBranches(city=...)` | ✅ Implemented |
| `branch_find_by_address` | "Filiale in der Alexanderstraße", "Bank am Alexanderplatz" | "Branch on Alexanderstraße", "Bank at Alexanderplatz" | `searchBranches(city=..., address=...)` | ✅ Implemented |
| `branch_find_by_plz` | "Filiale in 50667", "PLZ 10178" | "Branch in 50667", "Postal code 10178" | `searchBranches(postalCode=...)` | ✅ Implemented |
| `branch_filter_brand` | "Postbank in Berlin", "Deutsche Bank in Hamburg" | "Postbank in Berlin", "Deutsche Bank in Hamburg" | `searchBranches(brand=...)` | ✅ Implemented |
| `branch_accessibility` | "Barrierefrei", "Rollstuhl", "Rollator" | "Wheelchair accessible", "Accessible" | `searchBranches(accessible=true)` | ✅ Implemented |
| `branch_details` | "Öffnungszeiten der Filiale", "Telefonnummer" | "Opening hours", "Phone number" | `getBranch(branchId=...)` | ✅ Implemented |
| `atm_finder` | "Geldautomat", "Geld abheben", "ATM" | "ATM", "Cash machine", "Withdraw cash" | `searchBranches(city=...)` → filter `selfServices` | ✅ Via LLM |
| `branch_services` | "Schließfach", "Geld wechseln" | "Safe deposit box", "Exchange money" | `getBranch(branchId=...)` → check services arrays | ✅ Via LLM |
| `saturday_hours` | "Samstags geöffnet?", "Am Wochenende" | "Open on Saturday?", "Weekend" | `searchBranches(city=...)` → LLM parses hours | ✅ Via LLM |
| `repeat_slower` | "Nochmal", "Zu schnell", "Langsam" | "Again", "Too fast", "Slower" | (conversational) | ✅ In instruction |
| `frustration_escalation` | "Mitarbeiter", "Person", "Verstehe nicht" | "Human", "Person", "Don't understand" | `end_session(escalated=true)` | ✅ Implemented |
| `subsidiary_brand` | "Norisbank", "Fyrst", "Meine Postbank" | "Norisbank", "Fyrst", "My Postbank" | (verbal guidance + brand filter) | ✅ In instruction |

### 5.2 Entity Extraction Requirements

| Entity Type | Examples (DE) | Examples (EN) | Extraction Challenge |
|-------------|--------------|---------------|---------------------|
| `city` | "Köln", "Hamburg", "München" | "Cologne", "Hamburg", "Munich" | **German name required for API** (Munich→München, Cologne→Köln) |
| `address` / `landmark` | "Alexanderplatz", "Hauptstraße" | *(Same)* | Use short root for prefix match ("Alexander", not "Alexanderplatz") |
| `brand` | "Postbank", "Deutsche Bank" | *(Same)* | Norisbank/Fyrst = not in data → verbal redirect |
| `service` | "Schließfach", "Geld wechseln", "Bargeld einzahlen" | "Safe deposit", "Exchange", "Deposit cash" | Map to actual service strings in `selfServices`/`branchServices` |
| `accessibility` | "Rollstuhl", "barrierefrei", "Rollator" | "Wheelchair", "Accessible", "Walker" | Maps to `accessible=true` API parameter |

### 5.3 Critical Agent Instruction: City Name Translation

The `location_services_agent` instruction requires the LLM to **translate English city names to German for API calls**:

| User Says | API Call Must Use | Respond With |
|-----------|-------------------|-------------|
| "Munich" | `city="München"` | "Munich" (in English) |
| "Cologne" | `city="Köln"` | "Cologne" (in English) |
| "Nuremberg" | `city="Nürnberg"` | "Nuremberg" (in English) |
| "Hanover" | `city="Hannover"` | "Hanover" (in English) |

---

## 6. Conversational Agent Design Foundation

### 6.1 CES Agent Structure

The agent is deployed as a Google CX Agent Studio sub-agent:

```
voice_banking_agent (root)
   │
   └── location_services_agent (sub-agent)
           ├── Tool: searchBranches (OpenAPI → BFA Service)
           ├── Tool: getBranch (OpenAPI → BFA Service)
           └── Tool: end_session (built-in)
```

**Instruction File:** `ces-agent/acme_voice_agent/agents/location_services_agent/instruction.txt` (342 lines)

### 6.2 Flow Structure

```
┌─────────────────────────────────────┐
│  voice_banking_agent (Entry)         │
│  (Greets user, detects intent)       │
└──────────────┬──────────────────────┘
               │ Transfer on branch/location intent
        ┌──────▼──────────────────┐
        │ location_services_agent  │
        │ (No re-greeting)         │
        └──────┬──────────────────┘
               │
  ┌────────────▼────────────────┐
  │ Collect Location            │◄──── Loop back for refinement
  │ (City / Address / PLZ /     │      ("Not that one, closer")
  │  Landmark → German name)    │
  └────────────┬────────────────┘
               │
  ┌────────────▼────────────────┐
  │ searchBranches API Call      │
  │ (city, address, postalCode,  │
  │  brand, accessible, limit)   │
  └────────────┬────────────────┘
               │
  ┌────────────▼────────────────┐
  │ Present Results              │
  │ (top 3 + totalMatches count) │
  │ Offer: "Show more?"          │
  └────────────┬────────────────┘
               │
  ┌────────────▼────────────────┐    ┌──────────────────────┐
  │ Follow-up Handling           │───►│ getBranch (details)   │
  │                              │───►│ Repeat / Slow Down    │
  │                              │───►│ Show more (limit=20)  │
  │                              │───►│ Transfer back to root │
  │                              │───►│ end_session (goodbye) │
  └──────────────────────────────┘    └──────────────────────┘
```

### 6.3 Result Formatting Rules (from agent instruction)

1. Present **top 3 results** with name, address, and distance
2. State total using `totalMatches` (NOT `count` — `count` is page size)
3. Format: "I found X branches in [city]. Here are the nearest three:"
4. If `totalMatches > 3`, offer: "Would you like to see more?"
5. **Always respond in the user's language** (German user → German, English → English)
6. **German city names for API**: Munich→München, Cologne→Köln in the tool call, but respond in user's language

### 6.4 Error Handling & Clarification Strategies

| Error Type | Strategy (DE) | Strategy (EN) |
|------------|---------------|---------------|
| No results (`totalMatches=0`) | "Leider habe ich keine Filialen in [Ort] gefunden. Soll ich in einer nahegelegenen Stadt suchen?" | "I couldn't find any branches in [location]. Would you like me to search a nearby city?" |
| Invalid coordinates (400) | "Es gab ein Problem mit den Suchparametern. Bitte nennen Sie eine Stadt oder Postleitzahl." | "There was an issue with the search. Please provide a city or postal code." |
| Branch not found (404) | "Diese Filiale konnte leider nicht gefunden werden. Möchten Sie nach einer anderen suchen?" | "I couldn't find that branch. Would you like to search for a different one?" |
| Service unavailable (5xx) | Retry once → "Der Filialservice ist momentan nicht erreichbar. Soll ich Sie mit einem Mitarbeiter verbinden?" | Retry once → "The branch service is temporarily unavailable. Shall I connect you with a representative?" |
| Out of scope (non-location) | Transfer back to `voice_banking_agent` | Transfer back to `voice_banking_agent` |

---

## 7. Accessibility & Inclusion Recommendations

### 7.1 Voice Prompt Phrasing Guidelines

| Principle | ❌ Bad Example | ✅ Good Example |
|-----------|--------------|----------------|
| **Short sentences** | "Die nächstgelegene Filiale mit persönlicher Beratung und barrierefreiem Zugang in der von Ihnen genannten Stadt befindet sich in der Alexanderstraße." | "In Berlin gibt es eine barrierefreie Filiale. In der Alexanderstraße 5. Mit persönlicher Beratung." |
| **Concrete counts** | "Mehrere Ergebnisse gefunden." | "Ich habe 18 Filialen in Köln gefunden." |
| **Active voice** | "Die Filiale wird um 18 Uhr geschlossen." | "Die Filiale schließt um 18 Uhr." |
| **Offered repetition** | *(just moves on)* | "Soll ich das nochmal wiederholen?" |
| **Numbers dictated** | "069-910-00" | "null… sechs… neun… Bindestrich… neun… eins… null… null… null." |

### 7.2 Patience Mechanisms

| Mechanism | Implementation |
|-----------|---------------|
| **Extended no-input timeout** | 8 seconds (1st), 12 seconds (2nd) |
| **Repetition tolerance** | Never penalise asking "nochmal" — repeat up to 5 times |
| **Slow TTS option** | Detect "langsam" → reduce speech rate by 25% |
| **Persistent session** | 5+ minute timeout (vs. typical 2 min) |

### 7.3 Reassurance Strategies

| Trigger | Response (DE) | Response (EN) |
|---------|---------------|---------------|
| First interaction | "Ich helfe Ihnen gerne, eine Filiale zu finden. Sagen Sie mir einfach die Stadt." | "I'm happy to help you find a branch. Just tell me the city." |
| Postbank brand mention | "Ich verstehe, dass die Veränderungen nicht einfach sind. Ich helfe Ihnen gerne." | "I understand the changes haven't been easy. I'm happy to help." |
| Norisbank mention | "Als Norisbank-Kunde können Sie alle Deutsche Bank Filialen nutzen." | "As a Norisbank customer, you can use any Deutsche Bank branch." |
| Fyrst mention | "Als Fyrst-Kunde steht Ihnen das gesamte Deutsche Bank Filialnetz zur Verfügung." | "As a Fyrst customer, the entire Deutsche Bank branch network is available to you." |
| Before handover | "Sie werden jetzt mit einem Mitarbeiter verbunden. Ich habe ihm schon gesagt, worum es geht." | "You're being connected to a representative. I've already told them what this is about." |

---

## 8. Remaining Implementation Gaps

These are gaps that still exist relative to the use cases defined above:

| # | Gap | Impact | Priority | Blocking Use Cases |
|---|-----|--------|----------|--------------------|
| 1 | **No service-specific filter parameter** in the API (e.g., `service=Wertschließfächer`) | LLM must fetch all results and check arrays manually; works but less efficient | 🟡 P2 | UC-BF-11 (workaround: LLM parses) |
| 2 | **No Saturday/day-specific filter parameter** | LLM must parse `openingHours` string manually; works but less efficient | 🟡 P2 | UC-BF-08 (workaround: LLM parses) |
| 3 | **No standalone ATM entries** in dataset | ATM = branch with `Bargeldauszahlung` in `selfServices`; no 24/7 pure ATM locations | 🟡 P2 | UC-BF-06 (workaround: explain that branches have ATMs) |
| 4 | **Opening hours nullable** for ~113 branches | Agent must handle "hours unknown" gracefully | 🟡 P2 | UC-BF-05, UC-BF-08 |
| 5 | **Phone number null** for most Postbank entries | Cannot provide phone for Postbank self-service locations | 🟡 P3 | UC-BF-09 |
| 6 | **No sentiment/frustration detection** in CES | Relies on keyword matching ("Mitarbeiter", "verstehe nicht") | 🟡 P3 | UC-BF-07 |
| 7 | **No appointment booking** | Agent can only provide phone number, not schedule appointments | 🟡 P3 | UC-BF-04, UC-BF-11 |
| 8 | **Norisbank / Fyrst not in dataset** | Verbal guidance only; cannot search for their branches | 🟢 P4 | UC-BF-02 (by design) |

---

## 9. Evaluation Test Matrix

### Existing Evaluations

| Evaluation | File | Tests Use Case | Status |
|------------|------|---------------|--------|
| `branch_search_munich` | `evaluations/branch_search_munich/` | UC-BF-01 (city search, Munich→München translation, result formatting) | ✅ Active |
| `german_branch_search` | `evaluations/german_branch_search/` | UC-BF-01 + UC-BF-03 (German language, Berlin search, accessibility follow-up) | ✅ Active |
| `edge_case_no_results` | `evaluations/edge_case_no_results/` | UC-BF-13 (no results, don't fabricate, don't judge city validity) | ✅ Active |
| `agent_handover_roundtrip` | `evaluations/agent_handover_roundtrip/` | UC-BF-07 + agent transfer flow (entry → location → back to root) | ✅ Active |
| `session_end_live_agent` | `evaluations/session_end_live_agent/` | UC-BF-07 (live agent escalation, end_session tool) | ✅ Active |
| `off_topic_redirect` | `evaluations/off_topic_redirect/` | Scope limitation (non-location queries redirected) | ✅ Active |
| `prompt_injection_attempt` | `evaluations/prompt_injection_attempt/` | Security (no system prompt leakage, no persona change) | ✅ Active |

### Recommended New Evaluations

| Evaluation Name | Tests Use Case | Scenario Description |
|-----------------|---------------|---------------------|
| `postbank_brand_search` | UC-BF-02 | Search for Postbank in Berlin. Verify: (1) agent uses `brand="Postbank"` parameter, (2) agent explains Postbank = self-service only, (3) agent offers Deutsche Bank alternative for advisory. |
| `norisbank_fyrst_redirect` | UC-BF-02 | Ask about Norisbank/Fyrst branches. Verify: (1) agent explains these are not in the directory, (2) agent redirects to Deutsche Bank, (3) agent does not fabricate Norisbank branch data. |
| `advisory_branch_search` | UC-BF-04 | Ask for a branch with personal advisory in München. Verify: (1) agent presents Deutsche Bank branches with non-empty `branchServices`, (2) agent does NOT present Postbank (self-service only), (3) agent mentions specific services available. |
| `opening_hours_query` | UC-BF-05 | Ask about opening hours for a specific branch. Verify: (1) agent calls `getBranch`, (2) agent presents hours in readable format, (3) agent correctly interprets day-specific questions ("open on Saturday?"). |
| `atm_cash_machine_search` | UC-BF-06 | Ask for a Geldautomat/ATM in Berlin. Verify: (1) agent searches branches, (2) agent identifies branches with `Bargeldauszahlung` in `selfServices`, (3) agent does NOT fabricate standalone ATM locations, (4) agent mentions accessibility and transit if available. |
| `saturday_hours_search` | UC-BF-08 | Ask for a Saturday-open branch in Berlin. Verify: (1) agent searches by city, (2) agent identifies branches where `openingHours` includes "Sa", (3) agent presents correct Saturday times, (4) agent does NOT claim branches are open on Saturday if hours don't include "Sa". |
| `service_specific_search` | UC-BF-11 | Ask for Wertschließfächer (safe deposit box) in München. Verify: (1) agent searches München branches, (2) agent identifies branch with "Wertschließfächer" in `branchServices`, (3) agent tells user that Postbank branches don't have counter services. |
| `parking_transit_query` | UC-BF-12 | Ask about parking and transit for a Berlin branch. Verify: (1) agent calls `getBranch`, (2) agent presents `parkingInfo` and `transitInfo`, (3) agent handles null values gracefully ("no parking info available"). |
| `multi_turn_refinement` | UC-BF-10 | Search München, reject first result, ask for closer alternative. Verify: (1) context retained from first search, (2) next result presented without re-asking for city, (3) agent calls `searchBranches` with higher limit. |

### Use Case → Evaluation Traceability

| Use Case | Covered by Evaluation(s) | Coverage |
|----------|--------------------------|----------|
| UC-BF-01: City search | `branch_search_munich`, `german_branch_search` | ✅ Full |
| UC-BF-02: Multi-brand | `postbank_brand_search`*, `norisbank_fyrst_redirect`* | 🟡 Proposed |
| UC-BF-03: Accessibility | `german_branch_search` | ✅ Partial (follow-up only) |
| UC-BF-04: Advisory branch | `advisory_branch_search`* | 🟡 Proposed |
| UC-BF-05: Opening hours | `opening_hours_query`* | 🟡 Proposed |
| UC-BF-06: ATM / cash machine | `atm_cash_machine_search`* | 🟡 Proposed |
| UC-BF-07: Frustration → human | `session_end_live_agent`, `agent_handover_roundtrip` | ✅ Full |
| UC-BF-08: Saturday hours | `saturday_hours_search`* | 🟡 Proposed |
| UC-BF-09: Repeat / slow down | *(conversational — hard to evaluate automatically)* | 🔴 Not automated |
| UC-BF-10: Multi-turn refine | `multi_turn_refinement`* | 🟡 Proposed |
| UC-BF-11: Service query | `service_specific_search`* | 🟡 Proposed |
| UC-BF-12: Parking / transit | `parking_transit_query`* | 🟡 Proposed |
| UC-BF-13: No results | `edge_case_no_results` | ✅ Full |

\* = Proposed new evaluation

---

## Appendix A: Changelog

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-07 |  | Initial version |
| 2.0 | 2026-02-09 |  | **Major revision:** Corrected data model to match actual `Branch.java` (added `brand`, `selfServices`, `branchServices`, `transitInfo`, `parkingInfo`; removed non-existent `type` field). Updated architecture to reflect BFA Service Resource (393 branches from JSON, not 11 seed locations). Corrected API capabilities — city/address/postal-code search IS implemented. Reworked ATM use case (UC-BF-06) to reflect that no standalone ATM entries exist; ATM = branch with `Bargeldauszahlung` in `selfServices`. Added UC-BF-12 (parking/transit). Added UC-BF-13 (no results edge case). Added Section 9 (Evaluation Test Matrix) mapping use cases to existing + proposed evaluations. Removed outdated Appendix B (enhancement proposals for fields that already exist). Updated all use case voice examples to use actual branch data from `branches.json`. Fixed agent source path. |

---

*Document Control: Created 2026-02-07, Revised 2026-02-09 by  | Review Required: Product Owner, UX Lead*
