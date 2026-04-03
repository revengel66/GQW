package com.example.gqw.analytics.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ChartPointDto(
    Instant time,
    BigDecimal value
) {
}

