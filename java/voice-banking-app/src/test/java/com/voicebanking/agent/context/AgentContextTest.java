package com.voicebanking.agent.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentContext ThreadLocal functionality.
 */
class AgentContextTest {
    
    @AfterEach
    void cleanup() {
        AgentContext.clear();
    }
    
    @Nested
    @DisplayName("Basic Context Operations")
    class BasicOperations {
        
        @Test
        void shouldCreateContextWithTimestamp() {
            AgentContext context = new AgentContext("test-agent", "testTool", "session-123");
            
            assertEquals("test-agent", context.agentId());
            assertEquals("testTool", context.toolId());
            assertEquals("session-123", context.sessionId());
            assertNotNull(context.startTime());
            assertTrue(context.startTime().isBefore(Instant.now().plusSeconds(1)));
        }
        
        @Test
        void shouldSetAndGetCurrentContext() {
            AgentContext context = new AgentContext("agent-1", "tool-1", "session-1");
            
            assertTrue(AgentContext.getCurrent().isEmpty());
            
            AgentContext.setCurrent(context);
            
            assertTrue(AgentContext.getCurrent().isPresent());
            assertEquals("agent-1", AgentContext.getCurrent().get().agentId());
        }
        
        @Test
        void shouldClearContext() {
            AgentContext context = new AgentContext("agent-1", "tool-1", "session-1");
            AgentContext.setCurrent(context);
            
            assertTrue(AgentContext.getCurrent().isPresent());
            
            AgentContext.clear();
            
            assertTrue(AgentContext.getCurrent().isEmpty());
        }
    }
    
    @Nested
    @DisplayName("Thread Isolation")
    class ThreadIsolation {
        
        @Test
        void shouldIsolateContextBetweenThreads() throws InterruptedException {
            AgentContext mainContext = new AgentContext("main-agent", "mainTool", "main-session");
            AgentContext.setCurrent(mainContext);
            
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Boolean> childHasContext = new AtomicReference<>();
            
            Thread childThread = new Thread(() -> {
                // Child thread should NOT see parent's context
                childHasContext.set(AgentContext.getCurrent().isPresent());
                latch.countDown();
            });
            
            childThread.start();
            latch.await();
            
            // Main thread still has context
            assertTrue(AgentContext.getCurrent().isPresent());
            // Child thread did not have context
            assertFalse(childHasContext.get());
        }
    }
    
    @Nested
    @DisplayName("Execute With Context")
    class ExecuteWith {
        
        @Test
        void shouldExecuteRunnableWithContext() {
            AgentContext context = new AgentContext("exec-agent", "execTool", "exec-session");
            
            assertTrue(AgentContext.getCurrent().isEmpty());
            
            context.executeWith(() -> {
                assertTrue(AgentContext.getCurrent().isPresent());
                assertEquals("exec-agent", AgentContext.getCurrent().get().agentId());
            });
            
            // Context cleared after execution
            assertTrue(AgentContext.getCurrent().isEmpty());
        }
        
        @Test
        void shouldExecuteSupplierWithContext() {
            AgentContext context = new AgentContext("supplier-agent", "supplierTool", "supplier-session");
            
            String result = context.executeWith(() -> {
                assertTrue(AgentContext.getCurrent().isPresent());
                return AgentContext.getCurrent().get().agentId();
            });
            
            assertEquals("supplier-agent", result);
            assertTrue(AgentContext.getCurrent().isEmpty());
        }
        
        @Test
        void shouldClearContextEvenOnException() {
            AgentContext context = new AgentContext("error-agent", "errorTool", "error-session");
            
            assertThrows(RuntimeException.class, () -> {
                context.executeWith(() -> {
                    throw new RuntimeException("Test exception");
                });
            });
            
            // Context should be cleared even after exception
            assertTrue(AgentContext.getCurrent().isEmpty());
        }
    }
    
    @Nested
    @DisplayName("Duration Tracking")
    class DurationTracking {
        
        @Test
        void shouldTrackDuration() throws InterruptedException {
            AgentContext context = new AgentContext("duration-agent", "durationTool", "duration-session");
            
            // Wait a bit
            Thread.sleep(50);
            
            long durationMs = context.durationMs();
            assertTrue(durationMs >= 50, "Duration should be at least 50ms");
            assertTrue(durationMs < 1000, "Duration should be less than 1s");
        }
    }
}
