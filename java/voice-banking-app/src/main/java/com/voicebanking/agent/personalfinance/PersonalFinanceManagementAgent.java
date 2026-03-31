package com.voicebanking.agent.personalfinance;

import com.voicebanking.agent.Agent;
import com.voicebanking.agent.personalfinance.domain.*;
import com.voicebanking.agent.personalfinance.integration.TransactionAggregator;
import com.voicebanking.agent.personalfinance.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PersonalFinanceManagementAgent — Spending Analysis & Budget Tracking
 *
 * Provides factual personal finance insights based on transaction data.
 * No recommendations or advice are given.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
@Component
public class PersonalFinanceManagementAgent implements Agent {
    private static final Logger log = LoggerFactory.getLogger(PersonalFinanceManagementAgent.class);

    private static final String AGENT_ID = "personal-finance";
    private static final List<String> TOOL_IDS = List.of(
            "getSpendingBreakdown",
            "getMonthlyTrend",
            "getIncomeExpenseSummary",
            "getBudgetStatus",
            "getRecurringTransactions",
            "getTopMerchants",
            "getUnusualActivity"
    );

    private final TransactionAggregator transactionAggregator;
    private final SpendingAnalysisService spendingAnalysisService;
    private final BudgetTrackingService budgetTrackingService;
    private final CashFlowService cashFlowService;
    private final RecurringDetectionService recurringDetectionService;
    private final UnusualActivityService unusualActivityService;
    private final VoiceResponseFormatter voiceFormatter;
    private final PolicyEnforcer policyEnforcer;

