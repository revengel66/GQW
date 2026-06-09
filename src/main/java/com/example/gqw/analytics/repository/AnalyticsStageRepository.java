package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.AnalyticsStage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsStageRepository extends JpaRepository<AnalyticsStage, Long> {

    List<AnalyticsStage> findByEventIdOrderByStageOrder(Long eventId);

    List<AnalyticsStage> findByEventIdInOrderByEventIdAscStageOrderAsc(Collection<Long> eventIds);

    List<AnalyticsStage> findByEventIdInAndStageTypeCodeOrderByEventIdAscStageOrderAsc(Collection<Long> eventIds, String stageTypeCode);

    Optional<AnalyticsStage> findTopByEventIdOrderByStageOrderDesc(Long eventId);

    long countByStageTypeCode(String stageTypeCode);
}

