package com.example.gqw.analytics.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnalyticsEventSnapshot {

    private final UUID eventUid;
    private final String eventTypeCode;
    private final Long userId;
    private final String sessionId;
    private final String requestPath;
    private final String httpMethod;
    private final String traceId;
    private final String moduleCodeHint;
    private final Instant startedAt;
    private Instant endedAt;
    private Integer statusCode;
    private boolean error;
    private String errorMessage;
    private final List<EventAttributeSnapshot> attributes = new ArrayList<>();
    private final Map<Long, StageSnapshot> stages = new LinkedHashMap<>();

    public AnalyticsEventSnapshot(
        UUID eventUid,
        String eventTypeCode,
        Long userId,
        String sessionId,
        String requestPath,
        String httpMethod,
        String traceId,
        String moduleCodeHint,
        Instant startedAt
    ) {
        this.eventUid = eventUid;
        this.eventTypeCode = eventTypeCode;
        this.userId = userId;
        this.sessionId = sessionId;
        this.requestPath = requestPath;
        this.httpMethod = httpMethod;
        this.traceId = traceId;
        this.moduleCodeHint = moduleCodeHint;
        this.startedAt = startedAt;
    }

    public UUID eventUid() {
        return eventUid;
    }

    public String eventTypeCode() {
        return eventTypeCode;
    }

    public Long userId() {
        return userId;
    }

    public String sessionId() {
        return sessionId;
    }

    public String requestPath() {
        return requestPath;
    }

    public String httpMethod() {
        return httpMethod;
    }

    public String traceId() {
        return traceId;
    }

    public String moduleCodeHint() {
        return moduleCodeHint;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public boolean error() {
        return error;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public List<EventAttributeSnapshot> attributes() {
        return attributes;
    }

    public List<StageSnapshot> stages() {
        return new ArrayList<>(stages.values());
    }

    public void addAttribute(String attributeTypeCode, String value, boolean json) {
        attributes.add(new EventAttributeSnapshot(attributeTypeCode, value, json));
    }

    public void addStage(Long localStageId, String stageTypeCode, int stageOrder, Instant startedAt) {
        stages.put(localStageId, new StageSnapshot(localStageId, stageTypeCode, stageOrder, startedAt));
    }

    public void finishStageSuccess(Long localStageId, Instant endedAt) {
        StageSnapshot stage = stages.get(localStageId);
        if (stage != null) {
            stage.finishSuccess(endedAt);
        }
    }

    public void finishStageError(Long localStageId, String errorMessage, Instant endedAt) {
        StageSnapshot stage = stages.get(localStageId);
        if (stage != null) {
            stage.finishError(errorMessage, endedAt);
        }
    }

    public void markStageLogWindow(Long localStageId, Instant logStartedAt, Instant logEndedAt) {
        StageSnapshot stage = stages.get(localStageId);
        if (stage != null) {
            stage.markLogWindow(logStartedAt, logEndedAt);
        }
    }

    public void addMetricNum(Long localStageId, String metricTypeCode, BigDecimal value, String unit) {
        StageSnapshot stage = stages.get(localStageId);
        if (stage != null) {
            stage.metrics.add(StageMetricSnapshot.numeric(localStageId, metricTypeCode, value, unit, Instant.now()));
        }
    }

    public void addMetricText(Long localStageId, String metricTypeCode, String value, String unit) {
        StageSnapshot stage = stages.get(localStageId);
        if (stage != null) {
            stage.metrics.add(StageMetricSnapshot.text(localStageId, metricTypeCode, value, unit, Instant.now()));
        }
    }

    public void finishSuccess(Integer statusCode, Instant endedAt) {
        this.statusCode = statusCode;
        this.endedAt = endedAt;
        this.error = false;
    }

    public void finishError(Integer statusCode, String errorMessage, Instant endedAt) {
        this.statusCode = statusCode;
        this.endedAt = endedAt;
        this.error = true;
        this.errorMessage = errorMessage;
    }

    public record EventAttributeSnapshot(String attributeTypeCode, String value, boolean json) {
    }

    public static final class StageSnapshot {
        private final Long localStageId;
        private final String stageTypeCode;
        private final int stageOrder;
        private final Instant startedAt;
        private Instant endedAt;
        private Integer durationMs;
        private Instant logStartedAt;
        private Instant logEndedAt;
        private boolean error;
        private String errorMessage;
        private final List<StageMetricSnapshot> metrics = new ArrayList<>();

        private StageSnapshot(Long localStageId, String stageTypeCode, int stageOrder, Instant startedAt) {
            this.localStageId = localStageId;
            this.stageTypeCode = stageTypeCode;
            this.stageOrder = stageOrder;
            this.startedAt = startedAt;
        }

        public Long localStageId() {
            return localStageId;
        }

        public String stageTypeCode() {
            return stageTypeCode;
        }

        public int stageOrder() {
            return stageOrder;
        }

        public Instant startedAt() {
            return startedAt;
        }

        public Instant endedAt() {
            return endedAt;
        }

        public Integer durationMs() {
            return durationMs;
        }

        public Instant logStartedAt() {
            return logStartedAt;
        }

        public Instant logEndedAt() {
            return logEndedAt;
        }

        public boolean error() {
            return error;
        }

        public String errorMessage() {
            return errorMessage;
        }

        public List<StageMetricSnapshot> metrics() {
            return metrics;
        }

        private void finishSuccess(Instant endedAt) {
            this.endedAt = endedAt;
            this.error = false;
            updateDuration();
        }

        private void finishError(String errorMessage, Instant endedAt) {
            this.endedAt = endedAt;
            this.error = true;
            this.errorMessage = errorMessage;
            updateDuration();
        }

        private void markLogWindow(Instant logStartedAt, Instant logEndedAt) {
            if (logStartedAt == null || logEndedAt == null) {
                return;
            }
            if (this.logStartedAt == null && this.logEndedAt == null) {
                this.logStartedAt = logStartedAt;
                this.logEndedAt = logEndedAt;
            }
        }

        private void updateDuration() {
            if (startedAt != null && endedAt != null) {
                this.durationMs = (int) Duration.between(startedAt, endedAt).toMillis();
            }
        }
    }

    public record StageMetricSnapshot(
        Long localStageId,
        String metricTypeCode,
        BigDecimal numericValue,
        String textValue,
        String unit,
        boolean numeric,
        Instant recordedAt
    ) {
        private static StageMetricSnapshot numeric(
            Long localStageId,
            String metricTypeCode,
            BigDecimal value,
            String unit,
            Instant recordedAt
        ) {
            return new StageMetricSnapshot(localStageId, metricTypeCode, value, null, unit, true, recordedAt);
        }

        private static StageMetricSnapshot text(
            Long localStageId,
            String metricTypeCode,
            String value,
            String unit,
            Instant recordedAt
        ) {
            return new StageMetricSnapshot(localStageId, metricTypeCode, null, value, unit, false, recordedAt);
        }
    }
}