    public PersonalFinanceManagementAgent(
            TransactionAggregator transactionAggregator,
            SpendingAnalysisService spendingAnalysisService,
            BudgetTrackingService budgetTrackingService,
            CashFlowService cashFlowService,
            RecurringDetectionService recurringDetectionService,
            UnusualActivityService unusualActivityService,
            VoiceResponseFormatter voiceFormatter,
            PolicyEnforcer policyEnforcer) {
        this.transactionAggregator = transactionAggregator;
        this.spendingAnalysisService = spendingAnalysisService;
        this.budgetTrackingService = budgetTrackingService;
        this.cashFlowService = cashFlowService;
        this.recurringDetectionService = recurringDetectionService;
        this.unusualActivityService = unusualActivityService;
        this.voiceFormatter = voiceFormatter;
        this.policyEnforcer = policyEnforcer;
        log.info("PersonalFinanceManagementAgent initialized with {} tools", TOOL_IDS.size());
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    @Override
    public String getDescription() {
        return "Provides factual spending analysis, budget tracking, and cash flow summaries without financial advice";
    }

    @Override
    public List<String> getToolIds() {
        return TOOL_IDS;
    }

    @Override
    public Map<String, Object> executeTool(String toolId, Map<String, Object> input) {
        log.debug("Executing tool: {} with input: {}", toolId, input);

        try {
            return switch (toolId) {
                case "getSpendingBreakdown" -> getSpendingBreakdown(input);
                case "getMonthlyTrend" -> getMonthlyTrend(input);
                case "getIncomeExpenseSummary" -> getIncomeExpenseSummary(input);
                case "getBudgetStatus" -> getBudgetStatus(input);
                case "getRecurringTransactions" -> getRecurringTransactions(input);
                case "getTopMerchants" -> getTopMerchants(input);
                case "getUnusualActivity" -> getUnusualActivity(input);
                default -> errorResponse("UNKNOWN_TOOL", "Unknown tool: " + toolId);
            };
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", toolId, e.getMessage(), e);
            return errorResponse("EXECUTION_ERROR", e.getMessage());
        }
    }

    private Map<String, Object> getSpendingBreakdown(Map<String, Object> input) {
        PeriodRange range = parseMonthRange(input);
        String language = (String) input.getOrDefault("lang", "en");
        String customerId = (String) input.get("customerId");

        List<PfmTransaction> transactions = transactionAggregator.aggregateTransactions(
                customerId, range.start(), range.end());
        SpendingBreakdown breakdown = spendingAnalysisService.calculateBreakdown(
                transactions, range.start(), range.end());

        String voiceResponse = voiceFormatter.formatBreakdown(breakdown, language);
        PolicyEnforcer.PolicyResult policy = policyEnforcer.enforce(voiceResponse, language);

        return successResponse(breakdown.toMap(), policy.text(), policy.disclaimer(), "getSpendingBreakdown");
    }

    private Map<String, Object> getMonthlyTrend(Map<String, Object> input) {
        int months = input.get("months") instanceof Number
                ? ((Number) input.get("months")).intValue()
                : 3;
        String language = (String) input.getOrDefault("lang", "en");
        String customerId = (String) input.get("customerId");

        LocalDate start = YearMonth.now().minusMonths(months - 1).atDay(1);
        LocalDate end = YearMonth.now().atEndOfMonth();
        List<PfmTransaction> transactions = transactionAggregator.aggregateTransactions(customerId, start, end);

        SpendingTrend trend = spendingAnalysisService.calculateMonthlyTrend(transactions, months);
        String voiceResponse = voiceFormatter.formatTrend(trend, language);
        PolicyEnforcer.PolicyResult policy = policyEnforcer.enforce(voiceResponse, language);

        return successResponse(trend.toMap(), policy.text(), policy.disclaimer(), "getMonthlyTrend");
    }

    private Map<String, Object> getIncomeExpenseSummary(Map<String, Object> input) {
        PeriodRange range = parseMonthRange(input);
        String language = (String) input.getOrDefault("lang", "en");
        String customerId = (String) input.get("customerId");

        List<PfmTransaction> transactions = transactionAggregator.aggregateTransactions(
                customerId, range.start(), range.end());
        CashFlowSummary summary = cashFlowService.calculateCashFlow(transactions, range.start(), range.end());

        String voiceResponse = voiceFormatter.formatCashFlow(summary, language);
        PolicyEnforcer.PolicyResult policy = policyEnforcer.enforce(voiceResponse, language);

        return successResponse(summary.toMap(), policy.text(), policy.disclaimer(), "getIncomeExpenseSummary");
    }

    private Map<String, Object> getBudgetStatus(Map<String, Object> input) {
        String language = (String) input.getOrDefault("lang", "en");
        String customerId = (String) input.get("customerId");
        YearMonth month = parseMonth(input);

        List<PfmTransaction> transactions = transactionAggregator.aggregateTransactions(
                customerId, month.atDay(1), month.atEndOfMonth());
        List<BudgetStatus> statuses = budgetTrackingService.getBudgetStatus(transactions, month);

        String voiceResponse = voiceFormatter.formatBudgetStatus(statuses, language);
        PolicyEnforcer.PolicyResult policy = policyEnforcer.enforce(voiceResponse, language);

        return successResponse(
            Map.of(
                "month", month.toString(),
                "budgets", statuses.stream().map(BudgetStatus::toMap).toList()
            ),
            policy.text(),
            policy.disclaimer(),
            "getBudgetStatus"
        );
    }

    private Map<String, Object> getRecurringTransactions(Map<String, Object> input) {
        String language = (String) input.getOrDefault("lang", "en");
        String customerId = (String) input.get("customerId");

        LocalDate start = LocalDate.now().minusDays(90);
        LocalDate end = LocalDate.now();
        List<PfmTransaction> transactions = transactionAggregator.aggregateTransactions(customerId, start, end);
        List<RecurringTransaction> recurring = recurringDetectionService.detectRecurring(transactions);

        String voiceResponse = voiceFormatter.formatRecurring(recurring, language);
        PolicyEnforcer.PolicyResult policy = policyEnforcer.enforce(voiceResponse, language);

        return successResponse(
            Map.of(
                "recurring", recurring.stream().map(RecurringTransaction::toMap).toList()
            ),
            policy.text(),
            policy.disclaimer(),
            "getRecurringTransactions"
        );
    }

    private Map<String, Object> getTopMerchants(Map<String, Object> input) {
        String language = (String) input.getOrDefault("lang", "en");
        String customerId = (String) input.get("customerId");
        int limit = input.get("limit") instanceof Number
                ? ((Number) input.get("limit")).intValue()
                : 5;

        LocalDate start = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now();
        List<PfmTransaction> transactions = transactionAggregator.aggregateTransactions(customerId, start, end);
        List<Map<String, Object>> merchants = spendingAnalysisService.getTopMerchants(transactions, limit);

        String voiceResponse = voiceFormatter.formatTopMerchants(merchants, language);
        PolicyEnforcer.PolicyResult policy = policyEnforcer.enforce(voiceResponse, language);

        return successResponse(
            Map.of("merchants", merchants),
            policy.text(),
            policy.disclaimer(),
            "getTopMerchants"
        );
    }

    private Map<String, Object> getUnusualActivity(Map<String, Object> input) {
        String language = (String) input.getOrDefault("lang", "en");
        String customerId = (String) input.get("customerId");
        PeriodRange range = parseMonthRange(input);

        List<PfmTransaction> transactions = transactionAggregator.aggregateTransactions(
                customerId, range.start(), range.end());
        List<UnusualActivity> unusual = unusualActivityService.detectUnusual(transactions);

        String voiceResponse = voiceFormatter.formatUnusual(unusual, language);
        PolicyEnforcer.PolicyResult policy = policyEnforcer.enforce(voiceResponse, language);

        return successResponse(
            Map.of(
                "anomalies", unusual.stream().map(UnusualActivity::toMap).toList()
            ),
            policy.text(),
            policy.disclaimer(),
            "getUnusualActivity"
        );
    }

    private PeriodRange parseMonthRange(Map<String, Object> input) {
        YearMonth month = parseMonth(input);
        return new PeriodRange(month.atDay(1), month.atEndOfMonth());
    }

    private YearMonth parseMonth(Map<String, Object> input) {
        Object monthObj = input.get("month");
        if (monthObj != null) {
            try {
                return YearMonth.parse(monthObj.toString());
            } catch (Exception e) {
                log.warn("Invalid month format: {}", monthObj);
            }
        }
        return YearMonth.now();
    }

    private Map<String, Object> errorResponse(String code, String message) {
        return Map.of(
                "success", false,
                "error", Map.of(
                        "code", code,
                        "message", message
                ),
                "metadata", buildMetadata("error")
        );
    }

    private Map<String, Object> buildMetadata(String toolId) {
        return Map.of(
                "agentId", AGENT_ID,
                "toolId", toolId,
                "timestamp", java.time.Instant.now().toString()
        );
    }

    private Map<String, Object> successResponse(Object data, String voiceResponse, String disclaimer, String toolId) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("voiceResponse", voiceResponse);
        if (disclaimer != null && !disclaimer.isBlank()) {
            response.put("disclaimer", disclaimer);
        }
        response.put("metadata", buildMetadata(toolId));
        return response;
    }

    private record PeriodRange(LocalDate start, LocalDate end) {}
}
