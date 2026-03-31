package com.voicebanking.agent.handover.service;

import com.voicebanking.agent.handover.domain.QueueStatus;
import com.voicebanking.agent.handover.integration.CallCenterClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;

@Service
public class QueueStatusService {
    private static final Logger log = LoggerFactory.getLogger(QueueStatusService.class);

    private final CallCenterClient callCenterClient;
    private final LocalTime businessHoursStart;
    private final LocalTime businessHoursEnd;
    private final ZoneId timezone;

    public QueueStatusService(
            CallCenterClient callCenterClient,
            @Value("${handover.business-hours.start:09:00}") String start,
            @Value("${handover.business-hours.end:18:00}") String end,
            @Value("${handover.business-hours.timezone:Europe/Berlin}") String tz) {
        this.callCenterClient = callCenterClient;
        this.businessHoursStart = LocalTime.parse(start);
        this.businessHoursEnd = LocalTime.parse(end);
        this.timezone = ZoneId.of(tz);
    }

    public QueueStatus getQueueStatus(String queueId) {
        log.debug("Checking queue status for: {}", queueId);

        if (!isWithinBusinessHours()) {
            log.info("Outside business hours for queue: {}", queueId);
            return QueueStatus.afterHours(queueId);
        }

        return callCenterClient.getQueueStatus(queueId);
    }

    public boolean checkAgentAvailability(String queueId) {
        if (!isWithinBusinessHours()) {
            return false;
        }
        return callCenterClient.checkAvailability(queueId);
    }

    public int getEstimatedWaitTime(String queueId) {
        QueueStatus status = getQueueStatus(queueId);
        return status.estimatedWaitMinutes();
    }

    public boolean isWithinBusinessHours() {
        LocalDateTime now = LocalDateTime.now(timezone);
        DayOfWeek day = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();

        // Weekdays only
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }

        return !time.isBefore(businessHoursStart) && time.isBefore(businessHoursEnd);
    }
}
