package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.AggregationGranularity;
import com.example.gqw.analytics.entity.AggregationRun;
import com.example.gqw.analytics.entity.AggregationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AggregationRunRepository extends JpaRepository<AggregationRun, Long> {

    Optional<AggregationRun> findTopByGranularityAndStatusOrderByPeriodEndDesc(
        AggregationGranularity granularity,
        AggregationStatus status
    );
}

