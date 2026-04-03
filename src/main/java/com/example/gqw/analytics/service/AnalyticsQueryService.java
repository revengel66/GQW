package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AggregatedMetric;
import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.entity.AnalyticsEventAttribute;
import com.example.gqw.analytics.entity.AnalyticsStage;
import com.example.gqw.analytics.entity.AnalyticsStageMetric;
import com.example.gqw.analytics.repository.AggregatedMetricRepository;
import com.example.gqw.analytics.repository.AnalyticsEventAttributeRepository;
import com.example.gqw.analytics.repository.AnalyticsEventRepository;
import com.example.gqw.analytics.repository.AnalyticsStageMetricRepository;
import com.example.gqw.analytics.repository.AnalyticsStageRepository;
import com.example.gqw.analytics.service.dto.ChartPointDto;
import com.example.gqw.analytics.service.dto.DashboardResponseDto;
import com.example.gqw.analytics.service.dto.EventDashboardResponseDto;
import com.example.gqw.analytics.service.dto.EventDetailsResponseDto;
import com.example.gqw.analytics.service.dto.EventListItemDto;
import com.example.gqw.analytics.service.dto.EventListResponseDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsQueryService {

    private final AggregatedMetricRepository aggregatedMetricRepository;
    private final AnalyticsEventRepository eventRepository;
    private final AnalyticsStageRepository stageRepository;
    private final AnalyticsStageMetricRepository stageMetricRepository;
    private final AnalyticsEventAttributeRepository eventAttributeRepository;

    public AnalyticsQueryService(
        AggregatedMetricRepository aggregatedMetricRepository,
        AnalyticsEventRepository eventRepository,
        AnalyticsStageRepository stageRepository,
        AnalyticsStageMetricRepository stageMetricRepository,
        AnalyticsEventAttributeRepository eventAttributeRepository
    ) {
        this.aggregatedMetricRepository = aggregatedMetricRepository;
        this.eventRepository = eventRepository;
        this.stageRepository = stageRepository;
        this.stageMetricRepository = stageMetricRepository;
        this.eventAttributeRepository = eventAttributeRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponseDto getDashboardData(Instant from, Instant to, String eventTypeCode, String stageTypeCode) {
        List<AggregatedMetric> metrics = aggregatedMetricRepository.findByFilter(from, to, eventTypeCode, stageTypeCode);
        if (metrics.isEmpty()) {
            return new DashboardResponseDto(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }

        long total = metrics.stream().mapToLong(AggregatedMetric::getSampleCount).sum();
        BigDecimal avgMs = avg(metrics.stream().map(AggregatedMetric::getAvgMs).toList());
        BigDecimal p95 = avg(metrics.stream().map(AggregatedMetric::getP95Ms).toList());
        BigDecimal errorRate = avg(metrics.stream().map(AggregatedMetric::getErrorRate).toList());

        List<ChartPointDto> points = new ArrayList<>();
        for (AggregatedMetric metric : metrics) {
            points.add(new ChartPointDto(metric.getPeriodStart(), metric.getP95Ms()));
        }
        return new DashboardResponseDto(total, avgMs, p95, errorRate, points);
    }

    @Transactional(readOnly = true)
    public EventListResponseDto getEventList(
        Instant from,
        Instant to,
        String eventTypeCode,
        String status,
        int page,
        int size
    ) {
        Page<AnalyticsEvent> pageData = eventRepository.findAllByFilter(
            from,
            to,
            eventTypeCode,
            status,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"))
        );

        List<EventListItemDto> items = pageData.getContent().stream()
            .map(e -> new EventListItemDto(
                e.getEventUid(),
                e.getEventTypeCode(),
                Boolean.TRUE.equals(e.getIsError()),
                e.getStatusCode(),
                e.getDurationMs(),
                e.getStartedAt()
            ))
            .toList();
        return new EventListResponseDto(items, pageData.getTotalElements(), pageData.getNumber(), pageData.getSize());
    }

    @Transactional(readOnly = true)
    public EventDetailsResponseDto getEventDetails(UUID eventUid) {
        AnalyticsEvent event = eventRepository.findByEventUid(eventUid)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventUid));
        List<AnalyticsStage> stages = stageRepository.findByEventIdOrderByStageOrder(event.getId());
        List<AnalyticsStageMetric> metrics = stages.stream()
            .flatMap(s -> stageMetricRepository.findByStageId(s.getId()).stream())
            .toList();
        List<AnalyticsEventAttribute> attributes = eventAttributeRepository.findByEventId(event.getId());
        return new EventDetailsResponseDto(event, stages, metrics, attributes);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsEventAttribute> getEventAttributes(UUID eventUid) {
        AnalyticsEvent event = eventRepository.findByEventUid(eventUid)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventUid));
        return eventAttributeRepository.findByEventId(event.getId());
    }

    @Transactional(readOnly = true)
    public EventDashboardResponseDto getEventDashboardData(Instant from, Instant to, String eventTypeCode) {
        List<AggregatedMetric> metrics = aggregatedMetricRepository.findByFilter(from, to, eventTypeCode, null);
        if (metrics.isEmpty()) {
            return new EventDashboardResponseDto(eventTypeCode, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
        long total = metrics.stream().mapToLong(AggregatedMetric::getSampleCount).sum();
        BigDecimal avgMs = avg(metrics.stream().map(AggregatedMetric::getAvgMs).toList());
        BigDecimal p95 = avg(metrics.stream().map(AggregatedMetric::getP95Ms).toList());
        BigDecimal p99 = avg(metrics.stream().map(AggregatedMetric::getP99Ms).toList());
        BigDecimal errorRate = avg(metrics.stream().map(AggregatedMetric::getErrorRate).toList());

        List<ChartPointDto> points = metrics.stream()
            .map(m -> new ChartPointDto(m.getPeriodStart(), m.getP95Ms()))
            .toList();
        return new EventDashboardResponseDto(eventTypeCode, total, avgMs, p95, p99, errorRate, points);
    }

    private BigDecimal avg(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream()
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = values.stream().filter(v -> v != null).count();
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return sum.divide(BigDecimal.valueOf(count), 3, RoundingMode.HALF_UP);
    }
}

