package com.example.gqw.analytics.service;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsRollupBootstrapState {

    public static final String INITIALIZING_WARNING =
        "Rollup initializing. Analytics data will appear after background aggregation completes.";
    private static final String FAILED_WARNING_PREFIX = "Rollup bootstrap failed: ";

    private final AtomicReference<Status> status = new AtomicReference<>(Status.READY);
    private final AtomicReference<String> failure = new AtomicReference<>();
    private volatile boolean blockReads;

    public void prepare(boolean usableRollupDataExists) {
        failure.set(null);
        blockReads = !usableRollupDataExists;
        status.set(usableRollupDataExists ? Status.READY : Status.INITIALIZING);
    }

    public void complete() {
        blockReads = false;
        status.set(Status.READY);
    }

    public void fail(Throwable throwable) {
        String message = throwable == null ? "Unknown rollup bootstrap error" : throwable.getMessage();
        failure.set(message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message);
        status.set(Status.FAILED);
    }

    public boolean isInitializing() {
        return status.get() == Status.INITIALIZING;
    }

    public boolean shouldBlockReads() {
        return blockReads && status.get() != Status.READY;
    }

    public String readWarning() {
        if (status.get() == Status.FAILED) {
            return FAILED_WARNING_PREFIX + failure.get();
        }
        return INITIALIZING_WARNING;
    }

    public String failure() {
        return failure.get();
    }

    private enum Status {
        READY,
        INITIALIZING,
        FAILED
    }
}
