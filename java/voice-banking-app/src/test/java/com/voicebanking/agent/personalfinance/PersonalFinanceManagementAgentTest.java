package com.voicebanking.agent.personalfinance;

import com.voicebanking.agent.personalfinance.domain.*;
import com.voicebanking.agent.personalfinance.integration.TransactionAggregator;
import com.voicebanking.agent.personalfinance.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PersonalFinanceManagementAgent.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
@ExtendWith(MockitoExtension.class)
class PersonalFinanceManagementAgentTest {

    @Mock
    private TransactionAggregator transactionAggregator;

    @Mock
    private SpendingAnalysisService spendingAnalysisService;

    @Mock
    private BudgetTrackingService budgetTrackingService;

    @Mock
    private CashFlowService cashFlowService;

    @Mock
    private RecurringDetectionService recurringDetectionService;

    @Mock
    private UnusualActivityService unusualActivityService;

    @Mock
    private VoiceResponseFormatter voiceFormatter;

    @Mock
    private PolicyEnforcer policyEnforcer;

    private PersonalFinanceManagementAgent agent;

    @BeforeEach
    void setUp() {
        agent = new PersonalFinanceManagementAgent(
                transactionAggregator,
                spendingAnalysisService,
                budgetTrackingService,
                cashFlowService,
                recurringDetectionService,
                unusualActivityService,
                voiceFormatter,
                policyEnforcer
        );

        lenient().when(policyEnforcer.enforce(anyString(), anyString()))
            .thenReturn(new PolicyEnforcer.PolicyResult("voice", false, null));
        lenient().when(transactionAggregator.aggregateTransactions(any(), any(), any()))
            .thenReturn(List.of(sampleTransaction()));
    }

    @Test
    @DisplayName("Agent ID should be personal-finance")
    void agentId_shouldMatch() {
        assertEquals("personal-finance", agent.getAgentId());
    }

    @Test
    @DisplayName("Agent should expose 7 tools")
    void toolIds_shouldContainSevenTools() {
        List<String> tools = agent.getToolIds();
        assertEquals(7, tools.size());
        assertTrue(tools.contains("getSpendingBreakdown"));
        assertTrue(tools.contains("getMonthlyTrend"));
        assertTrue(tools.contains("getIncomeExpenseSummary"));
        assertTrue(tools.contains("getBudgetStatus"));
        assertTrue(tools.contains("getRecurringTransactions"));
        assertTrue(tools.contains("getTopMerchants"));
        assertTrue(tools.contains("getUnusualActivity"));
    }

    @Test
    @DisplayName("Unknown tool should return error")
    void executeTool_unknownTool_shouldReturnError() {
        Map<String, Object> result = agent.executeTool("unknown", Map.of());
        assertFalse((Boolean) result.get("success"));
        assertNotNull(result.get("error"));
    }

    @Nested
    @DisplayName("getSpendingBreakdown Tool")
    class SpendingBreakdownTests {
        @Test
        @DisplayName("Should return breakdown data")
        void shouldReturnBreakdown() {
            SpendingBreakdown breakdown = new SpendingBreakdown(
                    LocalDate.now().minusDays(30),
                    LocalDate.now(),
                    Map.of("EUR", new BigDecimal("120.00")),
                    List.of(new SpendingCategorySummary(
                            SpendingCategory.FOOD,
                            new BigDecimal("120.00"),
                            new BigDecimal("100"),
                            2,
                            "EUR"
                    ))
            );
            when(spendingAnalysisService.calculateBreakdown(anyList(), any(), any())).thenReturn(breakdown);
            when(voiceFormatter.formatBreakdown(eq(breakdown), anyString())).thenReturn("voice");

            Map<String, Object> result = agent.executeTool("getSpendingBreakdown", Map.of("month", "2026-01"));

            assertTrue((Boolean) result.get("success"));
            assertNotNull(result.get("data"));
            assertNotNull(result.get("voiceResponse"));
        }
    }

    @Nested
    @DisplayName("getMonthlyTrend Tool")
    class MonthlyTrendTests {
        @Test
        @DisplayName("Should return trend data")
        void shouldReturnTrend() {
            SpendingTrend trend = new SpendingTrend(List.of(
                    new TrendPoint(YearMonth.now(), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, "EUR")
            ));
            when(spendingAnalysisService.calculateMonthlyTrend(anyList(), anyInt())).thenReturn(trend);
            when(voiceFormatter.formatTrend(eq(trend), anyString())).thenReturn("voice");

            Map<String, Object> result = agent.executeTool("getMonthlyTrend", Map.of("months", 3));

            assertTrue((Boolean) result.get("success"));
            assertNotNull(result.get("data"));
        }
    }

