package com.voicebanking.controller;

import com.voicebanking.agent.banking.domain.Account;
import com.voicebanking.agent.banking.domain.Balance;
import com.voicebanking.agent.banking.domain.Transaction;
import com.voicebanking.agent.banking.service.BankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Account Controller
 * 
 * <p>Direct API endpoints for banking operations (for testing/debugging).</p>
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AccountController {
    
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);
    
    private final BankingService bankingService;
    
    public AccountController(BankingService bankingService) {
        this.bankingService = bankingService;
    }
    
    /**
     * Get all accounts
     * 
     * GET /api/accounts
     */
    @GetMapping("/accounts")
    public ResponseEntity<List<Account>> getAccounts() {
        log.info("GET /api/accounts");
        return ResponseEntity.ok(bankingService.getAccounts());
    }
    
    /**
     * Get account balance
     * 
     * GET /api/accounts/{id}/balance
     */
    @GetMapping("/accounts/{id}/balance")
    public ResponseEntity<Balance> getBalance(@PathVariable String id) {
        log.info("GET /api/accounts/{}/balance", id);
        return bankingService.getBalance(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get account transactions
     * 
     * GET /api/accounts/{id}/transactions?limit=10
     */
    @GetMapping("/accounts/{id}/transactions")
    public ResponseEntity<List<Transaction>> getTransactions(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        
        log.info("GET /api/accounts/{}/transactions?limit={}", id, limit);
        List<Transaction> transactions = bankingService.getTransactions(id, limit);
        return ResponseEntity.ok(transactions);
    }
}
