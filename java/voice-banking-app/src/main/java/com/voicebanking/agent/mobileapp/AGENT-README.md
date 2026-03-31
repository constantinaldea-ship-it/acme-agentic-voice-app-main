# MobileAppAssistanceAgent

> **Agent ID:** `mobile-app-assistance`  
> **Category:** Category 1 — Mobile App AI Assistance  
> **Status:** ✅ Implemented  
> **Implements:** [AGENT-005 Implementation Plan](../../../../../docs/implementation-plan/AGENT-005-mobile-app-assistance.md)

---

## Overview

The MobileAppAssistanceAgent provides step-by-step guidance for using the Acme Bank Mobile Banking app. It handles "How do I...?" queries for app navigation, feature discovery, troubleshooting, and settings configuration.

**Key Capabilities:**
- Step-by-step feature guides with voice-friendly formatting
- Feature search and discovery
- Navigation paths showing how to reach features
- Deep link generation for direct app navigation
- Troubleshooting for common app issues
- Platform-specific guidance (iOS/Android)

---

## Problem Statement

### Current State
Users frequently contact the call center with "How do I...?" questions about the mobile app:
- "How do I set up Touch ID?"
- "Where do I find my IBAN?"
- "How do I send money to someone?"
- "The app keeps crashing, what should I do?"

### Solution
This agent provides self-service guidance that:
1. Understands what feature the user needs help with
2. Provides clear, numbered steps optimized for voice
3. Handles platform differences (iOS vs Android)
4. Troubleshoots common issues
5. Offers escalation when issues can't be resolved

---

## Tools

| Tool | Purpose | Input | Output |
|------|---------|-------|--------|
| `getFeatureGuide` | Step-by-step guide for a feature | `featureId`, `platform` | StepGuide with voice response |
| `findFeature` | Search features by query | `query`, `platform`, `limit` | List of matching features |
| `getNavigationPath` | Path to reach a feature | `featureId`, `platform` | NavigationPath with menu hierarchy |
| `getDeepLink` | Deep link for direct navigation | `featureId` | Deep link URL |
| `listFeatures` | List features by category | `category`, `platform` | List of features |
| `getTroubleshootingGuide` | Troubleshoot an issue | `issueType`, `description` | TroubleshootingSolution |

---

## Architecture

### Architecture Pattern
```
User Query: "How do I set up Touch ID?"
         │
         ▼
MobileAppAssistanceAgent
    │
    ├── AppFeatureCatalog.getFeature()
    │       └── Find feature by ID or search
    │
    ├── FeatureGuideService.getGuide()
    │       └── Get platform-specific step guide
    │
    ├── NavigationService.getNavigationPath()
    │       └── Get menu path to feature
    │
    └── TroubleshootingService.getTroubleshootingSolution()
            └── Get solution for issues
```

### Key Design Decisions

1. **Voice-Optimized Guides:** Maximum 5 steps per guide, clear action verbs, pacing-friendly
2. **Platform-Specific Content:** iOS and Android guides stored separately with fallback to generic
3. **Feature Catalog:** 40+ features covering Security, Transfers, Accounts, Cards, Settings, Support
4. **Keyword Search:** Features matched by name, description, and tagged keywords
5. **Deep Links:** Direct navigation URLs for supported features (app version 5.0+)

---

## Package Structure

```
mobileapp/
├── MobileAppAssistanceAgent.java       # Main agent (6 tools)
├── AGENT-README.md                      # This file
├── CHANGELOG.md                         # Change history
├── catalog/
│   ├── AppFeatureCatalog.java           # Catalog interface
│   └── StaticAppFeatureCatalog.java     # Static implementation
├── domain/
│   ├── AppFeature.java                  # Feature model
│   ├── FeatureCategory.java             # Category enum
│   ├── IssueType.java                   # Issue type enum
│   ├── NavigationPath.java              # Navigation path model
│   ├── Platform.java                    # Platform enum
│   ├── Step.java                        # Individual step
│   ├── StepGuide.java                   # Complete guide
│   └── TroubleshootingSolution.java     # Troubleshooting solution
└── service/
    ├── FeatureGuideService.java         # Guide generation
    ├── NavigationService.java           # Navigation paths
    └── TroubleshootingService.java      # Issue resolution
```

