package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.AggregatedMetric;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AggregatedMetricRepository extends JpaRepository<AggregatedMetric, Long> {

    @Modifying
    @Transactional(transactionManager = "analyticsTransactionManager")
    @Query("""
        delete from AggregatedMetric m
        where m.periodStart = :periodStart and m.periodEnd = :periodEnd
        """)
    int deleteByPeriod(@Param("periodStart") Instant periodStart, @Param("periodEnd") Instant periodEnd);

    @Query("""
        select m from AggregatedMetric m
        where m.periodStart between :from and :to
          and (:eventTypeCode is null or m.eventTypeCode = :eventTypeCode)
          and (:stageTypeCode is null or m.stageTypeCode = :stageTypeCode)
        order by m.periodStart asc
        """)
    List<AggregatedMetric> findByFilter(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("eventTypeCode") String eventTypeCode,
        @Param("stageTypeCode") String stageTypeCode
    );
}

