package com.voicebanking.agent.handover.service;

import com.voicebanking.agent.handover.domain.HandoverContext;
import com.voicebanking.agent.handover.domain.HandoverResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class HandoverLogService {
    private static final Logger log = LoggerFactory.getLogger(HandoverLogService.class);

    public void logHandoverInitiated(String sessionId, HandoverContext context) {
        log.info("HANDOVER_INITIATED sessionId={} customerId={} reason={} intent={} timestamp={}",
                mask(sessionId),
                mask(context.getCustomerId()),
                context.getHandoverReason(),
                context.getDetectedIntent(),
                Instant.now());
    }

    public void logHandoverRouted(String sessionId, String queueId, int estimatedWait) {
        log.info("HANDOVER_ROUTED sessionId={} queueId={} estimatedWaitMinutes={} timestamp={}",
                mask(sessionId),
                queueId,
                estimatedWait,
                Instant.now());
    }

    public void logHandoverCompleted(String sessionId, HandoverResult result) {
        log.info("HANDOVER_COMPLETED {} sessionId={} status={} handoverId={} message={} timestamp={}",
                result.isSuccess() ? "SUCCESS" : "FAILURE",
                mask(sessionId),
                result.status(),
                result.handoverId(),
                result.customerMessage(),
                Instant.now());
    }

    public void logHandoverFailed(String sessionId, String reason, Exception ex) {
        log.error("HANDOVER_FAILED sessionId={} reason={} timestamp={}",
                mask(sessionId),
                reason,
                Instant.now(),
                ex);
    }

    public void logContextSent(String sessionId, int contextSizeBytes) {
        log.debug("HANDOVER_CONTEXT_SENT sessionId={} sizeBytes={} timestamp={}",
                mask(sessionId),
                contextSizeBytes,
                Instant.now());
    }

    public void logQueueStatusCheck(String queueId, boolean available, int waitTime) {
        log.debug("QUEUE_STATUS_CHECK queueId={} available={} waitTimeMinutes={}",
                queueId,
                available,
                waitTime);
    }

    private String mask(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
