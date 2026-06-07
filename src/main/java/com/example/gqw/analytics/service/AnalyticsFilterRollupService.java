package com.example.gqw.analytics.service;

import java.sql.Date;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsFilterRollupService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AnalyticsRuntimeSettingsService runtimeSettingsService;
    private final Clock clock;
    private final boolean defaultEnabled;
    private final int defaultLongRangeDays;
    private final int defaultRefreshRecentDays;
    private final int defaultRefreshIntervalMinutes;
    private final Object refreshLock = new Object();
    private volatile Instant lastScheduledRefreshAt;

    public AnalyticsFilterRollupService(
        NamedParameterJdbcTemplate jdbcTemplate,
        AnalyticsRuntimeSettingsService runtimeSettingsService,
        @Value("${app.analytics.filter-rollup.enabled:true}") boolean enabled,
        @Value("${app.analytics.filter-rollup.long-range-days:30}") int longRangeDays,
        @Value("${app.analytics.filter-rollup.refresh-recent-days:7}") int refreshRecentDays,
        @Value("${app.analytics.filter-rollup.refresh-interval-minutes:10}") int refreshIntervalMinutes
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.runtimeSettingsService = runtimeSettingsService;
        this.clock = Clock.systemUTC();
        this.defaultEnabled = enabled;
        this.defaultLongRangeDays = Math.max(1, longRangeDays);
        this.defaultRefreshRecentDays = Math.max(1, refreshRecentDays);
        this.defaultRefreshIntervalMinutes = Math.max(1, refreshIntervalMinutes);
    }

    @Transactional
    public void initializeIfNeeded() {
        if (!isEnabled() || !rollupTablesExist()) {
            return;
        }
        int refreshRecentDays = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_FILTER_ROLLUP_REFRESH_RECENT_DAYS,
            defaultRefreshRecentDays,
            1,
            365
        );
        if (rollupIsEmpty()) {
            rebuildAll();
            return;
        }
        refreshRecentWindow(refreshRecentDays);
    }

    @Transactional
    public void refreshRecentNow() {
        if (!isEnabled() || !rollupTablesExist()) {
            return;
        }
        int refreshRecentDays = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_FILTER_ROLLUP_REFRESH_RECENT_DAYS,
            defaultRefreshRecentDays,
            1,
            365
        );
        synchronized (refreshLock) {
            refreshRecentWindow(refreshRecentDays);
            lastScheduledRefreshAt = Instant.now(clock);
        }
    }

    @Transactional
    public void rebuildAllNow() {
        if (!isEnabled() || !rollupTablesExist()) {
            return;
        }
        synchronized (refreshLock) {
            rebuildAll();
            lastScheduledRefreshAt = Instant.now(clock);
        }
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void scheduledRefresh() {
        int intervalMinutes = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_FILTER_ROLLUP_REFRESH_INTERVAL_MINUTES,
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
            if (!isEnabled() || !rollupTablesExist()) {
                return;
            }
            int refreshRecentDays = runtimeSettingsService.getInt(
                AnalyticsRuntimeSettingsService.KEY_FILTER_ROLLUP_REFRESH_RECENT_DAYS,
                defaultRefreshRecentDays,
                1,
                365
            );
            refreshRecentWindow(refreshRecentDays);
            lastScheduledRefreshAt = now;
        }
    }

    @Transactional(readOnly = true)
    public boolean shouldUseRollup(Instant from, Instant to, String requestPath) {
        if (!isEnabled() || !rollupTablesExist() || !hasRollupData()) {
            return false;
        }
        if (requestPath != null) {
            return false;
        }
        long rangeDays = DateRange.daysBetweenInclusive(from, to);
        int longRangeDays = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_FILTER_ROLLUP_LONG_RANGE_DAYS,
            defaultLongRangeDays,
            1,
            3650
        );
        return rangeDays >= longRangeDays;
    }

    private boolean isEnabled() {
        return runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_FILTER_ROLLUP_ENABLED,
            defaultEnabled
        );
    }

    @Transactional(readOnly = true)
    public List<String> findEventTypeCodes(
        Instant from,
        Instant to,
        String moduleCode
    ) {
        MapSqlParameterSource params = baseRangeParams(from, to)
            .addValue("moduleCode", moduleCode, Types.VARCHAR);
        return jdbcTemplate.queryForList(
            """
                select distinct event_type_code
                from analytics.filter_event_type_day
                where day_start between :fromDate and :toDate
                  and (:moduleCode is null or module_code = :moduleCode)
                order by event_type_code asc
            """,
            params,
            String.class
        );
    }

    @Transactional(readOnly = true)
    public List<String> findModuleCodes(
        Instant from,
        Instant to,
        Collection<String> eventTypeCodes
    ) {
        if (eventTypeCodes == null || eventTypeCodes.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = baseRangeParams(from, to)
            .addValue("eventTypeCodes", eventTypeCodes);
        return jdbcTemplate.queryForList(
            """
                select distinct module_code
                from analytics.filter_event_type_day
                where day_start between :fromDate and :toDate
                  and event_type_code in (:eventTypeCodes)
                  and module_code is not null
                order by module_code asc
            """,
            params,
            String.class
        );
    }

    @Transactional(readOnly = true)
    public List<String> findAttributeTypeCodes(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode
    ) {
        MapSqlParameterSource params = baseRangeParams(from, to)
            .addValue("moduleCode", moduleCode, Types.VARCHAR)
            .addValue("eventTypeCode", eventTypeCode, Types.VARCHAR);
        return jdbcTemplate.queryForList(
            """
                select distinct attribute_type_code
                from analytics.filter_attr_value_day
                where day_start between :fromDate and :toDate
                  and (:moduleCode is null or module_code = :moduleCode)
                  and (:eventTypeCode is null or event_type_code = :eventTypeCode)
                order by attribute_type_code asc
            """,
            params,
            String.class
        );
    }

    @Transactional(readOnly = true)
    public List<String> findAttributeValues(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode,
        String attributeCode
    ) {
        MapSqlParameterSource params = baseRangeParams(from, to)
            .addValue("moduleCode", moduleCode, Types.VARCHAR)
            .addValue("eventTypeCode", eventTypeCode, Types.VARCHAR)
            .addValue("attributeCode", attributeCode, Types.VARCHAR);
        return jdbcTemplate.queryForList(
            """
                select distinct attribute_value
                from analytics.filter_attr_value_day
                where day_start between :fromDate and :toDate
                  and (:moduleCode is null or module_code = :moduleCode)
                  and (:eventTypeCode is null or event_type_code = :eventTypeCode)
                  and attribute_type_code = :attributeCode
                order by attribute_value asc
            """,
            params,
            String.class
        );
    }

    private void refreshRecentWindow(int recentDays) {
        LocalDate today = LocalDate.now(clock);
        LocalDate fromDate = today.minusDays(Math.max(1, recentDays) - 1L);
        Map<String, Object> params = Map.of("fromDate", Date.valueOf(fromDate));

        jdbcTemplate.update(
            "delete from analytics.filter_event_type_day where day_start >= :fromDate",
            params
        );
        jdbcTemplate.update(
            "delete from analytics.filter_attr_value_day where day_start >= :fromDate",
            params
        );

        jdbcTemplate.update(
            """
                insert into analytics.filter_event_type_day (
                    day_start,
                    module_code,
                    event_type_code,
                    sample_count
                )
                select
                    (e.started_at at time zone 'UTC')::date as day_start,
                    e.module_code,
                    e.event_type_code,
                    count(*) as sample_count
                from analytics.event e
                where e.started_at >= :fromDate
                  and e.module_code is not null
                  and e.event_type_code is not null
                group by 1, 2, 3
                on conflict (day_start, module_code, event_type_code)
                do update set sample_count = excluded.sample_count
            """,
            params
        );

        jdbcTemplate.update(
            """
                insert into analytics.filter_attr_value_day (
                    day_start,
                    module_code,
                    event_type_code,
                    attribute_type_code,
                    attribute_value,
                    sample_count
                )
                select
                    (e.started_at at time zone 'UTC')::date as day_start,
                    e.module_code,
                    e.event_type_code,
                    a.attribute_type_code,
                    left(trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))), 255) as attribute_value,
                    count(*) as sample_count
                from analytics.event_attribute a
                join analytics.event e on e.id = a.event_id
                where e.started_at >= :fromDate
                  and e.module_code is not null
                  and e.event_type_code is not null
                  and a.attribute_type_code is not null
                  and trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))) <> ''
                group by 1, 2, 3, 4, 5
                on conflict (day_start, module_code, event_type_code, attribute_type_code, attribute_value)
                do update set sample_count = excluded.sample_count
            """,
            params
        );
    }

    private void rebuildAll() {
        jdbcTemplate.update("delete from analytics.filter_event_type_day", Map.of());
        jdbcTemplate.update("delete from analytics.filter_attr_value_day", Map.of());

        jdbcTemplate.update(
            """
                insert into analytics.filter_event_type_day (
                    day_start,
                    module_code,
                    event_type_code,
                    sample_count
                )
                select
                    (e.started_at at time zone 'UTC')::date as day_start,
                    e.module_code,
                    e.event_type_code,
                    count(*) as sample_count
                from analytics.event e
                where e.module_code is not null
                  and e.event_type_code is not null
                group by 1, 2, 3
                on conflict (day_start, module_code, event_type_code)
                do update set sample_count = excluded.sample_count
            """,
            Map.of()
        );

        jdbcTemplate.update(
            """
                insert into analytics.filter_attr_value_day (
                    day_start,
                    module_code,
                    event_type_code,
                    attribute_type_code,
                    attribute_value,
                    sample_count
                )
                select
                    (e.started_at at time zone 'UTC')::date as day_start,
                    e.module_code,
                    e.event_type_code,
                    a.attribute_type_code,
                    left(trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))), 255) as attribute_value,
                    count(*) as sample_count
                from analytics.event_attribute a
                join analytics.event e on e.id = a.event_id
                where e.module_code is not null
                  and e.event_type_code is not null
                  and a.attribute_type_code is not null
                  and trim(coalesce(nullif(a.attr_value, ''), nullif(a.attr_value_json, ''))) <> ''
                group by 1, 2, 3, 4, 5
                on conflict (day_start, module_code, event_type_code, attribute_type_code, attribute_value)
                do update set sample_count = excluded.sample_count
            """,
            Map.of()
        );
    }

    private boolean rollupTablesExist() {
        return tableExists("analytics.filter_event_type_day") && tableExists("analytics.filter_attr_value_day");
    }

    private boolean hasRollupData() {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from analytics.filter_event_type_day",
            Map.of(),
            Integer.class
        );
        return count != null && count > 0;
    }

    private boolean rollupIsEmpty() {
        return !hasRollupData();
    }

    private boolean tableExists(String regclass) {
        Boolean exists = jdbcTemplate.queryForObject(
            "select to_regclass(:regclass) is not null",
            Map.of("regclass", regclass),
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    private static MapSqlParameterSource baseRangeParams(Instant from, Instant to) {
        return new MapSqlParameterSource()
            .addValue("fromDate", Date.valueOf(from.atZone(ZoneOffset.UTC).toLocalDate()))
            .addValue("toDate", Date.valueOf(to.atZone(ZoneOffset.UTC).toLocalDate()));
    }

    private static final class DateRange {
        private DateRange() {
        }

        private static long daysBetweenInclusive(Instant from, Instant to) {
            LocalDate fromDate = from.atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate toDate = to.atZone(ZoneOffset.UTC).toLocalDate();
            if (toDate.isBefore(fromDate)) {
                return 0;
            }
            return fromDate.datesUntil(toDate.plusDays(1)).count();
        }
    }
}
