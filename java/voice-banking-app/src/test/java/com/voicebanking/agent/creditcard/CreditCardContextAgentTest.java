package com.voicebanking.agent.creditcard;

import com.voicebanking.agent.creditcard.domain.*;
import com.voicebanking.agent.creditcard.integration.CardManagementClient;
import com.voicebanking.agent.creditcard.service.CreditCardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CreditCardContextAgent using stub implementations.
 * Tests all tools and edge cases following the HumanHandoverAgentTest pattern.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@DisplayName("CreditCardContextAgent Tests")
class CreditCardContextAgentTest {

    private CreditCardContextAgent agent;
    private StubCardManagementClient cardManagementClient;

    @BeforeEach
    void setUp() {
        cardManagementClient = new StubCardManagementClient();
        CreditCardService creditCardService = new CreditCardService(cardManagementClient);
        agent = new CreditCardContextAgent(creditCardService);
    }

    @Nested
    @DisplayName("Agent Identity Tests")
    class AgentIdentityTests {
        
        @Test
        @DisplayName("Should have correct agent ID")
        void shouldHaveCorrectAgentId() {
            assertThat(agent.getAgentId()).isEqualTo("credit-card-context");
        }

        @Test
        @DisplayName("Should have appropriate description")
        void shouldHaveDescription() {
            assertThat(agent.getDescription())
                    .contains("credit card")
                    .containsIgnoringCase("spending");
        }

        @Test
        @DisplayName("Should have exactly 5 tools")
        void shouldHaveFiveTools() {
            assertThat(agent.getToolIds())
                    .hasSize(5)
                    .contains(
                            "getCreditCardBalance",
                            "getCreditCardTransactions",
                            "getCreditCardStatement",
                            "getCreditCardLimit",
                            "getCreditCardRewards"
                    );
        }

        @Test
        @DisplayName("Should support all registered tools")
        void shouldSupportAllTools() {
            for (String toolId : agent.getToolIds()) {
                assertThat(agent.supportsTool(toolId)).isTrue();
            }
        }

        @Test
        @DisplayName("Should not support unknown tool")
        void shouldNotSupportUnknownTool() {
            assertThat(agent.supportsTool("unknownTool")).isFalse();
        }
    }

    @Nested
    @DisplayName("getCreditCardBalance Tests")
    class GetCreditCardBalanceTests {

