package com.example.gqw.analytics.service.dto;

import java.util.List;

public record EventListResponseDto(
    List<EventListItemDto> items,
    long total,
    int page,
    int size
) {
}

