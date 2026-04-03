package com.example.gqw.analytics.service.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponseDto(
    long totalEvents,
    BigDecimal avgMs,
    BigDecimal p95Ms,
    BigDecimal errorRate,
    List<ChartPointDto> chartSeries
) {
}

