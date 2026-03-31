package com.voicebanking.agent.handover;

import com.voicebanking.agent.handover.domain.*;
import com.voicebanking.agent.handover.integration.CallCenterClient;
import com.voicebanking.agent.handover.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for HumanHandoverAgent using stub implementations
 * to avoid Mockito compatibility issues with Java 25.
 */
@DisplayName("HumanHandoverAgent Tests")
class HumanHandoverAgentTest {

    private HumanHandoverAgent agent;
    private StubContextBuilderService contextBuilderService;
    private StubQueueStatusService queueStatusService;
    private StubHandoverLogService handoverLogService;
    private StubCallCenterClient callCenterClient;

    @BeforeEach
    void setUp() {
        contextBuilderService = new StubContextBuilderService();
        queueStatusService = new StubQueueStatusService();
        handoverLogService = new StubHandoverLogService();
        callCenterClient = new StubCallCenterClient();

        agent = new HumanHandoverAgent(
                contextBuilderService,
                queueStatusService,
                handoverLogService,
                callCenterClient,
                "general"
        );
    }

    @Test
    @DisplayName("Should have correct agent ID")
    void shouldHaveCorrectAgentId() {
        assertThat(agent.getAgentId()).isEqualTo("human-handover");
    }

    @Test
    @DisplayName("Should have appropriate description")
    void shouldHaveDescription() {
        assertThat(agent.getDescription()).contains("human agent");
    }

    @Test
    @DisplayName("Should provide all 6 tools")
    void shouldProvideAllTools() {
        assertThat(agent.getToolIds()).hasSize(6);
        assertThat(agent.getToolIds()).containsExactlyInAnyOrder(
                "initiateHandover",
                "buildContextPayload",
                "checkAgentAvailability",
                "getQueueWaitTime",
                "routeToQueue",
                "sendHandoverNotification"
        );
    }

    @Test
    @DisplayName("Should support all defined tools")
    void shouldSupportAllDefinedTools() {
        assertThat(agent.supportsTool("initiateHandover")).isTrue();
        assertThat(agent.supportsTool("buildContextPayload")).isTrue();
        assertThat(agent.supportsTool("checkAgentAvailability")).isTrue();
        assertThat(agent.supportsTool("getQueueWaitTime")).isTrue();
        assertThat(agent.supportsTool("routeToQueue")).isTrue();
        assertThat(agent.supportsTool("sendHandoverNotification")).isTrue();
    }

    @Test
    @DisplayName("Should not support unknown tools")
    void shouldNotSupportUnknownTools() {
        assertThat(agent.supportsTool("unknownTool")).isFalse();
    }

