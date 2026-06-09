package com.example.gqw.analytics.service;

import org.springframework.beans.factory.annotation.Qualifier;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsDataLifecycleService {

    private static final int GR_1M = 1;
    private static final int GR_5M = 5;
    private static final int GR_1H = 60;
    private static final int GR_1D = 1440;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AnalyticsRuntimeSettingsService runtimeSettingsService;
    private final AnalyticsScheduledJobsPolicy scheduledJobsPolicy;
    private final Clock clock;
    private final boolean defaultLifecycleEnabled;
    private final Object runLock = new Object();
    private volatile Instant lastRunAt;

    public AnalyticsDataLifecycleService(
        @Qualifier("analyticsNamedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
        AnalyticsRuntimeSettingsService runtimeSettingsService,
        AnalyticsScheduledJobsPolicy scheduledJobsPolicy,
        @Value("${app.analytics.lifecycle.enabled:true}") boolean lifecycleEnabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.runtimeSettingsService = runtimeSettingsService;
        this.scheduledJobsPolicy = scheduledJobsPolicy;
        this.clock = Clock.systemUTC();
        this.defaultLifecycleEnabled = lifecycleEnabled;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional(transactionManager = "analyticsTransactionManager")
    public void scheduledMaintenance() {
        if (!scheduledJobsPolicy.isEnabled()) {
            return;
        }
        if (!runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_LIFECYCLE_ENABLED,
            defaultLifecycleEnabled
        )) {
            return;
        }

        int intervalMinutes = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_LIFECYCLE_INTERVAL_MINUTES,
            30,
            1,
            720
        );

        Instant now = Instant.now(clock);
        synchronized (runLock) {
            if (lastRunAt != null && now.isBefore(lastRunAt.plus(intervalMinutes, ChronoUnit.MINUTES))) {
                return;
            }
            runCleanup(now);
            lastRunAt = now;
        }
    }

    @Transactional(transactionManager = "analyticsTransactionManager")
    public void runMaintenanceNow() {
        Instant now = Instant.now(clock);
        synchronized (runLock) {
            runCleanup(now);
            lastRunAt = now;
        }
    }

    private void runCleanup(Instant now) {
        purgeRawData(now);
        purgeEventRollups(now);
        purgeStageRollups(now);
        purgeStageMetricRollups(now);
        purgeFilterRollups(now);
    }

    private void purgeRawData(Instant now) {
        if (!tableExists("analytics.event")) {
            return;
        }
        int retentionDays = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_RAW_RETENTION_DAYS,
            90,
            7,
            3650
        );
        int batchSize = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_LIFECYCLE_DELETE_BATCH_SIZE,
            5000,
            100,
            200000
        );
        Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);

        int iterations = 0;
        while (iterations < 20) {
            MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cutoff", Timestamp.from(cutoff))
                .addValue("batchSize", batchSize);

            if (tableExists("analytics.event_attribute")) {
                jdbcTemplate.update(
                    """
                        with doomed as (
                            select id
                            from analytics.event
                            where started_at < :cutoff
                            order by started_at asc
                            limit :batchSize
                        )
                        delete from analytics.event_attribute a
                        using doomed d
                        where a.event_id = d.id
                    """,
                    params
                );
            }

            if (tableExists("analytics.stage_metric") && tableExists("analytics.stage")) {
                jdbcTemplate.update(
                    """
                        with doomed as (
                            select id
                            from analytics.event
                            where started_at < :cutoff
                            order by started_at asc
                            limit :batchSize
                        ),
                        doomed_stage as (
                            select s.id
                            from analytics.stage s
                            join doomed d on d.id = s.event_id
                        )
                        delete from analytics.stage_metric sm
                        using doomed_stage ds
                        where sm.stage_id = ds.id
                    """,
                    params
                );
            }

            if (tableExists("analytics.stage")) {
                jdbcTemplate.update(
                    """
                        with doomed as (
                            select id
                            from analytics.event
                            where started_at < :cutoff
                            order by started_at asc
                            limit :batchSize
                        )
                        delete from analytics.stage s
                        using doomed d
                        where s.event_id = d.id
                    """,
                    params
                );
            }

            int deletedEvents = jdbcTemplate.update(
                """
                    with doomed as (
                        select id
                        from analytics.event
                        where started_at < :cutoff
                        order by started_at asc
                        limit :batchSize
                    )
                    delete from analytics.event e
                    using doomed d
                    where e.id = d.id
                """,
                params
            );

            if (deletedEvents < batchSize) {
                break;
            }
            iterations++;
        }
    }

    private void purgeEventRollups(Instant now) {
        if (!tableExists("analytics.event_rollup_bucket")) {
            return;
        }
        int aggregateRetentionDays = aggregateRetentionDays();
        pruneRollupByGranularity(
            "analytics.event_rollup_bucket",
            now,
            cappedRetentionDays(
                runtimeSettingsService.getInt(AnalyticsRuntimeSettingsService.KEY_EVENT_ROLLUP_RETENTION_1M_DAYS, 30, 1, 3650),
                aggregateRetentionDays
            ),
            GR_1M
        );
        pruneRollupByGranularity(
            "analytics.event_rollup_bucket",
            now,
            cappedRetentionDays(
                runtimeSettingsService.getInt(AnalyticsRuntimeSettingsService.KEY_EVENT_ROLLUP_RETENTION_5M_DAYS, 90, 1, 3650),
                aggregateRetentionDays
            ),
            GR_5M
        );
        pruneRollupByGranularity(
            "analytics.event_rollup_bucket",
            now,
            cappedRetentionDays(
                runtimeSettingsService.getInt(AnalyticsRuntimeSettingsService.KEY_EVENT_ROLLUP_RETENTION_1H_DAYS, 730, 1, 3650),
                aggregateRetentionDays
            ),
            GR_1H
        );
        pruneRollupByGranularity(
            "analytics.event_rollup_bucket",
            now,
            cappedRetentionDays(
                runtimeSettingsService.getInt(AnalyticsRuntimeSettingsService.KEY_EVENT_ROLLUP_RETENTION_1D_DAYS, 1095, 1, 3650),
                aggregateRetentionDays
            ),
            GR_1D
        );
    }

    private void purgeStageRollups(Instant now) {
        if (!tableExists("analytics.stage_rollup_bucket")) {
            return;
        }
        int aggregateRetentionDays = aggregateRetentionDays();
        pruneRollupByGranularity(
            "analytics.stage_rollup_bucket",
            now,
            cappedRetentionDays(
                runtimeSettingsService.getInt(AnalyticsRuntimeSettingsService.KEY_STAGE_ROLLUP_RETENTION_1M_DAYS, 30, 1, 3650),
                aggregateRetentionDays
            ),
            GR_1M
        );
        pruneRollupByGranularity(
            "analytics.stage_rollup_bucket",
            now,
            cappedRetentionDays(
                runtimeSettingsService.getInt(AnalyticsRuntimeSettingsService.KEY_STAGE_ROLLUP_RETENTION_5M_DAYS, 90, 1, 3650),
                aggregateRetentionDays
            ),
            GR_5M
        );
        pruneRollupByGranularity(
            "analytics.stage_rollup_bucket",
            now,
            cappedRetentionDays(
                runtimeSettingsService.getInt(AnalyticsRuntimeSettingsService.KEY_STAGE_ROLLUP_RETENTION_1H_DAYS, 730, 1, 3650),
                aggregateRetentionDays
            ),
            GR_1H
        );
        pruneRollupByGranularity(
            "analytics.stage_rollup_bucket",
            now,
            cappedRetentionDays(
                runtimeSettingsService.getInt(AnalyticsRuntimeSettingsService.KEY_STAGE_ROLLUP_RETENTION_1D_DAYS, 1095, 1, 3650),
                aggregateRetentionDays
            ),
            GR_1D
        );
    }

    private void purgeStageMetricRollups(Instant now) {
        if (!tableExists("analytics.stage_metric_rollup_bucket")) {
            return;
        }
        int aggregateRetentionDays = aggregateRetentionDays();
        pruneRollupByGranularity(
            "analytics.stage_metric_rollup_bucket",
            now,
            cappedRetentionDays(
                runtimeSettingsService.getInt(AnalyticsRuntimeSettingsService.KEY_STAGE_METRIC_ROLLUP_RETENTION_1M_DAYS, 30, 1, 3650),
                aggregateRetentionDays
            ),
            GR_1M
        );
        pruneRollupByGranularity(
            "analytics.stage_metric_rollup_bucket",
            now,
            cappedRetentionDays(
                runtimeSettingsService.getInt(AnalyticsRuntimeSettingsService.KEY_STAGE_METRIC_ROLLUP_RETENTION_5M_DAYS, 90, 1, 3650),
                aggregateRetentionDays
            ),
            GR_5M
        );
        pruneRollupByGranularity(
            "analytics.stage_metric_rollup_bucket",
            now,
            cappedRetentionDays(
                runtimeSettingsService.getInt(AnalyticsRuntimeSettingsService.KEY_STAGE_METRIC_ROLLUP_RETENTION_1H_DAYS, 730, 1, 3650),
                aggregateRetentionDays
            ),
            GR_1H
        );
        pruneRollupByGranularity(
            "analytics.stage_metric_rollup_bucket",
            now,
            cappedRetentionDays(
                runtimeSettingsService.getInt(AnalyticsRuntimeSettingsService.KEY_STAGE_METRIC_ROLLUP_RETENTION_1D_DAYS, 1095, 1, 3650),
                aggregateRetentionDays
            ),
            GR_1D
        );
    }

    private void purgeFilterRollups(Instant now) {
        int retentionDays = cappedRetentionDays(
            runtimeSettingsService.getInt(
                AnalyticsRuntimeSettingsService.KEY_FILTER_ROLLUP_RETENTION_DAYS,
                730,
                7,
                3650
            ),
            aggregateRetentionDays()
        );
        Date cutoffDate = Date.valueOf(now.minus(retentionDays, ChronoUnit.DAYS).atZone(clock.getZone()).toLocalDate());
        Map<String, Object> params = Map.of("cutoffDate", cutoffDate);

        if (tableExists("analytics.filter_event_type_day")) {
            jdbcTemplate.update(
                "delete from analytics.filter_event_type_day where day_start < :cutoffDate",
                params
            );
        }
        if (tableExists("analytics.filter_attr_value_day")) {
            jdbcTemplate.update(
                "delete from analytics.filter_attr_value_day where day_start < :cutoffDate",
                params
            );
        }
    }

    private int aggregateRetentionDays() {
        return runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_AGGREGATE_RETENTION_DAYS,
            1095,
            7,
            3650
        );
    }

    private static int cappedRetentionDays(int retentionDays, int aggregateRetentionDays) {
        return Math.min(retentionDays, aggregateRetentionDays);
    }

    private void pruneRollupByGranularity(String tableName, Instant now, int retentionDays, int granularityMinutes) {
        Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
        jdbcTemplate.update(
            "delete from " + tableName + " where granularity_minutes = :granularity and bucket_start < :cutoff",
            new MapSqlParameterSource()
                .addValue("granularity", granularityMinutes)
                .addValue("cutoff", Timestamp.from(cutoff))
        );
    }

    private boolean tableExists(String qualifiedTable) {
        String[] parts = qualifiedTable == null ? new String[0] : qualifiedTable.split("\\.", 2);
        if (parts.length != 2) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from information_schema.tables
                where lower(table_schema) = :schema
                  and lower(table_name) = :table
            """,
            Map.of(
                "schema", parts[0].toLowerCase(),
                "table", parts[1].toLowerCase()
            ),
            Integer.class
        );
        return count != null && count > 0;
    }
}