    @Nested
    @DisplayName("getIncomeExpenseSummary Tool")
    class CashFlowTests {
        @Test
        @DisplayName("Should return cash flow summary")
        void shouldReturnCashFlow() {
            CashFlowSummary summary = new CashFlowSummary(
                    LocalDate.now().minusDays(30),
                    LocalDate.now(),
                    Map.of("EUR", new BigDecimal("1000")),
                    Map.of("EUR", new BigDecimal("500")),
                    Map.of("EUR", new BigDecimal("500"))
            );
            when(cashFlowService.calculateCashFlow(anyList(), any(), any())).thenReturn(summary);
            when(voiceFormatter.formatCashFlow(eq(summary), anyString())).thenReturn("voice");

            Map<String, Object> result = agent.executeTool("getIncomeExpenseSummary", Map.of("month", "2026-01"));

            assertTrue((Boolean) result.get("success"));
            assertNotNull(result.get("data"));
        }
    }

    @Nested
    @DisplayName("getBudgetStatus Tool")
    class BudgetStatusTests {
        @Test
        @DisplayName("Should return budget status list")
        void shouldReturnBudgetStatus() {
            Budget budget = new Budget(SpendingCategory.FOOD, new BigDecimal("500"), "EUR", BudgetPeriod.MONTHLY, LocalDate.now());
            BudgetStatus status = new BudgetStatus(budget, new BigDecimal("200"), new BigDecimal("300"), new BigDecimal("40"), true, 10);
            when(budgetTrackingService.getBudgetStatus(anyList(), any())).thenReturn(List.of(status));
            when(voiceFormatter.formatBudgetStatus(anyList(), anyString())).thenReturn("voice");

            Map<String, Object> result = agent.executeTool("getBudgetStatus", Map.of("month", "2026-01"));

            assertTrue((Boolean) result.get("success"));
            assertNotNull(result.get("data"));
        }
    }

    @Nested
    @DisplayName("getRecurringTransactions Tool")
    class RecurringTests {
        @Test
        @DisplayName("Should return recurring transactions")
        void shouldReturnRecurring() {
            RecurringTransaction recurring = new RecurringTransaction(
                    "Netflix", new BigDecimal("12.99"), "EUR", "monthly", 3, SpendingCategory.ENTERTAINMENT, new BigDecimal("12.99"));
            when(recurringDetectionService.detectRecurring(anyList())).thenReturn(List.of(recurring));
            when(voiceFormatter.formatRecurring(anyList(), anyString())).thenReturn("voice");

            Map<String, Object> result = agent.executeTool("getRecurringTransactions", Map.of());

            assertTrue((Boolean) result.get("success"));
            assertNotNull(result.get("data"));
        }
    }

    @Nested
    @DisplayName("getTopMerchants Tool")
    class TopMerchantsTests {
        @Test
        @DisplayName("Should return top merchants")
        void shouldReturnTopMerchants() {
            List<Map<String, Object>> merchants = List.of(
                    Map.of("merchantName", "Amazon", "totalAmount", new BigDecimal("99.99"), "transactionCount", 2, "currency", "EUR")
            );
            when(spendingAnalysisService.getTopMerchants(anyList(), anyInt())).thenReturn(merchants);
            when(voiceFormatter.formatTopMerchants(anyList(), anyString())).thenReturn("voice");

            Map<String, Object> result = agent.executeTool("getTopMerchants", Map.of("limit", 5));

            assertTrue((Boolean) result.get("success"));
            assertNotNull(result.get("data"));
        }
    }

    @Nested
    @DisplayName("getUnusualActivity Tool")
    class UnusualTests {
        @Test
        @DisplayName("Should return unusual activity")
        void shouldReturnUnusual() {
            UnusualActivity unusual = new UnusualActivity(LocalDate.now(), "Electronics", new BigDecimal("800"), "EUR", "amount_above_average");
            when(unusualActivityService.detectUnusual(anyList())).thenReturn(List.of(unusual));
            when(voiceFormatter.formatUnusual(anyList(), anyString())).thenReturn("voice");

            Map<String, Object> result = agent.executeTool("getUnusualActivity", Map.of("month", "2026-01"));

            assertTrue((Boolean) result.get("success"));
            assertNotNull(result.get("data"));
        }
    }

    private PfmTransaction sampleTransaction() {
        return new PfmTransaction(
                "BANK",
                "txn-1",
                LocalDate.now(),
                "Grocery Store",
                "Grocery Store",
                new BigDecimal("-45.00"),
                "EUR",
                SpendingCategory.FOOD
        );
    }
}
