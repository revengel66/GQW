package com.example.gqw.analytics.service;

import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalRootCauseFactorDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalRootCauseResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AnalyticsUniversalRootCauseService {

    private static final int DEFAULT_LIMIT = 12;
    private static final int MAX_LIMIT = 30;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AnalyticsUniversalRootCauseService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UniversalRootCauseResponse rootCause(
        Instant from,
        Instant to,
        Collection<String> eventCodes,
        Collection<String> stageTypeCodes,
        String moduleCode,
        String attributeCode,
        String attributeValue,
        Integer limit
    ) {
        String safeAttributeCode = normalizeText(attributeCode);
        if (safeAttributeCode == null) {
            return new UniversalRootCauseResponse("", null, 0, 0, 0, List.of());
        }
        String safeAttributeValue = normalizeText(attributeValue);
        List<String> safeEventCodes = normalizeList(eventCodes);
        List<String> safeStageCodes = normalizeList(stageTypeCodes);
        String safeModuleCode = normalizeText(moduleCode);
        int safeLimit = Math.max(1, Math.min(MAX_LIMIT, limit == null ? DEFAULT_LIMIT : limit));

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", Timestamp.from(from))
            .addValue("to", Timestamp.from(to))
            .addValue("attributeCode", safeAttributeCode, Types.VARCHAR)
            .addValue("attributeValue", safeAttributeValue, Types.VARCHAR)
            .addValue("attributeValueEnabled", safeAttributeValue != null, Types.BOOLEAN)
            .addValue("eventCodes", safeEventCodes.isEmpty() ? List.of("__none__") : safeEventCodes)
            .addValue("stageTypeCodes", safeStageCodes.isEmpty() ? List.of("__none__") : safeStageCodes)
            .addValue("eventFilterEnabled", !safeEventCodes.isEmpty(), Types.BOOLEAN)
            .addValue("stageFilterEnabled", !safeStageCodes.isEmpty(), Types.BOOLEAN)
            .addValue("moduleCode", safeModuleCode, Types.VARCHAR)
            .addValue("moduleEnabled", safeModuleCode != null, Types.BOOLEAN)
            .addValue("limit", safeLimit, Types.INTEGER);

        String sql = """
            with selected_base as (
                select
                    e.id as event_id,
                    trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))) as selected_value,
                    case when e.duration_ms is null or e.duration_ms < 0 then 0 else e.duration_ms end as duration_ms,
                    case when e.is_error = true then 1 else 0 end as error_flag
                from analytics.event_attribute a
                join analytics.event e on e.id = a.event_id
                where e.started_at between :from and :to
                  and a.attribute_type_code = :attributeCode
                  and trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))) <> ''
                  and (:attributeValueEnabled = false or trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))) = :attributeValue)
                  and (:eventFilterEnabled = false or e.event_type_code in (:eventCodes))
                  and (:moduleEnabled = false or coalesce(e.module_code, 'DEFAULT') = :moduleCode)
                  and (
                      :stageFilterEnabled = false
                      or exists (
                          select 1
                          from analytics.stage s
                          where s.event_id = e.id
                            and s.stage_type_code in (:stageTypeCodes)
                      )
                  )
            ),
            grouped as (
                select
                    selected_value,
                    cast(count(*) as bigint) as count,
                    cast(coalesce(avg(duration_ms), 0) as numeric(12, 3)) as avg_ms,
                    cast(coalesce(percentile_cont(0.95) within group (order by cast(duration_ms as numeric)), 0) as numeric(12, 3)) as p95_ms,
                    cast(case when count(*) = 0 then 0 else (cast(sum(error_flag) as numeric) / count(*)) end as numeric(12, 6)) as error_rate
                from selected_base
                group by selected_value
            ),
            enriched as (
                select
                    selected_value,
                    count,
                    cast(case when sum(count) over() = 0 then 0 else ((cast(count as numeric) / sum(count) over()) * 100) end as numeric(12, 4)) as share,
                    avg_ms,
                    p95_ms,
                    error_rate,
                    cast(avg(count) over() as numeric(12, 3)) as avg_count_baseline,
                    cast(avg(p95_ms) over() as numeric(12, 3)) as avg_p95_baseline,
                    cast(avg(avg_ms) over() as numeric(12, 3)) as avg_ms_baseline
                from grouped
            ),
            classified as (
                select
                    *,
                    case
                        when error_rate > 0
                          or p95_ms >= 3000
                          or avg_ms >= 1500
                          or (avg_p95_baseline > 0 and p95_ms >= avg_p95_baseline * 2.5 and p95_ms >= 1000)
                          or (avg_ms_baseline > 0 and avg_ms >= avg_ms_baseline * 2.5 and avg_ms >= 750)
                        then 'critical'
                        when p95_ms >= 1000
                          or avg_ms >= 500
                          or share >= 5
                          or (avg_count_baseline > 0 and count >= avg_count_baseline * 1.5)
                          or (avg_p95_baseline > 0 and p95_ms >= avg_p95_baseline * 1.5 and p95_ms >= 500)
                          or (avg_ms_baseline > 0 and avg_ms >= avg_ms_baseline * 1.5 and avg_ms >= 250)
                        then 'warning'
                        else 'normal'
                    end as severity_level
                from enriched
            ),
            problem_values as (
                select selected_value, severity_level
                from classified
                where severity_level in ('critical', 'warning')
            ),
            problem_events as (
                select distinct b.event_id, b.duration_ms, b.error_flag, p.severity_level
                from selected_base b
                join problem_values p on p.selected_value = b.selected_value
            ),
            problem_stats as (
                select
                    coalesce(count(*), 0)::bigint as problem_event_count,
                    coalesce((select count(*) from problem_values where severity_level = 'critical'), 0)::bigint as critical_value_count,
                    coalesce((select count(*) from problem_values where severity_level = 'warning'), 0)::bigint as warning_value_count
                from problem_events
            ),
            factor_counts as (
                select
                    fa.attribute_type_code as factor_code,
                    trim(coalesce(nullif(fa.attr_value, ''), nullif(fa.attr_value_json, ''))) as factor_value,
                    case
                        when upper(fa.attribute_type_code) in ('HTTP_PATH', 'REQUEST_PATH', 'PATH') then 1
                        when upper(fa.attribute_type_code) in ('REFERRER', 'REFERER') then 2
                        when upper(fa.attribute_type_code) in ('CATEGORY', 'CATEGORY_SLUG') then 3
                        when upper(fa.attribute_type_code) in ('CONTROLLER', 'CONTROLLER_NAME', 'METHOD') then 4
                        when upper(fa.attribute_type_code) in ('CLIENT_TYPE', 'USER_TYPE', 'USER_ROLE') then 5
                        when upper(fa.attribute_type_code) in ('HTTP_METHOD', 'HTTP_STATUS') then 6
                        when upper(fa.attribute_type_code) like '%REQUEST%ID%'
                          or upper(fa.attribute_type_code) like '%SESSION%HASH%'
                          or upper(fa.attribute_type_code) like '%USER%HASH%'
                          or upper(fa.attribute_type_code) like '%TRACE%ID%'
                          or upper(fa.attribute_type_code) like '%UUID%'
                        then 9
                        when upper(fa.attribute_type_code) in ('USER_AGENT') then 10
                        else 7
                    end as factor_priority,
                    cast(count(distinct pe.event_id) as bigint) as count,
                    cast(case when (select problem_event_count from problem_stats) = 0 then 0
                        else ((cast(count(distinct pe.event_id) as numeric) / (select problem_event_count from problem_stats)) * 100)
                    end as numeric(12, 4)) as share,
                    cast(coalesce(avg(pe.duration_ms), 0) as numeric(12, 3)) as avg_ms,
                    cast(coalesce(percentile_cont(0.95) within group (order by cast(pe.duration_ms as numeric)), 0) as numeric(12, 3)) as p95_ms,
                    cast(case when count(distinct pe.event_id) = 0 then 0 else (cast(sum(pe.error_flag) as numeric) / count(distinct pe.event_id)) end as numeric(12, 6)) as error_rate
                from problem_events pe
                join analytics.event_attribute fa on fa.event_id = pe.event_id
                where fa.attribute_type_code <> :attributeCode
                  and trim(coalesce(nullif(fa.attr_value, ''), nullif(fa.attr_value_json, ''))) <> ''
                group by fa.attribute_type_code, trim(coalesce(nullif(fa.attr_value, ''), nullif(fa.attr_value_json, '')))
            ),
            ranked_factors as (
                select
                    fc.*,
                    row_number() over (
                        partition by factor_code
                        order by share desc, count desc, p95_ms desc, avg_ms desc, factor_value asc
                    ) as factor_rank
                from factor_counts fc
            ),
            top_factors as (
                select *
                from ranked_factors
                where factor_rank <= 3
                order by factor_priority asc, share desc, count desc, p95_ms desc, avg_ms desc, factor_code asc, factor_value asc
                limit :limit
            )
            select
                tf.factor_code,
                tf.factor_value,
                tf.count,
                tf.share,
                tf.avg_ms,
                tf.p95_ms,
                tf.error_rate,
                ps.problem_event_count,
                ps.critical_value_count,
                ps.warning_value_count
            from problem_stats ps
            left join top_factors tf on true
            order by tf.factor_priority asc nulls last, tf.share desc nulls last, tf.count desc nulls last, tf.p95_ms desc nulls last
            """;

        List<UniversalRootCauseFactorDto> factors = new ArrayList<>();
        final long[] problemEventCount = {0L};
        final long[] criticalValueCount = {0L};
        final long[] warningValueCount = {0L};
        jdbcTemplate.query(sql, params, rs -> {
            problemEventCount[0] = rs.getLong("problem_event_count");
            criticalValueCount[0] = rs.getLong("critical_value_count");
            warningValueCount[0] = rs.getLong("warning_value_count");
            String factorCode = rs.getString("factor_code");
            if (factorCode != null) {
                factors.add(new UniversalRootCauseFactorDto(
                    factorCode,
                    rs.getString("factor_value"),
                    rs.getLong("count"),
                    scale(rs.getBigDecimal("share"), 4),
                    scale(rs.getBigDecimal("avg_ms"), 3),
                    scale(rs.getBigDecimal("p95_ms"), 3),
                    scale(rs.getBigDecimal("error_rate"), 6)
                ));
            }
        });
        return new UniversalRootCauseResponse(
            safeAttributeCode,
            safeAttributeValue,
            problemEventCount[0],
            criticalValueCount[0],
            warningValueCount[0],
            factors
        );
    }

    public UniversalRootCauseResponse eventRootCause(
        Instant from,
        Instant to,
        Collection<String> eventCodes,
        Collection<String> stageTypeCodes,
        String moduleCode,
        Integer limit
    ) {
        List<String> safeEventCodes = normalizeList(eventCodes);
        List<String> safeStageCodes = normalizeList(stageTypeCodes);
        String safeModuleCode = normalizeText(moduleCode);
        int safeLimit = Math.max(1, Math.min(MAX_LIMIT, limit == null ? DEFAULT_LIMIT : limit));

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", Timestamp.from(from))
            .addValue("to", Timestamp.from(to))
            .addValue("eventCodes", safeEventCodes.isEmpty() ? List.of("__none__") : safeEventCodes)
            .addValue("eventFilterEnabled", !safeEventCodes.isEmpty(), Types.BOOLEAN)
            .addValue("stageTypeCodes", safeStageCodes.isEmpty() ? List.of("__none__") : safeStageCodes)
            .addValue("stageFilterEnabled", !safeStageCodes.isEmpty(), Types.BOOLEAN)
            .addValue("moduleCode", safeModuleCode, Types.VARCHAR)
            .addValue("moduleEnabled", safeModuleCode != null, Types.BOOLEAN)
            .addValue("limit", safeLimit, Types.INTEGER);

        String sql = """
            with selected_events as (
                select
                    e.id as event_id,
                    case when e.duration_ms is null or e.duration_ms < 0 then 0 else e.duration_ms end as duration_ms,
                    case when e.is_error = true then 1 else 0 end as error_flag,
                    case
                        when e.is_error = true
                          or coalesce(e.duration_ms, 0) >= 3000
                        then 'critical'
                        when coalesce(e.duration_ms, 0) >= 1000
                        then 'warning'
                        else 'normal'
                    end as severity_level
                from analytics.event e
                where e.started_at between :from and :to
                  and (:eventFilterEnabled = false or e.event_type_code in (:eventCodes))
                  and (:moduleEnabled = false or coalesce(e.module_code, 'DEFAULT') = :moduleCode)
                  and (
                      :stageFilterEnabled = false
                      or exists (
                          select 1
                          from analytics.stage s
                          where s.event_id = e.id
                            and s.stage_type_code in (:stageTypeCodes)
                      )
                  )
            ),
            problem_events as (
                select *
                from selected_events
                where severity_level in ('critical', 'warning')
            ),
            problem_stats as (
                select
                    coalesce(count(*), 0)::bigint as problem_event_count,
                    coalesce(sum(case when severity_level = 'critical' then 1 else 0 end), 0)::bigint as critical_value_count,
                    coalesce(sum(case when severity_level = 'warning' then 1 else 0 end), 0)::bigint as warning_value_count
                from problem_events
            ),
            factor_counts as (
                select
                    fa.attribute_type_code as factor_code,
                    trim(coalesce(nullif(fa.attr_value, ''), nullif(fa.attr_value_json, ''))) as factor_value,
                    case
                        when upper(fa.attribute_type_code) in ('HTTP_PATH', 'REQUEST_PATH', 'PATH') then 1
                        when upper(fa.attribute_type_code) in ('REFERRER', 'REFERER') then 2
                        when upper(fa.attribute_type_code) in ('CATEGORY', 'CATEGORY_SLUG', 'CATEGORY_NAME') then 3
                        when upper(fa.attribute_type_code) in ('CLIENT_TYPE', 'USER_AGENT', 'HTTP_STATUS', 'HTTP_METHOD') then 4
                        when upper(fa.attribute_type_code) like '%REQUEST%ID%'
                          or upper(fa.attribute_type_code) like '%SESSION%HASH%'
                          or upper(fa.attribute_type_code) like '%USER%HASH%'
                          or upper(fa.attribute_type_code) like '%TRACE%ID%'
                          or upper(fa.attribute_type_code) like '%UUID%'
                          or upper(fa.attribute_type_code) in ('ENTITY_ID')
                        then 5
                        else 6
                    end as factor_priority,
                    cast(count(distinct pe.event_id) as bigint) as count,
                    cast(case when (select problem_event_count from problem_stats) = 0 then 0
                        else ((cast(count(distinct pe.event_id) as numeric) / (select problem_event_count from problem_stats)) * 100)
                    end as numeric(12, 4)) as share,
                    cast(coalesce(avg(pe.duration_ms), 0) as numeric(12, 3)) as avg_ms,
                    cast(coalesce(percentile_cont(0.95) within group (order by cast(pe.duration_ms as numeric)), 0) as numeric(12, 3)) as p95_ms,
                    cast(case when count(distinct pe.event_id) = 0 then 0 else (cast(sum(pe.error_flag) as numeric) / count(distinct pe.event_id)) end as numeric(12, 6)) as error_rate
                from problem_events pe
                join analytics.event_attribute fa on fa.event_id = pe.event_id
                where trim(coalesce(nullif(fa.attr_value, ''), nullif(fa.attr_value_json, ''))) <> ''
                group by fa.attribute_type_code, trim(coalesce(nullif(fa.attr_value, ''), nullif(fa.attr_value_json, '')))
            ),
            ranked_factors as (
                select
                    fc.*,
                    row_number() over (
                        partition by factor_code
                        order by share desc, count desc, p95_ms desc, avg_ms desc, factor_value asc
                    ) as factor_rank
                from factor_counts fc
            ),
            top_factors as (
                select *
                from ranked_factors
                where factor_rank <= 3
                order by factor_priority asc, share desc, count desc, p95_ms desc, avg_ms desc, factor_code asc, factor_value asc
                limit :limit
            )
            select
                tf.factor_code,
                tf.factor_value,
                tf.count,
                tf.share,
                tf.avg_ms,
                tf.p95_ms,
                tf.error_rate,
                ps.problem_event_count,
                ps.critical_value_count,
                ps.warning_value_count
            from problem_stats ps
            left join top_factors tf on true
            order by tf.factor_priority asc nulls last, tf.share desc nulls last, tf.count desc nulls last, tf.p95_ms desc nulls last
            """;

        List<UniversalRootCauseFactorDto> factors = new ArrayList<>();
        final long[] problemEventCount = {0L};
        final long[] criticalValueCount = {0L};
        final long[] warningValueCount = {0L};
        jdbcTemplate.query(sql, params, rs -> {
            problemEventCount[0] = rs.getLong("problem_event_count");
            criticalValueCount[0] = rs.getLong("critical_value_count");
            warningValueCount[0] = rs.getLong("warning_value_count");
            String factorCode = rs.getString("factor_code");
            if (factorCode != null) {
                factors.add(new UniversalRootCauseFactorDto(
                    factorCode,
                    rs.getString("factor_value"),
                    rs.getLong("count"),
                    scale(rs.getBigDecimal("share"), 4),
                    scale(rs.getBigDecimal("avg_ms"), 3),
                    scale(rs.getBigDecimal("p95_ms"), 3),
                    scale(rs.getBigDecimal("error_rate"), 6)
                ));
            }
        });
        return new UniversalRootCauseResponse(
            safeEventCodes.size() == 1 ? safeEventCodes.get(0) : "",
            null,
            problemEventCount[0],
            criticalValueCount[0],
            warningValueCount[0],
            factors
        );
    }

    private static List<String> normalizeList(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .map(AnalyticsUniversalRootCauseService::normalizeText)
            .filter(value -> value != null)
            .distinct()
            .toList();
    }

    private static String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }
}
