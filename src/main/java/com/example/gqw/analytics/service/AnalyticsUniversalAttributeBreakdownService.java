package com.example.gqw.analytics.service;

import org.springframework.beans.factory.annotation.Qualifier;

import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalAttributeBreakdownResponse;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.UniversalAttributeBreakdownRowDto;
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
public class AnalyticsUniversalAttributeBreakdownService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_SOFT_LIMIT = 500;
    private static final int DEFAULT_HARD_LIMIT = 2000;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AnalyticsUniversalAttributeBreakdownService(@Qualifier("analyticsNamedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UniversalAttributeBreakdownResponse breakdown(
        Instant from,
        Instant to,
        Collection<String> eventCodes,
        Collection<String> stageTypeCodes,
        String moduleCode,
        String attributeCode,
        Integer limit,
        Integer offset,
        String sortBy,
        String sortDir
    ) {
        String safeAttributeCode = normalizeText(attributeCode);
        if (safeAttributeCode == null) {
            return new UniversalAttributeBreakdownResponse("", 0, 0, 0, 0, 0, List.of());
        }
        int safeLimit = Math.max(1, Math.min(MAX_LIMIT, limit == null ? DEFAULT_LIMIT : limit));
        int safeOffset = Math.max(0, offset == null ? 0 : offset);
        List<String> safeEventCodes = normalizeList(eventCodes);
        List<String> safeStageCodes = normalizeList(stageTypeCodes);
        String safeModuleCode = normalizeText(moduleCode);
        String direction = "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
        boolean defaultSort = isDefaultSort(sortBy);
        String sortOrder = sortOrder(sortBy, direction);

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", Timestamp.from(from))
            .addValue("to", Timestamp.from(to))
            .addValue("attributeCode", safeAttributeCode, Types.VARCHAR)
            .addValue("eventCodes", safeEventCodes.isEmpty() ? List.of("__none__") : safeEventCodes)
            .addValue("stageTypeCodes", safeStageCodes.isEmpty() ? List.of("__none__") : safeStageCodes)
            .addValue("eventFilterEnabled", !safeEventCodes.isEmpty(), Types.BOOLEAN)
            .addValue("stageFilterEnabled", !safeStageCodes.isEmpty(), Types.BOOLEAN)
            .addValue("moduleCode", safeModuleCode, Types.VARCHAR)
            .addValue("moduleEnabled", safeModuleCode != null, Types.BOOLEAN)
            .addValue("limit", safeLimit, Types.INTEGER)
            .addValue("offset", safeOffset, Types.INTEGER)
            .addValue("defaultSort", defaultSort, Types.BOOLEAN)
            .addValue("softLimit", DEFAULT_SOFT_LIMIT, Types.INTEGER)
            .addValue("hardLimit", DEFAULT_HARD_LIMIT, Types.INTEGER);

        String sql = """
            with context_events as (
                select
                    e.id as event_id,
                    case when e.duration_ms is null or e.duration_ms < 0 then 0 else e.duration_ms end as duration_ms,
                    case when e.is_error = true then 1 else 0 end as error_flag
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
            context_problem_stats as (
                select coalesce(count(*), 0)::bigint as problem_event_count
                from context_events
                where error_flag > 0 or duration_ms >= 1000
            ),
            base as (
                select
                    trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))) as value,
                    ce.duration_ms,
                    ce.error_flag
                from analytics.event_attribute a
                join context_events ce on ce.event_id = a.event_id
                where a.attribute_type_code = :attributeCode
                  and trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))) <> ''
            ),
            grouped as (
                select
                    value,
                    cast(count(*) as bigint) as count,
                    cast(coalesce(avg(duration_ms), 0) as numeric(12, 3)) as avg_ms,
                    cast(coalesce(percentile_cont(0.95) within group (order by cast(duration_ms as numeric)), 0) as numeric(12, 3)) as p95_ms,
                    cast(case when count(*) = 0 then 0 else (cast(sum(error_flag) as numeric) / count(*)) end as numeric(12, 6)) as error_rate
                from base
                group by value
            ),
            enriched as (
                select
                    value,
                    count,
                    cast(case when sum(count) over() = 0 then 0 else ((cast(count as numeric) / sum(count) over()) * 100) end as numeric(12, 4)) as share,
                    avg_ms,
                    p95_ms,
                    error_rate,
                    cast(count(*) over() as bigint) as total_values,
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
            stats as (
                select
                    coalesce(count(*), 0)::bigint as total_values,
                    coalesce(sum(case when severity_level = 'critical' then 1 else 0 end), 0)::bigint as critical_total,
                    coalesce(sum(case when severity_level = 'warning' then 1 else 0 end), 0)::bigint as warning_total,
                    coalesce(sum(case when severity_level = 'normal' then 1 else 0 end), 0)::bigint as normal_total,
                    (select problem_event_count from context_problem_stats) as problem_event_count
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
                    row_number() over (order by %s, c.value asc) as rn,
                    case
                        when :defaultSort = true and :offset = 0
                        then least(:hardLimit, greatest(:limit, least(:softLimit, s.critical_total + s.warning_total)))
                        else :limit
                    end::integer as page_limit
                from classified c
                cross join stats s
            )
            select
                value,
                count,
                share,
                avg_ms,
                p95_ms,
                error_rate,
                severity_level,
                result_total_values as total_values,
                critical_total,
                warning_total,
                normal_total,
                problem_event_count
            from ranked
            where rn > :offset and rn <= (:offset + page_limit)
            order by rn
            """.formatted(sortOrder);

        List<UniversalAttributeBreakdownRowDto> rows = new ArrayList<>();
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
            rows.add(new UniversalAttributeBreakdownRowDto(
                rs.getString("value"),
                rs.getLong("count"),
                scale(rs.getBigDecimal("share"), 4),
                scale(rs.getBigDecimal("avg_ms"), 3),
                scale(rs.getBigDecimal("p95_ms"), 3),
                scale(rs.getBigDecimal("error_rate"), 6),
                rs.getString("severity_level")
            ));
        });
        return new UniversalAttributeBreakdownResponse(safeAttributeCode, total[0], criticalTotal[0], warningTotal[0], normalTotal[0], problemEventCount[0], rows);
    }

    private static boolean isDefaultSort(String sortBy) {
        String normalized = String.valueOf(sortBy).toLowerCase(Locale.ROOT);
        return normalized.isBlank() || "null".equals(normalized) || "interesting".equals(normalized) || "riskscore".equals(normalized);
    }

    private static String sortOrder(String sortBy, String direction) {
        return switch (String.valueOf(sortBy).toLowerCase(Locale.ROOT)) {
            case "value" -> "value " + direction;
            case "share" -> "share " + direction;
            case "avg", "avgms" -> "avg_ms " + direction;
            case "p95", "p95ms" -> "p95_ms " + direction;
            case "error", "errorrate" -> "error_rate " + direction;
            case "count" -> "count " + direction;
            case "interesting", "riskscore" -> interestingSortOrder(direction);
            default -> interestingSortOrder("desc");
        };
    }

    private static String interestingSortOrder(String direction) {
        String safeDirection = "asc".equalsIgnoreCase(direction) ? "asc" : "desc";
        return "case severity_level when 'critical' then 2 when 'warning' then 1 else 0 end " + safeDirection
            + ", case when severity_level = 'critical' then error_rate else 0 end " + safeDirection
            + ", case when severity_level = 'critical' then p95_ms else 0 end " + safeDirection
            + ", case when severity_level = 'critical' then avg_ms else 0 end " + safeDirection
            + ", case when severity_level = 'critical' then count else 0 end " + safeDirection
            + ", case when severity_level = 'critical' then share else 0 end " + safeDirection
            + ", case when severity_level = 'warning' then p95_ms else 0 end " + safeDirection
            + ", case when severity_level = 'warning' then avg_ms else 0 end " + safeDirection
            + ", case when severity_level = 'warning' then count else 0 end " + safeDirection
            + ", case when severity_level = 'warning' then share else 0 end " + safeDirection
            + ", case when severity_level = 'normal' then count else 0 end " + safeDirection
            + ", case when severity_level = 'normal' then share else 0 end " + safeDirection
            + ", case when severity_level = 'normal' then p95_ms else 0 end " + safeDirection
            + ", case when severity_level = 'normal' then avg_ms else 0 end " + safeDirection;
    }

    private static List<String> normalizeList(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .map(AnalyticsUniversalAttributeBreakdownService::normalizeText)
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
