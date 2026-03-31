package com.voicebanking.poc.eventstore.service;

import com.voicebanking.poc.eventstore.model.Event;
import com.voicebanking.poc.eventstore.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class H2EventStoreServiceTest {

    @Mock
    private EventRepository repository;

    private H2EventStoreService service;

    @BeforeEach
    void setUp() {
        service = new H2EventStoreService(repository);
    }

    @Test
    void writeEvent_shouldPersistAndReturnEvent() {
        var event = new Event("audit", "{\"action\":\"login\"}", Instant.now(), null);
        var saved = new Event("audit", "{\"action\":\"login\"}", event.getTimestamp(), null);
        saved.setId(UUID.randomUUID());

        when(repository.save(any(Event.class))).thenReturn(saved);

        Event result = service.writeEvent(event);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getEventType()).isEqualTo("audit");
        verify(repository).save(event);
    }

    @Test
    void writeEvent_shouldSetTimestampWhenNull() {
        var event = new Event("metric", "{\"cpu\":0.5}", null, null);
        var saved = new Event("metric", "{\"cpu\":0.5}", Instant.now(), null);
        saved.setId(UUID.randomUUID());

        when(repository.save(any(Event.class))).thenReturn(saved);

        service.writeEvent(event);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTimestamp()).isNotNull();
    }

    @Test
    void readEvents_withTypeAndTimeRange_shouldFilterByAll() {
        var from = Instant.parse("2026-03-01T00:00:00Z");
        var to = Instant.parse("2026-03-10T00:00:00Z");

        when(repository.findByEventTypeAndTimestampBetweenOrderByTimestampDesc("audit", from, to))
                .thenReturn(List.of());

        List<Event> result = service.readEvents("audit", from, to);

        assertThat(result).isEmpty();
        verify(repository).findByEventTypeAndTimestampBetweenOrderByTimestampDesc("audit", from, to);
    }

    @Test
    void readEvents_withTypeOnly_shouldFilterByType() {
        var event = new Event("log", "{}", Instant.now(), null);
        event.setId(UUID.randomUUID());

        when(repository.findByEventTypeOrderByTimestampDesc("log")).thenReturn(List.of(event));

        List<Event> result = service.readEvents("log", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getEventType()).isEqualTo("log");
        verify(repository).findByEventTypeOrderByTimestampDesc("log");
    }

    @Test
    void readEvents_withTimeRangeOnly_shouldFilterByRange() {
        var from = Instant.parse("2026-03-01T00:00:00Z");
        var to = Instant.parse("2026-03-10T00:00:00Z");

        when(repository.findByTimestampBetweenOrderByTimestampDesc(from, to)).thenReturn(List.of());

        List<Event> result = service.readEvents(null, from, to);

        assertThat(result).isEmpty();
        verify(repository).findByTimestampBetweenOrderByTimestampDesc(from, to);
    }

    @Test
    void readEvents_withNoFilters_shouldReturnAll() {
        when(repository.findAll()).thenReturn(List.of());

        List<Event> result = service.readEvents(null, null, null);

        assertThat(result).isEmpty();
        verify(repository).findAll();
    }

    @Test
    void readEvents_withTypeAndPartialRange_missingTo_shouldFilterByTypeOnly() {
        when(repository.findByEventTypeOrderByTimestampDesc("audit")).thenReturn(List.of());

        List<Event> result = service.readEvents("audit", Instant.now(), null);

        assertThat(result).isEmpty();
        verify(repository).findByEventTypeOrderByTimestampDesc("audit");
    }

    @Test
    void readEvents_withPartialRange_missingFrom_shouldReturnAll() {
        when(repository.findAll()).thenReturn(List.of());

        List<Event> result = service.readEvents(null, null, Instant.now());

        assertThat(result).isEmpty();
        verify(repository).findAll();
    }
}
