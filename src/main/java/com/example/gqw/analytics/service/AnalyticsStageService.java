package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import com.example.gqw.analytics.entity.AnalyticsStage;
import com.example.gqw.analytics.entity.StageType;
import com.example.gqw.analytics.repository.AnalyticsStageRepository;
import com.example.gqw.analytics.repository.StageTypeRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsStageService {

    private final AnalyticsStageRepository stageRepository;
    private final StageTypeRepository stageTypeRepository;

    public AnalyticsStageService(AnalyticsStageRepository stageRepository, StageTypeRepository stageTypeRepository) {
        this.stageRepository = stageRepository;
        this.stageTypeRepository = stageTypeRepository;
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public AnalyticsStage createStage(AnalyticsEvent event, String stageTypeCode, int stageOrder) {
        StageType stageType = stageTypeRepository.findById(stageTypeCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown stage type: " + stageTypeCode));
        if (!Boolean.TRUE.equals(stageType.getIsActive())) {
            throw new IllegalArgumentException("Inactive stage type: " + stageTypeCode);
        }

        AnalyticsStage stage = new AnalyticsStage();
        stage.setEventId(event.getId());
        stage.setStageTypeCode(stageTypeCode);
        stage.setStageOrder(stageOrder);
        stage.setStartedAt(Instant.now());
        stage.setIsError(false);
        return stageRepository.save(stage);
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void finishStageSuccess(Long stageId) {
        AnalyticsStage stage = stageRepository.findById(stageId)
            .orElseThrow(() -> new IllegalArgumentException("Stage not found: " + stageId));
        Instant endedAt = Instant.now();
        stage.setEndedAt(endedAt);
        stage.setIsError(false);
        stage.setDurationMs((int) Duration.between(stage.getStartedAt(), endedAt).toMillis());
        stageRepository.save(stage);
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void finishStageError(Long stageId, String errorMessage) {
        AnalyticsStage stage = stageRepository.findById(stageId)
            .orElseThrow(() -> new IllegalArgumentException("Stage not found: " + stageId));
        Instant endedAt = Instant.now();
        stage.setEndedAt(endedAt);
        stage.setIsError(true);
        stage.setErrorMessage(errorMessage);
        stage.setDurationMs((int) Duration.between(stage.getStartedAt(), endedAt).toMillis());
        stageRepository.save(stage);
    }

    @Transactional(transactionManager = "analyticsTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void markStageLogWindow(Long stageId, Instant logStartedAt, Instant logEndedAt) {
        if (stageId == null || logStartedAt == null || logEndedAt == null) {
            return;
        }
        AnalyticsStage stage = stageRepository.findById(stageId)
            .orElseThrow(() -> new IllegalArgumentException("Stage not found: " + stageId));
        if (stage.getLogStartedAt() != null && stage.getLogEndedAt() != null) {
            return;
        }
        stage.setLogStartedAt(logStartedAt);
        stage.setLogEndedAt(logEndedAt);
        stageRepository.save(stage);
    }
}