---

## Feature Categories

| Category | Feature Count | Examples |
|----------|---------------|----------|
| SECURITY | 8 | Touch ID, Face ID, Change PIN, Two-Factor Auth |
| TRANSFERS | 6 | SEPA Transfer, Internal Transfer, Standing Order |
| ACCOUNTS | 5 | View Balance, Transaction History, Download Statement |
| CARDS | 7 | Block Card, View PIN, Card Limits, Apple/Google Pay |
| SETTINGS | 10 | Push Notifications, Language, Appearance, Cache |
| SUPPORT | 4 | Contact Support, In-App Chat, FAQs, Report Issue |

**Total:** 40 features cataloged

---

## Voice Response Examples

### Feature Guide
```
"To set up Touch ID, follow these 5 steps. Step 1: Open the Acme Bank app 
and tap Menu in the top left. Step 2: Tap Settings, then Security Settings. 
Step 3: Toggle on Touch ID for Login. Step 4: Place your finger on the home 
button when prompted. Step 5: Confirm with your app PIN. Would you like me 
to repeat any step?"
```

### Navigation Path
```
"From Home, tap Menu, then Settings, then Security Settings, then Touch ID."
```

### Feature Search
```
"I found 3 features: SEPA Transfer, Internal Transfer, Instant Transfer. 
Which one would you like help with?"
```

### Troubleshooting
```
"Let me help you with that login issue. Try these steps: Step 1: Wait 30 
minutes for the lockout to expire. Step 2: Try logging in again with your 
correct PIN. Step 3: If you've forgotten your PIN, tap 'Forgot PIN' on the 
login screen. Did that resolve your issue?"
```

---

## Implementation Status

| Planned Tool | Status | Notes |
|--------------|--------|-------|
| `getFeatureGuide` | ✅ Implemented | Platform-specific guides |
| `findFeature` | ✅ Implemented | Keyword search |
| `getNavigationPath` | ✅ Implemented | Menu paths |
| `getDeepLink` | ✅ Implemented | 30+ features with deep links |
| `listFeatures` | ✅ Implemented | By category or all |
| `getTroubleshootingGuide` | ✅ Implemented | 7 issue types covered |

**All 6 planned tools implemented.** ✅

---

## Testing

### Unit Tests
- `MobileAppAssistanceAgentTest.java` — Comprehensive test suite

### Test Categories
```
1. Feature guide retrieval (iOS, Android, generic)
2. Feature search (exact match, keyword, no results)
3. Navigation path generation
4. Deep link retrieval
5. Feature listing (by category, by platform)
6. Troubleshooting (by type, by description)
7. Error handling (missing input, unknown feature)
```

### Golden Test Cases
```
1. "How do I set up Touch ID?" → iOS-specific guide
2. "How do I send money?" → Finds transfer features
3. "The app keeps crashing" → Performance troubleshooting
4. "Where is security settings?" → Navigation path
5. "Show me all card features" → Lists 7 card features
```

---

## Dependencies

### Depends On
- None (standalone agent)

### May Integrate With
- **KnowledgeCompilerAgent** — For FAQ content (future)
- **HumanHandoverAgent** — For escalation when troubleshooting fails

---

## Configuration

No special configuration required. Agent auto-registers via `@Component`.

### Optional Properties
```properties
# None currently required
```

---

## Related Documents

- [Agent Interface](../Agent.java)
- [AGENT-005 Implementation Plan](../../../../../docs/implementation-plan/AGENT-005-mobile-app-assistance.md)
- [AGENT-ARCHITECTURE-MASTER](../../../../../docs/implementation-plan/AGENT-ARCHITECTURE-MASTER.md)
- [FR-008 Mobile App Usage Guidance](../../../../../docs/functional-requirements/fr-008-mobile-app-usage-guidance.md)

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2026-01-25 |  | Initial implementation |
