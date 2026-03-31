# KnowledgeCompilerAgent Changelog

All notable changes to the KnowledgeCompilerAgent are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [Unreleased]

### Planned
- Vector search integration for semantic FAQ matching
- Multi-language knowledge base support
- Knowledge freshness tracking and alerts
- Integration with CMS for dynamic content updates

---

## [1.0.0] - 2026-01-24

### Added
- Initial implementation of KnowledgeCompilerAgent
- **Tools implemented:**
  - `searchFAQ` - Search frequently asked questions
  - `getProductInfo` - Retrieve product details (credit cards, accounts)
  - `getAppGuidance` - Mobile app usage instructions
  - `getGeneralInfo` - General bank information
- Keyword-based search with relevance scoring
- Category-based filtering
- Spring `@Component` registration for auto-discovery

### Technical Notes
- Agent ID: `knowledge-compiler`
- Package: `com.voicebanking.agent.knowledge`
- Dependencies: KnowledgeBase, FAQRepository

### Content Coverage
- FR-001: General public information ✓
- FR-002: Credit card product info ✓
- FR-003: Giro/Debit account info ✓
- FR-008: Mobile app guidance ✓

---

## Version History

| Version | Date | Author | Summary |
|---------|------|--------|---------|
| 1.0.0 | 2026-01-24 |  | Initial implementation |

---

## Migration Notes

### From 0.x to 1.0.0
N/A - Initial release

---

## Owner

**Team:** Stream 2 - Knowledge & Products  
**Contact:** @knowledge-lead
