package com.example.gqw.analytics.service;

import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalModuleBreakdownResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalModuleBreakdownRowDto;
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
public class AnalyticsUniversalModuleBreakdownService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_RCA_LIMIT = 24;
    private static final int MAX_RCA_LIMIT = 40;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AnalyticsUniversalModuleBreakdownService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UniversalModuleBreakdownResponse breakdown(
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
        List<String> safeEventCodes = normalizeList(eventCodes);
        List<String> safeStageCodes = normalizeList(stageTypeCodes);
        String safeModuleCode = normalizeText(moduleCode);
        int safeLimit = Math.max(1, Math.min(MAX_LIMIT, limit == null ? DEFAULT_LIMIT : limit));
        int safeOffset = Math.max(0, offset == null ? 0 : offset);
        String direction = "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";

        MapSqlParameterSource params = baseParams(from, to, safeEventCodes, safeStageCodes)
            .addValue("moduleCode", safeModuleCode, Types.VARCHAR)
            .addValue("moduleEnabled", safeModuleCode != null, Types.BOOLEAN)
            .addValue("limit", safeLimit, Types.INTEGER)
            .addValue("offset", safeOffset, Types.INTEGER);

        String sql = """
            with base as (
                select
                    e.id as event_id,
                    coalesce(e.module_code, 'DEFAULT') as module_code,
                    coalesce(mt.name, coalesce(e.module_code, 'DEFAULT')) as module_name,
                    s.stage_type_code,
                    coalesce(st.name, s.stage_type_code) as stage_type_name,
                    coalesce(et.is_system, false) as system_event,
                    case when s.duration_ms is null or s.duration_ms < 0 then 0 else s.duration_ms end as duration_ms,
                    case when coalesce(s.is_error, false) or coalesce(e.is_error, false) then 1 else 0 end as error_flag
                from analytics.stage s
                join analytics.event e on e.id = s.event_id
                join analytics.event_type et on et.code = e.event_type_code
                left join analytics.module_type mt on mt.code = e.module_code
                left join analytics.stage_type st on st.code = s.stage_type_code
                where e.started_at between :from and :to
                  and (:eventFilterEnabled = false or e.event_type_code in (:eventCodes))
                  and (:stageFilterEnabled = false or s.stage_type_code in (:stageTypeCodes))
                  and (:moduleEnabled = false or coalesce(e.module_code, 'DEFAULT') = :moduleCode)
            ),
            context_problem_stats as (
                select coalesce(count(*), 0)::bigint as problem_event_count
                from base
                where error_flag > 0 or duration_ms >= 1000
            ),
            grouped as (
                select
                    concat(module_code, '|', stage_type_code, '|', case when system_event then 'SYSTEM' else 'USER' end) as module_key,
                    module_code,
                    module_name,
                    stage_type_code,
                    stage_type_name,
                    system_event,
                    cast(count(*) as bigint) as count,
                    cast(sum(error_flag) as bigint) as error_count,
                    cast(case when count(*) = 0 then 0 else cast(sum(error_flag) as numeric) / count(*) end as numeric(12, 6)) as error_rate,
                    cast(coalesce(avg(duration_ms), 0) as numeric(12, 3)) as avg_ms,
                    cast(coalesce(percentile_disc(0.95) within group (order by duration_ms), 0) as numeric(12, 3)) as p95_ms,
                    cast(count(distinct event_id) as bigint) as event_count
                from base
                group by module_code, module_name, stage_type_code, stage_type_name, system_event
            ),
            enriched as (
                select
                    *,
                    cast(case when sum(count) over() = 0 then 0 else cast(count as numeric) / sum(count) over() * 100 end as numeric(12, 4)) as share,
                    cast(avg(count) over() as numeric(12, 3)) as avg_count_baseline
                from grouped
            ),
            classified as (
                select
                    *,
                    case
                        when error_rate > 0.05 or p95_ms >= 3000 or avg_ms >= 1500
                          or (share >= 20 and (error_rate > 0 or p95_ms >= 1000 or avg_ms >= 500))
                        then 'critical'
                        when error_rate > 0 or p95_ms >= 1000 or avg_ms >= 500 or share >= 5
                          or (avg_count_baseline > 0 and count >= avg_count_baseline * 1.5)
                        then 'warning'
                        else 'normal'
                    end as severity_level
                from enriched
            ),
            stats as (
                select
                    count(*)::bigint as total_values,
                    sum(case when severity_level = 'critical' then 1 else 0 end)::bigint as critical_total,
                    sum(case when severity_level = 'warning' then 1 else 0 end)::bigint as warning_total,
                    sum(case when severity_level = 'normal' then 1 else 0 end)::bigint as normal_total,
                    (select problem_event_count from context_problem_stats) as problem_event_count
                from classified
            ),
            ranked as (
                select
                    c.*,
                    stats.total_values,
                    stats.critical_total,
                    stats.warning_total,
                    stats.normal_total,
                    stats.problem_event_count,
                    row_number() over (order by %s, c.module_name asc, c.stage_type_name asc) as rn
                from classified c
                cross join stats
            )
            select *
            from ranked
            where rn > :offset and rn <= (:offset + :limit)
            order by rn
            """.formatted(sortOrder(sortBy, direction));

        List<UniversalModuleBreakdownRowDto> rows = new ArrayList<>();
        long[] totals = new long[4];
        long[] problemEventCount = new long[1];
        jdbcTemplate.query(sql, params, rs -> {
            if (totals[0] == 0L) {
                totals[0] = rs.getLong("total_values");
                totals[1] = rs.getLong("critical_total");
                totals[2] = rs.getLong("warning_total");
                totals[3] = rs.getLong("normal_total");
                problemEventCount[0] = rs.getLong("problem_event_count");
            }
            rows.add(new UniversalModuleBreakdownRowDto(
                rs.getString("module_key"),
                rs.getString("module_code"),
                rs.getString("module_name"),
                rs.getString("stage_type_code"),
                rs.getString("stage_type_name"),
                rs.getLong("count"),
                scale(rs.getBigDecimal("share"), 4),
                rs.getLong("error_count"),
                scale(rs.getBigDecimal("error_rate"), 6),
                scale(rs.getBigDecimal("avg_ms"), 3),
                scale(rs.getBigDecimal("p95_ms"), 3),
                rs.getLong("event_count"),
                rs.getBoolean("system_event"),
                rs.getString("severity_level")
            ));
        });
        return new UniversalModuleBreakdownResponse(totals[0], totals[1], totals[2], totals[3], problemEventCount[0], rows);
    }

    public UniversalRootCauseResponse rootCause(
        Instant from,
        Instant to,
        Collection<String> eventCodes,
        Collection<String> stageTypeCodes,
        String moduleCode,
        String selectedStageTypeCode,
        Boolean systemEventsOnly,
        Integer limit
    ) {
        List<String> safeEventCodes = normalizeList(eventCodes);
        List<String> safeStageCodes = normalizeList(stageTypeCodes);
        String safeModuleCode = normalizeText(moduleCode);
        String safeSelectedStage = normalizeText(selectedStageTypeCode);
        int safeLimit = Math.max(1, Math.min(MAX_RCA_LIMIT, limit == null ? DEFAULT_RCA_LIMIT : limit));

        MapSqlParameterSource params = baseParams(from, to, safeEventCodes, safeStageCodes)
            .addValue("moduleCode", safeModuleCode, Types.VARCHAR)
            .addValue("moduleEnabled", safeModuleCode != null, Types.BOOLEAN)
            .addValue("selectedStageTypeCode", safeSelectedStage, Types.VARCHAR)
            .addValue("selectedStageEnabled", safeSelectedStage != null, Types.BOOLEAN)
            .addValue("systemEventsOnly", Boolean.TRUE.equals(systemEventsOnly), Types.BOOLEAN)
            .addValue("systemScopeEnabled", systemEventsOnly != null, Types.BOOLEAN)
            .addValue("limit", safeLimit, Types.INTEGER);

        String sql = """
            with selected_stages as (
                select
                    s.event_id,
                    s.stage_type_code,
                    case when s.duration_ms is null or s.duration_ms < 0 then 0 else s.duration_ms end as duration_ms,
                    case when coalesce(s.is_error, false) or coalesce(e.is_error, false) then 1 else 0 end as error_flag,
                    case
                        when coalesce(s.is_error, false) or coalesce(e.is_error, false)
                          or coalesce(s.duration_ms, 0) >= 3000 then 'critical'
                        when coalesce(s.duration_ms, 0) >= 1000 then 'warning'
                        else 'normal'
                    end as severity_level
                from analytics.stage s
                join analytics.event e on e.id = s.event_id
                join analytics.event_type et on et.code = e.event_type_code
                where e.started_at between :from and :to
                  and (:eventFilterEnabled = false or e.event_type_code in (:eventCodes))
                  and (:stageFilterEnabled = false or s.stage_type_code in (:stageTypeCodes))
                  and (:moduleEnabled = false or coalesce(e.module_code, 'DEFAULT') = :moduleCode)
                  and (:selectedStageEnabled = false or s.stage_type_code = :selectedStageTypeCode)
                  and (:systemScopeEnabled = false or coalesce(et.is_system, false) = :systemEventsOnly)
            ),
            problem_events as (
                select distinct event_id
                from selected_stages
                where severity_level in ('critical', 'warning')
            ),
            problem_stats as (
                select
                    count(*)::bigint as problem_event_count,
                    (select count(*) from selected_stages where severity_level = 'critical')::bigint as critical_value_count,
                    (select count(*) from selected_stages where severity_level = 'warning')::bigint as warning_value_count
                from problem_events
            ),
            event_factors as (
                select 'EVENT_TYPE' as factor_code, e.event_type_code as factor_value, 0 as priority,
                    e.id as event_id, coalesce(e.duration_ms, 0) as duration_ms, case when e.is_error then 1 else 0 end as error_flag
                from problem_events pe join analytics.event e on e.id = pe.event_id
            ),
            error_factors as (
                select 'ERROR_MESSAGE' as factor_code,
                    coalesce(nullif(trim(e.error_message), ''), concat('HTTP ', coalesce(e.status_code, 0))) as factor_value,
                    1 as priority, e.id as event_id, coalesce(e.duration_ms, 0) as duration_ms, 1 as error_flag
                from problem_events pe join analytics.event e on e.id = pe.event_id
                where coalesce(e.is_error, false) = true
            ),
            attribute_factors as (
                select a.attribute_type_code as factor_code,
                    trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))) as factor_value,
                    case
                        when upper(a.attribute_type_code) in ('HTTP_PATH', 'REQUEST_PATH', 'PATH') then 2
                        when upper(a.attribute_type_code) in ('REFERRER', 'REFERER') then 3
                        when upper(a.attribute_type_code) in ('CATEGORY', 'CATEGORY_SLUG', 'CATEGORY_NAME') then 4
                        when upper(a.attribute_type_code) in ('CLIENT_TYPE', 'USER_AGENT', 'HTTP_STATUS', 'HTTP_METHOD') then 5
                        when upper(a.attribute_type_code) like '%REQUEST%ID%'
                          or upper(a.attribute_type_code) like '%SESSION%HASH%'
                          or upper(a.attribute_type_code) like '%USER%HASH%'
                          or upper(a.attribute_type_code) like '%TRACE%ID%'
                          or upper(a.attribute_type_code) in ('ENTITY_ID', 'UUID') then 6
                        else 7
                    end as priority,
                    e.id as event_id, coalesce(e.duration_ms, 0) as duration_ms, case when e.is_error then 1 else 0 end as error_flag
                from problem_events pe
                join analytics.event e on e.id = pe.event_id
                join analytics.event_attribute a on a.event_id = e.id
                where trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))) <> ''
            ),
            all_factors as (
                select * from event_factors
                union all select * from error_factors
                union all select * from attribute_factors
            ),
            factor_counts as (
                select
                    factor_code, factor_value, priority,
                    count(distinct event_id)::bigint as count,
                    cast(case when (select problem_event_count from problem_stats) = 0 then 0
                        else count(distinct event_id)::numeric / (select problem_event_count from problem_stats) * 100 end as numeric(12, 4)) as share,
                    cast(coalesce(avg(duration_ms), 0) as numeric(12, 3)) as avg_ms,
                    cast(coalesce(percentile_cont(0.95) within group (order by duration_ms::numeric), 0) as numeric(12, 3)) as p95_ms,
                    cast(case when count(distinct event_id) = 0 then 0 else sum(error_flag)::numeric / count(distinct event_id) end as numeric(12, 6)) as error_rate
                from all_factors
                group by factor_code, factor_value, priority
            ),
            ranked as (
                select *, row_number() over (partition by factor_code order by share desc, count desc, p95_ms desc) as factor_rank
                from factor_counts
            ),
            top_factors as (
                select * from ranked
                where factor_rank <= 5
                order by priority, share desc, count desc, p95_ms desc
                limit :limit
            )
            select tf.*, ps.problem_event_count, ps.critical_value_count, ps.warning_value_count
            from problem_stats ps
            left join top_factors tf on true
            order by tf.priority asc nulls last, tf.share desc nulls last, tf.count desc nulls last
            """;

        List<UniversalRootCauseFactorDto> factors = new ArrayList<>();
        long[] stats = new long[3];
        jdbcTemplate.query(sql, params, rs -> {
            stats[0] = rs.getLong("problem_event_count");
            stats[1] = rs.getLong("critical_value_count");
            stats[2] = rs.getLong("warning_value_count");
            if (rs.getString("factor_code") != null) {
                factors.add(new UniversalRootCauseFactorDto(
                    rs.getString("factor_code"),
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
            safeModuleCode == null ? "MODULE" : safeModuleCode,
            safeSelectedStage,
            stats[0],
            stats[1],
            stats[2],
            factors
        );
    }

    private static MapSqlParameterSource baseParams(
        Instant from,
        Instant to,
        List<String> eventCodes,
        List<String> stageTypeCodes
    ) {
        return new MapSqlParameterSource()
            .addValue("from", Timestamp.from(from))
            .addValue("to", Timestamp.from(to))
            .addValue("eventCodes", eventCodes.isEmpty() ? List.of("__none__") : eventCodes)
            .addValue("stageTypeCodes", stageTypeCodes.isEmpty() ? List.of("__none__") : stageTypeCodes)
            .addValue("eventFilterEnabled", !eventCodes.isEmpty(), Types.BOOLEAN)
            .addValue("stageFilterEnabled", !stageTypeCodes.isEmpty(), Types.BOOLEAN);
    }

    private static String sortOrder(String sortBy, String direction) {
        return switch (String.valueOf(sortBy).toLowerCase(Locale.ROOT)) {
            case "module", "name", "value" -> "module_name " + direction + ", stage_type_name " + direction;
            case "share" -> "share " + direction;
            case "errors", "errorcount" -> "error_count " + direction;
            case "error", "errorrate" -> "error_rate " + direction;
            case "avg", "avgms" -> "avg_ms " + direction;
            case "p95", "p95ms" -> "p95_ms " + direction;
            case "events", "eventcount" -> "event_count " + direction;
            case "count" -> "count " + direction;
            default -> "case severity_level when 'critical' then 2 when 'warning' then 1 else 0 end desc, "
                + "error_rate desc, share desc, count desc, p95_ms desc, avg_ms desc";
        };
    }

    private static List<String> normalizeList(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .map(AnalyticsUniversalModuleBreakdownService::normalizeText)
            .filter(value -> value != null)
            .distinct()
            .toList();
    }

    private static String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return (value == null ? BigDecimal.ZERO : value).setScale(scale, RoundingMode.HALF_UP);
    }
}