    @Test
    @DisplayName("checkAgentAvailability should return availability info")
    void checkAgentAvailabilityShouldWork() {
        queueStatusService.setAvailable(true);
        queueStatusService.setAgentCount(5);

        Map<String, Object> input = new HashMap<>();
        input.put("queueId", "general");

        Map<String, Object> result = agent.executeTool("checkAgentAvailability", input);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("available")).isEqualTo(true);
        assertThat(result.get("queueId")).isEqualTo("general");
    }

    @Test
    @DisplayName("getQueueWaitTime should return wait time info")
    void getQueueWaitTimeShouldWork() {
        queueStatusService.setEstimatedWaitMinutes(10);

        Map<String, Object> input = new HashMap<>();
        input.put("queueId", "general");

        Map<String, Object> result = agent.executeTool("getQueueWaitTime", input);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("estimatedWaitMinutes")).isEqualTo(10);
    }

    @Test
    @DisplayName("buildContextPayload should return context map")
    void buildContextPayloadShouldWork() {
        Map<String, Object> input = new HashMap<>();
        input.put("sessionId", "sess-456");
        input.put("reason", "USER_REQUEST");

        Map<String, Object> result = agent.executeTool("buildContextPayload", input);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("context")).isNotNull();
    }

    @Test
    @DisplayName("initiateHandover should succeed when agents available")
    void initiateHandoverShouldSucceedWhenAvailable() {
        queueStatusService.setAvailable(true);
        queueStatusService.setWithinBusinessHours(true);
        callCenterClient.setRouteSuccess(true);

        Map<String, Object> input = new HashMap<>();
        input.put("sessionId", "sess-456");
        input.put("reason", "USER_REQUEST");
        input.put("queueId", "general");

        Map<String, Object> result = agent.executeTool("initiateHandover", input);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("handoverId")).isNotNull();
    }

    @Test
    @DisplayName("initiateHandover should fail after hours")
    void initiateHandoverShouldFailAfterHours() {
        queueStatusService.setAvailable(false);
        queueStatusService.setWithinBusinessHours(false);

        Map<String, Object> input = new HashMap<>();
        input.put("sessionId", "sess-456");
        input.put("reason", "USER_REQUEST");
        input.put("queueId", "general");

        Map<String, Object> result = agent.executeTool("initiateHandover", input);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("status")).isEqualTo("AFTER_HOURS");
    }

    @Test
    @DisplayName("routeToQueue should route successfully")
    void routeToQueueShouldWork() {
        callCenterClient.setRouteSuccess(true);

        Map<String, Object> input = new HashMap<>();
        input.put("sessionId", "sess-456");
        input.put("queueId", "general");
        input.put("reason", "USER_REQUEST");

        Map<String, Object> result = agent.executeTool("routeToQueue", input);

        assertThat(result.get("success")).isEqualTo(true);
    }

    @Test
    @DisplayName("sendHandoverNotification should send context")
    void sendHandoverNotificationShouldWork() {
        callCenterClient.setSendContextSuccess(true);

        Map<String, Object> input = new HashMap<>();
        input.put("ticketId", "HO-123");
        input.put("sessionId", "sess-456");
        input.put("reason", "USER_REQUEST");

        Map<String, Object> result = agent.executeTool("sendHandoverNotification", input);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("ticketId")).isEqualTo("HO-123");
    }

    @Test
    @DisplayName("Unknown tool should return error")
    void unknownToolShouldReturnError() {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> result = agent.executeTool("unknownTool", input);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("error")).contains("Unknown tool");
    }

    @Test
    @DisplayName("Should handle exceptions gracefully")
    void shouldHandleExceptionsGracefully() {
        queueStatusService.setShouldThrow(true);

        Map<String, Object> input = new HashMap<>();
        input.put("queueId", "general");

        Map<String, Object> result = agent.executeTool("checkAgentAvailability", input);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("error")).contains("Service unavailable");
    }

    // ========== Stub implementations ==========

    static class StubContextBuilderService extends ContextBuilderService {
        StubContextBuilderService() {
            super(null, null);
        }

        @Override
        public HandoverContext buildContext(String sessionId, HandoverReason reason) {
            return new HandoverContext.Builder()
                    .customerId("CUST-123")
                    .sessionId(sessionId)
                    .handoverReason(reason)
                    .build();
        }

        @Override
        public HandoverContext buildContext(String sessionId, HandoverReason reason, String policyCategory) {
            return buildContext(sessionId, reason);
        }
    }

    static class StubQueueStatusService extends QueueStatusService {
        private boolean available = true;
        private boolean withinBusinessHours = true;
        private int agentCount = 5;
        private int estimatedWaitMinutes = 5;
        private boolean shouldThrow = false;

        StubQueueStatusService() {
            super(null, "00:00", "23:59", "UTC");
        }

        void setAvailable(boolean available) {
            this.available = available;
        }

        void setWithinBusinessHours(boolean withinBusinessHours) {
            this.withinBusinessHours = withinBusinessHours;
        }

        void setAgentCount(int agentCount) {
            this.agentCount = agentCount;
        }

        void setEstimatedWaitMinutes(int estimatedWaitMinutes) {
            this.estimatedWaitMinutes = estimatedWaitMinutes;
        }

        void setShouldThrow(boolean shouldThrow) {
            this.shouldThrow = shouldThrow;
        }

        @Override
        public QueueStatus getQueueStatus(String queueId) {
            if (shouldThrow) {
                throw new RuntimeException("Service unavailable");
            }
            if (!withinBusinessHours) {
                return QueueStatus.afterHours(queueId);
            }
            return new QueueStatus(queueId, "Test Queue", available, agentCount, 3, estimatedWaitMinutes, true, Instant.now());
        }

        @Override
        public boolean checkAgentAvailability(String queueId) {
            if (shouldThrow) {
                throw new RuntimeException("Service unavailable");
            }
            return available;
        }

        @Override
        public int getEstimatedWaitTime(String queueId) {
            return estimatedWaitMinutes;
        }

        @Override
        public boolean isWithinBusinessHours() {
            return withinBusinessHours;
        }
    }

    static class StubHandoverLogService extends HandoverLogService {
        StubHandoverLogService() {
            super();
        }

        @Override
        public void logHandoverInitiated(String sessionId, HandoverContext context) {
            // No-op for testing
        }

        @Override
        public void logHandoverCompleted(String sessionId, HandoverResult result) {
            // No-op for testing
        }

        @Override
        public void logQueueStatusCheck(String queueId, boolean available, int agentCount) {
            // No-op for testing
        }

        @Override
        public void logHandoverRouted(String sessionId, String queueId, int waitTime) {
            // No-op for testing
        }

        @Override
        public void logContextSent(String sessionId, int contextSize) {
            // No-op for testing
        }
    }

    static class StubCallCenterClient implements CallCenterClient {
        private boolean routeSuccess = true;
        private boolean sendContextSuccess = true;

        void setRouteSuccess(boolean routeSuccess) {
            this.routeSuccess = routeSuccess;
        }

        void setSendContextSuccess(boolean sendContextSuccess) {
            this.sendContextSuccess = sendContextSuccess;
        }

        @Override
        public boolean checkAvailability(String queueId) {
            return true;
        }

        @Override
        public QueueStatus getQueueStatus(String queueId) {
            return new QueueStatus(queueId, "Test Queue", true, 5, 3, 5, true, Instant.now());
        }

        @Override
        public HandoverResult routeHandover(String queueId, HandoverContext context) {
            if (routeSuccess) {
                return HandoverResult.queued("HO-" + System.currentTimeMillis(), queueId, 4, 5);
            } else {
                return HandoverResult.failed("HO-" + System.currentTimeMillis(), "Routing failed");
            }
        }

        @Override
        public boolean sendContext(String handoverId, HandoverContext context) {
            return sendContextSuccess;
        }

        @Override
        public boolean cancelHandover(String ticketId) {
            return true;
        }
    }
}
