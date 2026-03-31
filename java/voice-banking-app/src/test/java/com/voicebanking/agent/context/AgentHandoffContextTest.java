package com.voicebanking.agent.context;

import com.voicebanking.domain.dto.OrchestratorRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentHandoffContext.
 */
class AgentHandoffContextTest {
    
    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {
        
        @Test
        void shouldCreateFromOrchestratorRequest() {
            OrchestratorRequest request = new OrchestratorRequest(
                null,
                "Show my balance",
                "session-123",
                true
            );
            
            AgentHandoffContext handoff = AgentHandoffContext.from(request, "getBalance");
            
            assertEquals("session-123", handoff.sessionId());
            assertTrue(handoff.consentGranted());
            assertEquals("getBalance", handoff.originalTool());
            assertNotNull(handoff.correlationId());
            assertNull(handoff.userId());
            assertNull(handoff.legitimationToken());
            assertTrue(handoff.conversationContext().isEmpty());
        }
        
        @Test
        void shouldCreateWithExplicitUserId() {
            OrchestratorRequest request = new OrchestratorRequest(
                null,
                "Show my balance",
                "session-456",
                false
            );
            
            AgentHandoffContext handoff = AgentHandoffContext.withUserId(
                "user-123", 
                request, 
                "listAccounts"
            );
            
            assertEquals("user-123", handoff.userId());
            assertEquals("session-456", handoff.sessionId());
            assertFalse(handoff.consentGranted());
            assertEquals("listAccounts", handoff.originalTool());
        }
    }
    
    @Nested
    @DisplayName("Immutable Transformations")
    class ImmutableTransformations {
        
        @Test
        void shouldAddLegitimationToken() {
            AgentHandoffContext original = new AgentHandoffContext(
                "user-1",
                "session-1",
                "corr-1",
                true,
                null,
                "getBalance",
                Map.of()
            );
            
            assertFalse(original.hasLegitimation());
            
            AgentHandoffContext withToken = original.withLegitimation("token-123");
            
            // Original unchanged
            assertNull(original.legitimationToken());
            assertFalse(original.hasLegitimation());
            
            // New context has token
            assertEquals("token-123", withToken.legitimationToken());
            assertTrue(withToken.hasLegitimation());
            
            // Other fields unchanged
            assertEquals(original.userId(), withToken.userId());
            assertEquals(original.sessionId(), withToken.sessionId());
        }
        
        @Test
        void shouldAddConversationContext() {
            AgentHandoffContext original = new AgentHandoffContext(
                "user-1",
                "session-1",
                "corr-1",
                true,
                null,
                "getBalance",
                Map.of("existing", "value")
            );
            
            AgentHandoffContext withContext = original.withAdditionalContext(
                Map.of("newKey", "newValue")
            );
            
            // Original unchanged
            assertEquals(1, original.conversationContext().size());
            
            // New context has merged values
            assertEquals(2, withContext.conversationContext().size());
            assertEquals("value", withContext.conversationContext().get("existing"));
            assertEquals("newValue", withContext.conversationContext().get("newKey"));
        }
    }
    
    @Nested
    @DisplayName("Legitimation Check")
    class LegitimationCheck {
        
        @Test
        void shouldReturnFalseForNullToken() {
            AgentHandoffContext handoff = new AgentHandoffContext(
                null, "session", "corr", true, null, "tool", Map.of()
            );
            
            assertFalse(handoff.hasLegitimation());
        }
        
        @Test
        void shouldReturnFalseForBlankToken() {
            AgentHandoffContext handoff = new AgentHandoffContext(
                null, "session", "corr", true, "   ", "tool", Map.of()
            );
            
            assertFalse(handoff.hasLegitimation());
        }
        
        @Test
        void shouldReturnTrueForValidToken() {
            AgentHandoffContext handoff = new AgentHandoffContext(
                null, "session", "corr", true, "valid-token", "tool", Map.of()
            );
            
            assertTrue(handoff.hasLegitimation());
        }
    }
}
