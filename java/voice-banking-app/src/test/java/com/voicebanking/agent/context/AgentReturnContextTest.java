package com.voicebanking.agent.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentReturnContext.
 */
class AgentReturnContextTest {
    
    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {
        
        @Test
        void shouldCreateCompletedContext() {
            Map<String, Object> data = Map.of("balance", 500);
            
            AgentReturnContext context = AgentReturnContext.completed(data);
            
            assertEquals(AgentReturnContext.ReturnReason.COMPLETED, context.returnReason());
            assertTrue(context.isSuccess());
            assertFalse(context.isError());
            assertFalse(context.needsHumanIntervention());
            assertEquals(500, context.resultData().get("balance"));
            assertNull(context.targetIntent());
        }
        
        @Test
        void shouldCreateCompletedWithConversationContext() {
            Map<String, Object> data = Map.of("result", "done");
            Map<String, Object> convCtx = Map.of("turn", 3);
            
            AgentReturnContext context = AgentReturnContext.completed(data, convCtx);
            
            assertTrue(context.isSuccess());
            assertEquals(3, context.conversationContext().get("turn"));
        }
        
        @Test
        void shouldCreateNeedsHandoverContext() {
            AgentReturnContext context = AgentReturnContext.needsHandover("Complex dispute");
            
            assertEquals(AgentReturnContext.ReturnReason.NEEDS_HANDOVER, context.returnReason());
            assertTrue(context.needsHumanIntervention());
            assertFalse(context.isSuccess());
            assertEquals("Complex dispute", context.resultData().get("handoverReason"));
        }
        
        @Test
        void shouldCreateOutOfScopeContext() {
            AgentReturnContext context = AgentReturnContext.outOfScope("location-services");
            
            assertEquals(AgentReturnContext.ReturnReason.OUT_OF_SCOPE, context.returnReason());
            assertEquals("location-services", context.targetIntent());
            assertFalse(context.isSuccess());
        }
        
        @Test
        void shouldCreateErrorContext() {
            AgentReturnContext context = AgentReturnContext.error("Connection timeout");
            
            assertEquals(AgentReturnContext.ReturnReason.ERROR, context.returnReason());
            assertTrue(context.isError());
            assertFalse(context.isSuccess());
            assertEquals("Connection timeout", context.resultData().get("error"));
        }
        
        @Test
        void shouldCreateUserCancelledContext() {
            AgentReturnContext context = AgentReturnContext.userCancelled();
            
            assertEquals(AgentReturnContext.ReturnReason.USER_CANCELLED, context.returnReason());
            assertFalse(context.isSuccess());
            assertFalse(context.isError());
        }
    }
    
    @Nested
    @DisplayName("Return Reason Enum")
    class ReturnReasonEnum {
        
        @Test
        void shouldHaveAllExpectedReasons() {
            AgentReturnContext.ReturnReason[] reasons = AgentReturnContext.ReturnReason.values();
            
            assertEquals(5, reasons.length);
            assertNotNull(AgentReturnContext.ReturnReason.valueOf("COMPLETED"));
            assertNotNull(AgentReturnContext.ReturnReason.valueOf("NEEDS_HANDOVER"));
            assertNotNull(AgentReturnContext.ReturnReason.valueOf("OUT_OF_SCOPE"));
            assertNotNull(AgentReturnContext.ReturnReason.valueOf("ERROR"));
            assertNotNull(AgentReturnContext.ReturnReason.valueOf("USER_CANCELLED"));
        }
    }
    
    @Nested
    @DisplayName("Status Checks")
    class StatusChecks {
        
        @Test
        void shouldIdentifySuccessCorrectly() {
            assertTrue(AgentReturnContext.completed(Map.of()).isSuccess());
            assertFalse(AgentReturnContext.error("err").isSuccess());
            assertFalse(AgentReturnContext.needsHandover("reason").isSuccess());
            assertFalse(AgentReturnContext.outOfScope("intent").isSuccess());
            assertFalse(AgentReturnContext.userCancelled().isSuccess());
        }
        
        @Test
        void shouldIdentifyErrorCorrectly() {
            assertFalse(AgentReturnContext.completed(Map.of()).isError());
            assertTrue(AgentReturnContext.error("err").isError());
            assertFalse(AgentReturnContext.needsHandover("reason").isError());
        }
        
        @Test
        void shouldIdentifyHandoverCorrectly() {
            assertFalse(AgentReturnContext.completed(Map.of()).needsHumanIntervention());
            assertFalse(AgentReturnContext.error("err").needsHumanIntervention());
            assertTrue(AgentReturnContext.needsHandover("reason").needsHumanIntervention());
        }
    }
}
