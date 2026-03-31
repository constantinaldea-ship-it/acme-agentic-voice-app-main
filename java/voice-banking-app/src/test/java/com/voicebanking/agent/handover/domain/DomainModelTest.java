package com.voicebanking.agent.handover.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Domain Model Tests")
class DomainModelTest {

    @Test
    @DisplayName("HandoverReason should have all expected values")
    void handoverReasonShouldHaveAllValues() {
        assertThat(HandoverReason.values()).hasSize(5);
        assertThat(HandoverReason.valueOf("USER_REQUEST")).isNotNull();
        assertThat(HandoverReason.valueOf("LOW_CONFIDENCE")).isNotNull();
        assertThat(HandoverReason.valueOf("POLICY_ESCALATE")).isNotNull();
        assertThat(HandoverReason.valueOf("ERROR")).isNotNull();
        assertThat(HandoverReason.valueOf("TIMEOUT")).isNotNull();
    }

    @Test
    @DisplayName("HandoverReason should provide description")
    void handoverReasonShouldProvideDescription() {
        assertThat(HandoverReason.USER_REQUEST.getDescription()).isNotEmpty();
        assertThat(HandoverReason.USER_REQUEST.getDescription()).contains("User");
    }

    @Test
    @DisplayName("HandoverReason should identify high priority")
    void handoverReasonShouldIdentifyHighPriority() {
        assertThat(HandoverReason.USER_REQUEST.isHighPriority()).isTrue();
        assertThat(HandoverReason.ERROR.isHighPriority()).isTrue();
        assertThat(HandoverReason.LOW_CONFIDENCE.isHighPriority()).isFalse();
    }

    @Test
    @DisplayName("QueueStatus should calculate canAcceptHandover correctly")
    void queueStatusShouldCalculateCanAcceptHandover() {
        QueueStatus available = new QueueStatus("q1", "General", true, 5, 3, 5, true, Instant.now());
        QueueStatus noAgents = new QueueStatus("q2", "Empty", true, 0, 0, 0, true, Instant.now());
        QueueStatus afterHours = QueueStatus.afterHours("q3");

        assertThat(available.canAcceptHandover()).isTrue();
        assertThat(noAgents.canAcceptHandover()).isFalse();
        assertThat(afterHours.canAcceptHandover()).isFalse();
    }

    @Test
    @DisplayName("QueueStatus should provide appropriate wait time messages")
    void queueStatusShouldProvideWaitTimeMessages() {
        QueueStatus shortWait = new QueueStatus("q1", "General", true, 5, 1, 1, true, Instant.now());
        QueueStatus mediumWait = new QueueStatus("q2", "General", true, 3, 5, 4, true, Instant.now());
        QueueStatus longWait = new QueueStatus("q3", "General", true, 2, 10, 10, true, Instant.now());

        assertThat(shortWait.getWaitTimeMessage()).contains("shortly");
        assertThat(mediumWait.getWaitTimeMessage()).contains("approximately");
        assertThat(longWait.getWaitTimeMessage()).contains("high call volume");
    }

    @Test
    @DisplayName("QueueStatus.afterHours should create valid status")
    void queueStatusAfterHoursShouldWork() {
        QueueStatus status = QueueStatus.afterHours("general");

        assertThat(status.queueId()).isEqualTo("general");
        assertThat(status.available()).isFalse();
        assertThat(status.withinBusinessHours()).isFalse();
        assertThat(status.canAcceptHandover()).isFalse();
    }

