# BankingOperationsAgent Changelog

All notable changes to the BankingOperationsAgent are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [Unreleased]

### Planned
- Multi-currency support for international transfers
- Scheduled/recurring transaction queries
- Account nickname resolution

---

## [1.0.0] - 2026-01-24

### Added
- Initial implementation of BankingOperationsAgent
- **Tools implemented:**
  - `getAccountBalance` - Retrieve balance for checking/savings/credit accounts
  - `getTransactionHistory` - Query transactions with date/amount filters
  - `listAccounts` - List all accounts for authenticated user
  - `transferFunds` - Transfer between accounts with validation
- Spring `@Component` registration for auto-discovery
- Integration with `AccountService` and `TransactionService`
- Input validation for all tool parameters
- Error handling with descriptive messages

### Technical Notes
- Agent ID: `banking-operations`
- Package: `com.voicebanking.agent.banking`
- Dependencies: AccountService, TransactionService

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

**Team:** Stream 3 - Context-Aware Banking  
**Contact:** @banking-ops-lead
