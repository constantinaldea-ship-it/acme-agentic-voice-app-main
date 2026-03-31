# MobileAppAssistanceAgent Changelog

All notable changes to the MobileAppAssistanceAgent are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [Unreleased]

### Planned
- Integration with KnowledgeCompilerAgent for FAQ content
- Dynamic guide loading from external JSON files
- Version-specific feature variations
- Analytics tracking for popular guides
- Multi-language support (German guides)

---

## [1.0.0] - 2026-01-25

### Added

#### Agent Implementation
- Initial implementation of MobileAppAssistanceAgent
- Agent ID: `mobile-app-assistance`
- Package: `com.voicebanking.agent.mobileapp`
- Spring `@Component` registration for auto-discovery

#### Tools Implemented (6 total)
- `getFeatureGuide` — Step-by-step guide for a feature
- `findFeature` — Search features by query
- `getNavigationPath` — Navigation path to reach a feature
- `getDeepLink` — Deep link URL for direct navigation
- `listFeatures` — List features by category
- `getTroubleshootingGuide` — Troubleshoot common issues

#### Domain Models
- `AppFeature` — Feature model with metadata
- `FeatureCategory` — Category enum (SECURITY, TRANSFERS, ACCOUNTS, CARDS, SETTINGS, SUPPORT)
- `Platform` — Platform enum (IOS, ANDROID, BOTH)
- `Step` — Individual step in a guide
- `StepGuide` — Complete step-by-step guide
- `NavigationPath` — Menu path to reach a feature
- `IssueType` — Troubleshooting issue types
- `TroubleshootingSolution` — Troubleshooting solution with steps

#### Catalog
- `AppFeatureCatalog` — Interface for feature catalog
- `StaticAppFeatureCatalog` — Static implementation with 40 features

#### Services
- `FeatureGuideService` — Guide generation with platform variants
- `NavigationService` — Navigation path generation
- `TroubleshootingService` — Troubleshooting solution lookup

#### Feature Coverage
- 8 Security features (Touch ID, Face ID, PIN, 2FA, etc.)
- 6 Transfer features (SEPA, Internal, Standing Order, etc.)
- 5 Account features (Balance, Transactions, Statements, IBAN)
- 7 Card features (Block, Unblock, PIN, Limits, Wallet)
- 10 Settings features (Notifications, Language, Theme, etc.)
- 4 Support features (Contact, Chat, FAQs, Report)

#### Troubleshooting Coverage
- LOGIN — Wrong PIN, locked out, forgot password
- PERFORMANCE — Crashes, slow, freezing
- SECURITY — Biometric issues (Touch ID, Face ID, Fingerprint)
- FEATURE — Not working, missing features
- UPDATE — Can't update, version issues
- NETWORK — Connection, offline, timeout
- NOTIFICATION — Not receiving, too many

### Technical Notes

- Voice responses optimized for clarity and pacing
- Maximum 5 steps per guide for voice comprehension
- Platform-specific guides for iOS and Android with generic fallback
- Deep links supported for 30+ features (app version 5.0+)
- Keyword-based feature search with relevance ranking

### UX Considerations

- All responses include `voiceResponse` field for TTS output
- Step numbering: "Step 1, Step 2..." for clear progression
- Escalation path when troubleshooting fails
- Suggestions offered when exact feature not found

---

## Version History

| Version | Date | Author | Summary |
|---------|------|--------|---------|
| 1.0.0 | 2026-01-25 |  | Initial implementation with 6 tools |

---
