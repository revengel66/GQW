package com.example.gqw.analytics.service;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private final Clock clock;
    private final boolean enabled;
    private final int longRangeDays;
    private final int refreshRecentDays;

    public AnalyticsFilterRollupService(
        NamedParameterJdbcTemplate jdbcTemplate,
        @Value("${app.analytics.filter-rollup.enabled:true}") boolean enabled,
        @Value("${app.analytics.filter-rollup.long-range-days:30}") int longRangeDays,
        @Value("${app.analytics.filter-rollup.refresh-recent-days:7}") int refreshRecentDays
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = Clock.systemUTC();
        this.enabled = enabled;
        this.longRangeDays = Math.max(1, longRangeDays);
        this.refreshRecentDays = Math.max(1, refreshRecentDays);
    }

    @Transactional
    public void initializeIfNeeded() {
        if (!enabled || !rollupTablesExist()) {
            return;
        }
        if (rollupIsEmpty()) {
            rebuildAll();
            return;
        }
        refreshRecentWindow(refreshRecentDays);
    }

    @Scheduled(cron = "${app.analytics.filter-rollup.refresh-cron:0 */10 * * * *}")
    @Transactional
    public void scheduledRefresh() {
        if (!enabled || !rollupTablesExist()) {
            return;
        }
        refreshRecentWindow(refreshRecentDays);
    }

    @Transactional(readOnly = true)
    public boolean shouldUseRollup(Instant from, Instant to, String requestPath) {
        if (!enabled || !rollupTablesExist() || !hasRollupData()) {
            return false;
        }
        if (requestPath != null) {
            return false;
        }
        long rangeDays = DateRange.daysBetweenInclusive(from, to);
        return rangeDays >= longRangeDays;
    }

    @Transactional(readOnly = true)
    public List<String> findEventTypeCodes(
        Instant from,
        Instant to,
        String moduleCode
    ) {
        MapSqlParameterSource params = baseRangeParams(from, to)
            .addValue("moduleCode", moduleCode);
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
    public List<String> findAttributeTypeCodes(
        Instant from,
        Instant to,
        String moduleCode,
        String eventTypeCode
    ) {
        MapSqlParameterSource params = baseRangeParams(from, to)
            .addValue("moduleCode", moduleCode)
            .addValue("eventTypeCode", eventTypeCode);
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
            .addValue("moduleCode", moduleCode)
            .addValue("eventTypeCode", eventTypeCode)
            .addValue("attributeCode", attributeCode);
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
