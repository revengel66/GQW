package com.example.gqw.analytics.aop;

import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

public final class AnalyticsEventContext {

    private final UUID eventUid;
    private final AtomicInteger stageOrder = new AtomicInteger(1);
    private final Deque<Long> activeStageIds = new ArrayDeque<>();

    public AnalyticsEventContext(UUID eventUid) {
        this.eventUid = eventUid;
    }

    public UUID eventUid() {
        return eventUid;
    }

    public int nextStageOrder() {
        return stageOrder.getAndIncrement();
    }

    public void pushStageId(Long stageId) {
        if (stageId != null) {
            activeStageIds.push(stageId);
        }
    }

    public void popStageId(Long stageId) {
        if (stageId == null || activeStageIds.isEmpty()) {
            return;
        }
        if (stageId.equals(activeStageIds.peek())) {
            activeStageIds.pop();
            return;
        }
        activeStageIds.remove(stageId);
    }

    public Long currentStageId() {
        return activeStageIds.peek();
    }
}
