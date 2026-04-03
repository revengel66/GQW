package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    Optional<AnalyticsEvent> findByEventUid(UUID eventUid);

    @Query("""
        select e from AnalyticsEvent e
        where e.startedAt between :from and :to
          and (:eventTypeCode is null or e.eventTypeCode = :eventTypeCode)
          and (:status is null or (:status = 'ERROR' and e.isError = true) or (:status = 'SUCCESS' and e.isError = false))
        order by e.startedAt desc
        """)
    Page<AnalyticsEvent> findAllByFilter(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("eventTypeCode") String eventTypeCode,
        @Param("status") String status,
        Pageable pageable
    );

    @Query("""
        select e from AnalyticsEvent e
        where e.startedAt between :from and :to
          and (:eventTypeCode is null or e.eventTypeCode = :eventTypeCode)
        """)
    List<AnalyticsEvent> findAllForAggregation(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("eventTypeCode") String eventTypeCode
    );
}

