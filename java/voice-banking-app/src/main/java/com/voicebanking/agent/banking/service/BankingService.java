package com.voicebanking.agent.banking.service;

import com.voicebanking.agent.banking.domain.Account;
import com.voicebanking.agent.banking.domain.Balance;
import com.voicebanking.agent.banking.domain.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mock Banking Service
 * 
 * <p>In-memory banking service with seeded data for PoC.
 * Provides read-only access to accounts, balances, and transactions.</p>
 * 
 * <p>All data is deterministic and matches the TypeScript PoC for comparison testing.</p>
 */
@Service
public class BankingService {
    
    private static final Logger log = LoggerFactory.getLogger(BankingService.class);
    
    private final Map<String, Account> accounts;
    private final Map<String, Balance> balances;
    private final Map<String, List<Transaction>> transactions;
    
    public BankingService() {
        this.accounts = seedAccounts();
        this.balances = seedBalances();
        this.transactions = seedTransactions();
        log.info("MockBankingService initialized with {} accounts, {} balances, {} transaction sets",
            accounts.size(), balances.size(), transactions.size());
    }
    
    /**
     * Get all accounts
     */
    public List<Account> getAccounts() {
        return new ArrayList<>(accounts.values());
    }
    
    /**
     * Get account by ID
     */
    public Optional<Account> getAccount(String accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }
    
    /**
     * Get balance for account
     */
    public Optional<Balance> getBalance(String accountId) {
        return Optional.ofNullable(balances.get(accountId));
    }
    
    /**
     * Get transactions for account
     * 
     * @param accountId Account ID
     * @param limit Maximum number of transactions (null for all)
     * @return List of transactions, newest first
     */
    public List<Transaction> getTransactions(String accountId, Integer limit) {
        List<Transaction> txns = transactions.getOrDefault(accountId, Collections.emptyList());
        
        // Sort by date descending (newest first)
        List<Transaction> sorted = txns.stream()
            .sorted(Comparator.comparing(Transaction::date).reversed())
            .collect(Collectors.toList());
        
        if (limit != null && limit > 0) {
            return sorted.stream().limit(limit).collect(Collectors.toList());
        }
        
        return sorted;
    }
    
    /**
     * Query transactions with filters
     */
    public List<Transaction> queryTransactions(
            String accountId,
            Instant dateFrom,
            Instant dateTo,
            String textQuery,
            Integer limit) {
        
        List<Transaction> txns = transactions.getOrDefault(accountId, Collections.emptyList());
        
        // Apply filters
        var filtered = txns.stream()
            .filter(t -> dateFrom == null || !t.date().isBefore(dateFrom))
            .filter(t -> dateTo == null || !t.date().isAfter(dateTo))
            .filter(t -> textQuery == null || 
                t.description().toLowerCase().contains(textQuery.toLowerCase()))
            .sorted(Comparator.comparing(Transaction::date).reversed())
            .collect(Collectors.toList());
        
        if (limit != null && limit > 0) {
            return filtered.stream().limit(limit).collect(Collectors.toList());
        }
        
        return filtered;
    }
    
    // ============================================================
    // Seed Data (matches TypeScript PoC)
    // ============================================================
    
    private Map<String, Account> seedAccounts() {
        var accts = new HashMap<String, Account>();
        
        accts.put("acc-checking-001", new Account(
            "acc-checking-001",
            Account.AccountType.CHECKING,
            "Everyday Checking",
            "USD",
            "1234"
        ));
        
        accts.put("acc-savings-001", new Account(
            "acc-savings-001",
            Account.AccountType.SAVINGS,
            "High-Yield Savings",
            "USD",
            "5678"
        ));
        
        accts.put("acc-card-001", new Account(
            "acc-card-001",
            Account.AccountType.CARD,
            "Rewards Card",
            "USD",
            "9012"
        ));
        
        return Collections.unmodifiableMap(accts);
    }
    
