package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.entity.AnalyticsStage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsAsyncWriteBuffer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAsyncWriteBuffer.class);
    private static final long WARN_INTERVAL_MS = 10_000L;

    private final AnalyticsEventService eventService;
    private final AnalyticsStageService stageService;
    private final AnalyticsEventAttributeService eventAttributeService;
    private final AnalyticsStageMetricService stageMetricService;
    private final boolean enabled;
    private final int queueCapacity;
    private final int configuredWorkerThreads;
    private final long offerTimeoutMs;
    private final long shutdownTimeoutSeconds;
    private final AtomicLong tempStageIds = new AtomicLong(-1L);
    private final AtomicLong droppedOperations = new AtomicLong();
    private final AtomicLong lastWarnAtMs = new AtomicLong();
    private final ConcurrentMap<Long, Long> stageIdMap = new ConcurrentHashMap<>();

    private BlockingQueue<AnalyticsWriteOperation> queue;
    private ExecutorService worker;
    private volatile boolean running;

    public AnalyticsAsyncWriteBuffer(
        AnalyticsEventService eventService,
        AnalyticsStageService stageService,
        AnalyticsEventAttributeService eventAttributeService,
        AnalyticsStageMetricService stageMetricService,
        @Value("${app.analytics.async.enabled:false}") boolean enabled,
        @Value("${app.analytics.async.queue-capacity:50000}") int queueCapacity,
        @Value("${app.analytics.async.worker-threads:1}") int workerThreads,
        @Value("${app.analytics.async.offer-timeout-ms:5}") long offerTimeoutMs,
        @Value("${app.analytics.async.shutdown-timeout-seconds:30}") long shutdownTimeoutSeconds
    ) {
        this.eventService = eventService;
        this.stageService = stageService;
        this.eventAttributeService = eventAttributeService;
        this.stageMetricService = stageMetricService;
        this.enabled = enabled;
        this.queueCapacity = Math.max(1, queueCapacity);
        this.configuredWorkerThreads = Math.max(1, workerThreads);
        this.offerTimeoutMs = Math.max(0L, offerTimeoutMs);
        this.shutdownTimeoutSeconds = Math.max(1L, shutdownTimeoutSeconds);
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            return;
        }
        queue = new ArrayBlockingQueue<>(queueCapacity);
        running = true;
        worker = Executors.newSingleThreadExecutor(new AnalyticsWriterThreadFactory());
        worker.submit(this::runWriter);
        log.info(
            "Analytics async write buffer enabled: capacity={}, configuredWorkerThreads={}, activeWorkerThreads=1, offerTimeoutMs={}, shutdownTimeoutSeconds={}",
            queueCapacity,
            configuredWorkerThreads,
            offerTimeoutMs,
            shutdownTimeoutSeconds
        );
    }

    @PreDestroy
    public void stop() {
        if (!enabled || worker == null) {
            return;
        }
        running = false;
        worker.shutdown();
        try {
            if (!worker.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                long pending = queue == null ? 0 : queue.size();
                log.warn("Analytics async writer shutdown timeout: pendingOperations={}", pending);
                worker.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long droppedOperations() {
        return droppedOperations.get();
    }

    public int pendingOperations() {
        return queue == null ? 0 : queue.size();
    }

    public UUID startEvent(
        String eventTypeCode,
        Long userId,
        String sessionId,
        String requestPath,
        String httpMethod,
        String traceId
    ) {
        UUID eventUid = UUID.randomUUID();
        submit(new StartEventOperation(eventUid, eventTypeCode, userId, sessionId, requestPath, httpMethod, traceId));
        return eventUid;
    }

    public void setEventStartedAtIfEarlier(UUID eventUid, Instant startedAt) {
        if (eventUid == null || startedAt == null) {
            return;
        }
        submit(new SetEventStartedAtOperation(eventUid, startedAt));
    }

    public void extendEventDurationIfLater(UUID eventUid, Instant endedAtCandidate) {
        if (eventUid == null || endedAtCandidate == null) {
            return;
        }
        submit(new ExtendEventDurationOperation(eventUid, endedAtCandidate));
    }

    public void addAttribute(UUID eventUid, String attributeTypeCode, String value) {
        if (eventUid == null) {
            return;
        }
        submit(new AddAttributeOperation(eventUid, attributeTypeCode, value, false));
    }

    public void addAttributeJson(UUID eventUid, String attributeTypeCode, String valueJson) {
        if (eventUid == null) {
            return;
        }
        submit(new AddAttributeOperation(eventUid, attributeTypeCode, valueJson, true));
    }

    public Long startStage(UUID eventUid, String stageTypeCode, int stageOrder) {
        if (eventUid == null) {
            return null;
        }
        Long tempStageId = tempStageIds.getAndDecrement();
        submit(new StartStageOperation(tempStageId, eventUid, stageTypeCode, stageOrder));
        return tempStageId;
    }

    public void recordMetricNum(Long stageId, String metricTypeCode, BigDecimal value, String unit) {
        if (stageId == null) {
            return;
        }
        submit(new RecordMetricNumOperation(stageId, metricTypeCode, value, unit));
    }

    public void recordMetricText(Long stageId, String metricTypeCode, String value, String unit) {
        if (stageId == null) {
            return;
        }
        submit(new RecordMetricTextOperation(stageId, metricTypeCode, value, unit));
    }

    public void finishStageSuccess(Long stageId) {
        if (stageId == null) {
            return;
        }
        submit(new FinishStageSuccessOperation(stageId));
    }

    public void finishStageError(Long stageId, String errorMessage) {
        if (stageId == null) {
            return;
        }
        submit(new FinishStageErrorOperation(stageId, errorMessage));
    }

    public void markStageLogWindow(Long stageId, Instant logStartedAt, Instant logEndedAt) {
        if (stageId == null || logStartedAt == null || logEndedAt == null) {
            return;
        }
        submit(new MarkStageLogWindowOperation(stageId, logStartedAt, logEndedAt));
    }

    public void finishEventSuccess(UUID eventUid, Integer statusCode) {
        if (eventUid == null) {
            return;
        }
        submit(new FinishEventSuccessOperation(eventUid, statusCode));
    }

    public void finishEventError(UUID eventUid, Integer statusCode, String errorMessage) {
        if (eventUid == null) {
            return;
        }
        submit(new FinishEventErrorOperation(eventUid, statusCode, errorMessage));
    }

    private void submit(AnalyticsWriteOperation operation) {
        if (queue == null) {
            dropOperation(operation, null);
            return;
        }
        try {
            boolean accepted = queue.offer(operation, offerTimeoutMs, TimeUnit.MILLISECONDS);
            if (!accepted) {
                dropOperation(operation, null);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            dropOperation(operation, ex);
        }
    }

    private void dropOperation(AnalyticsWriteOperation operation, Exception cause) {
        long dropped = droppedOperations.incrementAndGet();
        long now = System.currentTimeMillis();
        long last = lastWarnAtMs.get();
        if (now - last < WARN_INTERVAL_MS || !lastWarnAtMs.compareAndSet(last, now)) {
            return;
        }
        if (cause == null) {
            log.warn(
                "Analytics async write dropped: operation={} droppedTotal={} pending={}",
                operation.name(),
                dropped,
                pendingOperations()
            );
        } else {
            log.warn(
                "Analytics async write dropped: operation={} droppedTotal={} pending={} reason={}",
                operation.name(),
                dropped,
                pendingOperations(),
                cause.getMessage()
            );
        }
    }

    private void runWriter() {
        while (running || (queue != null && !queue.isEmpty())) {
            try {
                AnalyticsWriteOperation operation = queue.poll(200L, TimeUnit.MILLISECONDS);
                if (operation == null) {
                    continue;
                }
                execute(operation);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void execute(AnalyticsWriteOperation operation) {
        try {
            operation.execute(this);
        } catch (RuntimeException ex) {
            warnOperationFailure(operation, ex);
        }
    }

    private void warnOperationFailure(AnalyticsWriteOperation operation, RuntimeException ex) {
        long now = System.currentTimeMillis();
        long last = lastWarnAtMs.get();
        if (now - last < WARN_INTERVAL_MS || !lastWarnAtMs.compareAndSet(last, now)) {
            return;
        }
        log.warn(
            "Analytics async write failed: operation={} pending={} droppedTotal={} reason={}",
            operation.name(),
            pendingOperations(),
            droppedOperations.get(),
            ex.getMessage(),
            ex
        );
    }

    private Long resolveStageId(Long stageId) {
        if (stageId == null) {
            return null;
        }
        if (stageId >= 0) {
            return stageId;
        }
        return stageIdMap.get(stageId);
    }

    private void removeStageMapping(Long stageId) {
        if (stageId != null && stageId < 0) {
            stageIdMap.remove(stageId);
        }
    }

    private interface AnalyticsWriteOperation {
        String name();
        void execute(AnalyticsAsyncWriteBuffer buffer);
    }

    private record StartEventOperation(
        UUID eventUid,
        String eventTypeCode,
        Long userId,
        String sessionId,
        String requestPath,
        String httpMethod,
        String traceId
    ) implements AnalyticsWriteOperation {
        @Override
        public String name() {
            return "startEvent";
        }

        @Override
        public void execute(AnalyticsAsyncWriteBuffer buffer) {
            buffer.eventService.createEvent(eventUid, eventTypeCode, userId, sessionId, requestPath, httpMethod, traceId);
        }
    }

    private record SetEventStartedAtOperation(UUID eventUid, Instant startedAt) implements AnalyticsWriteOperation {
        @Override
        public String name() {
            return "setEventStartedAtIfEarlier";
        }

        @Override
        public void execute(AnalyticsAsyncWriteBuffer buffer) {
            buffer.eventService.setStartedAtIfEarlier(eventUid, startedAt);
        }
    }

    private record ExtendEventDurationOperation(UUID eventUid, Instant endedAtCandidate) implements AnalyticsWriteOperation {
        @Override
        public String name() {
            return "extendEventDurationIfLater";
        }

        @Override
        public void execute(AnalyticsAsyncWriteBuffer buffer) {
            buffer.eventService.extendEventDurationIfLater(eventUid, endedAtCandidate);
        }
    }

    private record AddAttributeOperation(
        UUID eventUid,
        String attributeTypeCode,
        String value,
        boolean json
    ) implements AnalyticsWriteOperation {
        @Override
        public String name() {
            return json ? "addAttributeJson" : "addAttribute";
        }

        @Override
        public void execute(AnalyticsAsyncWriteBuffer buffer) {
            AnalyticsEvent event = buffer.eventService.findByEventUid(eventUid);
            if (json) {
                buffer.eventAttributeService.addJsonAttribute(event.getId(), attributeTypeCode, value);
            } else {
                buffer.eventAttributeService.addTextAttribute(event.getId(), attributeTypeCode, value);
            }
        }
    }

    private record StartStageOperation(
        Long tempStageId,
        UUID eventUid,
        String stageTypeCode,
        int stageOrder
    ) implements AnalyticsWriteOperation {
        @Override
        public String name() {
            return "startStage";
        }

        @Override
        public void execute(AnalyticsAsyncWriteBuffer buffer) {
            AnalyticsEvent event = buffer.eventService.findByEventUid(eventUid);
            AnalyticsStage stage = buffer.stageService.createStage(event, stageTypeCode, stageOrder);
            buffer.stageIdMap.put(tempStageId, stage.getId());
        }
    }

    private record RecordMetricNumOperation(
        Long stageId,
        String metricTypeCode,
        BigDecimal value,
        String unit
    ) implements AnalyticsWriteOperation {
        @Override
        public String name() {
            return "recordMetricNum";
        }

        @Override
        public void execute(AnalyticsAsyncWriteBuffer buffer) {
            Long actualStageId = buffer.resolveStageId(stageId);
            if (actualStageId != null) {
                buffer.stageMetricService.recordMetricNum(actualStageId, metricTypeCode, value, unit);
            }
        }
    }

    private record RecordMetricTextOperation(
        Long stageId,
        String metricTypeCode,
        String value,
        String unit
    ) implements AnalyticsWriteOperation {
        @Override
        public String name() {
            return "recordMetricText";
        }

        @Override
        public void execute(AnalyticsAsyncWriteBuffer buffer) {
            Long actualStageId = buffer.resolveStageId(stageId);
            if (actualStageId != null) {
                buffer.stageMetricService.recordMetricText(actualStageId, metricTypeCode, value, unit);
            }
        }
    }

    private record FinishStageSuccessOperation(Long stageId) implements AnalyticsWriteOperation {
        @Override
        public String name() {
            return "finishStageSuccess";
        }

        @Override
        public void execute(AnalyticsAsyncWriteBuffer buffer) {
            Long actualStageId = buffer.resolveStageId(stageId);
            if (actualStageId != null) {
                buffer.stageService.finishStageSuccess(actualStageId);
                buffer.removeStageMapping(stageId);
            }
        }
    }

    private record FinishStageErrorOperation(Long stageId, String errorMessage) implements AnalyticsWriteOperation {
        @Override
        public String name() {
            return "finishStageError";
        }

        @Override
        public void execute(AnalyticsAsyncWriteBuffer buffer) {
            Long actualStageId = buffer.resolveStageId(stageId);
            if (actualStageId != null) {
                buffer.stageService.finishStageError(actualStageId, errorMessage);
                buffer.removeStageMapping(stageId);
            }
        }
    }

    private record MarkStageLogWindowOperation(
        Long stageId,
        Instant logStartedAt,
        Instant logEndedAt
    ) implements AnalyticsWriteOperation {
        @Override
        public String name() {
            return "markStageLogWindow";
        }

        @Override
        public void execute(AnalyticsAsyncWriteBuffer buffer) {
            Long actualStageId = buffer.resolveStageId(stageId);
            if (actualStageId != null) {
                buffer.stageService.markStageLogWindow(actualStageId, logStartedAt, logEndedAt);
            }
        }
    }

    private record FinishEventSuccessOperation(UUID eventUid, Integer statusCode) implements AnalyticsWriteOperation {
        @Override
        public String name() {
            return "finishEventSuccess";
        }

        @Override
        public void execute(AnalyticsAsyncWriteBuffer buffer) {
            buffer.eventService.finishEventSuccess(eventUid, statusCode);
        }
    }

    private record FinishEventErrorOperation(
        UUID eventUid,
        Integer statusCode,
        String errorMessage
    ) implements AnalyticsWriteOperation {
        @Override
        public String name() {
            return "finishEventError";
        }

        @Override
        public void execute(AnalyticsAsyncWriteBuffer buffer) {
            buffer.eventService.finishEventError(eventUid, statusCode, errorMessage);
        }
    }

    private static final class AnalyticsWriterThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "analytics-async-writer-" + counter.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
