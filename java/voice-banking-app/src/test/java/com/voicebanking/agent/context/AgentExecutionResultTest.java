package com.voicebanking.agent.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentExecutionResult.
 */
class AgentExecutionResultTest {
    
    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {
        
        @Test
        void shouldCreateCompletedResult() {
            Map<String, Object> data = Map.of("balance", 1000.50);
            
            AgentExecutionResult result = AgentExecutionResult.completed(data);
            
            assertTrue(result.isSuccess());
            assertFalse(result.isError());
            assertFalse(result.needsHumanIntervention());
            assertEquals(AgentReturnContext.ReturnReason.COMPLETED, result.returnReason());
            assertEquals(1000.50, result.resultData().get("balance"));
        }
        
        @Test
        void shouldCreateCompletedResultWithConversationContext() {
            Map<String, Object> data = Map.of("result", "success");
            Map<String, Object> context = Map.of("lastIntent", "balance_inquiry");
            
            AgentExecutionResult result = AgentExecutionResult.completed(data, context);
            
            assertTrue(result.isSuccess());
            assertEquals("balance_inquiry", result.returnContext().conversationContext().get("lastIntent"));
        }
        
        @Test
        void shouldCreateHandoverResult() {
            AgentExecutionResult result = AgentExecutionResult.needsHandover("Complex query requires human assistance");
            
            assertFalse(result.isSuccess());
            assertFalse(result.isError());
            assertTrue(result.needsHumanIntervention());
            assertEquals(AgentReturnContext.ReturnReason.NEEDS_HANDOVER, result.returnReason());
            assertEquals(true, result.resultData().get("handoverRequired"));
        }
        
        @Test
        void shouldCreateOutOfScopeResult() {
            AgentExecutionResult result = AgentExecutionResult.outOfScope("banking-operations");
            
            assertFalse(result.isSuccess());
            assertEquals(AgentReturnContext.ReturnReason.OUT_OF_SCOPE, result.returnReason());
            assertEquals(true, result.resultData().get("outOfScope"));
            assertEquals("banking-operations", result.resultData().get("suggestedIntent"));
        }
        
        @Test
        void shouldCreateErrorResult() {
            AgentExecutionResult result = AgentExecutionResult.error("Service unavailable");
            
            assertFalse(result.isSuccess());
            assertTrue(result.isError());
            assertEquals(AgentReturnContext.ReturnReason.ERROR, result.returnReason());
            assertEquals("Service unavailable", result.resultData().get("message"));
        }
        
        @Test
        void shouldCreateUserCancelledResult() {
            AgentExecutionResult result = AgentExecutionResult.userCancelled();
            
            assertFalse(result.isSuccess());
            assertEquals(AgentReturnContext.ReturnReason.USER_CANCELLED, result.returnReason());
            assertEquals(true, result.resultData().get("cancelled"));
        }
    }
    
    @Nested
    @DisplayName("Status Checks")
    class StatusChecks {
        
        @Test
        void shouldReturnCorrectStatusForCompleted() {
            AgentExecutionResult result = AgentExecutionResult.completed(Map.of());
            
            assertTrue(result.isSuccess());
            assertFalse(result.isError());
            assertFalse(result.needsHumanIntervention());
        }
        
        @Test
        void shouldReturnCorrectStatusForError() {
            AgentExecutionResult result = AgentExecutionResult.error("error");
            
            assertFalse(result.isSuccess());
            assertTrue(result.isError());
            assertFalse(result.needsHumanIntervention());
        }
        
        @Test
        void shouldReturnCorrectStatusForHandover() {
            AgentExecutionResult result = AgentExecutionResult.needsHandover("reason");
            
            assertFalse(result.isSuccess());
            assertFalse(result.isError());
            assertTrue(result.needsHumanIntervention());
        }
    }
}