        @Test
        @DisplayName("Should return balance for valid card")
        void shouldReturnBalanceForValidCard() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardBalance", input);
            
            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("balance")).isNotNull();
            assertThat(result.get("card")).isNotNull();
            assertThat(result.get("voiceResponse")).isNotNull();
        }

        @Test
        @DisplayName("Should include masked card number in response")
        void shouldIncludeMaskedCardNumber() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardBalance", input);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> card = (Map<String, Object>) result.get("card");
            String maskedNumber = (String) card.get("maskedCardNumber");
            
            assertThat(maskedNumber).startsWith("****");
            assertThat(maskedNumber).contains("4821");
        }

        @Test
        @DisplayName("Should return error for missing card ID")
        void shouldReturnErrorForMissingCardId() {
            Map<String, Object> input = new HashMap<>();
            
            Map<String, Object> result = agent.executeTool("getCreditCardBalance", input);
            
            assertThat(result.get("success")).isEqualTo(false);
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) result.get("error");
            assertThat(error.get("code")).isEqualTo("MISSING_CARD_ID");
        }

        @Test
        @DisplayName("Should return error for non-existent card")
        void shouldReturnErrorForNonExistentCard() {
            Map<String, Object> input = Map.of("cardId", "non-existent-card");
            
            Map<String, Object> result = agent.executeTool("getCreditCardBalance", input);
            
            assertThat(result.get("success")).isEqualTo(false);
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) result.get("error");
            assertThat(error.get("code")).isEqualTo("CARD_NOT_FOUND");
        }

        @Test
        @DisplayName("Should include utilization in balance")
        void shouldIncludeUtilization() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardBalance", input);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> balance = (Map<String, Object>) result.get("balance");
            assertThat(balance).containsKey("utilizationPercentage");
        }

        @Test
        @DisplayName("Should format voice response correctly")
        void shouldFormatVoiceResponseCorrectly() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardBalance", input);
            
            String voiceResponse = (String) result.get("voiceResponse");
            assertThat(voiceResponse)
                    .contains("Gold")
                    .contains("MasterCard")
                    .contains("4821")
                    .containsIgnoringCase("balance");
        }
    }

    @Nested
    @DisplayName("getCreditCardTransactions Tests")
    class GetCreditCardTransactionsTests {

        @Test
        @DisplayName("Should return transactions for valid card")
        void shouldReturnTransactionsForValidCard() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardTransactions", input);
            
            assertThat(result.get("success")).isEqualTo(true);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transactions = (List<Map<String, Object>>) result.get("transactions");
            assertThat(transactions).isNotEmpty();
        }

        @Test
        @DisplayName("Should filter transactions by date range")
        void shouldFilterByDateRange() {
            Map<String, Object> input = new HashMap<>();
            input.put("cardId", "card-gold-4821");
            input.put("startDate", LocalDate.now().minusDays(7).toString());
            input.put("endDate", LocalDate.now().toString());
            
            Map<String, Object> result = agent.executeTool("getCreditCardTransactions", input);
            
            assertThat(result.get("success")).isEqualTo(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) result.get("summary");
            @SuppressWarnings("unchecked")
            Map<String, Object> dateRange = (Map<String, Object>) summary.get("dateRange");
            assertThat(dateRange.get("start")).isEqualTo(LocalDate.now().minusDays(7).toString());
        }

        @Test
        @DisplayName("Should filter transactions by category")
        void shouldFilterByCategory() {
            Map<String, Object> input = new HashMap<>();
            input.put("cardId", "card-gold-4821");
            input.put("category", "SHOPPING");
            
            Map<String, Object> result = agent.executeTool("getCreditCardTransactions", input);
            
            assertThat(result.get("success")).isEqualTo(true);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transactions = (List<Map<String, Object>>) result.get("transactions");
            for (Map<String, Object> tx : transactions) {
                assertThat(tx.get("category")).isEqualTo("SHOPPING");
            }
        }

        @Test
        @DisplayName("Should return error for invalid category")
        void shouldReturnErrorForInvalidCategory() {
            Map<String, Object> input = new HashMap<>();
            input.put("cardId", "card-gold-4821");
            input.put("category", "INVALID_CATEGORY");
            
            Map<String, Object> result = agent.executeTool("getCreditCardTransactions", input);
            
            assertThat(result.get("success")).isEqualTo(false);
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) result.get("error");
            assertThat(error.get("code")).isEqualTo("INVALID_CATEGORY");
        }

        @Test
        @DisplayName("Should include transaction summary")
        void shouldIncludeTransactionSummary() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardTransactions", input);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) result.get("summary");
            assertThat(summary).containsKeys("totalTransactions", "purchaseCount", "purchaseTotal");
        }

        @Test
        @DisplayName("Should respect limit parameter")
        void shouldRespectLimitParameter() {
            Map<String, Object> input = new HashMap<>();
            input.put("cardId", "card-gold-4821");
            input.put("limit", 3);
            
            Map<String, Object> result = agent.executeTool("getCreditCardTransactions", input);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transactions = (List<Map<String, Object>>) result.get("transactions");
            assertThat(transactions).hasSizeLessThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("getCreditCardStatement Tests")
    class GetCreditCardStatementTests {

        @Test
        @DisplayName("Should return statement for valid card and period")
        void shouldReturnStatementForValidCardAndPeriod() {
            Map<String, Object> input = new HashMap<>();
            input.put("cardId", "card-gold-4821");
            input.put("period", "2026-01");
            
            Map<String, Object> result = agent.executeTool("getCreditCardStatement", input);
            
            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("statement")).isNotNull();
        }

        @Test
        @DisplayName("Should default to current month if period not specified")
        void shouldDefaultToCurrentMonth() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardStatement", input);
            
            assertThat(result.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("Should include statement totals")
        void shouldIncludeStatementTotals() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardStatement", input);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> statement = (Map<String, Object>) result.get("statement");
            assertThat(statement).containsKeys(
                    "openingBalance", "closingBalance", "purchasesTotal", "paymentsTotal"
            );
        }

        @Test
        @DisplayName("Should include transactions in statement")
        void shouldIncludeTransactionsInStatement() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardStatement", input);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> statement = (Map<String, Object>) result.get("statement");
            assertThat(statement).containsKey("transactions");
        }

        @Test
        @DisplayName("Should format voice summary correctly")
        void shouldFormatVoiceSummaryCorrectly() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardStatement", input);
            
            String voiceResponse = (String) result.get("voiceResponse");
            assertThat(voiceResponse)
                    .containsIgnoringCase("statement")
                    .containsIgnoringCase("transactions");
        }
    }

    @Nested
    @DisplayName("getCreditCardLimit Tests")
    class GetCreditCardLimitTests {

        @Test
        @DisplayName("Should return limit for valid card")
        void shouldReturnLimitForValidCard() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardLimit", input);
            
            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("limit")).isNotNull();
        }

        @Test
        @DisplayName("Should include utilization percentage")
        void shouldIncludeUtilizationPercentage() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardLimit", input);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> limit = (Map<String, Object>) result.get("limit");
            assertThat(limit).containsKey("utilizationPercentage");
            assertThat(limit).containsKey("utilizationLevel");
        }

        @Test
        @DisplayName("Should include increase eligibility")
        void shouldIncludeIncreaseEligibility() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardLimit", input);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> limit = (Map<String, Object>) result.get("limit");
            assertThat(limit).containsKeys("increaseEligible", "maxIncreaseAmount");
        }

        @Test
        @DisplayName("Should format limit voice response")
        void shouldFormatLimitVoiceResponse() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardLimit", input);
            
            String voiceResponse = (String) result.get("voiceResponse");
            assertThat(voiceResponse)
                    .containsIgnoringCase("limit")
                    .containsIgnoringCase("available");
        }
    }

    @Nested
    @DisplayName("getCreditCardRewards Tests")
    class GetCreditCardRewardsTests {

        @Test
        @DisplayName("Should return rewards for valid card")
        void shouldReturnRewardsForValidCard() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardRewards", input);
            
            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("rewards")).isNotNull();
        }

        @Test
        @DisplayName("Should include program details")
        void shouldIncludeProgramDetails() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardRewards", input);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> rewards = (Map<String, Object>) result.get("rewards");
            assertThat(rewards).containsKeys("programName", "rewardsType", "currentBalance");
        }

        @Test
        @DisplayName("Should include earnings information")
        void shouldIncludeEarningsInfo() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardRewards", input);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> rewards = (Map<String, Object>) result.get("rewards");
            assertThat(rewards).containsKeys("earnedThisMonth", "earnedThisYear");
        }

        @Test
        @DisplayName("Should include expiring rewards warning")
        void shouldIncludeExpiringRewardsWarning() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardRewards", input);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> rewards = (Map<String, Object>) result.get("rewards");
            assertThat(rewards).containsKey("hasExpiringRewards");
        }

        @Test
        @DisplayName("Should format rewards voice response")
        void shouldFormatRewardsVoiceResponse() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardRewards", input);
            
            String voiceResponse = (String) result.get("voiceResponse");
            // Voice response should contain reward type (points or cashback) and earnings
            assertThat(voiceResponse)
                    .isNotBlank()
                    .satisfiesAnyOf(
                        v -> assertThat(v).containsIgnoringCase("points"),
                        v -> assertThat(v).containsIgnoringCase("cashback")
                    );
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should return error for unknown tool")
        void shouldReturnErrorForUnknownTool() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("unknownTool", input);
            
            assertThat(result.get("success")).isEqualTo(false);
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) result.get("error");
            assertThat(error.get("code")).isEqualTo("UNKNOWN_TOOL");
        }

        @Test
        @DisplayName("Should return error for blank card ID")
        void shouldReturnErrorForBlankCardId() {
            Map<String, Object> input = Map.of("cardId", "   ");
            
            Map<String, Object> result = agent.executeTool("getCreditCardBalance", input);
            
            assertThat(result.get("success")).isEqualTo(false);
        }

        @Test
        @DisplayName("Should include metadata in all responses")
        void shouldIncludeMetadataInAllResponses() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            for (String toolId : agent.getToolIds()) {
                Map<String, Object> result = agent.executeTool(toolId, input);
                assertThat(result).containsKey("metadata");
                
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
                assertThat(metadata).containsKeys("agentId", "toolId", "timestamp");
            }
        }
    }

    @Nested
    @DisplayName("Card Number Masking Tests")
    class CardNumberMaskingTests {

        @Test
        @DisplayName("Should mask card numbers in all responses")
        void shouldMaskCardNumbersInAllResponses() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardBalance", input);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> card = (Map<String, Object>) result.get("card");
            String maskedNumber = (String) card.get("maskedCardNumber");
            
            // Should show format ****-****-****-XXXX
            assertThat(maskedNumber).matches("\\*{4}-\\*{4}-\\*{4}-\\d{4}");
        }

        @Test
        @DisplayName("Should never expose full card number")
        void shouldNeverExposeFullCardNumber() {
            Map<String, Object> input = Map.of("cardId", "card-gold-4821");
            
            Map<String, Object> result = agent.executeTool("getCreditCardBalance", input);
            String resultString = result.toString();
            
            // Should not contain any 16-digit card number patterns
            assertThat(resultString).doesNotMatch(".*\\d{16}.*");
        }
    }

    // ========== Stub Implementation ==========

    static class StubCardManagementClient implements CardManagementClient {
        private final Map<String, CreditCard> cards = new HashMap<>();
        private final Map<String, CreditCardBalance> balances = new HashMap<>();
        private final Map<String, List<CreditCardTransaction>> transactions = new HashMap<>();
        private final Map<String, CreditCardLimit> limits = new HashMap<>();
        private final Map<String, CreditCardRewards> rewards = new HashMap<>();

        StubCardManagementClient() {
            initializeData();
        }

        private void initializeData() {
            // Gold card
            CreditCard goldCard = CreditCard.builder()
                    .cardId("card-gold-4821")
                    .customerId("CUST-001")
                    .cardType("Gold")
                    .cardNetwork("MasterCard")
                    .maskedCardNumber("****-****-****-4821")
                    .cardholderName("Max Mustermann")
                    .expiryDate(LocalDate.of(2028, 6, 30))
                    .status(CreditCard.CardStatus.ACTIVE)
                    .rewardsProgramId("miles-and-more")
                    .build();
            cards.put("card-gold-4821", goldCard);

            // Balance
            balances.put("card-gold-4821", CreditCardBalance.builder()
                    .cardId("card-gold-4821")
                    .currentBalance(new BigDecimal("1245.50"))
                    .availableCredit(new BigDecimal("8754.50"))
                    .creditLimit(new BigDecimal("10000.00"))
                    .minimumPayment(new BigDecimal("50.00"))
                    .paymentDueDate(LocalDate.now().plusDays(20))
                    .currency("EUR")
                    .build());

            // Transactions
            List<CreditCardTransaction> txns = new ArrayList<>();
            txns.add(CreditCardTransaction.builder()
                    .transactionId("TXN-001")
                    .cardId("card-gold-4821")
                    .transactionDate(LocalDateTime.now().minusDays(2))
                    .amount(new BigDecimal("-89.90"))
                    .merchantName("Amazon.de")
                    .category(SpendingCategory.SHOPPING)
                    .type(CreditCardTransaction.TransactionType.PURCHASE)
                    .status(CreditCardTransaction.TransactionStatus.POSTED)
                    .currency("EUR")
                    .build());
            txns.add(CreditCardTransaction.builder()
                    .transactionId("TXN-002")
                    .cardId("card-gold-4821")
                    .transactionDate(LocalDateTime.now().minusDays(3))
                    .amount(new BigDecimal("-45.50"))
                    .merchantName("Restaurant Atlantis")
                    .category(SpendingCategory.RESTAURANTS)
                    .type(CreditCardTransaction.TransactionType.PURCHASE)
                    .status(CreditCardTransaction.TransactionStatus.POSTED)
                    .currency("EUR")
                    .build());
            txns.add(CreditCardTransaction.builder()
                    .transactionId("TXN-003")
                    .cardId("card-gold-4821")
                    .transactionDate(LocalDateTime.now().minusDays(5))
                    .amount(new BigDecimal("-345.00"))
                    .merchantName("MediaMarkt")
                    .category(SpendingCategory.SHOPPING)
                    .type(CreditCardTransaction.TransactionType.PURCHASE)
                    .status(CreditCardTransaction.TransactionStatus.POSTED)
                    .currency("EUR")
                    .build());
            transactions.put("card-gold-4821", txns);

            // Limits
            limits.put("card-gold-4821", CreditCardLimit.builder()
                    .cardId("card-gold-4821")
                    .creditLimit(new BigDecimal("10000.00"))
                    .availableCredit(new BigDecimal("8754.50"))
                    .currentBalance(new BigDecimal("1245.50"))
                    .increaseEligible(true)
                    .maxIncreaseAmount(new BigDecimal("5000.00"))
                    .currency("EUR")
                    .build());

            // Rewards
            rewards.put("card-gold-4821", CreditCardRewards.builder()
                    .cardId("card-gold-4821")
                    .programName("Miles & More")
                    .rewardsType(CreditCardRewards.RewardsType.MILES)
                    .currentBalance(new BigDecimal("12450"))
                    .earnedThisMonth(new BigDecimal("845"))
                    .earnedThisYear(new BigDecimal("8920"))
                    .expiryDate(LocalDate.now().plusDays(60))
                    .expiringAmount(new BigDecimal("2000"))
                    .lastUpdated(LocalDate.now())
                    .build());
        }

        @Override
        public List<CreditCard> getCards(String customerId) {
            return cards.values().stream()
                    .filter(c -> customerId.equals(c.getCustomerId()))
                    .toList();
        }

        @Override
        public Optional<CreditCard> getCard(String cardId) {
            return Optional.ofNullable(cards.get(cardId));
        }

        @Override
        public Optional<CreditCardBalance> getBalance(String cardId) {
            return Optional.ofNullable(balances.get(cardId));
        }

        @Override
        public List<CreditCardTransaction> getTransactions(
                String cardId, LocalDate startDate, LocalDate endDate, int limit, int offset) {
            return transactions.getOrDefault(cardId, List.of()).stream()
                    .filter(t -> {
                        LocalDate txDate = t.getTransactionDate().toLocalDate();
                        return !txDate.isBefore(startDate) && !txDate.isAfter(endDate);
                    })
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<CreditCardStatement> getStatement(String cardId, int year, int month) {
            List<CreditCardTransaction> txns = transactions.getOrDefault(cardId, List.of());
            CreditCardBalance balance = balances.get(cardId);
            
            return Optional.of(CreditCardStatement.builder()
                    .statementId("STMT-" + cardId + "-" + year + "-" + month)
                    .cardId(cardId)
                    .periodStart(LocalDate.of(year, month, 1))
                    .periodEnd(LocalDate.of(year, month, 1).plusMonths(1).minusDays(1))
                    .openingBalance(BigDecimal.ZERO)
                    .closingBalance(balance != null ? balance.getCurrentBalance() : BigDecimal.ZERO)
                    .purchasesTotal(new BigDecimal("480.40"))
                    .paymentsTotal(BigDecimal.ZERO)
                    .feesTotal(BigDecimal.ZERO)
                    .interestTotal(BigDecimal.ZERO)
                    .transactionCount(txns.size())
                    .transactions(txns)
                    .currency("EUR")
                    .build());
        }

        @Override
        public Optional<CreditCardLimit> getLimit(String cardId) {
            return Optional.ofNullable(limits.get(cardId));
        }

        @Override
        public Optional<CreditCardRewards> getRewards(String cardId) {
            return Optional.ofNullable(rewards.get(cardId));
        }

        @Override
        public List<CreditCardTransaction> getTransactionsByCategory(
                String cardId, SpendingCategory category, LocalDate startDate, LocalDate endDate) {
            return getTransactions(cardId, startDate, endDate, 100, 0).stream()
                    .filter(t -> t.getCategory() == category)
                    .toList();
        }
    }
}
