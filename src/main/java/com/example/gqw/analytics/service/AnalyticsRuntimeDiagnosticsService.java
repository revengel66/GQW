package com.example.gqw.analytics.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsRuntimeDiagnosticsService {

    private static final List<Integer> SUPPORTED_GRANULARITIES = List.of(1, 5, 60, 1440);
    private static final List<String> TIME_SCOPES = List.of("EVENT", "STAGE", "METRIC");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AnalyticsRuntimeSettingsService runtimeSettingsService;
    private final AnalyticsLogArchiveIndexService logArchiveIndexService;
    private final Clock clock;

    public AnalyticsRuntimeDiagnosticsService(
        NamedParameterJdbcTemplate jdbcTemplate,
        AnalyticsRuntimeSettingsService runtimeSettingsService,
        AnalyticsLogArchiveIndexService logArchiveIndexService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.runtimeSettingsService = runtimeSettingsService;
        this.logArchiveIndexService = logArchiveIndexService;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public DiagnosticsView view() {
        Instant now = Instant.now(clock);

        List<TableDiagnostic> tables = List.of(
            readTableDiagnostic("analytics.event", "started_at"),
            readTableDiagnostic("analytics.stage", "started_at"),
            readTableDiagnostic("analytics.stage_metric", "recorded_at"),
            readTableDiagnostic("analytics.event_rollup_bucket", "bucket_start"),
            readTableDiagnostic("analytics.stage_rollup_bucket", "bucket_start"),
            readTableDiagnostic("analytics.stage_metric_rollup_bucket", "bucket_start"),
            readTableDiagnostic("analytics.filter_event_type_day", "day_start"),
            readTableDiagnostic("analytics.filter_attr_value_day", "day_start")
        );

        Map<String, WatermarkRow> dbWatermarks = loadDbWatermarks();
        List<WatermarkDiagnostic> watermarks = new ArrayList<>();
        List<EtaDiagnostic> eta = new ArrayList<>();

        boolean timeRollupEnabled = runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_TIME_ROLLUP_ENABLED,
            true
        );
        boolean stageMetricRollupEnabled = runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_STAGE_METRIC_ROLLUP_ENABLED,
            true
        );
        int timeRollupInterval = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_TIME_ROLLUP_REFRESH_INTERVAL_MINUTES,
            5,
            1,
            180
        );
        int stageMetricInterval = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_STAGE_METRIC_ROLLUP_REFRESH_INTERVAL_MINUTES,
            5,
            1,
            180
        );
        int filterInterval = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_FILTER_ROLLUP_REFRESH_INTERVAL_MINUTES,
            10,
            1,
            1440
        );

        for (String scope : TIME_SCOPES) {
            boolean enabled = switch (scope) {
                case "EVENT", "STAGE" -> timeRollupEnabled;
                case "METRIC" -> stageMetricRollupEnabled;
                default -> false;
            };
            int interval = "METRIC".equals(scope) ? stageMetricInterval : timeRollupInterval;
            for (Integer granularity : SUPPORTED_GRANULARITIES) {
                WatermarkRow row = dbWatermarks.get(buildWatermarkKey(scope, granularity));
                Instant watermarkAt = row == null ? null : row.watermarkAt();
                Instant updatedAt = row == null ? null : row.updatedAt();
                Long lagMinutes = watermarkAt == null
                    ? null
                    : Math.max(0L, Duration.between(watermarkAt, now).toMinutes());
                watermarks.add(new WatermarkDiagnostic(
                    scope,
                    granularity,
                    watermarkAt,
                    updatedAt,
                    lagMinutes,
                    enabled
                ));
                eta.add(buildEta(scope, granularity, enabled, interval, lagMinutes, watermarkAt));
            }
        }

        boolean filterRollupEnabled = runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_FILTER_ROLLUP_ENABLED,
            true
        );
        Instant filterWatermark = readFilterWatermark();
        Long filterLagMinutes = filterWatermark == null
            ? null
            : Math.max(0L, Duration.between(filterWatermark, now).toMinutes());
        watermarks.add(new WatermarkDiagnostic(
            "FILTER",
            1440,
            filterWatermark,
            filterWatermark,
            filterLagMinutes,
            filterRollupEnabled
        ));
        eta.add(buildEta("FILTER", 1440, filterRollupEnabled, filterInterval, filterLagMinutes, filterWatermark));

        SummaryDiagnostic summary = buildSummary(now, tables, watermarks);
        return new DiagnosticsView(now, summary, tables, watermarks, eta, logArchiveIndexService.diagnostics());
    }

    private SummaryDiagnostic buildSummary(
        Instant now,
        List<TableDiagnostic> tables,
        List<WatermarkDiagnostic> watermarks
    ) {
        long rawRowsEstimate = tables.stream()
            .filter(item -> "analytics.event".equals(item.tableName()))
            .map(TableDiagnostic::rowEstimate)
            .filter(value -> value != null && value > 0)
            .findFirst()
            .orElse(0L);
        long rollupRowsEstimate = tables.stream()
            .filter(item -> item.tableName().contains("rollup") || item.tableName().contains("filter_"))
            .map(TableDiagnostic::rowEstimate)
            .filter(value -> value != null && value > 0)
            .mapToLong(Long::longValue)
            .sum();
        Long maxLagMinutes = watermarks.stream()
            .filter(WatermarkDiagnostic::enabled)
            .map(WatermarkDiagnostic::lagMinutes)
            .filter(value -> value != null)
            .max(Long::compareTo)
            .orElse(null);
        Long minLagMinutes = watermarks.stream()
            .filter(WatermarkDiagnostic::enabled)
            .map(WatermarkDiagnostic::lagMinutes)
            .filter(value -> value != null)
            .min(Long::compareTo)
            .orElse(null);
        long staleCount = watermarks.stream()
            .filter(WatermarkDiagnostic::enabled)
            .filter(item -> item.lagMinutes() != null && item.lagMinutes() > item.granularityMinutes())
            .count();

        return new SummaryDiagnostic(
            now,
            rawRowsEstimate,
            rollupRowsEstimate,
            maxLagMinutes,
            minLagMinutes,
            staleCount
        );
    }

    private EtaDiagnostic buildEta(
        String scope,
        int granularityMinutes,
        boolean enabled,
        int refreshIntervalMinutes,
        Long lagMinutes,
        Instant watermarkAt
    ) {
        if (!enabled) {
            return new EtaDiagnostic(
                scope,
                granularityMinutes,
                "DISABLED",
                null,
                "Rollup для этого scope отключён в runtime-настройках."
            );
        }
        if (watermarkAt == null || lagMinutes == null) {
            return new EtaDiagnostic(
                scope,
                granularityMinutes,
                "BOOTSTRAP_REQUIRED",
                null,
                "Watermark ещё не создан. Нужен первый backfill."
            );
        }
        if (lagMinutes <= granularityMinutes) {
            return new EtaDiagnostic(
                scope,
                granularityMinutes,
                "UP_TO_DATE",
                0L,
                "Агрегация актуальна, отставание в пределах выбранной гранулярности."
            );
        }
        return new EtaDiagnostic(
            scope,
            granularityMinutes,
            "PENDING_REFRESH",
            (long) refreshIntervalMinutes,
            "Ожидается догон в ближайшем цикле планировщика."
        );
    }

    private Map<String, WatermarkRow> loadDbWatermarks() {
        if (!tableExists("analytics.time_rollup_watermark")) {
            return Map.of();
        }
        return jdbcTemplate.query(
            """
                select scope_code, granularity_minutes, watermark_at, updated_at
                from analytics.time_rollup_watermark
            """,
            rs -> {
                Map<String, WatermarkRow> map = new LinkedHashMap<>();
                while (rs.next()) {
                    String scope = String.valueOf(rs.getString("scope_code")).trim().toUpperCase();
                    int granularity = rs.getInt("granularity_minutes");
                    Timestamp watermarkTs = rs.getTimestamp("watermark_at");
                    Timestamp updatedTs = rs.getTimestamp("updated_at");
                    map.put(
                        buildWatermarkKey(scope, granularity),
                        new WatermarkRow(
                            scope,
                            granularity,
                            watermarkTs == null ? null : watermarkTs.toInstant(),
                            updatedTs == null ? null : updatedTs.toInstant()
                        )
                    );
                }
                return map;
            }
        );
    }

    private Instant readFilterWatermark() {
        if (!tableExists("analytics.filter_event_type_day")) {
            return null;
        }
        List<Instant> rows = jdbcTemplate.query(
            """
                select max(day_start) as max_day
                from analytics.filter_event_type_day
            """,
            (rs, rowNum) -> {
                LocalDate maxDay = rs.getObject("max_day", LocalDate.class);
                if (maxDay == null) {
                    return null;
                }
                return maxDay.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            }
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private TableDiagnostic readTableDiagnostic(String tableName, String timeColumn) {
        if (!tableExists(tableName)) {
            return new TableDiagnostic(tableName, false, null, null, null, null);
        }
        Long estimate = readRowEstimate(tableName);
        Long totalBytes = readTotalBytes(tableName);
        TimeRange range = readTimeRange(tableName, timeColumn);
        return new TableDiagnostic(
            tableName,
            true,
            estimate,
            totalBytes,
            range.minTime(),
            range.maxTime()
        );
    }

    private Long readRowEstimate(String tableName) {
        String[] parts = splitTableName(tableName);
        List<Long> rows = jdbcTemplate.query(
            """
                select round(c.reltuples)::bigint as approx_rows
                from pg_class c
                join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = :schemaName
                  and c.relname = :tableName
                  and c.relkind in ('r', 'p')
            """,
            new MapSqlParameterSource()
                .addValue("schemaName", parts[0])
                .addValue("tableName", parts[1]),
            (rs, rowNum) -> rs.getLong("approx_rows")
        );
        return rows.isEmpty() ? null : Math.max(0L, rows.getFirst());
    }

    private Long readTotalBytes(String tableName) {
        List<Long> rows = jdbcTemplate.query(
            """
                select pg_total_relation_size(:regclass::regclass) as bytes
            """,
            new MapSqlParameterSource().addValue("regclass", tableName),
            (rs, rowNum) -> rs.getLong("bytes")
        );
        return rows.isEmpty() ? null : Math.max(0L, rows.getFirst());
    }

    private TimeRange readTimeRange(String tableName, String timeColumn) {
        String sql = switch (tableName) {
            case "analytics.filter_event_type_day", "analytics.filter_attr_value_day" ->
                "select min(day_start)::timestamp as min_time, max(day_start)::timestamp as max_time from " + tableName;
            default ->
                "select min(" + timeColumn + ") as min_time, max(" + timeColumn + ") as max_time from " + tableName;
        };
        return jdbcTemplate.query(
            sql,
            rs -> {
                if (!rs.next()) {
                    return new TimeRange(null, null);
                }
                Timestamp min = rs.getTimestamp("min_time");
                Timestamp max = rs.getTimestamp("max_time");
                return new TimeRange(
                    min == null ? null : min.toInstant(),
                    max == null ? null : max.toInstant()
                );
            }
        );
    }

    private boolean tableExists(String regclass) {
        Boolean exists = jdbcTemplate.queryForObject(
            "select to_regclass(:regclass) is not null",
            Map.of("regclass", regclass),
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    private static String buildWatermarkKey(String scopeCode, int granularityMinutes) {
        return scopeCode + ":" + granularityMinutes;
    }

    private static String[] splitTableName(String tableName) {
        String[] parts = String.valueOf(tableName).split("\\.", 2);
        if (parts.length == 2) {
            return parts;
        }
        return new String[]{"public", parts[0]};
    }

    public record DiagnosticsView(
        Instant generatedAt,
        SummaryDiagnostic summary,
        List<TableDiagnostic> tables,
        List<WatermarkDiagnostic> watermarks,
        List<EtaDiagnostic> eta,
        AnalyticsLogArchiveIndexService.LogIndexDiagnostics logIndex
    ) {
    }

    public record SummaryDiagnostic(
        Instant generatedAt,
        long rawRowsEstimate,
        long rollupRowsEstimate,
        Long maxLagMinutes,
        Long minLagMinutes,
        long staleWatermarkCount
    ) {
    }

    public record TableDiagnostic(
        String tableName,
        boolean exists,
        Long rowEstimate,
        Long totalBytes,
        Instant minTime,
        Instant maxTime
    ) {
    }

    public record WatermarkDiagnostic(
        String scopeCode,
        int granularityMinutes,
        Instant watermarkAt,
        Instant updatedAt,
        Long lagMinutes,
        boolean enabled
    ) {
    }

    public record EtaDiagnostic(
        String scopeCode,
        int granularityMinutes,
        String status,
        Long etaMinutes,
        String details
    ) {
    }

    private record WatermarkRow(
        String scopeCode,
        int granularityMinutes,
        Instant watermarkAt,
        Instant updatedAt
    ) {
    }

    private record TimeRange(
        Instant minTime,
        Instant maxTime
    ) {
    }
}


