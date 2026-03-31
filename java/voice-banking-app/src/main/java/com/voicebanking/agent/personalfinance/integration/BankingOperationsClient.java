package com.voicebanking.agent.personalfinance.integration;

import com.voicebanking.agent.AgentRegistry;
import com.voicebanking.agent.banking.domain.Account;
import com.voicebanking.agent.banking.domain.Transaction;
import com.voicebanking.integration.bfa.BfaDto.AccountDto;
import com.voicebanking.integration.bfa.BfaDto.TransactionDto;
import com.voicebanking.integration.bfa.BfaResourceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Integration client for banking operations.
 * 
 * <p>This client supports two modes:</p>
 * <ul>
 *   <li><b>BFA Resource Mode</b> (default): Uses typed REST client to call bfa-service-resource</li>
 *   <li><b>Legacy Mode</b>: Uses AgentRegistry.executeTool() for backward compatibility</li>
 * </ul>
 * 
 * <p>Set {@code bfa.resource.enabled=true} to use the new typed REST client.</p>
 *
 * @author Augment Agent
 * @since 2026-01-25
 * @modified 2026-02-02 - Added BfaResourceClient integration
 */
@Component
public class BankingOperationsClient {
    private static final Logger log = LoggerFactory.getLogger(BankingOperationsClient.class);

    private final AgentRegistry agentRegistry;
    private final BfaResourceClient bfaClient;
    private final boolean useBfaResource;

    public BankingOperationsClient(
            @Lazy AgentRegistry agentRegistry,
            BfaResourceClient bfaClient,
            @Value("${bfa.resource.enabled:true}") boolean useBfaResource) {
        this.agentRegistry = agentRegistry;
        this.bfaClient = bfaClient;
        this.useBfaResource = useBfaResource;
        log.info("BankingOperationsClient initialized with BFA resource mode: {}", useBfaResource);
    }

    /**
     * List all accounts for the current user.
     *
     * @return list of accounts
     */
    public List<Account> listAccounts() {
        if (useBfaResource) {
            return listAccountsViaBfa();
        }
        return listAccountsViaAgentRegistry();
    }

    /**
     * Query transactions for an account.
     *
     * @param accountId account identifier
     * @param dateFrom start date (inclusive)
     * @param dateTo end date (inclusive)
     * @param limit maximum number of transactions
     * @return list of transactions
     */
    public List<Transaction> queryTransactions(String accountId, Instant dateFrom, Instant dateTo, Integer limit) {
        if (useBfaResource) {
            return queryTransactionsViaBfa(accountId, dateFrom, dateTo, limit);
        }
        return queryTransactionsViaAgentRegistry(accountId, dateFrom, dateTo, limit);
    }

    // ========================================
    // BFA Resource Client Methods (New)
    // ========================================

    private List<Account> listAccountsViaBfa() {
        log.debug("Listing accounts via BFA resource client");
        try {
            List<AccountDto> dtos = bfaClient.listAccounts();
            return dtos.stream()
                    .map(this::mapToAccount)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to list accounts via BFA: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<Transaction> queryTransactionsViaBfa(String accountId, Instant dateFrom, Instant dateTo, Integer limit) {
        log.debug("Querying transactions via BFA resource client for account: {}", accountId);
        try {
            List<TransactionDto> dtos = bfaClient.getAccountTransactions(
                    accountId, dateFrom, dateTo, limit != null ? limit : 100);
            return dtos.stream()
                    .map(this::mapToTransaction)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to query transactions via BFA: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private Account mapToAccount(AccountDto dto) {
        return new Account(
                dto.id(),
                mapAccountType(dto.type()),
                dto.name(),
                dto.currency(),
                dto.lastFour()
        );
    }

    private Account.AccountType mapAccountType(AccountDto.AccountType type) {
        return switch (type) {
            case CHECKING -> Account.AccountType.CHECKING;
            case SAVINGS -> Account.AccountType.SAVINGS;
            case MONEY_MARKET, CERTIFICATE_OF_DEPOSIT -> Account.AccountType.SAVINGS;
        };
    }

    private Transaction mapToTransaction(TransactionDto dto) {
        return new Transaction(
                dto.id(),
                dto.resourceId(),
                dto.timestamp(),
                dto.description() != null ? dto.description() : dto.merchantName(),
                dto.amount(),
                dto.currency(),
                mapTransactionType(dto.type())
        );
    }

    private Transaction.TransactionType mapTransactionType(TransactionDto.TransactionType type) {
        return switch (type) {
            case DEBIT -> Transaction.TransactionType.DEBIT;
            case CREDIT -> Transaction.TransactionType.CREDIT;
        };
    }

    // ========================================
    // Legacy AgentRegistry Methods (Deprecated)
    // ========================================

    /**
     * @deprecated Use {@link #listAccountsViaBfa()} via bfa.resource.enabled=true
     */
    @Deprecated(since = "2026-02-02", forRemoval = true)
    private List<Account> listAccountsViaAgentRegistry() {
        log.debug("Listing accounts via AgentRegistry (legacy mode)");
        Map<String, Object> result = agentRegistry.executeTool("listAccounts", Map.of());
        Object accountsObj = result.get("accounts");
        if (accountsObj instanceof List<?> list) {
            List<Account> accounts = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Account account) {
                    accounts.add(account);
                }
            }
            return accounts;
        }
        return List.of();
    }

    /**
     * @deprecated Use {@link #queryTransactionsViaBfa(String, Instant, Instant, Integer)} via bfa.resource.enabled=true
     */
    @Deprecated(since = "2026-02-02", forRemoval = true)
    private List<Transaction> queryTransactionsViaAgentRegistry(String accountId, Instant dateFrom, Instant dateTo, Integer limit) {
        log.debug("Querying transactions via AgentRegistry (legacy mode)");
        Map<String, Object> result = agentRegistry.executeTool("queryTransactions", Map.of(
                "accountId", accountId,
                "dateFrom", dateFrom != null ? dateFrom.toString() : null,
                "dateTo", dateTo != null ? dateTo.toString() : null,
                "limit", limit != null ? limit : 100
        ));

        Object txObj = result.get("transactions");
        if (txObj instanceof List<?> list) {
            List<Transaction> transactions = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Transaction transaction) {
                    transactions.add(transaction);
                }
            }
            return transactions;
        }

        log.warn("No transactions returned for account {}", accountId);
        return List.of();
    }
}
