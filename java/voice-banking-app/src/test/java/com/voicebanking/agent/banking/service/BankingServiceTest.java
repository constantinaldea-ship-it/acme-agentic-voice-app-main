package com.voicebanking.agent.banking.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * BankingService Unit Tests
 */
@SpringBootTest
@ActiveProfiles("local")
class BankingServiceTest {
    
    @Autowired
    private BankingService bankingService;
    
    @Test
    void shouldReturnThreeAccounts() {
        var accounts = bankingService.getAccounts();
        
        assertThat(accounts).hasSize(3);
        assertThat(accounts)
            .extracting("id")
            .contains("acc-checking-001", "acc-savings-001", "acc-card-001");
    }
    
    @Test
    void shouldReturnBalanceForCheckingAccount() {
        var balance = bankingService.getBalance("acc-checking-001");
        
        assertThat(balance).isPresent();
        assertThat(balance.get().accountId()).isEqualTo("acc-checking-001");
        assertThat(balance.get().currency()).isEqualTo("USD");
        assertThat(balance.get().available()).isNotNull();
    }
    
    @Test
    void shouldReturnEmptyForNonExistentAccount() {
        var balance = bankingService.getBalance("acc-nonexistent");
        
        assertThat(balance).isEmpty();
    }
    
    @Test
    void shouldReturnTransactionsForCheckingAccount() {
        var transactions = bankingService.getTransactions("acc-checking-001", null);
        
        assertThat(transactions).isNotEmpty();
        assertThat(transactions).hasSizeGreaterThanOrEqualTo(5);
    }
    
    @Test
    void shouldLimitTransactions() {
        var transactions = bankingService.getTransactions("acc-checking-001", 2);
        
        assertThat(transactions).hasSize(2);
    }
    
    @Test
    void shouldFilterTransactionsByText() {
        var transactions = bankingService.queryTransactions(
            "acc-checking-001",
            null,
            null,
            "Starbucks",
            null
        );
        
        assertThat(transactions).isNotEmpty();
        assertThat(transactions)
            .allMatch(t -> t.description().contains("Starbucks"));
    }
}
