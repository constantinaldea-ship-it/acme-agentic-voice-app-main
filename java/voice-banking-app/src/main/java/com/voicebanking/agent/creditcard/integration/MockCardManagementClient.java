package com.voicebanking.agent.creditcard.integration;

import com.voicebanking.agent.creditcard.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of CardManagementClient for development and testing.
 * Provides realistic sample data for credit card operations.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Component
@Profile("!production")
public class MockCardManagementClient implements CardManagementClient {
    private static final Logger log = LoggerFactory.getLogger(MockCardManagementClient.class);

    private final Map<String, CreditCard> cardsById = new ConcurrentHashMap<>();
    private final Map<String, List<CreditCard>> cardsByCustomer = new ConcurrentHashMap<>();
    private final Map<String, CreditCardBalance> balances = new ConcurrentHashMap<>();
    private final Map<String, List<CreditCardTransaction>> transactions = new ConcurrentHashMap<>();
    private final Map<String, CreditCardLimit> limits = new ConcurrentHashMap<>();
    private final Map<String, CreditCardRewards> rewards = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        initializeMockData();
        log.info("MockCardManagementClient initialized with {} cards", cardsById.size());
    }

    private void initializeMockData() {
        // Customer CUST-001: Gold MasterCard
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
                .activationDate(LocalDate.of(2023, 3, 15))
                .build();

        // Customer CUST-001: Platinum Visa
        CreditCard platinumCard = CreditCard.builder()
                .cardId("card-platinum-9456")
                .customerId("CUST-001")
                .cardType("Platinum")
                .cardNetwork("Visa")
                .maskedCardNumber("****-****-****-9456")
                .cardholderName("Max Mustermann")
                .expiryDate(LocalDate.of(2027, 12, 31))
                .status(CreditCard.CardStatus.ACTIVE)
                .rewardsProgramId("cashback-plus")
                .activationDate(LocalDate.of(2024, 1, 10))
                .build();

        // Customer CUST-002: Standard Visa
        CreditCard standardCard = CreditCard.builder()
                .cardId("card-standard-7123")
                .customerId("CUST-002")
                .cardType("Standard")
                .cardNetwork("Visa")
                .maskedCardNumber("****-****-****-7123")
                .cardholderName("Erika Musterfrau")
                .expiryDate(LocalDate.of(2026, 9, 30))
                .status(CreditCard.CardStatus.ACTIVE)
                .rewardsProgramId("basic-rewards")
                .activationDate(LocalDate.of(2022, 9, 1))
                .build();

        // Register cards
        registerCard(goldCard);
        registerCard(platinumCard);
        registerCard(standardCard);

        // Initialize balances
        initializeBalances();

        // Initialize transactions
        initializeTransactions();

        // Initialize limits
        initializeLimits();

        // Initialize rewards
        initializeRewards();
    }

    private void registerCard(CreditCard card) {
        cardsById.put(card.getCardId(), card);
        cardsByCustomer.computeIfAbsent(card.getCustomerId(), k -> new ArrayList<>()).add(card);
    }

    private void initializeBalances() {
        balances.put("card-gold-4821", CreditCardBalance.builder()
                .cardId("card-gold-4821")
                .currentBalance(new BigDecimal("1245.50"))
                .availableCredit(new BigDecimal("8754.50"))
                .creditLimit(new BigDecimal("10000.00"))
                .minimumPayment(new BigDecimal("50.00"))
                .paymentDueDate(LocalDate.now().plusDays(20))
                .lastStatementDate(LocalDate.now().minusDays(10))
                .lastPaymentAmount(new BigDecimal("500.00"))
                .lastPaymentDate(LocalDate.now().minusDays(25))
                .currency("EUR")
                .build());

        balances.put("card-platinum-9456", CreditCardBalance.builder()
                .cardId("card-platinum-9456")
                .currentBalance(new BigDecimal("3892.75"))
                .availableCredit(new BigDecimal("21107.25"))
                .creditLimit(new BigDecimal("25000.00"))
                .minimumPayment(new BigDecimal("150.00"))
                .paymentDueDate(LocalDate.now().plusDays(15))
                .lastStatementDate(LocalDate.now().minusDays(15))
                .lastPaymentAmount(new BigDecimal("1000.00"))
                .lastPaymentDate(LocalDate.now().minusDays(30))
                .currency("EUR")
                .build());

        balances.put("card-standard-7123", CreditCardBalance.builder()
                .cardId("card-standard-7123")
                .currentBalance(new BigDecimal("567.20"))
                .availableCredit(new BigDecimal("2432.80"))
                .creditLimit(new BigDecimal("3000.00"))
                .minimumPayment(new BigDecimal("25.00"))
                .paymentDueDate(LocalDate.now().plusDays(5))
                .lastStatementDate(LocalDate.now().minusDays(25))
                .lastPaymentAmount(new BigDecimal("200.00"))
                .lastPaymentDate(LocalDate.now().minusDays(20))
                .currency("EUR")
                .build());
    }

    private void initializeTransactions() {
        // Gold card transactions
        List<CreditCardTransaction> goldTransactions = new ArrayList<>();
        goldTransactions.add(createTransaction("TXN-001", "card-gold-4821", 
                LocalDateTime.now().minusDays(2), new BigDecimal("-89.90"), 
                "Amazon.de", "Munich", "DE", SpendingCategory.SHOPPING));
        goldTransactions.add(createTransaction("TXN-002", "card-gold-4821", 
                LocalDateTime.now().minusDays(3), new BigDecimal("-45.50"), 
                "Restaurant Atlantis", "Munich", "DE", SpendingCategory.RESTAURANTS));
        goldTransactions.add(createTransaction("TXN-003", "card-gold-4821", 
                LocalDateTime.now().minusDays(5), new BigDecimal("-345.00"), 
                "MediaMarkt", "Munich", "DE", SpendingCategory.SHOPPING));
        goldTransactions.add(createTransaction("TXN-004", "card-gold-4821", 
                LocalDateTime.now().minusDays(7), new BigDecimal("-28.50"), 
                "REWE Supermarkt", "Munich", "DE", SpendingCategory.GROCERIES));
        goldTransactions.add(createTransaction("TXN-005", "card-gold-4821", 
                LocalDateTime.now().minusDays(8), new BigDecimal("-65.00"), 
                "Shell Station", "Frankfurt", "DE", SpendingCategory.TRANSPORT));
        goldTransactions.add(createTransaction("TXN-006", "card-gold-4821", 
                LocalDateTime.now().minusDays(10), new BigDecimal("-12.99"), 
                "Netflix", "Online", "US", SpendingCategory.ENTERTAINMENT));
        goldTransactions.add(createTransaction("TXN-007", "card-gold-4821", 
                LocalDateTime.now().minusDays(12), new BigDecimal("-156.80"), 
                "Lufthansa", "Online", "DE", SpendingCategory.TRAVEL));
        goldTransactions.add(createTransaction("TXN-008", "card-gold-4821", 
                LocalDateTime.now().minusDays(15), new BigDecimal("500.00"), 
                "Payment Received", "", "DE", SpendingCategory.OTHER));
        goldTransactions.add(createTransaction("TXN-009", "card-gold-4821", 
                LocalDateTime.now().minusDays(18), new BigDecimal("-78.50"), 
                "Zalando", "Online", "DE", SpendingCategory.SHOPPING));
        goldTransactions.add(createTransaction("TXN-010", "card-gold-4821", 
                LocalDateTime.now().minusDays(20), new BigDecimal("-95.00"), 
                "Starbucks", "Munich", "DE", SpendingCategory.RESTAURANTS));
        transactions.put("card-gold-4821", goldTransactions);

        // Platinum card transactions
        List<CreditCardTransaction> platinumTransactions = new ArrayList<>();
        platinumTransactions.add(createTransaction("TXN-101", "card-platinum-9456", 
                LocalDateTime.now().minusDays(1), new BigDecimal("-425.00"), 
                "Booking.com Hotel", "Vienna", "AT", SpendingCategory.TRAVEL));
        platinumTransactions.add(createTransaction("TXN-102", "card-platinum-9456", 
                LocalDateTime.now().minusDays(4), new BigDecimal("-189.90"), 
                "Apple Store", "Munich", "DE", SpendingCategory.SHOPPING));
        platinumTransactions.add(createTransaction("TXN-103", "card-platinum-9456", 
                LocalDateTime.now().minusDays(6), new BigDecimal("-78.00"), 
                "IKEA", "Munich", "DE", SpendingCategory.SHOPPING));
        platinumTransactions.add(createTransaction("TXN-104", "card-platinum-9456", 
                LocalDateTime.now().minusDays(9), new BigDecimal("-150.00"), 
                "Ticketmaster Concert", "Online", "DE", SpendingCategory.ENTERTAINMENT));
        platinumTransactions.add(createTransaction("TXN-105", "card-platinum-9456", 
                LocalDateTime.now().minusDays(11), new BigDecimal("1000.00"), 
                "Payment Received", "", "DE", SpendingCategory.OTHER));
        transactions.put("card-platinum-9456", platinumTransactions);

        // Standard card transactions
        List<CreditCardTransaction> standardTransactions = new ArrayList<>();
        standardTransactions.add(createTransaction("TXN-201", "card-standard-7123", 
                LocalDateTime.now().minusDays(1), new BigDecimal("-35.50"), 
                "EDEKA", "Berlin", "DE", SpendingCategory.GROCERIES));
        standardTransactions.add(createTransaction("TXN-202", "card-standard-7123", 
                LocalDateTime.now().minusDays(3), new BigDecimal("-22.00"), 
                "Deutsche Bahn", "Berlin", "DE", SpendingCategory.TRANSPORT));
        standardTransactions.add(createTransaction("TXN-203", "card-standard-7123", 
                LocalDateTime.now().minusDays(5), new BigDecimal("-9.99"), 
                "Spotify", "Online", "SE", SpendingCategory.ENTERTAINMENT));
        transactions.put("card-standard-7123", standardTransactions);
    }

    private CreditCardTransaction createTransaction(
            String txnId, String cardId, LocalDateTime date, BigDecimal amount,
            String merchant, String city, String country, SpendingCategory category) {
        
        CreditCardTransaction.TransactionType type = amount.compareTo(BigDecimal.ZERO) > 0
                ? CreditCardTransaction.TransactionType.PAYMENT
                : CreditCardTransaction.TransactionType.PURCHASE;
        
        return CreditCardTransaction.builder()
                .transactionId(txnId)
                .cardId(cardId)
                .transactionDate(date)
                .postingDate(date.plusDays(1))
                .amount(amount)
                .currency("EUR")
                .merchantName(merchant)
                .merchantCity(city)
                .merchantCountry(country)
                .category(category)
                .type(type)
                .status(CreditCardTransaction.TransactionStatus.POSTED)
                .description(merchant)
                .rewardsEarned(amount.abs().multiply(new BigDecimal("0.01")))
                .build();
    }

    private void initializeLimits() {
        limits.put("card-gold-4821", CreditCardLimit.builder()
                .cardId("card-gold-4821")
                .creditLimit(new BigDecimal("10000.00"))
                .availableCredit(new BigDecimal("8754.50"))
                .currentBalance(new BigDecimal("1245.50"))
                .cashAdvanceLimit(new BigDecimal("2000.00"))
                .availableCashAdvance(new BigDecimal("2000.00"))
                .lastLimitChange(LocalDate.now().minusMonths(6))
                .increaseEligible(true)
                .maxIncreaseAmount(new BigDecimal("5000.00"))
                .currency("EUR")
                .build());

        limits.put("card-platinum-9456", CreditCardLimit.builder()
                .cardId("card-platinum-9456")
                .creditLimit(new BigDecimal("25000.00"))
                .availableCredit(new BigDecimal("21107.25"))
                .currentBalance(new BigDecimal("3892.75"))
                .cashAdvanceLimit(new BigDecimal("5000.00"))
                .availableCashAdvance(new BigDecimal("5000.00"))
                .lastLimitChange(LocalDate.now().minusMonths(3))
                .increaseEligible(true)
                .maxIncreaseAmount(new BigDecimal("10000.00"))
                .currency("EUR")
                .build());

        limits.put("card-standard-7123", CreditCardLimit.builder()
                .cardId("card-standard-7123")
                .creditLimit(new BigDecimal("3000.00"))
                .availableCredit(new BigDecimal("2432.80"))
                .currentBalance(new BigDecimal("567.20"))
                .cashAdvanceLimit(new BigDecimal("500.00"))
                .availableCashAdvance(new BigDecimal("500.00"))
                .lastLimitChange(LocalDate.now().minusYears(1))
                .increaseEligible(false)
                .maxIncreaseAmount(BigDecimal.ZERO)
                .currency("EUR")
                .build());
    }

    private void initializeRewards() {
        rewards.put("card-gold-4821", CreditCardRewards.builder()
                .cardId("card-gold-4821")
                .programName("Miles & More")
                .rewardsType(CreditCardRewards.RewardsType.MILES)
                .currentBalance(new BigDecimal("12450"))
                .pendingRewards(new BigDecimal("150"))
                .earnedThisMonth(new BigDecimal("845"))
                .earnedThisYear(new BigDecimal("8920"))
                .redeemedThisYear(new BigDecimal("5000"))
                .expiryDate(LocalDate.now().plusDays(60))
                .expiringAmount(new BigDecimal("2000"))
                .lastUpdated(LocalDate.now())
                .addRedemptionOption(new CreditCardRewards.RedemptionOption(
                        "opt-1", "Flight Upgrade", "Upgrade to Business Class on next flight",
                        new BigDecimal("10000"), new BigDecimal("500")))
                .addRedemptionOption(new CreditCardRewards.RedemptionOption(
                        "opt-2", "Lounge Access", "Single visit to any Star Alliance lounge",
                        new BigDecimal("3000"), new BigDecimal("50")))
                .build());

        rewards.put("card-platinum-9456", CreditCardRewards.builder()
                .cardId("card-platinum-9456")
                .programName("Cashback Plus")
                .rewardsType(CreditCardRewards.RewardsType.CASHBACK)
                .currentBalance(new BigDecimal("89.50"))
                .pendingRewards(new BigDecimal("12.30"))
                .earnedThisMonth(new BigDecimal("28.50"))
                .earnedThisYear(new BigDecimal("245.80"))
                .redeemedThisYear(new BigDecimal("100.00"))
                .expiryDate(null)  // Cashback doesn't expire
                .expiringAmount(BigDecimal.ZERO)
                .lastUpdated(LocalDate.now())
                .build());

        rewards.put("card-standard-7123", CreditCardRewards.builder()
                .cardId("card-standard-7123")
                .programName("Basic Rewards")
                .rewardsType(CreditCardRewards.RewardsType.POINTS)
                .currentBalance(new BigDecimal("1250"))
                .pendingRewards(new BigDecimal("50"))
                .earnedThisMonth(new BigDecimal("120"))
                .earnedThisYear(new BigDecimal("1450"))
                .redeemedThisYear(BigDecimal.ZERO)
                .expiryDate(LocalDate.now().plusMonths(12))
                .expiringAmount(BigDecimal.ZERO)
                .lastUpdated(LocalDate.now())
                .build());
    }

    @Override
    public List<CreditCard> getCards(String customerId) {
        log.debug("Getting cards for customer: {}", customerId);
        return cardsByCustomer.getOrDefault(customerId, List.of());
    }

    @Override
    public Optional<CreditCard> getCard(String cardId) {
        log.debug("Getting card: {}", cardId);
        return Optional.ofNullable(cardsById.get(cardId));
    }

    @Override
    public Optional<CreditCardBalance> getBalance(String cardId) {
        log.debug("Getting balance for card: {}", cardId);
        return Optional.ofNullable(balances.get(cardId));
    }

    @Override
    public List<CreditCardTransaction> getTransactions(
            String cardId, LocalDate startDate, LocalDate endDate, int limit, int offset) {
        log.debug("Getting transactions for card: {}, from {} to {}, limit {}, offset {}", 
                cardId, startDate, endDate, limit, offset);
        
        List<CreditCardTransaction> cardTxns = transactions.getOrDefault(cardId, List.of());
        
        return cardTxns.stream()
                .filter(t -> {
                    LocalDate txDate = t.getTransactionDate().toLocalDate();
                    return !txDate.isBefore(startDate) && !txDate.isAfter(endDate);
                })
                .sorted((a, b) -> b.getTransactionDate().compareTo(a.getTransactionDate()))
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<CreditCardStatement> getStatement(String cardId, int year, int month) {
        log.debug("Getting statement for card: {}, period: {}-{}", cardId, year, month);
        
        LocalDate periodStart = LocalDate.of(year, month, 1);
        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);
        
        List<CreditCardTransaction> txns = getTransactions(cardId, periodStart, periodEnd, 100, 0);
        
        if (txns.isEmpty() && !cardsById.containsKey(cardId)) {
            return Optional.empty();
        }

        BigDecimal purchases = txns.stream()
                .filter(CreditCardTransaction::isPurchase)
                .map(t -> t.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal payments = txns.stream()
                .filter(CreditCardTransaction::isCredit)
                .map(CreditCardTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CreditCardBalance balance = balances.get(cardId);
        
        return Optional.of(CreditCardStatement.builder()
                .statementId("STMT-" + cardId + "-" + year + "-" + month)
                .cardId(cardId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .openingBalance(balance != null ? balance.getCurrentBalance().subtract(purchases).add(payments) : BigDecimal.ZERO)
                .closingBalance(balance != null ? balance.getCurrentBalance() : BigDecimal.ZERO)
                .purchasesTotal(purchases)
                .paymentsTotal(payments)
                .feesTotal(BigDecimal.ZERO)
                .interestTotal(BigDecimal.ZERO)
                .cashAdvancesTotal(BigDecimal.ZERO)
                .minimumPayment(balance != null ? balance.getMinimumPayment() : BigDecimal.ZERO)
                .paymentDueDate(balance != null ? balance.getPaymentDueDate() : periodEnd.plusDays(20))
                .transactionCount(txns.size())
                .transactions(txns)
                .currency("EUR")
                .statementDate(periodEnd)
                .build());
    }

    @Override
    public Optional<CreditCardLimit> getLimit(String cardId) {
        log.debug("Getting limit for card: {}", cardId);
        return Optional.ofNullable(limits.get(cardId));
    }

    @Override
    public Optional<CreditCardRewards> getRewards(String cardId) {
        log.debug("Getting rewards for card: {}", cardId);
        return Optional.ofNullable(rewards.get(cardId));
    }

    @Override
    public List<CreditCardTransaction> getTransactionsByCategory(
            String cardId, SpendingCategory category, LocalDate startDate, LocalDate endDate) {
        log.debug("Getting transactions by category for card: {}, category: {}", cardId, category);
        
        return getTransactions(cardId, startDate, endDate, 100, 0).stream()
                .filter(t -> t.getCategory() == category)
                .collect(Collectors.toList());
    }
}
