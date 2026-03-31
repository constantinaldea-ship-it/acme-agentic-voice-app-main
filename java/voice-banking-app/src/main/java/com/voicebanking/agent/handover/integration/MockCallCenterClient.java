package com.voicebanking.agent.handover.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebanking.agent.handover.domain.HandoverContext;
import com.voicebanking.agent.handover.domain.HandoverResult;
import com.voicebanking.agent.handover.domain.QueueStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Profile("!production")
public class MockCallCenterClient implements CallCenterClient {
    private static final Logger log = LoggerFactory.getLogger(MockCallCenterClient.class);

    private final ObjectMapper objectMapper;
    private final Path handoverLogDir;
    private final LocalTime businessStart;
    private final LocalTime businessEnd;
    private final ZoneId timezone;

    private final Map<String, HandoverContext> activeHandovers = new ConcurrentHashMap<>();

    public MockCallCenterClient(
            ObjectMapper objectMapper,
            @Value("${handover.mock.log-dir:logs/handovers}") String logDir,
            @Value("${handover.business-hours.start:09:00}") String start,
            @Value("${handover.business-hours.end:18:00}") String end,
            @Value("${handover.business-hours.timezone:Europe/Berlin}") String tz) {
        this.objectMapper = objectMapper;
        this.handoverLogDir = Path.of(logDir);
        this.businessStart = LocalTime.parse(start);
        this.businessEnd = LocalTime.parse(end);
        this.timezone = ZoneId.of(tz);
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(handoverLogDir);
        log.info("MockCallCenterClient initialized, logging to: {}", handoverLogDir.toAbsolutePath());
    }

    @Override
    public boolean checkAvailability(String queueId) {
        if (!isWithinBusinessHours()) {
            log.debug("Queue {} unavailable: outside business hours", queueId);
            return false;
        }
        // 80% availability during business hours
        boolean available = ThreadLocalRandom.current().nextDouble() < 0.8;
        log.debug("Queue {} availability check: {}", queueId, available);
        return available;
    }

    @Override
    public QueueStatus getQueueStatus(String queueId) {
        if (!isWithinBusinessHours()) {
            return QueueStatus.afterHours(queueId);
        }

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int agentCount = rand.nextInt(2, 10);
        int queueDepth = rand.nextInt(0, 15);
        int waitTime = Math.max(1, queueDepth * 2 / agentCount);

        return new QueueStatus(queueId, "General", true, agentCount, queueDepth, waitTime, true, Instant.now());
    }

    @Override
    public HandoverResult routeHandover(String queueId, HandoverContext context) {
        String ticketId = "HO-" + System.currentTimeMillis();
        
        if (!isWithinBusinessHours()) {
            String contactInfo = "Call center is closed. Please try again during business hours (" +
                    businessStart + " - " + businessEnd + " CET, weekdays).";
            return HandoverResult.fallback(ticketId, contactInfo);
        }

        activeHandovers.put(ticketId, context);
        logToFile(ticketId, context);

        QueueStatus status = getQueueStatus(queueId);
        int position = status.queueDepth() + 1;
        return HandoverResult.queued(ticketId, queueId, position, status.estimatedWaitMinutes());
    }

    @Override
    public boolean sendContext(String ticketId, HandoverContext context) {
        logToFile(ticketId + "-context", context);
        log.info("Context sent for ticket: {}", ticketId);
        return true;
    }

    @Override
    public boolean cancelHandover(String ticketId) {
        HandoverContext removed = activeHandovers.remove(ticketId);
        if (removed != null) {
            log.info("Handover cancelled: {}", ticketId);
            return true;
        }
        return false;
    }

    private boolean isWithinBusinessHours() {
        LocalDateTime now = LocalDateTime.now(timezone);
        DayOfWeek day = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();

        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }

        return !time.isBefore(businessStart) && time.isBefore(businessEnd);
    }

    private void logToFile(String ticketId, HandoverContext context) {
        try {
            String filename = ticketId + ".json";
            Path filePath = handoverLogDir.resolve(filename);
            String json = objectMapper.writeValueAsString(context.toMap());
            Files.writeString(filePath, json);
            log.debug("Handover context saved to: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to save handover context", e);
        }
    }
}
