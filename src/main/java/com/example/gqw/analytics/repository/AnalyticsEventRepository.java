package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.AnalyticsEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    @Query("select min(e.startedAt) from AnalyticsEvent e")
    Instant findMinStartedAt();

    Optional<AnalyticsEvent> findByEventUid(UUID eventUid);

    @Query("""
        select e from AnalyticsEvent e
        where e.traceId = :traceId
          and e.requestPath = :requestPath
          and e.startedAt >= :from
          and e.eventTypeCode not like 'FRONTEND_%'
        order by e.startedAt desc
        """)
    List<AnalyticsEvent> findRecentNonFrontendByTraceAndPath(
        @Param("traceId") String traceId,
        @Param("requestPath") String requestPath,
        @Param("from") Instant from,
        Pageable pageable
    );

    @Query("""
        select e from AnalyticsEvent e
        where e.requestPath = :requestPath
          and e.startedAt >= :from
          and e.eventTypeCode not like 'FRONTEND_%'
        order by e.startedAt desc
        """)
    List<AnalyticsEvent> findRecentNonFrontendByPath(
        @Param("requestPath") String requestPath,
        @Param("from") Instant from,
        Pageable pageable
    );

    @Query("""
        select e from AnalyticsEvent e
        where e.traceId = :traceId
          and e.startedAt >= :from
          and e.isError = true
          and e.eventTypeCode not like 'FRONTEND_%'
        order by e.startedAt desc
        """)
    List<AnalyticsEvent> findRecentErrorNonFrontendByTrace(
        @Param("traceId") String traceId,
        @Param("from") Instant from,
        Pageable pageable
    );

    @Query("""
        select e from AnalyticsEvent e
        where e.traceId = :traceId
          and e.startedAt >= :from
          and e.eventTypeCode not like 'FRONTEND_%'
        order by e.startedAt desc
        """)
    List<AnalyticsEvent> findRecentNonFrontendByTrace(
        @Param("traceId") String traceId,
        @Param("from") Instant from,
        Pageable pageable
    );

    long countByEventTypeCode(String eventTypeCode);

    long countByModuleCode(String moduleCode);

    @Modifying
    @Query(
        value = """
            update analytics.event
            set module_code = :moduleCode
            where event_type_code = :eventTypeCode
            """,
        nativeQuery = true
    )
    int bulkUpdateModuleCodeByEventTypeCode(
        @Param("eventTypeCode") String eventTypeCode,
        @Param("moduleCode") String moduleCode
    );

    @Query("""
        select e from AnalyticsEvent e
        where e.startedAt between :from and :to
          and (:eventTypeCode is null or e.eventTypeCode = :eventTypeCode)
          and (:moduleCode is null or e.moduleCode = :moduleCode)
          and (:status is null or (:status = 'ERROR' and e.isError = true) or (:status = 'SUCCESS' and e.isError = false))
        order by e.startedAt desc
        """)
    Page<AnalyticsEvent> findAllByFilter(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("eventTypeCode") String eventTypeCode,
        @Param("moduleCode") String moduleCode,
        @Param("status") String status,
        Pageable pageable
    );

    @Query("""
        select e from AnalyticsEvent e
        where e.startedAt between :from and :to
          and (:eventTypeCode is null or e.eventTypeCode = :eventTypeCode)
          and (:moduleCode is null or e.moduleCode = :moduleCode)
        """)
    List<AnalyticsEvent> findAllForAggregation(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("eventTypeCode") String eventTypeCode,
        @Param("moduleCode") String moduleCode
    );

    @Query("""
        select e from AnalyticsEvent e
        where e.startedAt between :from and :to
          and (:eventTypeCode is null or e.eventTypeCode = :eventTypeCode)
          and (:moduleCode is null or e.moduleCode = :moduleCode)
        order by e.startedAt asc
        """)
    List<AnalyticsEvent> findAllByRangeOrdered(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("eventTypeCode") String eventTypeCode,
        @Param("moduleCode") String moduleCode
    );

    @Query("""
        select e from AnalyticsEvent e
        where e.startedAt between :from and :to
          and (:eventTypeCode is null or e.eventTypeCode = :eventTypeCode)
          and (:moduleCode is null or e.moduleCode = :moduleCode)
          and exists (
              select 1 from AnalyticsEventAttribute a
              where a.eventId = e.id
                and a.attributeTypeCode = :attributeCode
                and (
                    :attributeValue is null
                    or lower(coalesce(a.attrValue, coalesce(a.attrValueJson, ''))) like lower(concat('%', :attributeValue, '%'))
                )
          )
        order by e.startedAt asc
        """)
    List<AnalyticsEvent> findAllByRangeOrderedWithAttribute(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("eventTypeCode") String eventTypeCode,
        @Param("moduleCode") String moduleCode,
        @Param("attributeCode") String attributeCode,
        @Param("attributeValue") String attributeValue
    );

    @Query("""
        select distinct e.eventTypeCode from AnalyticsEvent e
        where e.startedAt between :from and :to
          and (:moduleCode is null or e.moduleCode = :moduleCode)
          and (:requestPath is null or lower(coalesce(cast(e.requestPath as string), '')) like lower(concat('%', :requestPath, '%')))
        order by e.eventTypeCode asc
        """)
    List<String> findDistinctEventTypeCodesByScope(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("moduleCode") String moduleCode,
        @Param("requestPath") String requestPath
    );

    @Query("""
        select distinct e.eventTypeCode from AnalyticsEvent e
        where e.startedAt between :from and :to
          and (:moduleCode is null or e.moduleCode = :moduleCode)
        order by e.eventTypeCode asc
        """)
    List<String> findDistinctEventTypeCodesByScopeNoPath(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("moduleCode") String moduleCode
    );

    @Query(
        value = """
            select e from AnalyticsEvent e
            where e.startedAt between :from and :to
              and (:eventTypeCode is null or e.eventTypeCode = :eventTypeCode)
              and (:moduleCode is null or e.moduleCode = :moduleCode)
              and (:isError is null or e.isError = :isError)
              and (:minDurationMs is null or e.durationMs >= :minDurationMs)
              and (:requestPath is null or lower(coalesce(cast(e.requestPath as string), '')) like lower(concat('%', :requestPath, '%')))
            order by e.startedAt desc
            """,
        countQuery = """
            select count(e.id) from AnalyticsEvent e
            where e.startedAt between :from and :to
              and (:eventTypeCode is null or e.eventTypeCode = :eventTypeCode)
              and (:moduleCode is null or e.moduleCode = :moduleCode)
              and (:isError is null or e.isError = :isError)
              and (:minDurationMs is null or e.durationMs >= :minDurationMs)
              and (:requestPath is null or lower(coalesce(cast(e.requestPath as string), '')) like lower(concat('%', :requestPath, '%')))
            """
    )
    Page<AnalyticsEvent> searchEventsBase(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("eventTypeCode") String eventTypeCode,
        @Param("moduleCode") String moduleCode,
        @Param("isError") Boolean isError,
        @Param("minDurationMs") Integer minDurationMs,
        @Param("requestPath") String requestPath,
        Pageable pageable
    );

    @Query(
        value = """
            select e from AnalyticsEvent e
            where e.startedAt between :from and :to
              and (:eventTypeCode is null or e.eventTypeCode = :eventTypeCode)
              and (:moduleCode is null or e.moduleCode = :moduleCode)
              and (:isError is null or e.isError = :isError)
              and (:minDurationMs is null or e.durationMs >= :minDurationMs)
              and (:requestPath is null or lower(coalesce(cast(e.requestPath as string), '')) like lower(concat('%', :requestPath, '%')))
              and exists (
                  select 1 from AnalyticsEventAttribute a
                  where a.eventId = e.id
                    and a.attributeTypeCode = :attributeCode
                    and (
                        :attributeValue is null
                        or lower(coalesce(a.attrValue, coalesce(a.attrValueJson, ''))) like lower(concat('%', :attributeValue, '%'))
                    )
              )
            order by e.startedAt desc
            """,
        countQuery = """
            select count(e.id) from AnalyticsEvent e
            where e.startedAt between :from and :to
              and (:eventTypeCode is null or e.eventTypeCode = :eventTypeCode)
              and (:moduleCode is null or e.moduleCode = :moduleCode)
              and (:isError is null or e.isError = :isError)
              and (:minDurationMs is null or e.durationMs >= :minDurationMs)
              and (:requestPath is null or lower(coalesce(cast(e.requestPath as string), '')) like lower(concat('%', :requestPath, '%')))
              and exists (
                  select 1 from AnalyticsEventAttribute a
                  where a.eventId = e.id
                    and a.attributeTypeCode = :attributeCode
                    and (
                        :attributeValue is null
                        or lower(coalesce(a.attrValue, coalesce(a.attrValueJson, ''))) like lower(concat('%', :attributeValue, '%'))
                    )
              )
            """
    )
    Page<AnalyticsEvent> searchEventsByAttribute(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("eventTypeCode") String eventTypeCode,
        @Param("moduleCode") String moduleCode,
        @Param("isError") Boolean isError,
        @Param("minDurationMs") Integer minDurationMs,
        @Param("requestPath") String requestPath,
        @Param("attributeCode") String attributeCode,
        @Param("attributeValue") String attributeValue,
        Pageable pageable
    );
}

