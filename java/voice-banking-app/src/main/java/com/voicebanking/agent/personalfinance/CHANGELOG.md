# PersonalFinanceManagementAgent Changelog

## [Unreleased]

### Added
- Initial implementation of PersonalFinanceManagementAgent
- Tools: spending breakdown, monthly trend, income/expense summary, budget status, recurring transactions, top merchants, unusual activity
- Policy enforcer for no-advice compliance
- Voice-first bilingual response formatting
- Integration with BankingOperationsAgent and CreditCardContextAgent

### Technical Notes
- Package: `com.voicebanking.agent.personalfinance`
- Dependencies: TransactionAggregator, SpendingAnalysisService, BudgetTrackingService, CashFlowService, RecurringDetectionService, UnusualActivityService
