package com.voicebanking.agent.handover.service;

import com.voicebanking.agent.handover.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("HandoverLogService Tests")
class HandoverLogServiceTest {

    private HandoverLogService logService;

    @BeforeEach
    void setUp() {
        logService = new HandoverLogService();
    }

    @Test
    @DisplayName("Should log handover initiated")
    void shouldLogHandoverInitiated() {
        HandoverContext context = new HandoverContext.Builder()
                .customerId("CUST-123")
                .sessionId("sess-456")
                .handoverReason(HandoverReason.USER_REQUEST)
                .build();

        // Should not throw
        assertThatCode(() -> logService.logHandoverInitiated("sess-456", context))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should log handover completed")
    void shouldLogHandoverCompleted() {
        HandoverResult result = HandoverResult.queued("HO-123", "general", 1, 5);

        assertThatCode(() -> logService.logHandoverCompleted("sess-456", result))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should log queue status check")
    void shouldLogQueueStatusCheck() {
        assertThatCode(() -> logService.logQueueStatusCheck("general", true, 5))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should log handover routed")
    void shouldLogHandoverRouted() {
        assertThatCode(() -> logService.logHandoverRouted("sess-456", "general", 5))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should log context sent")
    void shouldLogContextSent() {
        assertThatCode(() -> logService.logContextSent("sess-456", 1024))
                .doesNotThrowAnyException();
    }
}
