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

    @Query(
        value = """
            select count(e.id)
            from analytics.event e
            where e.started_at between :from and :to
              and (:eventTypeCode is null or e.event_type_code = :eventTypeCode)
              and (:moduleCode is null or e.module_code = :moduleCode)
              and (
                  cast(:requestPath as text) is null
                  or lower(coalesce(e.request_path, '')) like concat('%', lower(cast(:requestPath as text)), '%')
              )
            """,
        nativeQuery = true
    )
    long countByRangeForAdmin(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("eventTypeCode") String eventTypeCode,
        @Param("moduleCode") String moduleCode,
        @Param("requestPath") String requestPath
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

    @Query(
        value = """
            select count(e.id)
            from analytics.event e
            where e.started_at between :from and :to
              and (:eventTypeCode is null or e.event_type_code = :eventTypeCode)
              and (:moduleCode is null or e.module_code = :moduleCode)
              and (
                  cast(:requestPath as text) is null
                  or lower(coalesce(e.request_path, '')) like concat('%', lower(cast(:requestPath as text)), '%')
              )
              and exists (
                  select 1
                  from analytics.event_attribute a
                  where a.event_id = e.id
                    and a.attribute_type_code = :attributeCode
                    and (
                        cast(:attributeValue as text) is null
                        or lower(coalesce(a.attr_value, coalesce(a.attr_value_json, '')))
                            like concat('%', lower(cast(:attributeValue as text)), '%')
                    )
              )
            """,
        nativeQuery = true
    )
    long countByRangeWithAttributeForAdmin(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("eventTypeCode") String eventTypeCode,
        @Param("moduleCode") String moduleCode,
        @Param("requestPath") String requestPath,
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
        @Param("requestPath") String requestPath,
        Pageable pageable
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
        @Param("moduleCode") String moduleCode,
        Pageable pageable
    );

    @Query("""
        select distinct e.moduleCode from AnalyticsEvent e
        where e.startedAt between :from and :to
          and e.moduleCode is not null
          and e.eventTypeCode in :eventTypeCodes
          and (:requestPath is null or lower(coalesce(cast(e.requestPath as string), '')) like lower(concat('%', :requestPath, '%')))
        order by e.moduleCode asc
        """)
    List<String> findDistinctModuleCodesByScope(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("eventTypeCodes") java.util.Collection<String> eventTypeCodes,
        @Param("requestPath") String requestPath
    );

    @Query("""
        select distinct e.moduleCode from AnalyticsEvent e
        where e.startedAt between :from and :to
          and e.moduleCode is not null
          and e.eventTypeCode in :eventTypeCodes
        order by e.moduleCode asc
        """)
    List<String> findDistinctModuleCodesByScopeNoPath(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("eventTypeCodes") java.util.Collection<String> eventTypeCodes
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

    @Query(
        value = """
            select e.*
              from analytics.event e
              join analytics.event_type et on et.code = e.event_type_code
             where e.started_at between :from and :to
               and et.is_system = :systemEventsOnly
               and (:eventTypeFilterEnabled = false or e.event_type_code in (:eventTypeCodes))
               and (:moduleCode is null or e.module_code = :moduleCode)
               and (
                    :stageTypeCode is null
                    or exists (
                        select 1
                          from analytics.stage stage_filter
                         where stage_filter.event_id = e.id
                           and stage_filter.stage_type_code = :stageTypeCode
                    )
               )
               and (:isError is null or e.is_error = :isError)
               and (:errorKey is null or concat(coalesce(e.status_code, 0), '|', coalesce(nullif(trim(e.error_message), ''), 'UNKNOWN')) = :errorKey)
               and (:minDurationMs is null or e.duration_ms >= :minDurationMs)
               and (:requestPath is null or lower(coalesce(e.request_path, '')) like lower(concat('%', :requestPath, '%')))
               and (
                    :errorClass is null
                    or (:errorClass = 'NONE' and coalesce(e.is_error, false) = false)
                    or (:errorClass = 'VALIDATION' and coalesce(e.is_error, false) = true and coalesce(e.status_code, 0) < 500 and (
                        lower(coalesce(e.error_message, '')) like '%required request parameter%'
                        or lower(coalesce(e.error_message, '')) like '%validation%'
                        or lower(coalesce(e.error_message, '')) like '%bind%'
                        or lower(coalesce(e.error_message, '')) like '%failed to convert%'
                        or lower(coalesce(e.error_message, '')) like '%cannot be null%'
                        or lower(coalesce(e.error_message, '')) like '%must not%'
                        or lower(coalesce(e.error_message, '')) like '%некоррект%'
                        or lower(coalesce(e.error_message, '')) like '%не заполн%'
                        or lower(coalesce(e.error_message, '')) like '%обязатель%'
                        or lower(coalesce(e.error_message, '')) like '%валидац%'
                    ))
                    or (:errorClass = 'BUSINESS' and coalesce(e.is_error, false) = true and (
                        (e.status_code = 400 and not (
                            lower(coalesce(e.error_message, '')) like '%required request parameter%'
                            or lower(coalesce(e.error_message, '')) like '%validation%'
                            or lower(coalesce(e.error_message, '')) like '%bind%'
                            or lower(coalesce(e.error_message, '')) like '%failed to convert%'
                            or lower(coalesce(e.error_message, '')) like '%cannot be null%'
                            or lower(coalesce(e.error_message, '')) like '%must not%'
                            or lower(coalesce(e.error_message, '')) like '%некоррект%'
                            or lower(coalesce(e.error_message, '')) like '%не заполн%'
                            or lower(coalesce(e.error_message, '')) like '%обязатель%'
                            or lower(coalesce(e.error_message, '')) like '%валидац%'
                        ))
                        or e.status_code between 401 and 499
                    ))
                    or (:errorClass = 'SYSTEM' and coalesce(e.is_error, false) = true and (
                        coalesce(e.status_code, 0) >= 500
                        or (
                            (e.status_code is null or e.status_code < 400 or e.status_code > 499)
                            and not (
                                lower(coalesce(e.error_message, '')) like '%required request parameter%'
                                or lower(coalesce(e.error_message, '')) like '%validation%'
                                or lower(coalesce(e.error_message, '')) like '%bind%'
                                or lower(coalesce(e.error_message, '')) like '%failed to convert%'
                                or lower(coalesce(e.error_message, '')) like '%cannot be null%'
                                or lower(coalesce(e.error_message, '')) like '%must not%'
                                or lower(coalesce(e.error_message, '')) like '%некоррект%'
                                or lower(coalesce(e.error_message, '')) like '%не заполн%'
                                or lower(coalesce(e.error_message, '')) like '%обязатель%'
                                or lower(coalesce(e.error_message, '')) like '%валидац%'
                            )
                        )
                    ))
               )
               and (
                    :attributeCode is null
                    or exists (
                        select 1
                          from analytics.event_attribute a
                         where a.event_id = e.id
                           and a.attribute_type_code = :attributeCode
                           and (
                                :attributeValue is null
                                or lower(coalesce(a.attr_value, coalesce(a.attr_value_json, ''))) like lower(concat('%', :attributeValue, '%'))
                           )
                    )
               )
               and (
                    :metricFilterEnabled = false
                    or exists (
                        select 1
                          from analytics.stage s
                          join analytics.stage_metric m on m.stage_id = s.id
                         where s.event_id = e.id
                           and (:metricTypeCode is null or m.metric_type_code = :metricTypeCode)
                           and (:metricMinValue is null or m.metric_value_num >= :metricMinValue)
                           and (:metricMaxValue is null or m.metric_value_num <= :metricMaxValue)
                    )
               )
             order by
               case when :sortBy = 'durationMs' and :ascending = true then e.duration_ms end asc nulls last,
               case when :sortBy = 'durationMs' and :ascending = false then e.duration_ms end desc nulls last,
               case when :sortBy = 'statusCode' and :ascending = true then e.status_code end asc nulls last,
               case when :sortBy = 'statusCode' and :ascending = false then e.status_code end desc nulls last,
               case when :sortBy = 'eventTypeCode' and :ascending = true then e.event_type_code end asc nulls last,
               case when :sortBy = 'eventTypeCode' and :ascending = false then e.event_type_code end desc nulls last,
               case when :sortBy = 'traceId' and :ascending = true then e.trace_id end asc nulls last,
               case when :sortBy = 'traceId' and :ascending = false then e.trace_id end desc nulls last,
               case when :sortBy = 'requestPath' and :ascending = true then e.request_path end asc nulls last,
               case when :sortBy = 'requestPath' and :ascending = false then e.request_path end desc nulls last,
               case when :sortBy = 'isError' and :ascending = true then e.is_error end asc nulls last,
               case when :sortBy = 'isError' and :ascending = false then e.is_error end desc nulls last,
               case when :sortBy = 'metricValue' and :ascending = true then (
                    select min(m.metric_value_num)
                      from analytics.stage s
                      join analytics.stage_metric m on m.stage_id = s.id
                     where s.event_id = e.id
                       and (:metricTypeCode is null or m.metric_type_code = :metricTypeCode)
               ) end asc nulls last,
               case when :sortBy = 'metricValue' and :ascending = false then (
                    select min(m.metric_value_num)
                      from analytics.stage s
                      join analytics.stage_metric m on m.stage_id = s.id
                     where s.event_id = e.id
                       and (:metricTypeCode is null or m.metric_type_code = :metricTypeCode)
               ) end desc nulls last,
               case when :sortBy = 'startedAt' and :ascending = true then e.started_at end asc nulls last,
               case when :sortBy = 'startedAt' and :ascending = false then e.started_at end desc nulls last,
               e.started_at desc,
               e.id desc
            """,
        countQuery = """
            select count(e.id)
              from analytics.event e
              join analytics.event_type et on et.code = e.event_type_code
             where e.started_at between :from and :to
               and et.is_system = :systemEventsOnly
               and (:eventTypeFilterEnabled = false or e.event_type_code in (:eventTypeCodes))
               and (:moduleCode is null or e.module_code = :moduleCode)
               and (
                    :stageTypeCode is null
                    or exists (
                        select 1
                          from analytics.stage stage_filter
                         where stage_filter.event_id = e.id
                           and stage_filter.stage_type_code = :stageTypeCode
                    )
               )
               and (:isError is null or e.is_error = :isError)
               and (:errorKey is null or concat(coalesce(e.status_code, 0), '|', coalesce(nullif(trim(e.error_message), ''), 'UNKNOWN')) = :errorKey)
               and (:minDurationMs is null or e.duration_ms >= :minDurationMs)
               and (:requestPath is null or lower(coalesce(e.request_path, '')) like lower(concat('%', :requestPath, '%')))
               and (
                    :errorClass is null
                    or (:errorClass = 'NONE' and coalesce(e.is_error, false) = false)
                    or (:errorClass = 'VALIDATION' and coalesce(e.is_error, false) = true and coalesce(e.status_code, 0) < 500 and (
                        lower(coalesce(e.error_message, '')) like '%required request parameter%'
                        or lower(coalesce(e.error_message, '')) like '%validation%'
                        or lower(coalesce(e.error_message, '')) like '%bind%'
                        or lower(coalesce(e.error_message, '')) like '%failed to convert%'
                        or lower(coalesce(e.error_message, '')) like '%cannot be null%'
                        or lower(coalesce(e.error_message, '')) like '%must not%'
                        or lower(coalesce(e.error_message, '')) like '%некоррект%'
                        or lower(coalesce(e.error_message, '')) like '%не заполн%'
                        or lower(coalesce(e.error_message, '')) like '%обязатель%'
                        or lower(coalesce(e.error_message, '')) like '%валидац%'
                    ))
                    or (:errorClass = 'BUSINESS' and coalesce(e.is_error, false) = true and (
                        (e.status_code = 400 and not (
                            lower(coalesce(e.error_message, '')) like '%required request parameter%'
                            or lower(coalesce(e.error_message, '')) like '%validation%'
                            or lower(coalesce(e.error_message, '')) like '%bind%'
                            or lower(coalesce(e.error_message, '')) like '%failed to convert%'
                            or lower(coalesce(e.error_message, '')) like '%cannot be null%'
                            or lower(coalesce(e.error_message, '')) like '%must not%'
                            or lower(coalesce(e.error_message, '')) like '%некоррект%'
                            or lower(coalesce(e.error_message, '')) like '%не заполн%'
                            or lower(coalesce(e.error_message, '')) like '%обязатель%'
                            or lower(coalesce(e.error_message, '')) like '%валидац%'
                        ))
                        or e.status_code between 401 and 499
                    ))
                    or (:errorClass = 'SYSTEM' and coalesce(e.is_error, false) = true and (
                        coalesce(e.status_code, 0) >= 500
                        or (
                            (e.status_code is null or e.status_code < 400 or e.status_code > 499)
                            and not (
                                lower(coalesce(e.error_message, '')) like '%required request parameter%'
                                or lower(coalesce(e.error_message, '')) like '%validation%'
                                or lower(coalesce(e.error_message, '')) like '%bind%'
                                or lower(coalesce(e.error_message, '')) like '%failed to convert%'
                                or lower(coalesce(e.error_message, '')) like '%cannot be null%'
                                or lower(coalesce(e.error_message, '')) like '%must not%'
                                or lower(coalesce(e.error_message, '')) like '%некоррект%'
                                or lower(coalesce(e.error_message, '')) like '%не заполн%'
                                or lower(coalesce(e.error_message, '')) like '%обязатель%'
                                or lower(coalesce(e.error_message, '')) like '%валидац%'
                            )
                        )
                    ))
               )
               and (
                    :attributeCode is null
                    or exists (
                        select 1
                          from analytics.event_attribute a
                         where a.event_id = e.id
                           and a.attribute_type_code = :attributeCode
                           and (
                                :attributeValue is null
                                or lower(coalesce(a.attr_value, coalesce(a.attr_value_json, ''))) like lower(concat('%', :attributeValue, '%'))
                           )
                    )
               )
               and (
                    :metricFilterEnabled = false
                    or exists (
                        select 1
                          from analytics.stage s
                          join analytics.stage_metric m on m.stage_id = s.id
                         where s.event_id = e.id
                           and (:metricTypeCode is null or m.metric_type_code = :metricTypeCode)
                           and (:metricMinValue is null or m.metric_value_num >= :metricMinValue)
                           and (:metricMaxValue is null or m.metric_value_num <= :metricMaxValue)
                    )
               )
            """,
        nativeQuery = true
    )
    List<AnalyticsEvent> searchEventsScoped(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("eventTypeFilterEnabled") boolean eventTypeFilterEnabled,
        @Param("eventTypeCodes") java.util.Collection<String> eventTypeCodes,
        @Param("moduleCode") String moduleCode,
        @Param("stageTypeCode") String stageTypeCode,
        @Param("isError") Boolean isError,
        @Param("errorKey") String errorKey,
        @Param("errorClass") String errorClass,
        @Param("minDurationMs") Integer minDurationMs,
        @Param("requestPath") String requestPath,
        @Param("attributeCode") String attributeCode,
        @Param("attributeValue") String attributeValue,
        @Param("metricFilterEnabled") boolean metricFilterEnabled,
        @Param("metricTypeCode") String metricTypeCode,
        @Param("metricMinValue") java.math.BigDecimal metricMinValue,
        @Param("metricMaxValue") java.math.BigDecimal metricMaxValue,
        @Param("systemEventsOnly") boolean systemEventsOnly,
        @Param("sortBy") String sortBy,
        @Param("ascending") boolean ascending,
        Pageable pageable
    );
}

