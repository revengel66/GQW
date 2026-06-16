package com.example.gqw.analytics.repository;

import com.example.gqw.analytics.entity.AnalyticsEventAttribute;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsEventAttributeRepository extends JpaRepository<AnalyticsEventAttribute, Long> {

    List<AnalyticsEventAttribute> findByEventId(Long eventId);

    List<AnalyticsEventAttribute> findByEventIdIn(Collection<Long> eventIds);

    long countByEventIdIn(Collection<Long> eventIds);

    long countByAttributeTypeCode(String attributeTypeCode);

    @Query("""
        select distinct a.attributeTypeCode from AnalyticsEventAttribute a
        where exists (
            select 1 from AnalyticsEvent e
            where e.id = a.eventId
              and e.startedAt between :from and :to
              and (:moduleCode is null or e.moduleCode = :moduleCode)
              and (:eventTypeCode is null or e.eventTypeCode = :eventTypeCode)
              and (:requestPath is null or lower(coalesce(cast(e.requestPath as string), '')) like lower(concat('%', :requestPath, '%')))
        )
        order by a.attributeTypeCode asc
        """)
    List<String> findDistinctAttributeTypeCodesByScope(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("moduleCode") String moduleCode,
        @Param("eventTypeCode") String eventTypeCode,
        @Param("requestPath") String requestPath,
        Pageable pageable
    );

    @Query("""
        select distinct a.attributeTypeCode from AnalyticsEventAttribute a
        where exists (
            select 1 from AnalyticsEvent e
            where e.id = a.eventId
              and e.startedAt between :from and :to
              and (:moduleCode is null or e.moduleCode = :moduleCode)
              and (:eventTypeCode is null or e.eventTypeCode = :eventTypeCode)
        )
        order by a.attributeTypeCode asc
        """)
    List<String> findDistinctAttributeTypeCodesByScopeNoPath(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("moduleCode") String moduleCode,
        @Param("eventTypeCode") String eventTypeCode,
        Pageable pageable
    );

    @Query(
        value = """
            select distinct trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))) as v
            from analytics.event_attribute a
            join analytics.event e on e.id = a.event_id
            where e.started_at between :from and :to
              and (:moduleCode is null or e.module_code = :moduleCode)
              and (:eventTypeCode is null or e.event_type_code = :eventTypeCode)
              and (:requestPath is null or lower(coalesce(cast(e.request_path as text), '')) like lower(concat('%', :requestPath, '%')))
              and a.attribute_type_code = :attributeCode
              and trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))) <> ''
            order by v asc
            limit :limit
            """,
        nativeQuery = true
    )
    List<String> findDistinctAttributeValuesByScope(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("moduleCode") String moduleCode,
        @Param("eventTypeCode") String eventTypeCode,
        @Param("requestPath") String requestPath,
        @Param("attributeCode") String attributeCode,
        @Param("limit") int limit
    );

    @Query(
        value = """
            select distinct trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))) as v
            from analytics.event_attribute a
            join analytics.event e on e.id = a.event_id
            where e.started_at between :from and :to
              and (:moduleCode is null or e.module_code = :moduleCode)
              and (:eventTypeCode is null or e.event_type_code = :eventTypeCode)
              and a.attribute_type_code = :attributeCode
              and trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))) <> ''
            order by v asc
            limit :limit
            """,
        nativeQuery = true
    )
    List<String> findDistinctAttributeValuesByScopeNoPath(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("moduleCode") String moduleCode,
        @Param("eventTypeCode") String eventTypeCode,
        @Param("attributeCode") String attributeCode,
        @Param("limit") int limit
    );
}

