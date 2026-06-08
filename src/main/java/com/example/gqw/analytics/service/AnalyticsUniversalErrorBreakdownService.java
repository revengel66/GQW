package com.example.gqw.analytics.service;

import org.springframework.beans.factory.annotation.Qualifier;

import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalErrorBreakdownResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalErrorBreakdownRowDto;
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
import java.util.Locale;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AnalyticsUniversalErrorBreakdownService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_RCA_LIMIT = 18;
    private static final int MAX_RCA_LIMIT = 40;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AnalyticsUniversalErrorBreakdownService(@Qualifier("analyticsNamedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UniversalErrorBreakdownResponse breakdown(
        Instant from,
        Instant to,
        Collection<String> eventCodes,
        Collection<String> stageTypeCodes,
        String moduleCode,
        Integer limit,
        Integer offset,
        String sortBy,
        String sortDir
    ) {
        int safeLimit = Math.max(1, Math.min(MAX_LIMIT, limit == null ? DEFAULT_LIMIT : limit));
        int safeOffset = Math.max(0, offset == null ? 0 : offset);
        List<String> safeEventCodes = normalizeList(eventCodes);
        List<String> safeStageCodes = normalizeList(stageTypeCodes);
        String safeModuleCode = normalizeText(moduleCode);
        String direction = "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
        String sortOrder = sortOrder(sortBy, direction);

        MapSqlParameterSource params = baseParams(from, to, safeEventCodes, safeStageCodes, safeModuleCode)
            .addValue("limit", safeLimit, Types.INTEGER)
            .addValue("offset", safeOffset, Types.INTEGER);

        String sql = """
            with base as (
                select
                    e.id as event_id,
                    e.event_type_code,
                    coalesce(et.is_system, false) as system_event,
                    e.started_at,
                    case when e.duration_ms is null or e.duration_ms < 0 then 0 else e.duration_ms end as duration_ms,
                    coalesce(e.status_code, 0) as status_code,
                    trim(coalesce(nullif(e.error_message, ''), '')) as raw_error_message,
                    coalesce(nullif(trim(e.error_message), ''), concat('HTTP ', coalesce(e.status_code, 0)), 'Неизвестная ошибка') as error_message,
                    concat(coalesce(e.status_code, 0), '|', coalesce(nullif(trim(e.error_message), ''), 'UNKNOWN')) as error_key
                from analytics.event e
                join analytics.event_type et on et.code = e.event_type_code
                where e.started_at between :from and :to
                  and coalesce(e.is_error, false) = true
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
                    error_key,
                    error_message,
                    system_event,
                    case when count(distinct event_type_code) = 1 then min(event_type_code) else null end as event_type_code,
                    cast(count(*) as bigint) as count,
                    cast(count(distinct event_type_code) as bigint) as event_count,
                    cast(coalesce(avg(duration_ms), 0) as numeric(12, 3)) as avg_ms,
                    cast(coalesce(percentile_cont(0.95) within group (order by cast(duration_ms as numeric)), 0) as numeric(12, 3)) as p95_ms,
                    max(started_at) as last_seen
                from base
                group by error_key, error_message, system_event
            ),
            enriched as (
                select
                    *,
                    cast(case when sum(count) over() = 0 then 0 else ((cast(count as numeric) / sum(count) over()) * 100) end as numeric(12, 4)) as share,
                    cast(count(*) over() as bigint) as total_values,
                    cast(avg(count) over() as numeric(12, 3)) as avg_count_baseline,
                    cast(avg(event_count) over() as numeric(12, 3)) as avg_event_count_baseline,
                    cast(avg(p95_ms) over() as numeric(12, 3)) as avg_p95_baseline
                from grouped
            ),
            classified as (
                select
                    *,
                    case
                        when share >= 10
                          or count >= greatest(10, avg_count_baseline * 2)
                          or p95_ms >= 3000
                          or event_count >= greatest(3, avg_event_count_baseline * 2)
                        then 'critical'
                        when share >= 2
                          or count >= greatest(3, avg_count_baseline)
                          or p95_ms >= 1000
                          or event_count >= 2
                          or (avg_p95_baseline > 0 and p95_ms >= avg_p95_baseline * 1.5)
                        then 'warning'
                        else 'normal'
                    end as severity_level
                from enriched
            ),
            stats as (
                select
                    coalesce(count(*), 0)::bigint as total_values,
                    coalesce(sum(case when severity_level = 'critical' then 1 else 0 end), 0)::bigint as critical_total,
                    coalesce(sum(case when severity_level = 'warning' then 1 else 0 end), 0)::bigint as warning_total,
                    coalesce(sum(case when severity_level = 'normal' then 1 else 0 end), 0)::bigint as normal_total,
                    coalesce(sum(count), 0)::bigint as problem_event_count
                from classified
            ),
            ranked as (
                select
                    c.*,
                    s.total_values as result_total_values,
                    s.critical_total,
                    s.warning_total,
                    s.normal_total,
                    s.problem_event_count,
                    row_number() over (order by %s, c.error_message asc) as rn
                from classified c
                cross join stats s
            )
            select
                error_key,
                error_message,
                system_event,
                event_type_code,
                count,
                share,
                event_count,
                avg_ms,
                p95_ms,
                last_seen,
                severity_level,
                result_total_values as total_values,
                critical_total,
                warning_total,
                normal_total,
                problem_event_count
            from ranked
            where rn > :offset and rn <= (:offset + :limit)
            order by rn
            """.formatted(sortOrder);

        List<UniversalErrorBreakdownRowDto> rows = new ArrayList<>();
        final long[] total = {0L};
        final long[] criticalTotal = {0L};
        final long[] warningTotal = {0L};
        final long[] normalTotal = {0L};
        final long[] problemEventCount = {0L};
        jdbcTemplate.query(sql, params, rs -> {
            if (total[0] == 0L) {
                total[0] = rs.getLong("total_values");
                criticalTotal[0] = rs.getLong("critical_total");
                warningTotal[0] = rs.getLong("warning_total");
                normalTotal[0] = rs.getLong("normal_total");
                problemEventCount[0] = rs.getLong("problem_event_count");
            }
            Timestamp lastSeen = rs.getTimestamp("last_seen");
            rows.add(new UniversalErrorBreakdownRowDto(
                rs.getString("error_key"),
                rs.getString("error_message"),
                rs.getBoolean("system_event"),
                rs.getString("event_type_code"),
                rs.getLong("count"),
                scale(rs.getBigDecimal("share"), 4),
                rs.getLong("event_count"),
                scale(rs.getBigDecimal("avg_ms"), 3),
                scale(rs.getBigDecimal("p95_ms"), 3),
                lastSeen == null ? null : lastSeen.toInstant(),
                rs.getString("severity_level")
            ));
        });
        return new UniversalErrorBreakdownResponse(total[0], criticalTotal[0], warningTotal[0], normalTotal[0], problemEventCount[0], rows);
    }

    public UniversalRootCauseResponse rootCause(
        Instant from,
        Instant to,
        Collection<String> eventCodes,
        Collection<String> stageTypeCodes,
        String moduleCode,
        String errorKey,
        Boolean systemEventsOnly,
        Integer limit
    ) {
        List<String> safeEventCodes = normalizeList(eventCodes);
        List<String> safeStageCodes = normalizeList(stageTypeCodes);
        String safeModuleCode = normalizeText(moduleCode);
        String safeErrorKey = normalizeText(errorKey);
        int safeLimit = Math.max(1, Math.min(MAX_RCA_LIMIT, limit == null ? DEFAULT_RCA_LIMIT : limit));

        MapSqlParameterSource params = baseParams(from, to, safeEventCodes, safeStageCodes, safeModuleCode)
            .addValue("errorKey", safeErrorKey, Types.VARCHAR)
            .addValue("errorKeyEnabled", safeErrorKey != null, Types.BOOLEAN)
            .addValue("systemScopeEnabled", systemEventsOnly != null, Types.BOOLEAN)
            .addValue("systemEventsOnly", Boolean.TRUE.equals(systemEventsOnly), Types.BOOLEAN)
            .addValue("limit", safeLimit, Types.INTEGER);

        String sql = """
            with problem_events as (
                select
                    e.id as event_id,
                    e.event_type_code,
                    case when e.duration_ms is null or e.duration_ms < 0 then 0 else e.duration_ms end as duration_ms,
                    concat(coalesce(e.status_code, 0), '|', coalesce(nullif(trim(e.error_message), ''), 'UNKNOWN')) as error_key
                from analytics.event e
                join analytics.event_type et on et.code = e.event_type_code
                where e.started_at between :from and :to
                  and coalesce(e.is_error, false) = true
                  and (:systemScopeEnabled = false or coalesce(et.is_system, false) = :systemEventsOnly)
                  and (:errorKeyEnabled = false or concat(coalesce(e.status_code, 0), '|', coalesce(nullif(trim(e.error_message), ''), 'UNKNOWN')) = :errorKey)
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
            problem_stats as (
                select
                    coalesce(count(*), 0)::bigint as problem_event_count,
                    coalesce(count(distinct error_key), 0)::bigint as critical_value_count,
                    0::bigint as warning_value_count
                from problem_events
            ),
            event_factors as (
                select
                    'EVENT_TYPE' as factor_code,
                    pe.event_type_code as factor_value,
                    0 as factor_priority,
                    pe.event_id,
                    pe.duration_ms
                from problem_events pe
            ),
            attribute_factors as (
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
                    pe.event_id,
                    pe.duration_ms
                from problem_events pe
                join analytics.event_attribute fa on fa.event_id = pe.event_id
                where trim(coalesce(nullif(fa.attr_value, ''), nullif(fa.attr_value_json, ''))) <> ''
            ),
            all_factors as (
                select * from event_factors
                union all
                select * from attribute_factors
            ),
            factor_counts as (
                select
                    factor_code,
                    factor_value,
                    factor_priority,
                    cast(count(distinct event_id) as bigint) as count,
                    cast(case when (select problem_event_count from problem_stats) = 0 then 0
                        else ((cast(count(distinct event_id) as numeric) / (select problem_event_count from problem_stats)) * 100)
                    end as numeric(12, 4)) as share,
                    cast(coalesce(avg(duration_ms), 0) as numeric(12, 3)) as avg_ms,
                    cast(coalesce(percentile_cont(0.95) within group (order by cast(duration_ms as numeric)), 0) as numeric(12, 3)) as p95_ms,
                    1::numeric(12, 6) as error_rate
                from all_factors
                group by factor_code, factor_value, factor_priority
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
            "ERROR",
            safeErrorKey,
            problemEventCount[0],
            criticalValueCount[0],
            warningValueCount[0],
            factors
        );
    }

    private static MapSqlParameterSource baseParams(
        Instant from,
        Instant to,
        List<String> eventCodes,
        List<String> stageTypeCodes,
        String moduleCode
    ) {
        return new MapSqlParameterSource()
            .addValue("from", Timestamp.from(from))
            .addValue("to", Timestamp.from(to))
            .addValue("eventCodes", eventCodes.isEmpty() ? List.of("__none__") : eventCodes)
            .addValue("stageTypeCodes", stageTypeCodes.isEmpty() ? List.of("__none__") : stageTypeCodes)
            .addValue("eventFilterEnabled", !eventCodes.isEmpty(), Types.BOOLEAN)
            .addValue("stageFilterEnabled", !stageTypeCodes.isEmpty(), Types.BOOLEAN)
            .addValue("moduleCode", moduleCode, Types.VARCHAR)
            .addValue("moduleEnabled", moduleCode != null, Types.BOOLEAN);
    }

    private static String sortOrder(String sortBy, String direction) {
        return switch (String.valueOf(sortBy).toLowerCase(Locale.ROOT)) {
            case "error", "value", "message", "errormessage" -> "error_message " + direction;
            case "share" -> "share " + direction;
            case "events", "eventcount" -> "event_count " + direction;
            case "avg", "avgms" -> "avg_ms " + direction;
            case "p95", "p95ms" -> "p95_ms " + direction;
            case "lastseen" -> "last_seen " + direction;
            case "count" -> "count " + direction;
            case "interesting", "riskscore" -> interestingSortOrder(direction);
            default -> interestingSortOrder("desc");
        };
    }

    private static String interestingSortOrder(String direction) {
        String safeDirection = "asc".equalsIgnoreCase(direction) ? "asc" : "desc";
        return "case severity_level when 'critical' then 2 when 'warning' then 1 else 0 end " + safeDirection
            + ", share " + safeDirection
            + ", count " + safeDirection
            + ", event_count " + safeDirection
            + ", p95_ms " + safeDirection
            + ", avg_ms " + safeDirection
            + ", last_seen " + safeDirection;
    }

    private static List<String> normalizeList(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .map(AnalyticsUniversalErrorBreakdownService::normalizeText)
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
