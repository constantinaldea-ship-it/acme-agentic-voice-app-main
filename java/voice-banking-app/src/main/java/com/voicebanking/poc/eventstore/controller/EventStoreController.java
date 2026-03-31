package com.voicebanking.poc.eventstore.controller;

import com.voicebanking.poc.eventstore.dto.EventResponse;
import com.voicebanking.poc.eventstore.dto.WriteEventRequest;
import com.voicebanking.poc.eventstore.model.Event;
import com.voicebanking.poc.eventstore.service.EventStoreService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/poc/events")
@Profile("poc")
public class EventStoreController {

    private static final Logger log = LoggerFactory.getLogger(EventStoreController.class);

    private final EventStoreService eventStoreService;

    public EventStoreController(EventStoreService eventStoreService) {
        this.eventStoreService = eventStoreService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> writeEvent(@Valid @RequestBody WriteEventRequest request) {
        log.info("POST /api/poc/events type={}", request.eventType());

        var event = new Event(
                request.eventType(),
                request.payload(),
                Instant.now(),
                request.metadata()
        );

        Event saved = eventStoreService.writeEvent(event);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> readEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        log.info("GET /api/poc/events eventType={} from={} to={}", eventType, from, to);

        List<Event> events = eventStoreService.readEvents(eventType, from, to);
        List<EventResponse> response = events.stream().map(this::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    private EventResponse toResponse(Event e) {
        return new EventResponse(e.getId(), e.getEventType(), e.getPayload(), e.getTimestamp(), e.getMetadata());
    }
}
