# LocationServicesAgent Changelog

All notable changes to the LocationServicesAgent are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [Unreleased]

### Planned
- Integration with real geocoding service (Google Maps API)
- Real-time branch/ATM availability status
- Accessibility information for locations
- Public transit directions to branches

---

## [1.1.0] - 2026-01-24

### Added
- **New scaffold tools (awaiting implementation):**
  - `findNearbyATMs` - Locate ATMs within radius (scaffold)
  - `getBranchHours` - Get operating hours for branch (scaffold)
  - `geocodeAddress` - Convert address to coordinates (scaffold)
- Scaffold implementations return `_scaffold: true` flag

### Technical Notes
- Scaffolds prepared for Stream 4 implementation
- Each scaffold includes TODO documentation

---

## [1.0.0] - 2026-01-20

### Added
- Initial implementation of LocationServicesAgent
- **Tools implemented:**
  - `findNearbyBranches` - Find bank branches within radius
- Spring `@Component` registration for auto-discovery
- Mock location data for development/testing
- Distance calculation utilities

### Technical Notes
- Agent ID: `location-services`
- Package: `com.voicebanking.agent.location`
- Dependencies: LocationService (mock)

---

## Version History

| Version | Date | Author | Summary |
|---------|------|--------|---------|
| 1.1.0 | 2026-01-24 |  | Added 3 scaffold tools |
| 1.0.0 | 2026-01-20 |  | Initial implementation |

---

## Migration Notes

### From 1.0.0 to 1.1.0
- No breaking changes
- New tools are scaffolds only (return placeholder data)

---

## Owner

**Team:** Stream 4 - Advanced Features  
**Contact:** @location-services-lead
