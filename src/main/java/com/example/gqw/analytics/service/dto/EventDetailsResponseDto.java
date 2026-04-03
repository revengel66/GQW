package com.example.gqw.analytics.service.dto;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.entity.AnalyticsEventAttribute;
import com.example.gqw.analytics.entity.AnalyticsStage;
import com.example.gqw.analytics.entity.AnalyticsStageMetric;
import java.util.List;

public record EventDetailsResponseDto(
    AnalyticsEvent event,
    List<AnalyticsStage> stages,
    List<AnalyticsStageMetric> metrics,
    List<AnalyticsEventAttribute> attributes
) {
}