    @Test
    @DisplayName("HandoverResult.initiated should create success result")
    void handoverResultInitiatedShouldWork() {
        HandoverResult result = HandoverResult.initiated("HO-123");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.handoverId()).isEqualTo("HO-123");
        assertThat(result.status()).isEqualTo(HandoverResult.HandoverStatus.INITIATED);
    }

    @Test
    @DisplayName("HandoverResult.queued should create success result with queue info")
    void handoverResultQueuedShouldWork() {
        HandoverResult result = HandoverResult.queued("HO-123", "general", 5, 10);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.handoverId()).isEqualTo("HO-123");
        assertThat(result.queueId()).isEqualTo("general");
        assertThat(result.queuePosition()).isEqualTo(5);
        assertThat(result.estimatedWaitMinutes()).isEqualTo(10);
        assertThat(result.status()).isEqualTo(HandoverResult.HandoverStatus.QUEUED);
    }

    @Test
    @DisplayName("HandoverResult.failed should create failure result")
    void handoverResultFailedShouldWork() {
        HandoverResult result = HandoverResult.failed("HO-123", "Service unavailable");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.handoverId()).isEqualTo("HO-123");
        assertThat(result.errorMessage()).isEqualTo("Service unavailable");
        assertThat(result.customerMessage()).contains("unable to connect");
        assertThat(result.status()).isEqualTo(HandoverResult.HandoverStatus.FAILED);
    }

    @Test
    @DisplayName("HandoverContext builder should work correctly")
    void handoverContextBuilderShouldWork() {
        HandoverContext context = new HandoverContext.Builder()
                .customerId("CUST-123")
                .sessionId("sess-456")
                .handoverReason(HandoverReason.USER_REQUEST)
                .detectedIntent("balance_inquiry")
                .conversationSummary("Customer asked about balance")
                .build();

        assertThat(context.getCustomerId()).isEqualTo("CUST-123");
        assertThat(context.getSessionId()).isEqualTo("sess-456");
        assertThat(context.getHandoverReason()).isEqualTo(HandoverReason.USER_REQUEST);
        assertThat(context.getDetectedIntent()).isEqualTo("balance_inquiry");
        assertThat(context.getConversationSummary()).isEqualTo("Customer asked about balance");
    }

    @Test
    @DisplayName("ConversationTurnContext record should work correctly")
    void conversationTurnContextShouldWork() {
        ConversationTurnContext turn = new ConversationTurnContext(
                "Hello", "Welcome", "greeting", null, Map.of()
        );

        assertThat(turn.userInput()).isEqualTo("Hello");
        assertThat(turn.assistantResponse()).isEqualTo("Welcome");
        assertThat(turn.intent()).isEqualTo("greeting");
        assertThat(turn.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("ConversationTurnContext.userOnly should work")
    void conversationTurnContextUserOnlyShouldWork() {
        ConversationTurnContext turn = ConversationTurnContext.userOnly("Help me");

        assertThat(turn.userInput()).isEqualTo("Help me");
        assertThat(turn.assistantResponse()).isNull();
    }

    @Test
    @DisplayName("HandoverContext builder should support all methods")
    void handoverContextBuilderShouldSupportAllMethods() {
        Instant started = Instant.now().minusSeconds(60);
        Instant handover = Instant.now();
        ConversationTurnContext turn = new ConversationTurnContext(
                "Hello", "Hi", "greeting", null, Map.of()
        );

        HandoverContext context = HandoverContext.builder()
                .sessionId("sess-789")
                .customerId("CUST-456")
                .conversationSummary("Customer needs help")
                .detectedIntent("balance_inquiry")
                .entities(Map.of("accountType", "checking"))
                .addEntity("amount", 100)
                .toolsCalled(java.util.List.of("getBalance"))
                .addToolCalled("listAccounts")
                .handoverReason(HandoverReason.LOW_CONFIDENCE)
                .policyCategory("banking")
                .conversationHistory(java.util.List.of(turn))
                .addConversationTurn(ConversationTurnContext.userOnly("More help"))
                .customerSentiment("neutral")
                .startedAt(started)
                .handoverAt(handover)
                .additionalMetadata(Map.of("source", "voice"))
                .build();

        assertThat(context.getSessionId()).isEqualTo("sess-789");
        assertThat(context.getCustomerId()).isEqualTo("CUST-456");
        assertThat(context.getEntities()).containsKeys("accountType", "amount");
        assertThat(context.getToolsCalled()).contains("getBalance", "listAccounts");
        assertThat(context.getPolicyCategory()).isEqualTo("banking");
        assertThat(context.getConversationHistory()).hasSize(2);
        assertThat(context.getCustomerSentiment()).isEqualTo("neutral");
        assertThat(context.getStartedAt()).isEqualTo(started);
        assertThat(context.getHandoverAt()).isEqualTo(handover);
        assertThat(context.getAdditionalMetadata()).containsEntry("source", "voice");
        assertThat(context.getConversationDurationSeconds()).isEqualTo(60);
        assertThat(context.getTurnCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("HandoverContext toMap should include all fields")
    void handoverContextToMapShouldWork() {
        HandoverContext context = HandoverContext.builder()
                .sessionId("sess-123")
                .customerId("CUST-123")
                .handoverReason(HandoverReason.USER_REQUEST)
                .detectedIntent("transfer")
                .conversationSummary("Transfer request")
                .startedAt(Instant.now().minusSeconds(30))
                .build();

        Map<String, Object> map = context.toMap();

        assertThat(map).containsKeys(
                "sessionId", "customerId", "handoverReason", "detectedIntent",
                "conversationSummary", "conversationDurationSeconds", "turnCount"
        );
        assertThat(map.get("handoverReason")).isEqualTo("USER_REQUEST");
    }

    @Test
    @DisplayName("HandoverContext builder should throw on missing sessionId")
    void handoverContextBuilderShouldThrowOnMissingSessionId() {
        assertThatThrownBy(() ->
                HandoverContext.builder()
                        .customerId("CUST-123")
                        .handoverReason(HandoverReason.USER_REQUEST)
                        .build()
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("sessionId");
    }

    @Test
    @DisplayName("HandoverContext builder should throw on missing reason")
    void handoverContextBuilderShouldThrowOnMissingReason() {
        assertThatThrownBy(() ->
                HandoverContext.builder()
                        .sessionId("sess-123")
                        .customerId("CUST-123")
                        .build()
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("handoverReason");
    }

    @Test
    @DisplayName("HandoverResult toMap should include all fields")
    void handoverResultToMapShouldWork() {
        HandoverResult result = HandoverResult.queued("HO-999", "priority", 3, 8);
        Map<String, Object> map = result.toMap();

        assertThat(map).containsKeys(
                "handoverId", "status", "queuePosition", "estimatedWaitMinutes",
                "queueId", "customerMessage", "success", "timestamp"
        );
        assertThat(map.get("success")).isEqualTo(true);
        assertThat(map.get("queuePosition")).isEqualTo(3);
    }

    @Test
    @DisplayName("HandoverResult fallback should create valid result")
    void handoverResultFallbackShouldWork() {
        HandoverResult result = HandoverResult.fallback("HO-999", "Call 1-800-BANK");

        assertThat(result.status()).isEqualTo(HandoverResult.HandoverStatus.FALLBACK);
        assertThat(result.customerMessage()).isEqualTo("Call 1-800-BANK");
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("QueueStatus closed should create valid status")
    void queueStatusClosedShouldWork() {
        QueueStatus status = QueueStatus.closed("general", "Holiday");

        assertThat(status.queueId()).isEqualTo("general");
        assertThat(status.available()).isFalse();
        assertThat(status.withinBusinessHours()).isFalse();
    }

    @Test
    @DisplayName("HandoverReason isUserInitiated should work")
    void handoverReasonIsUserInitiatedShouldWork() {
        assertThat(HandoverReason.USER_REQUEST.isUserInitiated()).isTrue();
        assertThat(HandoverReason.ERROR.isUserInitiated()).isFalse();
        assertThat(HandoverReason.LOW_CONFIDENCE.isUserInitiated()).isFalse();
    }
}
