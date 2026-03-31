package com.voicebanking.agent.handover.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebanking.agent.handover.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MockCallCenterClient Tests")
class MockCallCenterClientTest {

    private MockCallCenterClient client;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        client = new MockCallCenterClient(
                objectMapper,
                tempDir.toString(),
                "00:00",   // Start at midnight for testing
                "23:59",   // End at 23:59 for testing
                "Europe/Berlin"
        );
        client.init();
    }

    @Test
    @DisplayName("Should return queue status")
    void shouldReturnQueueStatus() {
        QueueStatus status = client.getQueueStatus("general");

        assertThat(status).isNotNull();
        assertThat(status.queueId()).isEqualTo("general");
    }

    @Test
    @DisplayName("Queue status should have valid fields when within business hours")
    void queueStatusShouldHaveValidFields() {
        QueueStatus status = client.getQueueStatus("general");

        assertThat(status.queueId()).isNotNull();
        assertThat(status.withinBusinessHours()).isTrue(); // Because we set 00:00-23:59
        assertThat(status.agentCount()).isGreaterThanOrEqualTo(0);
        assertThat(status.queueDepth()).isGreaterThanOrEqualTo(0);
        assertThat(status.estimatedWaitMinutes()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should route handover successfully")
    void shouldRouteHandoverSuccessfully() {
        HandoverContext context = new HandoverContext.Builder()
                .customerId("CUST-123")
                .sessionId("sess-456")
                .handoverReason(HandoverReason.USER_REQUEST)
                .build();

        HandoverResult result = client.routeHandover("general", context);

        assertThat(result).isNotNull();
        assertThat(result.handoverId()).isNotNull();
        assertThat(result.handoverId()).startsWith("HO-");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should send context successfully")
    void shouldSendContextSuccessfully() {
        HandoverContext context = new HandoverContext.Builder()
                .customerId("CUST-123")
                .sessionId("sess-456")
                .handoverReason(HandoverReason.USER_REQUEST)
                .build();

        boolean sent = client.sendContext("HO-123", context);

        assertThat(sent).isTrue();
    }

    @Test
    @DisplayName("Should return queued result with queue info")
    void shouldReturnQueuedResultWithInfo() {
        HandoverContext context = new HandoverContext.Builder()
                .customerId("CUST-123")
                .sessionId("sess-456")
                .handoverReason(HandoverReason.ERROR)
                .build();

        HandoverResult result = client.routeHandover("priority", context);

        assertThat(result.queueId()).isEqualTo("priority");
        assertThat(result.queuePosition()).isGreaterThan(0);
        assertThat(result.estimatedWaitMinutes()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Result should be convertible to map")
    void resultShouldBeConvertibleToMap() {
        HandoverContext context = new HandoverContext.Builder()
                .customerId("CUST-123")
                .sessionId("sess-456")
                .handoverReason(HandoverReason.USER_REQUEST)
                .build();

        HandoverResult result = client.routeHandover("general", context);
        var map = result.toMap();

        assertThat(map).containsKey("success");
        assertThat(map).containsKey("handoverId");
        assertThat(map).containsKey("status");
    }
}
