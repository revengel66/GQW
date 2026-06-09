package com.example.gqw.analytics.service;

import com.example.gqw.analytics.aop.AnalyticsEventAspect;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsEventSnapshotBuffer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventSnapshotBuffer.class);
    private static final long WARN_INTERVAL_MS = 10_000L;

    private final AnalyticsEventSnapshotPersistenceService persistenceService;
    private final boolean enabled;
    private final int queueCapacity;
    private final long offerTimeoutMs;
    private final long shutdownTimeoutSeconds;
    private final ThreadLocal<AnalyticsEventSnapshot> currentSnapshot = new ThreadLocal<>();
    private final AtomicLong localStageIds = new AtomicLong(-1L);
    private final AtomicLong droppedSnapshots = new AtomicLong();
    private final AtomicLong failedSnapshots = new AtomicLong();
    private final AtomicLong lastWarnAtMs = new AtomicLong();

    private BlockingQueue<AnalyticsEventSnapshot> queue;
    private ExecutorService worker;
    private volatile boolean running;

    public AnalyticsEventSnapshotBuffer(
        AnalyticsEventSnapshotPersistenceService persistenceService,
        @Value("${app.analytics.snapshot.enabled:false}") boolean enabled,
        @Value("${app.analytics.snapshot.queue-capacity:${app.analytics.async.queue-capacity:50000}}") int queueCapacity,
        @Value("${app.analytics.snapshot.offer-timeout-ms:${app.analytics.async.offer-timeout-ms:5}}") long offerTimeoutMs,
        @Value("${app.analytics.snapshot.shutdown-timeout-seconds:${app.analytics.async.shutdown-timeout-seconds:30}}") long shutdownTimeoutSeconds
    ) {
        this.persistenceService = persistenceService;
        this.enabled = enabled;
        this.queueCapacity = Math.max(1, queueCapacity);
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
        worker = Executors.newSingleThreadExecutor(new SnapshotWriterThreadFactory());
        worker.submit(this::runWriter);
        log.info(
            "Analytics event snapshot buffer enabled: capacity={}, workerThreads=1, offerTimeoutMs={}, shutdownTimeoutSeconds={}",
            queueCapacity,
            offerTimeoutMs,
            shutdownTimeoutSeconds
        );
    }

    @PreDestroy
    public void stop() {
        currentSnapshot.remove();
        if (!enabled || worker == null) {
            return;
        }
        running = false;
        worker.shutdown();
        try {
            if (!worker.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("Analytics snapshot writer shutdown timeout: pendingSnapshots={}", pendingSnapshots());
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

    public boolean hasCurrentSnapshot() {
        return currentSnapshot.get() != null;
    }

    public int pendingSnapshots() {
        return queue == null ? 0 : queue.size();
    }

    public long droppedSnapshots() {
        return droppedSnapshots.get();
    }

    public long failedSnapshots() {
        return failedSnapshots.get();
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
        String moduleCodeHint = MDC.get(AnalyticsEventAspect.APP_MODULE_MDC_KEY);
        currentSnapshot.set(new AnalyticsEventSnapshot(
            eventUid,
            eventTypeCode,
            userId,
            sessionId,
            requestPath,
            httpMethod,
            traceId,
            moduleCodeHint,
            Instant.now()
        ));
        return eventUid;
    }

    public String resolveEventModuleCode(UUID eventUid) {
        AnalyticsEventSnapshot snapshot = currentSnapshot.get();
        if (snapshot == null || eventUid == null || !eventUid.equals(snapshot.eventUid())) {
            return null;
        }
        return snapshot.moduleCodeHint();
    }

    public void setEventStartedAtIfEarlier(UUID eventUid, Instant startedAt) {
        // Server-side snapshots already capture the event start timestamp on request path.
    }

    public void extendEventDurationIfLater(UUID eventUid, Instant endedAtCandidate) {
        // Frontend duration extension falls back to the async write buffer when no snapshot exists.
    }

    public void addAttribute(UUID eventUid, String attributeTypeCode, String value) {
        AnalyticsEventSnapshot snapshot = currentSnapshot.get();
        if (matches(snapshot, eventUid)) {
            snapshot.addAttribute(attributeTypeCode, value, false);
        }
    }

    public void addAttributeJson(UUID eventUid, String attributeTypeCode, String valueJson) {
        AnalyticsEventSnapshot snapshot = currentSnapshot.get();
        if (matches(snapshot, eventUid)) {
            snapshot.addAttribute(attributeTypeCode, valueJson, true);
        }
    }

    public Long startStage(UUID eventUid, String stageTypeCode, int stageOrder) {
        AnalyticsEventSnapshot snapshot = currentSnapshot.get();
        if (!matches(snapshot, eventUid)) {
            return null;
        }
        Long localStageId = localStageIds.getAndDecrement();
        snapshot.addStage(localStageId, stageTypeCode, stageOrder, Instant.now());
        return localStageId;
    }

    public void recordMetricNum(Long stageId, String metricTypeCode, BigDecimal value, String unit) {
        AnalyticsEventSnapshot snapshot = currentSnapshot.get();
        if (snapshot != null && stageId != null) {
            snapshot.addMetricNum(stageId, metricTypeCode, value, unit);
        }
    }

    public void recordMetricText(Long stageId, String metricTypeCode, String value, String unit) {
        AnalyticsEventSnapshot snapshot = currentSnapshot.get();
        if (snapshot != null && stageId != null) {
            snapshot.addMetricText(stageId, metricTypeCode, value, unit);
        }
    }

    public void finishStageSuccess(Long stageId) {
        AnalyticsEventSnapshot snapshot = currentSnapshot.get();
        if (snapshot != null && stageId != null) {
            snapshot.finishStageSuccess(stageId, Instant.now());
        }
    }

    public void finishStageError(Long stageId, String errorMessage) {
        AnalyticsEventSnapshot snapshot = currentSnapshot.get();
        if (snapshot != null && stageId != null) {
            snapshot.finishStageError(stageId, errorMessage, Instant.now());
        }
    }

    public void markStageLogWindow(Long stageId, Instant logStartedAt, Instant logEndedAt) {
        AnalyticsEventSnapshot snapshot = currentSnapshot.get();
        if (snapshot != null && stageId != null) {
            snapshot.markStageLogWindow(stageId, logStartedAt, logEndedAt);
        }
    }

    public void finishEventSuccess(UUID eventUid, Integer statusCode) {
        AnalyticsEventSnapshot snapshot = currentSnapshot.get();
        if (!matches(snapshot, eventUid)) {
            return;
        }
        snapshot.finishSuccess(statusCode, Instant.now());
        enqueueAndClear(snapshot);
    }

    public void finishEventError(UUID eventUid, Integer statusCode, String errorMessage) {
        AnalyticsEventSnapshot snapshot = currentSnapshot.get();
        if (!matches(snapshot, eventUid)) {
            return;
        }
        snapshot.finishError(statusCode, errorMessage, Instant.now());
        enqueueAndClear(snapshot);
    }

    private boolean matches(AnalyticsEventSnapshot snapshot, UUID eventUid) {
        return snapshot != null && eventUid != null && eventUid.equals(snapshot.eventUid());
    }

    private void enqueueAndClear(AnalyticsEventSnapshot snapshot) {
        currentSnapshot.remove();
        if (queue == null) {
            dropSnapshot("snapshotQueueNotStarted", null);
            return;
        }
        try {
            boolean accepted = queue.offer(snapshot, offerTimeoutMs, TimeUnit.MILLISECONDS);
            if (!accepted) {
                dropSnapshot("snapshotQueueFull", null);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            dropSnapshot("snapshotQueueInterrupted", ex);
        }
    }

    private void runWriter() {
        while (running || (queue != null && !queue.isEmpty())) {
            try {
                AnalyticsEventSnapshot snapshot = queue.poll(200L, TimeUnit.MILLISECONDS);
                if (snapshot == null) {
                    continue;
                }
                persist(snapshot);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void persist(AnalyticsEventSnapshot snapshot) {
        try {
            persistenceService.persist(snapshot);
        } catch (RuntimeException ex) {
            failedSnapshots.incrementAndGet();
            warn("Analytics snapshot write failed", ex);
        }
    }

    private void dropSnapshot(String reason, Exception cause) {
        droppedSnapshots.incrementAndGet();
        warn("Analytics snapshot dropped: reason=" + reason, cause);
    }

    private void warn(String message, Exception cause) {
        long now = System.currentTimeMillis();
        long last = lastWarnAtMs.get();
        if (now - last < WARN_INTERVAL_MS || !lastWarnAtMs.compareAndSet(last, now)) {
            return;
        }
        if (cause == null) {
            log.warn("{} pendingSnapshots={} droppedSnapshots={} failedSnapshots={}", message, pendingSnapshots(), droppedSnapshots(), failedSnapshots());
        } else {
            log.warn(
                "{} pendingSnapshots={} droppedSnapshots={} failedSnapshots={} detail={}",
                message,
                pendingSnapshots(),
                droppedSnapshots(),
                failedSnapshots(),
                cause.getMessage(),
                cause
            );
        }
    }

    private static final class SnapshotWriterThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "analytics-snapshot-writer-" + counter.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
