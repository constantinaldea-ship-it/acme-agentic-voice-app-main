# TextGeneratorAgent Changelog

All notable changes to the TextGeneratorAgent are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [Unreleased]

### Planned
- SSML (Speech Synthesis Markup Language) output option
- Persona-based response styling
- Response length optimization based on channel
- Multi-language response generation

---

## [1.0.0] - 2026-01-24

### Added
- Initial implementation of TextGeneratorAgent
- **Tools implemented:**
  - `formatCurrency` - Voice-optimized currency formatting
  - `formatDate` - Natural date formatting with relative options
  - `formatAccountNumber` - Masked account number formatting
  - `generateResponse` - Compose natural language responses
  - `formatList` - Voice-friendly list formatting
- Locale-aware formatting (default: de-DE for Acme Bank)
- Voice-optimized output (e.g., "one hundred twenty-three euros")
- Spring `@Component` registration for auto-discovery
- Comprehensive test coverage (109 tests)

### Technical Notes
- Agent ID: `text-generator`
- Package: `com.voicebanking.agent.text`
- Dependencies: None (self-contained formatters)

### Voice Optimization
- Numbers spoken naturally ("twenty-three" not "2-3")
- Dates use relative terms ("yesterday", "last Monday")
- Account numbers masked and chunked for clarity

---

## Version History

| Version | Date | Author | Summary |
|---------|------|--------|---------|
| 1.0.0 | 2026-01-24 |  | Initial implementation with 109 tests |

---

## Migration Notes

### From 0.x to 1.0.0
N/A - Initial release

---

## Testing

```bash
# Run TextGeneratorAgent tests
cd java/voice-banking-app
mvn test -Dtest="**/text/**Test"
```

**Test Coverage:** 109 tests, all passing

---

## Owner

**Team:** Stream 2 - Knowledge & Products  
**Contact:** @text-generation-lead
