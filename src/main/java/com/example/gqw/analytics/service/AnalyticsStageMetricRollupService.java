package com.example.gqw.analytics.service;

import com.example.gqw.analytics.web.dto.AnalyticsApiDto.TimeSeriesPointDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.TopValueDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsStageMetricRollupService {

    private static final String WATERMARK_SCOPE_METRIC = "METRIC";
    private static final String DEFAULT_MODULE_CODE = "DEFAULT";
    private static final Instant DATE_BIN_ORIGIN = Instant.parse("2001-01-01T00:00:00Z");
    private static final List<Integer> SUPPORTED_GRANULARITIES = List.of(1, 5, 60, 1440);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AnalyticsRuntimeSettingsService runtimeSettingsService;
    private final Clock clock;
    private final boolean defaultEnabled;
    private final int defaultOverlapMinutes;
    private final int defaultBootstrapLookbackDays;
    private final int defaultRefreshIntervalMinutes;
    private final Object refreshLock = new Object();
    private volatile Instant lastScheduledRefreshAt;

    public AnalyticsStageMetricRollupService(
        NamedParameterJdbcTemplate jdbcTemplate,
        AnalyticsRuntimeSettingsService runtimeSettingsService,
        @Value("${app.analytics.stage-metric-rollup.enabled:true}") boolean enabled,
        @Value("${app.analytics.stage-metric-rollup.refresh-interval-minutes:5}") int refreshIntervalMinutes,
        @Value("${app.analytics.stage-metric-rollup.overlap-minutes:10}") int overlapMinutes,
        @Value("${app.analytics.stage-metric-rollup.bootstrap-lookback-days:370}") int bootstrapLookbackDays
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.runtimeSettingsService = runtimeSettingsService;
        this.clock = Clock.systemUTC();
        this.defaultEnabled = enabled;
        this.defaultRefreshIntervalMinutes = Math.max(1, refreshIntervalMinutes);
        this.defaultOverlapMinutes = Math.max(1, overlapMinutes);
        this.defaultBootstrapLookbackDays = Math.max(1, bootstrapLookbackDays);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled() {
        if (!runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_STAGE_METRIC_ROLLUP_ENABLED,
            defaultEnabled
        )) {
            return false;
        }
        return tableExists("analytics.stage_metric_rollup_bucket")
            && tableExists("analytics.time_rollup_watermark");
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void scheduledRefresh() {
        int intervalMinutes = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_STAGE_METRIC_ROLLUP_REFRESH_INTERVAL_MINUTES,
            defaultRefreshIntervalMinutes,
            1,
            180
        );
        Instant now = Instant.now(clock);
        synchronized (refreshLock) {
            if (lastScheduledRefreshAt != null
                && now.isBefore(lastScheduledRefreshAt.plusSeconds(intervalMinutes * 60L))) {
                return;
            }
            refreshAllGranularities();
            lastScheduledRefreshAt = now;
        }
    }

    @Transactional
    public void initializeIfNeeded() {
        synchronized (refreshLock) {
            refreshAllGranularities();
            lastScheduledRefreshAt = Instant.now(clock);
        }
    }

    private void refreshAllGranularities() {
        if (!isEnabled()) {
            return;
        }
        int overlapMinutes = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_STAGE_METRIC_ROLLUP_OVERLAP_MINUTES,
            defaultOverlapMinutes,
            1,
            180
        );
        int bootstrapLookbackDays = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_STAGE_METRIC_ROLLUP_BOOTSTRAP_LOOKBACK_DAYS,
            defaultBootstrapLookbackDays,
            1,
            3650
        );
        for (Integer granularity : SUPPORTED_GRANULARITIES) {
            refreshMetricRollup(granularity, overlapMinutes, bootstrapLookbackDays);
        }
    }

    @Transactional(readOnly = true)
    public List<MetricSummaryPoint> loadMetricSummaries(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String stageTypeCode
    ) {
        if (!isEnabled()) {
            return queryRawMetricSummaries(from, to, moduleCode, eventTypeCode, stageTypeCode);
        }

        int sourceGranularity = chooseSourceGranularityMinutes(from, to, 1440);
        Instant watermark = readWatermark(WATERMARK_SCOPE_METRIC, sourceGranularity);
        Instant cutoff = clampCutoff(from, to, watermark);
        boolean tailMergeEnabled = runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_TAIL_MERGE_ENABLED,
            true
        );

        List<MetricSummaryPoint> result = new ArrayList<>();
        if (cutoff.isAfter(from)) {
            result.addAll(queryRollupMetricSummaries(from, cutoff, moduleCode, eventTypeCode, stageTypeCode, sourceGranularity));
        }
        if (tailMergeEnabled && to.isAfter(cutoff)) {
            result.addAll(queryRawMetricSummaries(cutoff, to, moduleCode, eventTypeCode, stageTypeCode));
        }
        return mergeMetricSummaries(result);
    }

    @Transactional(readOnly = true)
    public List<TimeSeriesPointDto> loadNumericSeries(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String stageTypeCode,
        String metricTypeCode,
        int targetBucketMinutes
    ) {
        if (!isEnabled()) {
            return seriesFromPoints(from, to, targetBucketMinutes, queryRawSeriesPoints(
                from,
                to,
                moduleCode,
                eventTypeCode,
                stageTypeCode,
                metricTypeCode,
                targetBucketMinutes
            ));
        }

        int sourceGranularity = chooseSourceGranularityMinutes(from, to, targetBucketMinutes);
        Instant watermark = readWatermark(WATERMARK_SCOPE_METRIC, sourceGranularity);
        Instant cutoff = clampCutoff(from, to, watermark);
        boolean tailMergeEnabled = runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_TAIL_MERGE_ENABLED,
            true
        );

        List<SeriesPoint> points = new ArrayList<>();
        if (cutoff.isAfter(from)) {
            points.addAll(queryRollupSeriesPoints(
                from,
                cutoff,
                moduleCode,
                eventTypeCode,
                stageTypeCode,
                metricTypeCode,
                sourceGranularity,
                targetBucketMinutes
            ));
        }
        if (tailMergeEnabled && to.isAfter(cutoff)) {
            points.addAll(queryRawSeriesPoints(
                cutoff,
                to,
                moduleCode,
                eventTypeCode,
                stageTypeCode,
                metricTypeCode,
                targetBucketMinutes
            ));
        }
        return seriesFromPoints(from, to, targetBucketMinutes, mergeSeriesPoints(points));
    }

    @Transactional(readOnly = true)
    public List<TopValueDto> loadTopValues(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String stageTypeCode,
        String metricTypeCode,
        int limit
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("fromTs", asTimestamp(from))
            .addValue("toTs", asTimestamp(to))
            .addValue("moduleCode", moduleCode, Types.VARCHAR)
            .addValue("eventTypeCode", eventTypeCode, Types.VARCHAR)
            .addValue("stageTypeCode", stageTypeCode, Types.VARCHAR)
            .addValue("metricTypeCode", metricTypeCode, Types.VARCHAR)
            .addValue("limit", Math.max(1, limit));

        return jdbcTemplate.query(
            """
                select
                    value as value,
                    count(*) as cnt
                from (
                    select
                        coalesce(nullif(trim(sm.metric_value_text), ''), sm.metric_value_num::text) as value
                    from analytics.stage_metric sm
                    join analytics.stage s on s.id = sm.stage_id
                    join analytics.event e on e.id = s.event_id
                    where sm.recorded_at >= :fromTs
                      and sm.recorded_at < :toTs
                      and (:moduleCode is null or e.module_code = :moduleCode)
                      and (:eventTypeCode is null or e.event_type_code = :eventTypeCode)
                      and (:stageTypeCode is null or s.stage_type_code = :stageTypeCode)
                      and (:metricTypeCode is null or sm.metric_type_code = :metricTypeCode)
                ) v
                where v.value is not null
                group by value
                order by cnt desc, value asc
                limit :limit
            """,
            params,
            (rs, rowNum) -> new TopValueDto(rs.getString("value"), rs.getLong("cnt"))
        );
    }

    private void refreshMetricRollup(int granularityMinutes, int overlapMinutes, int bootstrapLookbackDays) {
        Instant now = Instant.now(clock);
        Instant toExclusive = floorToBucket(now, granularityMinutes);
        if (toExclusive == null) {
            return;
        }

        Instant watermark = readWatermark(WATERMARK_SCOPE_METRIC, granularityMinutes);
        Instant fromInclusive = watermark == null
            ? resolveBootstrapStart(granularityMinutes, toExclusive, bootstrapLookbackDays)
            : floorToBucket(watermark.minusSeconds(overlapMinutes * 60L), granularityMinutes);
        if (fromInclusive == null || !toExclusive.isAfter(fromInclusive)) {
            return;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("fromTs", asTimestamp(fromInclusive))
            .addValue("toTs", asTimestamp(toExclusive))
            .addValue("granularityMinutes", granularityMinutes)
            .addValue("defaultModuleCode", DEFAULT_MODULE_CODE)
            .addValue("originTs", asTimestamp(DATE_BIN_ORIGIN));

        jdbcTemplate.update(
            """
                delete from analytics.stage_metric_rollup_bucket
                where granularity_minutes = :granularityMinutes
                  and bucket_start >= :fromTs
                  and bucket_start < :toTs
            """,
            params
        );

        jdbcTemplate.update(
            """
                insert into analytics.stage_metric_rollup_bucket (
                    bucket_start,
                    granularity_minutes,
                    module_code,
                    event_type_code,
                    stage_type_code,
                    metric_type_code,
                    unit,
                    sample_count,
                    numeric_count,
                    numeric_sum,
                    p95_value,
                    min_value,
                    max_value
                )
                select
                    date_bin(
                        (:granularityMinutes || ' minutes')::interval,
                        sm.recorded_at,
                        :originTs
                    ) as bucket_start,
                    :granularityMinutes as granularity_minutes,
                    coalesce(e.module_code, :defaultModuleCode) as module_code,
                    e.event_type_code,
                    s.stage_type_code,
                    sm.metric_type_code,
                    min(nullif(trim(sm.unit), '')) as unit,
                    count(*) as sample_count,
                    count(sm.metric_value_num) as numeric_count,
                    coalesce(sum(sm.metric_value_num), 0)::numeric(20, 3) as numeric_sum,
                    coalesce(
                        percentile_cont(0.95) within group (order by sm.metric_value_num)
                            filter (where sm.metric_value_num is not null),
                        0
                    )::numeric(20, 3) as p95_value,
                    min(sm.metric_value_num) as min_value,
                    max(sm.metric_value_num) as max_value
                from analytics.stage_metric sm
                join analytics.stage s on s.id = sm.stage_id
                join analytics.event e on e.id = s.event_id
                where sm.recorded_at >= :fromTs
                  and sm.recorded_at < :toTs
                  and e.event_type_code is not null
                  and s.stage_type_code is not null
                  and sm.metric_type_code is not null
                group by 1, 3, 4, 5, 6
                on conflict (
                    bucket_start,
                    granularity_minutes,
                    module_code,
                    event_type_code,
                    stage_type_code,
                    metric_type_code
                )
                do update set
                    unit = excluded.unit,
                    sample_count = excluded.sample_count,
                    numeric_count = excluded.numeric_count,
                    numeric_sum = excluded.numeric_sum,
                    p95_value = excluded.p95_value,
                    min_value = excluded.min_value,
                    max_value = excluded.max_value
            """,
            params
        );

        upsertWatermark(WATERMARK_SCOPE_METRIC, granularityMinutes, toExclusive);
    }

    private List<MetricSummaryPoint> queryRollupMetricSummaries(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String stageTypeCode,
        int sourceGranularityMinutes
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("fromTs", asTimestamp(from))
            .addValue("toTs", asTimestamp(to))
            .addValue("sourceGranularity", sourceGranularityMinutes)
            .addValue("moduleCode", moduleCode, Types.VARCHAR)
            .addValue("eventTypeCode", eventTypeCode, Types.VARCHAR)
            .addValue("stageTypeCode", stageTypeCode, Types.VARCHAR);

        return jdbcTemplate.query(
            """
                select
                    r.metric_type_code as metric_type_code,
                    min(nullif(trim(r.unit), '')) as unit,
                    sum(r.sample_count) as sample_count,
                    sum(r.numeric_count) as numeric_count,
                    coalesce(sum(r.numeric_sum), 0)::numeric(20, 3) as numeric_sum,
                    coalesce(sum(r.p95_value * r.numeric_count), 0)::numeric(20, 3) as p95_weighted_sum,
                    min(r.min_value) as min_value,
                    max(r.max_value) as max_value
                from analytics.stage_metric_rollup_bucket r
                where r.granularity_minutes = :sourceGranularity
                  and r.bucket_start >= :fromTs
                  and r.bucket_start < :toTs
                  and (:moduleCode is null or r.module_code = :moduleCode)
                  and (:eventTypeCode is null or r.event_type_code = :eventTypeCode)
                  and (:stageTypeCode is null or r.stage_type_code = :stageTypeCode)
                group by r.metric_type_code
                order by sum(r.sample_count) desc, r.metric_type_code asc
            """,
            params,
            metricSummaryMapper()
        );
    }

    private List<MetricSummaryPoint> queryRawMetricSummaries(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String stageTypeCode
    ) {
        if (!to.isAfter(from)) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("fromTs", asTimestamp(from))
            .addValue("toTs", asTimestamp(to))
            .addValue("moduleCode", moduleCode, Types.VARCHAR)
            .addValue("eventTypeCode", eventTypeCode, Types.VARCHAR)
            .addValue("stageTypeCode", stageTypeCode, Types.VARCHAR);

        return jdbcTemplate.query(
            """
                select
                    sm.metric_type_code as metric_type_code,
                    min(nullif(trim(sm.unit), '')) as unit,
                    count(*) as sample_count,
                    count(sm.metric_value_num) as numeric_count,
                    coalesce(sum(sm.metric_value_num), 0)::numeric(20, 3) as numeric_sum,
                    (
                        coalesce(
                            percentile_cont(0.95) within group (order by sm.metric_value_num)
                                filter (where sm.metric_value_num is not null),
                            0
                        ) * count(sm.metric_value_num)
                    )::numeric(20, 3) as p95_weighted_sum,
                    min(sm.metric_value_num) as min_value,
                    max(sm.metric_value_num) as max_value
                from analytics.stage_metric sm
                join analytics.stage s on s.id = sm.stage_id
                join analytics.event e on e.id = s.event_id
                where sm.recorded_at >= :fromTs
                  and sm.recorded_at < :toTs
                  and (:moduleCode is null or e.module_code = :moduleCode)
                  and (:eventTypeCode is null or e.event_type_code = :eventTypeCode)
                  and (:stageTypeCode is null or s.stage_type_code = :stageTypeCode)
                  and sm.metric_type_code is not null
                group by sm.metric_type_code
                order by count(*) desc, sm.metric_type_code asc
            """,
            params,
            metricSummaryMapper()
        );
    }

    private List<SeriesPoint> queryRollupSeriesPoints(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String stageTypeCode,
        String metricTypeCode,
        int sourceGranularityMinutes,
        int targetBucketMinutes
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("fromTs", asTimestamp(from))
            .addValue("toTs", asTimestamp(to))
            .addValue("sourceGranularity", sourceGranularityMinutes)
            .addValue("targetBucketMinutes", targetBucketMinutes)
            .addValue("originTs", asTimestamp(DATE_BIN_ORIGIN))
            .addValue("moduleCode", moduleCode, Types.VARCHAR)
            .addValue("eventTypeCode", eventTypeCode, Types.VARCHAR)
            .addValue("stageTypeCode", stageTypeCode, Types.VARCHAR)
            .addValue("metricTypeCode", metricTypeCode, Types.VARCHAR);

        return jdbcTemplate.query(
            """
                select
                    date_bin(
                        (:targetBucketMinutes || ' minutes')::interval,
                        r.bucket_start,
                        :originTs
                    ) as bucket_start,
                    sum(r.numeric_count) as numeric_count,
                    coalesce(sum(r.numeric_sum), 0)::numeric(20, 3) as numeric_sum,
                    coalesce(sum(r.p95_value * r.numeric_count), 0)::numeric(20, 3) as p95_weighted_sum
                from analytics.stage_metric_rollup_bucket r
                where r.granularity_minutes = :sourceGranularity
                  and r.bucket_start >= :fromTs
                  and r.bucket_start < :toTs
                  and r.numeric_count > 0
                  and (:moduleCode is null or r.module_code = :moduleCode)
                  and (:eventTypeCode is null or r.event_type_code = :eventTypeCode)
                  and (:stageTypeCode is null or r.stage_type_code = :stageTypeCode)
                  and (:metricTypeCode is null or r.metric_type_code = :metricTypeCode)
                group by 1
                order by 1 asc
            """,
            params,
            seriesPointMapper()
        );
    }

    private List<SeriesPoint> queryRawSeriesPoints(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String stageTypeCode,
        String metricTypeCode,
        int targetBucketMinutes
    ) {
        if (!to.isAfter(from)) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("fromTs", asTimestamp(from))
            .addValue("toTs", asTimestamp(to))
            .addValue("targetBucketMinutes", targetBucketMinutes)
            .addValue("originTs", asTimestamp(DATE_BIN_ORIGIN))
            .addValue("moduleCode", moduleCode, Types.VARCHAR)
            .addValue("eventTypeCode", eventTypeCode, Types.VARCHAR)
            .addValue("stageTypeCode", stageTypeCode, Types.VARCHAR)
            .addValue("metricTypeCode", metricTypeCode, Types.VARCHAR);

        return jdbcTemplate.query(
            """
                select
                    date_bin(
                        (:targetBucketMinutes || ' minutes')::interval,
                        sm.recorded_at,
                        :originTs
                    ) as bucket_start,
                    count(sm.metric_value_num) as numeric_count,
                    coalesce(sum(sm.metric_value_num), 0)::numeric(20, 3) as numeric_sum,
                    (
                        coalesce(
                            percentile_cont(0.95) within group (order by sm.metric_value_num)
                                filter (where sm.metric_value_num is not null),
                            0
                        ) * count(sm.metric_value_num)
                    )::numeric(20, 3) as p95_weighted_sum
                from analytics.stage_metric sm
                join analytics.stage s on s.id = sm.stage_id
                join analytics.event e on e.id = s.event_id
                where sm.recorded_at >= :fromTs
                  and sm.recorded_at < :toTs
                  and sm.metric_value_num is not null
                  and (:moduleCode is null or e.module_code = :moduleCode)
                  and (:eventTypeCode is null or e.event_type_code = :eventTypeCode)
                  and (:stageTypeCode is null or s.stage_type_code = :stageTypeCode)
                  and (:metricTypeCode is null or sm.metric_type_code = :metricTypeCode)
                group by 1
                order by 1 asc
            """,
            params,
            seriesPointMapper()
        );
    }

    private List<MetricSummaryPoint> mergeMetricSummaries(Collection<MetricSummaryPoint> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }

        Map<String, MutableMetricSummary> grouped = new LinkedHashMap<>();
        for (MetricSummaryPoint point : points) {
            if (point == null || point.metricTypeCode() == null || point.metricTypeCode().isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(point.metricTypeCode(), key -> new MutableMetricSummary()).accept(point);
        }
        return grouped.entrySet().stream()
            .map(entry -> entry.getValue().toPoint(entry.getKey()))
            .sorted((left, right) -> {
                int byCount = Long.compare(right.sampleCount(), left.sampleCount());
                if (byCount != 0) {
                    return byCount;
                }
                return left.metricTypeCode().compareTo(right.metricTypeCode());
            })
            .toList();
    }

    private List<SeriesPoint> mergeSeriesPoints(Collection<SeriesPoint> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        Map<Instant, MutableSeriesPoint> grouped = new LinkedHashMap<>();
        for (SeriesPoint point : points) {
            if (point == null || point.bucketStart() == null) {
                continue;
            }
            grouped.computeIfAbsent(point.bucketStart(), key -> new MutableSeriesPoint()).accept(point);
        }
        return grouped.entrySet().stream()
            .map(entry -> entry.getValue().toPoint(entry.getKey()))
            .sorted((left, right) -> left.bucketStart().compareTo(right.bucketStart()))
            .toList();
    }

    private List<TimeSeriesPointDto> seriesFromPoints(
        Instant from,
        Instant to,
        int bucketMinutes,
        Collection<SeriesPoint> points
    ) {
        long stepSeconds = bucketMinutes * 60L;
        Instant start = floorToBucket(from, bucketMinutes);
        Instant end = floorToBucket(to.minusSeconds(1L), bucketMinutes).plusSeconds(stepSeconds);

        Map<Instant, SeriesPoint> map = new LinkedHashMap<>();
        if (points != null) {
            for (SeriesPoint point : points) {
                map.put(point.bucketStart(), point);
            }
        }

        List<TimeSeriesPointDto> result = new ArrayList<>();
        for (Instant bucket = start; bucket.isBefore(end); bucket = bucket.plusSeconds(stepSeconds)) {
            SeriesPoint point = map.get(bucket);
            long count = point == null ? 0L : point.numericCount();
            BigDecimal avg = point == null
                ? BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)
                : divide(point.numericSum(), point.numericCount(), 3);
            BigDecimal p95 = point == null
                ? BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)
                : divide(point.p95WeightedSum(), point.numericCount(), 3);
            result.add(new TimeSeriesPointDto(
                AnalyticsSeriesTime.displayTimeForBucket(bucket, to, stepSeconds),
                count,
                avg,
                p95,
                BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
            ));
        }
        return result;
    }

    private int chooseSourceGranularityMinutes(Instant from, Instant to, int targetBucketMinutes) {
        long rangeMinutes = Math.max(1L, (to.getEpochSecond() - from.getEpochSecond()) / 60L);
        int preferred;
        if (rangeMinutes <= 12L * 60L) {
            preferred = 1;
        } else if (rangeMinutes <= 14L * 24L * 60L) {
            preferred = 5;
        } else if (rangeMinutes <= 120L * 24L * 60L) {
            preferred = 60;
        } else {
            preferred = 1440;
        }

        int boundedPreferred = Math.min(preferred, Math.max(1, targetBucketMinutes));
        int best = 1;
        for (Integer candidate : SUPPORTED_GRANULARITIES) {
            if (candidate <= boundedPreferred) {
                best = candidate;
            }
        }
        return best;
    }

    private Instant resolveBootstrapStart(int granularityMinutes, Instant toExclusive, int bootstrapLookbackDays) {
        Timestamp earliestTs = jdbcTemplate.queryForObject(
            "select min(recorded_at) from analytics.stage_metric",
            Map.of(),
            Timestamp.class
        );
        Instant earliest = earliestTs == null ? null : earliestTs.toInstant();
        if (earliest == null) {
            return null;
        }
        Instant lookbackLowerBound = toExclusive.minusSeconds(bootstrapLookbackDays * 24L * 60L * 60L);
        Instant bounded = earliest.isBefore(lookbackLowerBound) ? lookbackLowerBound : earliest;
        return floorToBucket(bounded, granularityMinutes);
    }

    private Instant clampCutoff(Instant from, Instant to, Instant watermark) {
        if (watermark == null) {
            return from;
        }
        if (watermark.isBefore(from)) {
            return from;
        }
        if (watermark.isAfter(to)) {
            return to;
        }
        return watermark;
    }

    private Instant readWatermark(String scopeCode, int granularityMinutes) {
        List<Timestamp> rows = jdbcTemplate.query(
            """
                select watermark_at
                from analytics.time_rollup_watermark
                where scope_code = :scopeCode
                  and granularity_minutes = :granularityMinutes
            """,
            new MapSqlParameterSource()
                .addValue("scopeCode", scopeCode)
                .addValue("granularityMinutes", granularityMinutes),
            (rs, rowNum) -> rs.getTimestamp("watermark_at")
        );
        if (rows.isEmpty()) {
            return null;
        }
        Timestamp value = rows.getFirst();
        return value == null ? null : value.toInstant();
    }

    private void upsertWatermark(String scopeCode, int granularityMinutes, Instant watermarkAt) {
        jdbcTemplate.update(
            """
                insert into analytics.time_rollup_watermark (
                    scope_code,
                    granularity_minutes,
                    watermark_at,
                    updated_at
                )
                values (
                    :scopeCode,
                    :granularityMinutes,
                    :watermarkAt,
                    now()
                )
                on conflict (scope_code, granularity_minutes)
                do update set
                    watermark_at = excluded.watermark_at,
                    updated_at = now()
            """,
            new MapSqlParameterSource()
                .addValue("scopeCode", scopeCode)
                .addValue("granularityMinutes", granularityMinutes)
                .addValue("watermarkAt", asTimestamp(watermarkAt))
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

    private Instant floorToBucket(Instant value, int bucketMinutes) {
        if (value == null) {
            return null;
        }
        long stepSeconds = Math.max(1, bucketMinutes) * 60L;
        long epochSecond = value.getEpochSecond();
        long normalized = Math.floorDiv(epochSecond, stepSeconds) * stepSeconds;
        return Instant.ofEpochSecond(normalized);
    }

    private Timestamp asTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private RowMapper<MetricSummaryPoint> metricSummaryMapper() {
        return new RowMapper<>() {
            @Override
            public MetricSummaryPoint mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new MetricSummaryPoint(
                    rs.getString("metric_type_code"),
                    rs.getString("unit"),
                    rs.getLong("sample_count"),
                    rs.getLong("numeric_count"),
                    scaled(rs.getBigDecimal("numeric_sum")),
                    scaled(rs.getBigDecimal("p95_weighted_sum")),
                    nullableScaled(rs.getBigDecimal("min_value")),
                    nullableScaled(rs.getBigDecimal("max_value"))
                );
            }
        };
    }

    private RowMapper<SeriesPoint> seriesPointMapper() {
        return new RowMapper<>() {
            @Override
            public SeriesPoint mapRow(ResultSet rs, int rowNum) throws SQLException {
                Timestamp ts = rs.getTimestamp("bucket_start");
                return new SeriesPoint(
                    ts == null ? null : ts.toInstant(),
                    rs.getLong("numeric_count"),
                    scaled(rs.getBigDecimal("numeric_sum")),
                    scaled(rs.getBigDecimal("p95_weighted_sum"))
                );
            }
        };
    }

    private static BigDecimal scaled(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullableScaled(BigDecimal value) {
        return value == null ? null : value.setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal divide(BigDecimal numerator, long denominator, int scale) {
        if (numerator == null || denominator <= 0L) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return numerator.divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP);
    }

    public record MetricSummaryPoint(
        String metricTypeCode,
        String unit,
        long sampleCount,
        long numericCount,
        BigDecimal numericSum,
        BigDecimal p95WeightedSum,
        BigDecimal minValue,
        BigDecimal maxValue
    ) {
        public boolean numeric() {
            return numericCount > 0L;
        }

        public BigDecimal avgValue() {
            return divide(numericSum, numericCount, 3);
        }

        public BigDecimal p95Value() {
            return divide(p95WeightedSum, numericCount, 3);
        }
    }

    private record SeriesPoint(
        Instant bucketStart,
        long numericCount,
        BigDecimal numericSum,
        BigDecimal p95WeightedSum
    ) {
    }

    private static final class MutableMetricSummary {
        private String unit;
        private long sampleCount;
        private long numericCount;
        private BigDecimal numericSum = BigDecimal.ZERO;
        private BigDecimal p95WeightedSum = BigDecimal.ZERO;
        private BigDecimal minValue;
        private BigDecimal maxValue;

        private void accept(MetricSummaryPoint point) {
            if (point == null) {
                return;
            }
            if ((unit == null || unit.isBlank()) && point.unit() != null && !point.unit().isBlank()) {
                unit = point.unit();
            }
            sampleCount += Math.max(0L, point.sampleCount());
            numericCount += Math.max(0L, point.numericCount());
            numericSum = numericSum.add(Objects.requireNonNullElse(point.numericSum(), BigDecimal.ZERO));
            p95WeightedSum = p95WeightedSum.add(Objects.requireNonNullElse(point.p95WeightedSum(), BigDecimal.ZERO));
            if (point.minValue() != null) {
                minValue = minValue == null ? point.minValue() : minValue.min(point.minValue());
            }
            if (point.maxValue() != null) {
                maxValue = maxValue == null ? point.maxValue() : maxValue.max(point.maxValue());
            }
        }

        private MetricSummaryPoint toPoint(String metricTypeCode) {
            return new MetricSummaryPoint(
                metricTypeCode,
                unit,
                sampleCount,
                numericCount,
                scaled(numericSum),
                scaled(p95WeightedSum),
                nullableScaled(minValue),
                nullableScaled(maxValue)
            );
        }
    }

    private static final class MutableSeriesPoint {
        private long numericCount;
        private BigDecimal numericSum = BigDecimal.ZERO;
        private BigDecimal p95WeightedSum = BigDecimal.ZERO;

        private void accept(SeriesPoint point) {
            if (point == null) {
                return;
            }
            numericCount += Math.max(0L, point.numericCount());
            numericSum = numericSum.add(Objects.requireNonNullElse(point.numericSum(), BigDecimal.ZERO));
            p95WeightedSum = p95WeightedSum.add(Objects.requireNonNullElse(point.p95WeightedSum(), BigDecimal.ZERO));
        }

        private SeriesPoint toPoint(Instant bucketStart) {
            return new SeriesPoint(bucketStart, numericCount, scaled(numericSum), scaled(p95WeightedSum));
        }
    }
}
