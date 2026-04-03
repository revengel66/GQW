package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.AnalyticsStage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsStageRepository extends JpaRepository<AnalyticsStage, Long> {

    List<AnalyticsStage> findByEventIdOrderByStageOrder(Long eventId);
}

