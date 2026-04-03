package com.example.gqw.analytics.service.dto;

import java.time.Instant;
import java.util.UUID;

public record EventListItemDto(
    UUID eventUid,
    String eventTypeCode,
    boolean isError,
    Integer statusCode,
    Integer durationMs,
    Instant startedAt
) {
}

