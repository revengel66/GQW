package com.example.gqw.analytics.service.dto;

import java.math.BigDecimal;
import java.util.List;

public record EventDashboardResponseDto(
    String eventTypeCode,
    long totalEvents,
    BigDecimal avgMs,
    BigDecimal p95Ms,
    BigDecimal p99Ms,
    BigDecimal errorRate,
    List<ChartPointDto> chartSeries
) {
}

