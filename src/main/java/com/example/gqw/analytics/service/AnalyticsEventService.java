package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.repository.AnalyticsEventRepository;
import com.example.gqw.analytics.repository.EventTypeRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsEventService {

    private final AnalyticsEventRepository eventRepository;
    private final EventTypeRepository eventTypeRepository;

    public AnalyticsEventService(AnalyticsEventRepository eventRepository, EventTypeRepository eventTypeRepository) {
        this.eventRepository = eventRepository;
        this.eventTypeRepository = eventTypeRepository;
    }

    @Transactional
    public AnalyticsEvent createEvent(
        String eventTypeCode,
        Long userId,
        String sessionId,
        String requestPath,
        String httpMethod,
        String traceId
    ) {
        eventTypeRepository.findById(eventTypeCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown event type: " + eventTypeCode));

        AnalyticsEvent event = new AnalyticsEvent();
        event.setEventUid(UUID.randomUUID());
        event.setEventTypeCode(eventTypeCode);
        event.setUserId(userId);
        event.setSessionId(sessionId);
        event.setRequestPath(requestPath);
        event.setHttpMethod(httpMethod);
        event.setTraceId(traceId);
        event.setStartedAt(Instant.now());
        event.setIsError(false);
        return eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public AnalyticsEvent findByEventUid(UUID eventUid) {
        return eventRepository.findByEventUid(eventUid)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventUid));
    }

    @Transactional
    public void finishEventSuccess(UUID eventUid, Integer statusCode) {
        AnalyticsEvent event = findByEventUid(eventUid);
        Instant endedAt = Instant.now();
        event.setEndedAt(endedAt);
        event.setStatusCode(statusCode);
        event.setIsError(false);
        event.setDurationMs((int) Duration.between(event.getStartedAt(), endedAt).toMillis());
        eventRepository.save(event);
    }

    @Transactional
    public void finishEventError(UUID eventUid, Integer statusCode, String errorMessage) {
        AnalyticsEvent event = findByEventUid(eventUid);
        Instant endedAt = Instant.now();
        event.setEndedAt(endedAt);
        event.setStatusCode(statusCode);
        event.setIsError(true);
        event.setErrorMessage(errorMessage);
        event.setDurationMs((int) Duration.between(event.getStartedAt(), endedAt).toMillis());
        eventRepository.save(event);
    }
}

