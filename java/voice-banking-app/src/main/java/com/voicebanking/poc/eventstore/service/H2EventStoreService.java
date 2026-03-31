package com.voicebanking.poc.eventstore.service;

import com.voicebanking.poc.eventstore.model.Event;
import com.voicebanking.poc.eventstore.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Profile("poc")
public class H2EventStoreService implements EventStoreService {

    private static final Logger log = LoggerFactory.getLogger(H2EventStoreService.class);

    private final EventRepository repository;

    public H2EventStoreService(EventRepository repository) {
        this.repository = repository;
    }

    @Override
    public Event writeEvent(Event event) {
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
        Event saved = repository.save(event);
        log.info("POC EventStore: persisted event id={} type={}", saved.getId(), saved.getEventType());
        return saved;
    }

    @Override
    public List<Event> readEvents(String eventType, Instant from, Instant to) {
        if (eventType != null && from != null && to != null) {
            return repository.findByEventTypeAndTimestampBetweenOrderByTimestampDesc(eventType, from, to);
        }
        if (eventType != null) {
            return repository.findByEventTypeOrderByTimestampDesc(eventType);
        }
        if (from != null && to != null) {
            return repository.findByTimestampBetweenOrderByTimestampDesc(from, to);
        }
        return repository.findAll();
    }
}
