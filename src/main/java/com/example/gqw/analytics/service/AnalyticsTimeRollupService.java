package com.example.gqw.analytics.service;

import org.springframework.beans.factory.annotation.Qualifier;

import com.example.gqw.analytics.web.dto.AnalyticsApiDto.TimeSeriesPointDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.sql.Types;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsTimeRollupService {

    private static final String WATERMARK_SCOPE_EVENT = "EVENT";
    private static final String WATERMARK_SCOPE_STAGE = "STAGE";
    private static final String DEFAULT_MODULE_CODE = "DEFAULT";
    private static final Instant DATE_BIN_ORIGIN = Instant.parse("2001-01-01T00:00:00Z");
    private static final List<Integer> SUPPORTED_GRANULARITIES = List.of(1, 5, 60, 1440);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AnalyticsRuntimeSettingsService runtimeSettingsService;
    private final AnalyticsScheduledJobsPolicy scheduledJobsPolicy;
    private final Clock clock;
    private final boolean defaultEnabled;
    private final int defaultOverlapMinutes;
    private final int defaultBootstrapLookbackDays;
    private final int defaultRefreshIntervalMinutes;
    private final Object refreshLock = new Object();
    private volatile Instant lastScheduledRefreshAt;

    public AnalyticsTimeRollupService(
        @Qualifier("analyticsNamedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
        AnalyticsRuntimeSettingsService runtimeSettingsService,
        AnalyticsScheduledJobsPolicy scheduledJobsPolicy,
        @Value("${app.analytics.time-rollup.enabled:true}") boolean enabled,
        @Value("${app.analytics.time-rollup.refresh-interval-minutes:5}") int refreshIntervalMinutes,
        @Value("${app.analytics.time-rollup.overlap-minutes:10}") int overlapMinutes,
        @Value("${app.analytics.time-rollup.bootstrap-lookback-days:370}") int bootstrapLookbackDays
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.runtimeSettingsService = runtimeSettingsService;
        this.scheduledJobsPolicy = scheduledJobsPolicy;
        this.clock = Clock.systemUTC();
        this.defaultEnabled = enabled;
        this.defaultRefreshIntervalMinutes = Math.max(1, refreshIntervalMinutes);
        this.defaultOverlapMinutes = Math.max(1, overlapMinutes);
        this.defaultBootstrapLookbackDays = Math.max(1, bootstrapLookbackDays);
        this.lastScheduledRefreshAt = Instant.now(this.clock);
    }

    @Transactional(transactionManager = "analyticsTransactionManager", readOnly = true)
    public boolean isEnabled() {
        if (!runtimeSettingsService.getBoolean(AnalyticsRuntimeSettingsService.KEY_TIME_ROLLUP_ENABLED, defaultEnabled)) {
            return false;
        }
        return tableExists("analytics.event_rollup_bucket")
            && tableExists("analytics.stage_rollup_bucket")
            && tableExists("analytics.time_rollup_watermark");
    }

    @Transactional(transactionManager = "analyticsTransactionManager", readOnly = true)
    public Instant findFirstEventBucketStart() {
        if (!isEnabled()) {
            return null;
        }
        Timestamp earliestTs = jdbcTemplate.queryForObject(
            "select min(bucket_start) from analytics.event_rollup_bucket",
            Map.of(),
            Timestamp.class
        );
        return earliestTs == null ? null : earliestTs.toInstant();
    }

    public int chooseSourceGranularityMinutes(Instant from, Instant to, int targetBucketMinutes) {
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

    @Transactional(transactionManager = "analyticsTransactionManager", readOnly = true)
    public List<AggregatePoint> loadEventAggregatePoints(
        Instant from,
        Instant to,
        String moduleCode,
        Set<String> eventTypeCodes,
        int targetBucketMinutes
    ) {
        if (!isEnabled()) {
            return queryRawEventPoints(from, to, moduleCode, eventTypeCodes, targetBucketMinutes);
        }

        int sourceGranularity = chooseSourceGranularityMinutes(from, to, targetBucketMinutes);
        Instant watermark = readWatermark(WATERMARK_SCOPE_EVENT, sourceGranularity);
        Instant cutoff = clampCutoff(from, to, watermark);
        boolean tailMergeEnabled = runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_TAIL_MERGE_ENABLED,
            true
        );
        if (!tailMergeEnabled && !cutoff.isAfter(from)) {
            return queryRawEventPoints(from, to, moduleCode, eventTypeCodes, targetBucketMinutes);
        }

        List<AggregatePoint> result = new ArrayList<>();
        if (cutoff.isAfter(from)) {
            result.addAll(queryRollupEventPoints(from, cutoff, moduleCode, eventTypeCodes, sourceGranularity, targetBucketMinutes));
        }
        if (tailMergeEnabled && to.isAfter(cutoff)) {
            result.addAll(queryRawEventPoints(cutoff, to, moduleCode, eventTypeCodes, targetBucketMinutes));
        }
        List<AggregatePoint> merged = mergeDuplicatePoints(result);
        if (merged.isEmpty()) {
            return queryRawEventPoints(from, to, moduleCode, eventTypeCodes, targetBucketMinutes);
        }
        return merged;
    }

    @Transactional(transactionManager = "analyticsTransactionManager", readOnly = true)
    public List<AggregatePoint> loadEventAggregatePoints(
        Instant from,
        Instant to,
        String moduleCode,
        Set<String> eventTypeCodes,
        int targetBucketMinutes,
        Boolean isError
    ) {
        if (isError == null) {
            return loadEventAggregatePoints(from, to, moduleCode, eventTypeCodes, targetBucketMinutes);
        }
        return queryRawEventPoints(from, to, moduleCode, eventTypeCodes, targetBucketMinutes, isError);
    }

    @Transactional(transactionManager = "analyticsTransactionManager", readOnly = true)
    public List<AggregatePoint> loadStageAggregatePoints(
        Instant from,
        Instant to,
        String moduleCode,
        Set<String> eventTypeCodes,
        Set<String> stageTypeCodes,
        int targetBucketMinutes
    ) {
        if (!isEnabled()) {
            return queryRawStagePoints(from, to, moduleCode, eventTypeCodes, stageTypeCodes, targetBucketMinutes);
        }

        int sourceGranularity = chooseSourceGranularityMinutes(from, to, targetBucketMinutes);
        Instant watermark = readWatermark(WATERMARK_SCOPE_STAGE, sourceGranularity);
        Instant cutoff = clampCutoff(from, to, watermark);
        boolean tailMergeEnabled = runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_TAIL_MERGE_ENABLED,
            true
        );
        if (!tailMergeEnabled && !cutoff.isAfter(from)) {
            return queryRawStagePoints(from, to, moduleCode, eventTypeCodes, stageTypeCodes, targetBucketMinutes);
        }

        List<AggregatePoint> result = new ArrayList<>();
        if (cutoff.isAfter(from)) {
            result.addAll(queryRollupStagePoints(
                from,
                cutoff,
                moduleCode,
                eventTypeCodes,
                stageTypeCodes,
                sourceGranularity,
                targetBucketMinutes
            ));
        }
        if (tailMergeEnabled && to.isAfter(cutoff)) {
            result.addAll(queryRawStagePoints(cutoff, to, moduleCode, eventTypeCodes, stageTypeCodes, targetBucketMinutes));
        }
        List<AggregatePoint> merged = mergeDuplicatePoints(result);
        if (merged.isEmpty()) {
            return queryRawStagePoints(from, to, moduleCode, eventTypeCodes, stageTypeCodes, targetBucketMinutes);
        }
        return merged;
    }

    @Transactional(transactionManager = "analyticsTransactionManager", readOnly = true)
    public Map<String, List<AggregatePoint>> loadStageAggregatePointsByEventAndStage(
        Instant from,
        Instant to,
        String moduleCode,
        Set<String> eventTypeCodes,
        Set<String> stageTypeCodes,
        int targetBucketMinutes
    ) {
        if (!isEnabled()) {
            return Map.of();
        }
        int sourceGranularity = chooseSourceGranularityMinutes(from, to, targetBucketMinutes);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("fromTs", asTimestamp(from))
            .addValue("toTs", asTimestamp(to))
            .addValue("sourceGranularity", sourceGranularity)
            .addValue("moduleCode", moduleCode, Types.VARCHAR);

        StringBuilder extra = new StringBuilder();
        if (eventTypeCodes != null && !eventTypeCodes.isEmpty()) {
            params.addValue("eventTypeCodes", eventTypeCodes);
            extra.append(" and r.event_type_code in (:eventTypeCodes) ");
        }
        if (stageTypeCodes != null && !stageTypeCodes.isEmpty()) {
            params.addValue("stageTypeCodes", stageTypeCodes);
            extra.append(" and r.stage_type_code in (:stageTypeCodes) ");
        }

        Map<String, List<AggregatePoint>> grouped = new LinkedHashMap<>();
        jdbcTemplate.query(
            """
                select
                    min(r.bucket_start) as bucket_start,
                    r.event_type_code,
                    r.stage_type_code as group_code,
                    sum(r.sample_count) as sample_count,
                    sum(r.error_count) as error_count,
                    sum(r.duration_sum) as duration_sum,
                    coalesce(sum(r.p95_ms * r.sample_count) / nullif(sum(r.sample_count), 0), 0)::numeric(12, 3) as p95_ms,
                    coalesce(sum(r.p99_ms * r.sample_count) / nullif(sum(r.sample_count), 0), 0)::numeric(12, 3) as p99_ms,
                    coalesce(max(r.max_ms), 0)::numeric(12, 3) as max_ms,
                    sum(r.self_duration_sum) as self_duration_sum,
                    coalesce(sum(r.self_p95_ms * r.sample_count) / nullif(sum(r.sample_count), 0), 0)::numeric(12, 3) as self_p95_ms,
                    coalesce(sum(r.self_p99_ms * r.sample_count) / nullif(sum(r.sample_count), 0), 0)::numeric(12, 3) as self_p99_ms,
                    coalesce(max(r.self_max_ms), 0)::numeric(12, 3) as self_max_ms
                from analytics.stage_rollup_bucket r
                where r.granularity_minutes = :sourceGranularity
                  and r.bucket_start >= :fromTs
                  and r.bucket_start < :toTs
                  and (:moduleCode is null or r.module_code = :moduleCode)
            """
                + extra
                + """
                group by r.event_type_code, r.stage_type_code
                order by r.event_type_code asc, r.stage_type_code asc
            """,
            params,
            rs -> {
                String eventTypeCode = rs.getString("event_type_code");
                AggregatePoint point = aggregatePointMapper().mapRow(rs, 0);
                grouped.computeIfAbsent(eventTypeCode, ignored -> new ArrayList<>()).add(point);
            }
        );
        return grouped;
    }

    @Transactional(transactionManager = "analyticsTransactionManager", readOnly = true)
    public List<AggregatePoint> loadStageAggregatePointsByEvent(
        Instant from,
        Instant to,
        String moduleCode,
        Set<String> eventTypeCodes,
        Set<String> stageTypeCodes,
        int targetBucketMinutes
    ) {
        if (!isEnabled()) {
            return queryRawStageEventPoints(from, to, moduleCode, eventTypeCodes, stageTypeCodes, targetBucketMinutes);
        }

        int sourceGranularity = chooseSourceGranularityMinutes(from, to, targetBucketMinutes);
        Instant watermark = readWatermark(WATERMARK_SCOPE_STAGE, sourceGranularity);
        Instant cutoff = clampCutoff(from, to, watermark);
        boolean tailMergeEnabled = runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_TAIL_MERGE_ENABLED,
            true
        );
        if (!tailMergeEnabled && !cutoff.isAfter(from)) {
            return queryRawStageEventPoints(from, to, moduleCode, eventTypeCodes, stageTypeCodes, targetBucketMinutes);
        }

        List<AggregatePoint> result = new ArrayList<>();
        if (cutoff.isAfter(from)) {
            result.addAll(queryRollupStageEventPoints(
                from,
                cutoff,
                moduleCode,
                eventTypeCodes,
                stageTypeCodes,
                sourceGranularity,
                targetBucketMinutes
            ));
        }
        if (tailMergeEnabled && to.isAfter(cutoff)) {
            result.addAll(queryRawStageEventPoints(cutoff, to, moduleCode, eventTypeCodes, stageTypeCodes, targetBucketMinutes));
        }
        List<AggregatePoint> merged = mergeDuplicatePoints(result);
        if (merged.isEmpty()) {
            return queryRawStageEventPoints(from, to, moduleCode, eventTypeCodes, stageTypeCodes, targetBucketMinutes);
        }
        return merged;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional(transactionManager = "analyticsTransactionManager")
    public void scheduledRefresh() {
        if (!scheduledJobsPolicy.isEnabled()) {
            return;
        }
        int intervalMinutes = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_TIME_ROLLUP_REFRESH_INTERVAL_MINUTES,
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

    private void refreshAllGranularities() {
        if (!isEnabled()) {
            return;
        }
        int overlapMinutes = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_TIME_ROLLUP_OVERLAP_MINUTES,
            defaultOverlapMinutes,
            1,
            180
        );
        int bootstrapLookbackDays = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_TIME_ROLLUP_BOOTSTRAP_LOOKBACK_DAYS,
            defaultBootstrapLookbackDays,
            1,
            3650
        );
        for (Integer granularity : SUPPORTED_GRANULARITIES) {
            refreshEventRollup(granularity, overlapMinutes, bootstrapLookbackDays);
            refreshStageRollup(granularity, overlapMinutes, bootstrapLookbackDays);
        }
    }

    @Transactional(transactionManager = "analyticsTransactionManager")
    public void initializeIfNeeded() {
        synchronized (refreshLock) {
            refreshAllGranularities();
            lastScheduledRefreshAt = Instant.now(clock);
        }
    }

    private void refreshEventRollup(int granularityMinutes, int overlapMinutes, int bootstrapLookbackDays) {
        Instant now = Instant.now(clock);
        Instant toExclusive = floorToBucket(now, granularityMinutes);
        if (toExclusive == null) {
            return;
        }

        Instant watermark = readWatermark(WATERMARK_SCOPE_EVENT, granularityMinutes);
        Instant fromInclusive = watermark == null
            ? resolveEventBootstrapStart(granularityMinutes, toExclusive, bootstrapLookbackDays)
            : floorToBucket(watermark.minusSeconds(overlapMinutes * 60L), granularityMinutes);
        if (fromInclusive == null || !toExclusive.isAfter(fromInclusive)) {
            return;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("fromTs", asTimestamp(fromInclusive))
            .addValue("toTs", asTimestamp(toExclusive))
            .addValue("granularityMinutes", granularityMinutes)
            .addValue("defaultModuleCode", DEFAULT_MODULE_CODE);

        jdbcTemplate.update(
            """
                delete from analytics.event_rollup_bucket
                where granularity_minutes = :granularityMinutes
                  and bucket_start >= :fromTs
                  and bucket_start < :toTs
            """,
            params
        );

        jdbcTemplate.update(
            """
                insert into analytics.event_rollup_bucket (
                    bucket_start,
                    granularity_minutes,
                    module_code,
                    event_type_code,
                    sample_count,
                    error_count,
                    duration_sum,
                    avg_ms,
                    p95_ms,
                    p99_ms,
                    max_ms
                )
                select
                    date_bin(
                        (:granularityMinutes || ' minutes')::interval,
                        e.started_at,
                        :originTs
                    ) as bucket_start,
                    :granularityMinutes as granularity_minutes,
                    coalesce(e.module_code, :defaultModuleCode) as module_code,
                    e.event_type_code,
                    count(*) as sample_count,
                    sum(case when e.is_error then 1 else 0 end) as error_count,
                    sum(case when e.duration_ms is null or e.duration_ms < 0 then 0 else e.duration_ms end) as duration_sum,
                    coalesce(avg(case when e.duration_ms is null or e.duration_ms < 0 then 0 else e.duration_ms end)::numeric, 0)::numeric(12, 3) as avg_ms,
                    coalesce(percentile_cont(0.95) within group (order by (case when e.duration_ms is null or e.duration_ms < 0 then 0 else e.duration_ms end)::numeric), 0)::numeric(12, 3) as p95_ms,
                    coalesce(percentile_cont(0.99) within group (order by (case when e.duration_ms is null or e.duration_ms < 0 then 0 else e.duration_ms end)::numeric), 0)::numeric(12, 3) as p99_ms,
                    coalesce(max(case when e.duration_ms is null or e.duration_ms < 0 then 0 else e.duration_ms end)::numeric, 0)::numeric(12, 3) as max_ms
                from analytics.event e
                where e.started_at >= :fromTs
                  and e.started_at < :toTs
                  and e.event_type_code is not null
                group by 1, 3, 4
                on conflict (bucket_start, granularity_minutes, module_code, event_type_code)
                do update set
                    sample_count = excluded.sample_count,
                    error_count = excluded.error_count,
                    duration_sum = excluded.duration_sum,
                    avg_ms = excluded.avg_ms,
                    p95_ms = excluded.p95_ms,
                    p99_ms = excluded.p99_ms,
                    max_ms = excluded.max_ms
            """,
            params.addValue("originTs", asTimestamp(DATE_BIN_ORIGIN))
        );

        upsertWatermark(WATERMARK_SCOPE_EVENT, granularityMinutes, toExclusive);
    }

    private void refreshStageRollup(int granularityMinutes, int overlapMinutes, int bootstrapLookbackDays) {
        Instant now = Instant.now(clock);
        Instant toExclusive = floorToBucket(now, granularityMinutes);
        if (toExclusive == null) {
            return;
        }

        Instant watermark = readWatermark(WATERMARK_SCOPE_STAGE, granularityMinutes);
        Instant fromInclusive = watermark == null
            ? resolveStageBootstrapStart(granularityMinutes, toExclusive, bootstrapLookbackDays)
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
                delete from analytics.stage_rollup_bucket
                where granularity_minutes = :granularityMinutes
                  and bucket_start >= :fromTs
                  and bucket_start < :toTs
            """,
            params
        );

        jdbcTemplate.update(
            """
                with stage_base as (
                    select
                        s.id,
                        s.event_id,
                        s.stage_type_code,
                        s.started_at,
                        greatest(
                            s.started_at,
                            coalesce(
                                s.ended_at,
                                s.started_at + greatest(coalesce(s.duration_ms, 0), 0) * interval '1 millisecond'
                            )
                        ) as ended_at,
                        greatest(coalesce(s.duration_ms, 0), 0)::numeric as duration_ms,
                        s.is_error,
                        coalesce(e.module_code, :defaultModuleCode) as module_code,
                        e.event_type_code
                    from analytics.stage s
                    join analytics.event e on e.id = s.event_id
                    where s.started_at >= :fromTs
                      and s.started_at < :toTs
                      and e.event_type_code is not null
                      and s.stage_type_code is not null
                ),
                child_ranges as (
                    select
                        parent.id as parent_id,
                        range_agg(tstzrange(child.started_at, child.ended_at, '[)')) as child_ranges
                    from stage_base parent
                    join stage_base child
                      on child.event_id = parent.event_id
                     and child.id <> parent.id
                     and child.ended_at > child.started_at
                     and child.started_at >= parent.started_at
                     and child.ended_at <= parent.ended_at
                     and (
                         child.started_at > parent.started_at
                         or child.ended_at < parent.ended_at
                     )
                    group by parent.id
                ),
                child_duration as (
                    select
                        cr.parent_id,
                        sum(extract(epoch from (upper(child_range) - lower(child_range))) * 1000)::numeric as nested_duration_ms
                    from child_ranges cr
                    cross join lateral unnest(cr.child_ranges) as nested(child_range)
                    group by cr.parent_id
                ),
                stage_values as (
                    select
                        parent.*,
                        greatest(
                            0,
                            parent.duration_ms - coalesce(child_duration.nested_duration_ms, 0)
                        )::numeric as self_duration_ms
                    from stage_base parent
                    left join child_duration on child_duration.parent_id = parent.id
                )
                insert into analytics.stage_rollup_bucket (
                    bucket_start,
                    granularity_minutes,
                    module_code,
                    event_type_code,
                    stage_type_code,
                    sample_count,
                    error_count,
                    duration_sum,
                    avg_ms,
                    p95_ms,
                    p99_ms,
                    max_ms,
                    self_duration_sum,
                    self_avg_ms,
                    self_p95_ms,
                    self_p99_ms,
                    self_max_ms
                )
                select
                    date_bin(
                        (:granularityMinutes || ' minutes')::interval,
                        s.started_at,
                        :originTs
                    ) as bucket_start,
                    :granularityMinutes as granularity_minutes,
                    s.module_code,
                    s.event_type_code,
                    s.stage_type_code,
                    count(*) as sample_count,
                    sum(case when s.is_error then 1 else 0 end) as error_count,
                    sum(s.duration_ms)::bigint as duration_sum,
                    coalesce(avg(s.duration_ms), 0)::numeric(12, 3) as avg_ms,
                    coalesce(percentile_cont(0.95) within group (order by s.duration_ms), 0)::numeric(12, 3) as p95_ms,
                    coalesce(percentile_cont(0.99) within group (order by s.duration_ms), 0)::numeric(12, 3) as p99_ms,
                    coalesce(max(s.duration_ms), 0)::numeric(12, 3) as max_ms,
                    sum(s.self_duration_ms)::bigint as self_duration_sum,
                    coalesce(avg(s.self_duration_ms), 0)::numeric(12, 3) as self_avg_ms,
                    coalesce(percentile_cont(0.95) within group (order by s.self_duration_ms), 0)::numeric(12, 3) as self_p95_ms,
                    coalesce(percentile_cont(0.99) within group (order by s.self_duration_ms), 0)::numeric(12, 3) as self_p99_ms,
                    coalesce(max(s.self_duration_ms), 0)::numeric(12, 3) as self_max_ms
                from stage_values s
                group by 1, 3, 4, 5
                on conflict (bucket_start, granularity_minutes, module_code, event_type_code, stage_type_code)
                do update set
                    sample_count = excluded.sample_count,
                    error_count = excluded.error_count,
                    duration_sum = excluded.duration_sum,
                    avg_ms = excluded.avg_ms,
                    p95_ms = excluded.p95_ms,
                    p99_ms = excluded.p99_ms,
                    max_ms = excluded.max_ms,
                    self_duration_sum = excluded.self_duration_sum,
                    self_avg_ms = excluded.self_avg_ms,
                    self_p95_ms = excluded.self_p95_ms,
                    self_p99_ms = excluded.self_p99_ms,
                    self_max_ms = excluded.self_max_ms
            """,
            params
        );

        upsertWatermark(WATERMARK_SCOPE_STAGE, granularityMinutes, toExclusive);
    }

    private List<AggregatePoint> queryRollupEventPoints(
        Instant from,
        Instant to,
        String moduleCode,
        Set<String> eventTypeCodes,
        int sourceGranularityMinutes,
        int targetBucketMinutes
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("fromTs", asTimestamp(from))
            .addValue("toTs", asTimestamp(to))
            .addValue("sourceGranularity", sourceGranularityMinutes)
            .addValue("targetBucketMinutes", targetBucketMinutes)
            .addValue("originTs", asTimestamp(DATE_BIN_ORIGIN))
            .addValue("moduleCode", moduleCode, Types.VARCHAR);

        String eventTypeClause = "";
        if (eventTypeCodes != null && !eventTypeCodes.isEmpty()) {
            params.addValue("eventTypeCodes", eventTypeCodes);
            eventTypeClause = " and r.event_type_code in (:eventTypeCodes) ";
        }

        return jdbcTemplate.query(
            """
                select
                    date_bin(
                        (:targetBucketMinutes || ' minutes')::interval,
                        r.bucket_start,
                        :originTs
                    ) as bucket_start,
                    r.event_type_code as group_code,
                    sum(r.sample_count) as sample_count,
                    sum(r.error_count) as error_count,
                    sum(r.duration_sum) as duration_sum,
                    coalesce(sum(r.p95_ms * r.sample_count) / nullif(sum(r.sample_count), 0), 0)::numeric(12, 3) as p95_ms,
                    coalesce(sum(r.p99_ms * r.sample_count) / nullif(sum(r.sample_count), 0), 0)::numeric(12, 3) as p99_ms,
                    coalesce(max(r.max_ms), 0)::numeric(12, 3) as max_ms
                from analytics.event_rollup_bucket r
                where r.granularity_minutes = :sourceGranularity
                  and r.bucket_start >= :fromTs
                  and r.bucket_start < :toTs
                  and (:moduleCode is null or r.module_code = :moduleCode)
            """
                + eventTypeClause
                + """
                group by 1, 2
                order by 1 asc, 2 asc
            """,
            params,
            aggregatePointMapper()
        );
    }

    private List<AggregatePoint> queryRawEventPoints(
        Instant from,
        Instant to,
        String moduleCode,
        Set<String> eventTypeCodes,
        int targetBucketMinutes
    ) {
        return queryRawEventPoints(from, to, moduleCode, eventTypeCodes, targetBucketMinutes, null);
    }

    private List<AggregatePoint> queryRawEventPoints(
        Instant from,
        Instant to,
        String moduleCode,
        Set<String> eventTypeCodes,
        int targetBucketMinutes,
        Boolean isError
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
            .addValue("isError", isError, Types.BOOLEAN);

        String eventTypeClause = "";
        if (eventTypeCodes != null && !eventTypeCodes.isEmpty()) {
            params.addValue("eventTypeCodes", eventTypeCodes);
            eventTypeClause = " and e.event_type_code in (:eventTypeCodes) ";
        }

        return jdbcTemplate.query(
            """
                select
                    date_bin(
                        (:targetBucketMinutes || ' minutes')::interval,
                        e.started_at,
                        :originTs
                    ) as bucket_start,
                    e.event_type_code as group_code,
                    count(*) as sample_count,
                    sum(case when e.is_error then 1 else 0 end) as error_count,
                    sum(case when e.duration_ms is null or e.duration_ms < 0 then 0 else e.duration_ms end) as duration_sum,
                    coalesce(percentile_cont(0.95) within group (order by (case when e.duration_ms is null or e.duration_ms < 0 then 0 else e.duration_ms end)::numeric), 0)::numeric(12, 3) as p95_ms,
                    coalesce(percentile_cont(0.99) within group (order by (case when e.duration_ms is null or e.duration_ms < 0 then 0 else e.duration_ms end)::numeric), 0)::numeric(12, 3) as p99_ms,
                    coalesce(max(case when e.duration_ms is null or e.duration_ms < 0 then 0 else e.duration_ms end)::numeric, 0)::numeric(12, 3) as max_ms
                from analytics.event e
                where e.started_at >= :fromTs
                  and e.started_at < :toTs
                  and (:moduleCode is null or e.module_code = :moduleCode)
                  and (:isError is null or e.is_error = :isError)
                  and e.event_type_code is not null
            """
                + eventTypeClause
                + """
                group by 1, 2
                order by 1 asc, 2 asc
            """,
            params,
            aggregatePointMapper()
        );
    }

    private List<AggregatePoint> queryRollupStagePoints(
        Instant from,
        Instant to,
        String moduleCode,
        Set<String> eventTypeCodes,
        Set<String> stageTypeCodes,
        int sourceGranularityMinutes,
        int targetBucketMinutes
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("fromTs", asTimestamp(from))
            .addValue("toTs", asTimestamp(to))
            .addValue("sourceGranularity", sourceGranularityMinutes)
            .addValue("targetBucketMinutes", targetBucketMinutes)
            .addValue("originTs", asTimestamp(DATE_BIN_ORIGIN))
            .addValue("moduleCode", moduleCode, Types.VARCHAR);

        StringBuilder extra = new StringBuilder();
        if (eventTypeCodes != null && !eventTypeCodes.isEmpty()) {
            params.addValue("eventTypeCodes", eventTypeCodes);
            extra.append(" and r.event_type_code in (:eventTypeCodes) ");
        }
        if (stageTypeCodes != null && !stageTypeCodes.isEmpty()) {
            params.addValue("stageTypeCodes", stageTypeCodes);
            extra.append(" and r.stage_type_code in (:stageTypeCodes) ");
        }

        return jdbcTemplate.query(
            """
                select
                    date_bin(
                        (:targetBucketMinutes || ' minutes')::interval,
                        r.bucket_start,
                        :originTs
                    ) as bucket_start,
                    r.stage_type_code as group_code,
                    sum(r.sample_count) as sample_count,
                    sum(r.error_count) as error_count,
                    sum(r.duration_sum) as duration_sum,
                    coalesce(sum(r.p95_ms * r.sample_count) / nullif(sum(r.sample_count), 0), 0)::numeric(12, 3) as p95_ms,
                    coalesce(sum(r.p99_ms * r.sample_count) / nullif(sum(r.sample_count), 0), 0)::numeric(12, 3) as p99_ms,
                    coalesce(max(r.max_ms), 0)::numeric(12, 3) as max_ms,
                    sum(r.self_duration_sum) as self_duration_sum,
                    coalesce(sum(r.self_p95_ms * r.sample_count) / nullif(sum(r.sample_count), 0), 0)::numeric(12, 3) as self_p95_ms,
                    coalesce(sum(r.self_p99_ms * r.sample_count) / nullif(sum(r.sample_count), 0), 0)::numeric(12, 3) as self_p99_ms,
                    coalesce(max(r.self_max_ms), 0)::numeric(12, 3) as self_max_ms
                from analytics.stage_rollup_bucket r
                where r.granularity_minutes = :sourceGranularity
                  and r.bucket_start >= :fromTs
                  and r.bucket_start < :toTs
                  and (:moduleCode is null or r.module_code = :moduleCode)
            """
                + extra
                + """
                group by 1, 2
                order by 1 asc, 2 asc
            """,
            params,
            aggregatePointMapper()
        );
    }

    private List<AggregatePoint> queryRawStagePoints(
        Instant from,
        Instant to,
        String moduleCode,
        Set<String> eventTypeCodes,
        Set<String> stageTypeCodes,
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
            .addValue("moduleCode", moduleCode, Types.VARCHAR);

        StringBuilder extra = new StringBuilder();
        if (eventTypeCodes != null && !eventTypeCodes.isEmpty()) {
            params.addValue("eventTypeCodes", eventTypeCodes);
            extra.append(" and e.event_type_code in (:eventTypeCodes) ");
        }
        if (stageTypeCodes != null && !stageTypeCodes.isEmpty()) {
            params.addValue("stageTypeCodes", stageTypeCodes);
            extra.append(" and s.stage_type_code in (:stageTypeCodes) ");
        }

        return jdbcTemplate.query(
            """
                with stage_base as (
                    select
                        s.id,
                        s.event_id,
                        s.stage_type_code,
                        s.started_at,
                        greatest(
                            s.started_at,
                            coalesce(
                                s.ended_at,
                                s.started_at + greatest(coalesce(s.duration_ms, 0), 0) * interval '1 millisecond'
                            )
                        ) as ended_at,
                        greatest(coalesce(s.duration_ms, 0), 0)::numeric as duration_ms,
                        s.is_error,
                        e.module_code,
                        e.event_type_code
                    from analytics.stage s
                    join analytics.event e on e.id = s.event_id
                    where s.started_at >= :fromTs
                      and s.started_at < :toTs
                      and (:moduleCode is null or e.module_code = :moduleCode)
                      and s.stage_type_code is not null
                      and e.event_type_code is not null
                """
                + extra
                + """
                ),
                stage_values as (
                    select
                        parent.*,
                        greatest(
                            0,
                            parent.duration_ms - coalesce((
                                select sum(
                                    extract(epoch from (upper(nested.child_range) - lower(nested.child_range))) * 1000
                                )
                                from unnest(coalesce((
                                    select range_agg(tstzrange(child.started_at, child.ended_at, '[)'))
                                    from stage_base child
                                    where child.event_id = parent.event_id
                                      and child.id <> parent.id
                                      and child.ended_at > child.started_at
                                      and child.started_at >= parent.started_at
                                      and child.ended_at <= parent.ended_at
                                      and (
                                          child.started_at > parent.started_at
                                          or child.ended_at < parent.ended_at
                                      )
                                ), '{}'::tstzmultirange)) as nested(child_range)
                            ), 0)
                        )::numeric as self_duration_ms
                    from stage_base parent
                )
                select
                    date_bin(
                        (:targetBucketMinutes || ' minutes')::interval,
                        s.started_at,
                        :originTs
                    ) as bucket_start,
                    s.stage_type_code as group_code,
                    count(*) as sample_count,
                    sum(case when s.is_error then 1 else 0 end) as error_count,
                    sum(s.duration_ms)::bigint as duration_sum,
                    coalesce(percentile_cont(0.95) within group (order by s.duration_ms), 0)::numeric(12, 3) as p95_ms,
                    coalesce(percentile_cont(0.99) within group (order by s.duration_ms), 0)::numeric(12, 3) as p99_ms,
                    coalesce(max(s.duration_ms), 0)::numeric(12, 3) as max_ms,
                    sum(s.self_duration_ms)::bigint as self_duration_sum,
                    coalesce(percentile_cont(0.95) within group (order by s.self_duration_ms), 0)::numeric(12, 3) as self_p95_ms,
                    coalesce(percentile_cont(0.99) within group (order by s.self_duration_ms), 0)::numeric(12, 3) as self_p99_ms,
                    coalesce(max(s.self_duration_ms), 0)::numeric(12, 3) as self_max_ms
                from stage_values s
                group by 1, 2
                order by 1 asc, 2 asc
            """,
            params,
            aggregatePointMapper()
        );
    }

    private List<AggregatePoint> queryRollupStageEventPoints(
        Instant from,
        Instant to,
        String moduleCode,
        Set<String> eventTypeCodes,
        Set<String> stageTypeCodes,
        int sourceGranularityMinutes,
        int targetBucketMinutes
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("fromTs", asTimestamp(from))
            .addValue("toTs", asTimestamp(to))
            .addValue("sourceGranularity", sourceGranularityMinutes)
            .addValue("targetBucketMinutes", targetBucketMinutes)
            .addValue("originTs", asTimestamp(DATE_BIN_ORIGIN))
            .addValue("moduleCode", moduleCode, Types.VARCHAR);

        StringBuilder extra = new StringBuilder();
        if (eventTypeCodes != null && !eventTypeCodes.isEmpty()) {
            params.addValue("eventTypeCodes", eventTypeCodes);
            extra.append(" and r.event_type_code in (:eventTypeCodes) ");
        }
        if (stageTypeCodes != null && !stageTypeCodes.isEmpty()) {
            params.addValue("stageTypeCodes", stageTypeCodes);
            extra.append(" and r.stage_type_code in (:stageTypeCodes) ");
        }

        return jdbcTemplate.query(
            """
                select
                    date_bin(
                        (:targetBucketMinutes || ' minutes')::interval,
                        r.bucket_start,
                        :originTs
                    ) as bucket_start,
                    r.event_type_code as group_code,
                    sum(r.sample_count) as sample_count,
                    sum(r.error_count) as error_count,
                    sum(r.duration_sum) as duration_sum,
                    coalesce(sum(r.p95_ms * r.sample_count) / nullif(sum(r.sample_count), 0), 0)::numeric(12, 3) as p95_ms,
                    coalesce(sum(r.p99_ms * r.sample_count) / nullif(sum(r.sample_count), 0), 0)::numeric(12, 3) as p99_ms,
                    coalesce(max(r.max_ms), 0)::numeric(12, 3) as max_ms
                from analytics.stage_rollup_bucket r
                where r.granularity_minutes = :sourceGranularity
                  and r.bucket_start >= :fromTs
                  and r.bucket_start < :toTs
                  and (:moduleCode is null or r.module_code = :moduleCode)
                  and r.event_type_code is not null
            """
                + extra
                + """
                group by 1, 2
                order by 1 asc, 2 asc
            """,
            params,
            aggregatePointMapper()
        );
    }

    private List<AggregatePoint> queryRawStageEventPoints(
        Instant from,
        Instant to,
        String moduleCode,
        Set<String> eventTypeCodes,
        Set<String> stageTypeCodes,
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
            .addValue("moduleCode", moduleCode, Types.VARCHAR);

        StringBuilder extra = new StringBuilder();
        if (eventTypeCodes != null && !eventTypeCodes.isEmpty()) {
            params.addValue("eventTypeCodes", eventTypeCodes);
            extra.append(" and e.event_type_code in (:eventTypeCodes) ");
        }
        if (stageTypeCodes != null && !stageTypeCodes.isEmpty()) {
            params.addValue("stageTypeCodes", stageTypeCodes);
            extra.append(" and s.stage_type_code in (:stageTypeCodes) ");
        }

        return jdbcTemplate.query(
            """
                select
                    date_bin(
                        (:targetBucketMinutes || ' minutes')::interval,
                        s.started_at,
                        :originTs
                    ) as bucket_start,
                    e.event_type_code as group_code,
                    count(*) as sample_count,
                    sum(case when s.is_error then 1 else 0 end) as error_count,
                    sum(case when s.duration_ms is null or s.duration_ms < 0 then 0 else s.duration_ms end) as duration_sum,
                    coalesce(percentile_cont(0.95) within group (order by (case when s.duration_ms is null or s.duration_ms < 0 then 0 else s.duration_ms end)::numeric), 0)::numeric(12, 3) as p95_ms,
                    coalesce(percentile_cont(0.99) within group (order by (case when s.duration_ms is null or s.duration_ms < 0 then 0 else s.duration_ms end)::numeric), 0)::numeric(12, 3) as p99_ms,
                    coalesce(max(case when s.duration_ms is null or s.duration_ms < 0 then 0 else s.duration_ms end)::numeric, 0)::numeric(12, 3) as max_ms
                from analytics.stage s
                join analytics.event e on e.id = s.event_id
                where s.started_at >= :fromTs
                  and s.started_at < :toTs
                  and (:moduleCode is null or e.module_code = :moduleCode)
                  and s.stage_type_code is not null
                  and e.event_type_code is not null
            """
                + extra
                + """
                group by 1, 2
                order by 1 asc, 2 asc
            """,
            params,
            aggregatePointMapper()
        );
    }

    public AnalyticsAccumulator accumulateAll(Collection<AggregatePoint> points) {
        AnalyticsAccumulator accumulator = new AnalyticsAccumulator();
        if (points == null) {
            return accumulator;
        }
        points.forEach(accumulator::accept);
        return accumulator;
    }

    public Map<String, AnalyticsAccumulator> accumulateByCode(Collection<AggregatePoint> points) {
        Map<String, AnalyticsAccumulator> map = new LinkedHashMap<>();
        if (points == null) {
            return map;
        }
        for (AggregatePoint point : points) {
            map.computeIfAbsent(point.groupCode(), key -> new AnalyticsAccumulator()).accept(point);
        }
        return map;
    }

    public List<TimeSeriesPointDto> seriesFromPoints(
        Instant from,
        Instant to,
        int bucketMinutes,
        Collection<AggregatePoint> points
    ) {
        long stepSeconds = bucketMinutes * 60L;
        Instant start = floorToBucket(from, bucketMinutes);
        Instant end = floorToBucket(to.minusSeconds(1L), bucketMinutes).plusSeconds(stepSeconds);

        Map<Instant, AnalyticsAccumulator> grouped = new LinkedHashMap<>();
        if (points != null) {
            for (AggregatePoint point : points) {
                grouped.computeIfAbsent(point.bucketStart(), key -> new AnalyticsAccumulator()).accept(point);
            }
        }

        List<TimeSeriesPointDto> result = new ArrayList<>();
        for (Instant bucket = start; bucket.isBefore(end); bucket = bucket.plusSeconds(stepSeconds)) {
            AnalyticsAccumulator acc = grouped.getOrDefault(bucket, new AnalyticsAccumulator());
            result.add(new TimeSeriesPointDto(
                AnalyticsSeriesTime.displayTimeForBucket(bucket, to, stepSeconds),
                acc.sampleCount(),
                acc.avgMs(),
                acc.p95Ms(),
                acc.p99Ms(),
                acc.errorRate()
            ));
        }
        return result;
    }

    public List<AggregatePoint> pointsForCode(Collection<AggregatePoint> points, String code) {
        if (points == null || code == null) {
            return List.of();
        }
        return points.stream()
            .filter(point -> code.equals(point.groupCode()))
            .toList();
    }

    private List<AggregatePoint> mergeDuplicatePoints(List<AggregatePoint> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        Map<PointKey, AnalyticsAccumulator> grouped = new LinkedHashMap<>();
        for (AggregatePoint point : points) {
            PointKey key = new PointKey(point.bucketStart(), point.groupCode());
            grouped.computeIfAbsent(key, ignored -> new AnalyticsAccumulator()).accept(point);
        }
        return grouped.entrySet().stream()
            .map(entry -> entry.getValue().toPoint(entry.getKey().bucketStart(), entry.getKey().groupCode()))
            .sorted((left, right) -> {
                int byTime = left.bucketStart().compareTo(right.bucketStart());
                if (byTime != 0) {
                    return byTime;
                }
                return left.groupCode().compareTo(right.groupCode());
            })
            .toList();
    }

    private Instant resolveEventBootstrapStart(int granularityMinutes, Instant toExclusive, int bootstrapLookbackDays) {
        Timestamp earliestTs = jdbcTemplate.queryForObject(
            "select min(started_at) from analytics.event",
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

    private Instant resolveStageBootstrapStart(int granularityMinutes, Instant toExclusive, int bootstrapLookbackDays) {
        Timestamp earliestTs = jdbcTemplate.queryForObject(
            "select min(started_at) from analytics.stage",
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

    private RowMapper<AggregatePoint> aggregatePointMapper() {
        return new RowMapper<>() {
            @Override
            public AggregatePoint mapRow(ResultSet rs, int rowNum) throws SQLException {
                Timestamp bucketTs = rs.getTimestamp("bucket_start");
                Instant bucketStart = bucketTs == null ? null : bucketTs.toInstant();
                String groupCode = rs.getString("group_code");
                long sampleCount = rs.getLong("sample_count");
                long errorCount = rs.getLong("error_count");
                long durationSum = rs.getLong("duration_sum");
                BigDecimal p95Ms = scale(rs.getBigDecimal("p95_ms"));
                BigDecimal p99Ms = scale(rs.getBigDecimal("p99_ms"));
                BigDecimal maxMs = scale(rs.getBigDecimal("max_ms"));
                long selfDurationSum = optionalLong(rs, "self_duration_sum", durationSum);
                BigDecimal selfP95Ms = optionalDecimal(rs, "self_p95_ms", p95Ms);
                BigDecimal selfP99Ms = optionalDecimal(rs, "self_p99_ms", p99Ms);
                BigDecimal selfMaxMs = optionalDecimal(rs, "self_max_ms", maxMs);
                return new AggregatePoint(
                    bucketStart,
                    groupCode,
                    sampleCount,
                    errorCount,
                    durationSum,
                    p95Ms,
                    p99Ms,
                    maxMs,
                    selfDurationSum,
                    selfP95Ms,
                    selfP99Ms,
                    selfMaxMs
                );
            }
        };
    }

    private long optionalLong(ResultSet rs, String column, long fallback) {
        try {
            return rs.getLong(column);
        } catch (SQLException ignored) {
            return fallback;
        }
    }

    private BigDecimal optionalDecimal(ResultSet rs, String column, BigDecimal fallback) {
        try {
            return scale(rs.getBigDecimal(column));
        } catch (SQLException ignored) {
            return scale(fallback);
        }
    }

    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    public record AggregatePoint(
        Instant bucketStart,
        String groupCode,
        long sampleCount,
        long errorCount,
        long durationSum,
        BigDecimal p95Ms,
        BigDecimal p99Ms,
        BigDecimal maxMs,
        long selfDurationSum,
        BigDecimal selfP95Ms,
        BigDecimal selfP99Ms,
        BigDecimal selfMaxMs
    ) {
        public AggregatePoint(
            Instant bucketStart,
            String groupCode,
            long sampleCount,
            long errorCount,
            long durationSum,
            BigDecimal p95Ms,
            BigDecimal p99Ms,
            BigDecimal maxMs
        ) {
            this(
                bucketStart,
                groupCode,
                sampleCount,
                errorCount,
                durationSum,
                p95Ms,
                p99Ms,
                maxMs,
                durationSum,
                p95Ms,
                p99Ms,
                maxMs
            );
        }
    }

    private record PointKey(
        Instant bucketStart,
        String groupCode
    ) {
    }

    public static final class AnalyticsAccumulator {
        private long sampleCount;
        private long errorCount;
        private long durationSum;
        private long selfDurationSum;
        private BigDecimal weightedP95 = BigDecimal.ZERO;
        private BigDecimal weightedP99 = BigDecimal.ZERO;
        private BigDecimal maxMs = BigDecimal.ZERO;
        private BigDecimal weightedSelfP95 = BigDecimal.ZERO;
        private BigDecimal weightedSelfP99 = BigDecimal.ZERO;
        private BigDecimal selfMaxMs = BigDecimal.ZERO;

        public void accept(AggregatePoint point) {
            if (point == null) {
                return;
            }
            long count = Math.max(0L, point.sampleCount());
            sampleCount += count;
            errorCount += Math.max(0L, point.errorCount());
            durationSum += Math.max(0L, point.durationSum());
            selfDurationSum += Math.max(0L, point.selfDurationSum());
            if (count > 0L) {
                weightedP95 = weightedP95.add(nonNull(point.p95Ms()).multiply(BigDecimal.valueOf(count)));
                weightedP99 = weightedP99.add(nonNull(point.p99Ms()).multiply(BigDecimal.valueOf(count)));
                weightedSelfP95 = weightedSelfP95.add(nonNull(point.selfP95Ms()).multiply(BigDecimal.valueOf(count)));
                weightedSelfP99 = weightedSelfP99.add(nonNull(point.selfP99Ms()).multiply(BigDecimal.valueOf(count)));
            }
            if (nonNull(point.maxMs()).compareTo(maxMs) > 0) {
                maxMs = nonNull(point.maxMs());
            }
            if (nonNull(point.selfMaxMs()).compareTo(selfMaxMs) > 0) {
                selfMaxMs = nonNull(point.selfMaxMs());
            }
        }

        public long sampleCount() {
            return sampleCount;
        }

        public long errorCount() {
            return errorCount;
        }

        public BigDecimal avgMs() {
            if (sampleCount <= 0L) {
                return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
            }
            return BigDecimal.valueOf(durationSum)
                .divide(BigDecimal.valueOf(sampleCount), 3, RoundingMode.HALF_UP);
        }

        public BigDecimal p95Ms() {
            if (sampleCount <= 0L) {
                return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
            }
            return weightedP95.divide(BigDecimal.valueOf(sampleCount), 3, RoundingMode.HALF_UP);
        }

        public BigDecimal p99Ms() {
            if (sampleCount <= 0L) {
                return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
            }
            return weightedP99.divide(BigDecimal.valueOf(sampleCount), 3, RoundingMode.HALF_UP);
        }

        public BigDecimal maxMs() {
            return maxMs.setScale(3, RoundingMode.HALF_UP);
        }

        public long selfDurationSum() {
            return selfDurationSum;
        }

        public BigDecimal avgSelfMs() {
            if (sampleCount <= 0L) {
                return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
            }
            return BigDecimal.valueOf(selfDurationSum)
                .divide(BigDecimal.valueOf(sampleCount), 3, RoundingMode.HALF_UP);
        }

        public BigDecimal p95SelfMs() {
            if (sampleCount <= 0L) {
                return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
            }
            return weightedSelfP95.divide(BigDecimal.valueOf(sampleCount), 3, RoundingMode.HALF_UP);
        }

        public BigDecimal p99SelfMs() {
            if (sampleCount <= 0L) {
                return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
            }
            return weightedSelfP99.divide(BigDecimal.valueOf(sampleCount), 3, RoundingMode.HALF_UP);
        }

        public BigDecimal selfMaxMs() {
            return selfMaxMs.setScale(3, RoundingMode.HALF_UP);
        }

        public BigDecimal selfShare() {
            if (durationSum <= 0L) {
                return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            }
            return BigDecimal.valueOf(selfDurationSum)
                .divide(BigDecimal.valueOf(durationSum), 4, RoundingMode.HALF_UP);
        }

        public BigDecimal errorRate() {
            if (sampleCount <= 0L) {
                return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            }
            return BigDecimal.valueOf(errorCount)
                .divide(BigDecimal.valueOf(sampleCount), 4, RoundingMode.HALF_UP);
        }

        public AggregatePoint toPoint(Instant bucketStart, String groupCode) {
            return new AggregatePoint(
                bucketStart,
                groupCode == null ? "" : groupCode.toUpperCase(Locale.ROOT),
                sampleCount,
                errorCount,
                durationSum,
                p95Ms(),
                p99Ms(),
                maxMs(),
                selfDurationSum,
                p95SelfMs(),
                p99SelfMs(),
                selfMaxMs()
            );
        }

        private static BigDecimal nonNull(BigDecimal value) {
            return Objects.requireNonNullElse(value, BigDecimal.ZERO);
        }
    }
}
