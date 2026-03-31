package com.voicebanking.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * PolicyGateService Unit Tests
 */
@SpringBootTest
@ActiveProfiles("local")
class PolicyGateServiceTest {
    
    @Autowired
    private PolicyGateService policyGate;
    
    @Test
    void shouldAllowGetBalanceTool() {
        var result = policyGate.evaluateToolCall("getBalance", true);
        
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }
    
    @Test
    void shouldAllowListAccountsTool() {
        var result = policyGate.evaluateToolCall("listAccounts", true);
        
        assertThat(result.allowed()).isTrue();
    }
    
    @Test
    void shouldAllowQueryTransactionsTool() {
        var result = policyGate.evaluateToolCall("queryTransactions", true);
        
        assertThat(result.allowed()).isTrue();
    }
    
    @Test
    void shouldBlockTransferTool() {
        var result = policyGate.evaluateToolCall("transfer", true);
        
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason())
            .contains("not in the allowed list")
            .contains("read-only");
    }
    
    @Test
    void shouldBlockPaymentTool() {
        var result = policyGate.evaluateToolCall("makePayment", true);
        
        assertThat(result.allowed()).isFalse();
    }
    
    @Test
    void shouldIdentifyBlockedIntent() {
        assertThat(policyGate.isIntentBlocked("transfer")).isTrue();
        assertThat(policyGate.isIntentBlocked("payment")).isTrue();
        assertThat(policyGate.isIntentBlocked("send_money")).isTrue();
        assertThat(policyGate.isIntentBlocked("delete_account")).isTrue();
    }
    
    @Test
    void shouldAllowReadOnlyIntents() {
        assertThat(policyGate.isIntentBlocked("balance_inquiry")).isFalse();
        assertThat(policyGate.isIntentBlocked("list_accounts")).isFalse();
    }
}