    private Map<String, Balance> seedBalances() {
        var bals = new HashMap<String, Balance>();
        var now = Instant.now();
        
        bals.put("acc-checking-001", new Balance(
            "acc-checking-001",
            new BigDecimal("1250.50"),
            new BigDecimal("1250.50"),
            "USD",
            now
        ));
        
        bals.put("acc-savings-001", new Balance(
            "acc-savings-001",
            new BigDecimal("5420.00"),
            new BigDecimal("5420.00"),
            "USD",
            now
        ));
        
        bals.put("acc-card-001", new Balance(
            "acc-card-001",
            new BigDecimal("-342.18"),  // Card balance (negative = owed)
            new BigDecimal("-342.18"),
            "USD",
            now
        ));
        
        return Collections.unmodifiableMap(bals);
    }
    
    private Map<String, List<Transaction>> seedTransactions() {
        var txns = new HashMap<String, List<Transaction>>();
        var now = Instant.now();
        
        // Checking account transactions
        var checkingTxns = new ArrayList<Transaction>();
        checkingTxns.add(new Transaction(
            "txn-001",
            "acc-checking-001",
            now.minus(1, ChronoUnit.DAYS),
            "Starbucks Coffee",
            new BigDecimal("-5.45"),
            "USD",
            Transaction.TransactionType.DEBIT
        ));
        checkingTxns.add(new Transaction(
            "txn-002",
            "acc-checking-001",
            now.minus(2, ChronoUnit.DAYS),
            "Salary Deposit",
            new BigDecimal("2500.00"),
            "USD",
            Transaction.TransactionType.CREDIT
        ));
        checkingTxns.add(new Transaction(
            "txn-003",
            "acc-checking-001",
            now.minus(3, ChronoUnit.DAYS),
            "Grocery Store",
            new BigDecimal("-87.32"),
            "USD",
            Transaction.TransactionType.DEBIT
        ));
        checkingTxns.add(new Transaction(
            "txn-004",
            "acc-checking-001",
            now.minus(5, ChronoUnit.DAYS),
            "Gas Station",
            new BigDecimal("-45.00"),
            "USD",
            Transaction.TransactionType.DEBIT
        ));
        checkingTxns.add(new Transaction(
            "txn-005",
            "acc-checking-001",
            now.minus(7, ChronoUnit.DAYS),
            "Amazon Purchase",
            new BigDecimal("-62.99"),
            "USD",
            Transaction.TransactionType.DEBIT
        ));
        txns.put("acc-checking-001", Collections.unmodifiableList(checkingTxns));
        
        // Savings account transactions
        var savingsTxns = new ArrayList<Transaction>();
        savingsTxns.add(new Transaction(
            "txn-101",
            "acc-savings-001",
            now.minus(1, ChronoUnit.DAYS),
            "Interest Payment",
            new BigDecimal("20.00"),
            "USD",
            Transaction.TransactionType.CREDIT
        ));
        savingsTxns.add(new Transaction(
            "txn-102",
            "acc-savings-001",
            now.minus(30, ChronoUnit.DAYS),
            "Transfer from Checking",
            new BigDecimal("500.00"),
            "USD",
            Transaction.TransactionType.CREDIT
        ));
        txns.put("acc-savings-001", Collections.unmodifiableList(savingsTxns));
        
        // Card transactions
        var cardTxns = new ArrayList<Transaction>();
        cardTxns.add(new Transaction(
            "txn-201",
            "acc-card-001",
            now.minus(1, ChronoUnit.DAYS),
            "Restaurant",
            new BigDecimal("-42.18"),
            "USD",
            Transaction.TransactionType.DEBIT
        ));
        cardTxns.add(new Transaction(
            "txn-202",
            "acc-card-001",
            now.minus(3, ChronoUnit.DAYS),
            "Online Shopping",
            new BigDecimal("-150.00"),
            "USD",
            Transaction.TransactionType.DEBIT
        ));
        cardTxns.add(new Transaction(
            "txn-203",
            "acc-card-001",
            now.minus(5, ChronoUnit.DAYS),
            "Hotel Booking",
            new BigDecimal("-150.00"),
            "USD",
            Transaction.TransactionType.DEBIT
        ));
        txns.put("acc-card-001", Collections.unmodifiableList(cardTxns));
        
        return Collections.unmodifiableMap(txns);
    }
}
