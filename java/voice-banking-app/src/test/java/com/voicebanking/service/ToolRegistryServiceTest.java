package com.voicebanking.service;

import com.voicebanking.agent.banking.domain.Balance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * ToolRegistryService Unit Tests
 */
@SpringBootTest
@ActiveProfiles("local")
class ToolRegistryServiceTest {
    
    @Autowired
    private ToolRegistryService toolRegistry;
    
    @Test
    void shouldHaveFourTools() {
        var toolNames = toolRegistry.getToolNames();
        
        assertThat(toolNames).containsExactlyInAnyOrder(
            "getBalance",
            "listAccounts",
            "queryTransactions",
            "findNearbyBranches"
        );
    }
    
    @Test
    void shouldExecuteGetBalanceTool() {
        var result = toolRegistry.executeTool("getBalance", Map.of(
            "accountId", "acc-checking-001"
        ));
        
        assertThat(result).isInstanceOf(Balance.class);
        Balance balance = (Balance) result;
        assertThat(balance.accountId()).isEqualTo("acc-checking-001");
    }
    
    @Test
    void shouldExecuteGetBalanceWithDefaultAccount() {
        var result = toolRegistry.executeTool("getBalance", Map.of());
        
        assertThat(result).isInstanceOf(Balance.class);
        Balance balance = (Balance) result;
        assertThat(balance.accountId()).isEqualTo("acc-checking-001");  // Default
    }
    
    @Test
    void shouldExecuteListAccountsTool() {
        var result = toolRegistry.executeTool("listAccounts", Map.of());
        
        assertThat(result).isInstanceOf(List.class);
        List<?> accounts = (List<?>) result;
        assertThat(accounts).hasSize(3);
    }
    
    @Test
    void shouldExecuteQueryTransactionsTool() {
        var result = toolRegistry.executeTool("queryTransactions", Map.of(
            "accountId", "acc-checking-001",
            "limit", 5
        ));
        
        assertThat(result).isInstanceOf(List.class);
        List<?> transactions = (List<?>) result;
        assertThat(transactions).hasSizeLessThanOrEqualTo(5);
    }
    
    @Test
    void shouldThrowExceptionForUnknownTool() {
        assertThatThrownBy(() -> 
            toolRegistry.executeTool("unknownTool", Map.of())
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown tool");
    }
    
    @Test
    void shouldThrowExceptionForInvalidAccountId() {
        assertThatThrownBy(() -> 
            toolRegistry.executeTool("getBalance", Map.of("accountId", "invalid"))
        )
            .isInstanceOf(RuntimeException.class);
    }
}
