package com.example.gqw.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventLogEntryDto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

class AnalyticsLogArchiveIndexServiceTest {

    private AnalyticsRuntimeSettingsService settingsService;
    private AnalyticsLogArchiveIndexService logIndexService;
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:log-index-test-" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("create schema if not exists analytics");
        jdbcTemplate.execute(
            """
                create table analytics.runtime_setting (
                    setting_key varchar(160) not null primary key,
                    setting_value varchar(2048),
                    updated_at timestamp with time zone not null default now(),
                    updated_by varchar(128)
                )
            """
        );
        settingsService = new AnalyticsRuntimeSettingsService(new NamedParameterJdbcTemplate(dataSource));
        logIndexService = new AnalyticsLogArchiveIndexService(
            new NamedParameterJdbcTemplate(dataSource),
            settingsService,
            new AnalyticsScheduledJobsPolicy(true)
        );
        ReflectionTestUtils.setField(logIndexService, "appLogFilePath", tempDir.resolve("logs").resolve("gqw.log").toString());
        ReflectionTestUtils.setField(logIndexService, "moduleLogDir", tempDir.resolve("logs").resolve("analytics").resolve("modules").toString());
    }

    @Test
    void archiveTraceReadRespectsMaxLinesPerTrace() throws Exception {
        settingsService.update(Map.of(
            AnalyticsRuntimeSettingsService.KEY_LOG_ARCHIVE_ALLOWED_DIRECTORY, tempDir.toString(),
            AnalyticsRuntimeSettingsService.KEY_LOG_ARCHIVE_READ_ENABLED, "true",
            AnalyticsRuntimeSettingsService.KEY_LOG_ARCHIVE_READ_MAX_LINES, "500",
            AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_MAX_LINES_PER_TRACE, "10"
        ), "test");

        Path log = tempDir.resolve("trace.log");
        Files.writeString(log, String.join(System.lineSeparator(),
            "2026-06-04 10:00:00.000 INFO [trace:trace-a] [event:event-a] [module:DEFAULT] --- [main] com.example.Service : first",
            "2026-06-04 10:00:01.000 WARN [trace:trace-a] [event:event-a] [module:DEFAULT] --- [main] com.example.Service : second",
            "2026-06-04 10:00:02.000 ERROR [trace:trace-a] [event:event-a] [module:DEFAULT] --- [main] com.example.Service : third"
        ));

        settingsService.update(Map.of(AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_MAX_LINES_PER_TRACE, "10"), "test");
        List<EventLogEntryDto> tenLineLimit = logIndexService.loadTraceLinesFromArchive(
            lookup(log),
            "trace-a",
            "event-a"
        );

        settingsService.update(Map.of(AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_MAX_LINES_PER_TRACE, "2"), "test");
        List<EventLogEntryDto> twoLineLimit = logIndexService.loadTraceLinesFromArchive(
            lookup(log),
            "trace-a",
            "event-a"
        );

        assertEquals(3, tenLineLimit.size());
        assertEquals(2, twoLineLimit.size());
    }

    @Test
    void findsArchiveCandidatesForEventDateWithSingleDigitAndSequenceSuffix() throws Exception {
        settingsService.update(Map.of(
            AnalyticsRuntimeSettingsService.KEY_LOG_ARCHIVE_ALLOWED_DIRECTORY, tempDir.toString()
        ), "test");
        Path archiveDir = tempDir.resolve("logs").resolve("analytics").resolve("modules").resolve("archive");
        Files.createDirectories(archiveDir);
        Files.writeString(archiveDir.resolve("SHOP.2026-06-03.0.log.gz"), "not a real gzip");
        Files.writeString(archiveDir.resolve("SHOP.2026-06-03.10.log.gz"), "not a real gzip");
        Files.writeString(archiveDir.resolve("ADMIN.2026-06-03.1.log.gz"), "not a real gzip");
        Files.writeString(archiveDir.resolve("SHOP.2026-06-02.1.log.gz"), "not a real gzip");

        List<String> candidates = logIndexService.findArchiveCandidatesForDate(
            "SHOP",
            Instant.parse("2026-06-03T12:00:00Z"),
            10
        );

        assertEquals(2, candidates.size());
        assertTrue(candidates.contains("SHOP.2026-06-03.0.log.gz"));
        assertTrue(candidates.contains("SHOP.2026-06-03.10.log.gz"));
    }

    @Test
    void traceLookupDoesNotScanArchiveCandidatesWhenTraceIsNotIndexed() throws Exception {
        settingsService.update(Map.of(
            AnalyticsRuntimeSettingsService.KEY_LOG_ARCHIVE_ALLOWED_DIRECTORY, tempDir.toString()
        ), "test");
        Path archiveDir = tempDir.resolve("logs").resolve("analytics").resolve("modules").resolve("archive");
        Files.createDirectories(archiveDir);
        Files.writeString(archiveDir.resolve("SHOP.2026-06-03.10.log.gz"), "not indexed yet");

        AnalyticsLogViewService logViewService = new AnalyticsLogViewService(
            logIndexService,
            new AnalyticsLoggingPolicy(settingsService)
        );
        ReflectionTestUtils.setField(logViewService, "appLogFilePath", tempDir.resolve("logs").resolve("gqw.log").toString());
        ReflectionTestUtils.setField(logViewService, "moduleLogDir", tempDir.resolve("logs").resolve("analytics").resolve("modules").toString());

        AnalyticsLogViewService.TraceLogLookupResult result = logViewService.loadTraceLogs(
            "trace-pending-index",
            "event-pending-index",
            "SHOP",
            Instant.parse("2026-06-03T12:00:00Z"),
            Instant.parse("2026-06-03T12:00:01Z")
        );

        assertEquals("NOT_FOUND", result.status().status());
        assertEquals(null, result.status().fileName());
        assertEquals(null, result.status().summary());
        assertTrue(result.rows().isEmpty());
    }

    @Test
    void skippedTooLargeFileIsNotTreatedAsUnchanged() throws Exception {
        jdbcTemplate.execute(
            """
                create table analytics.log_file_index (
                    id bigint generated by default as identity primary key,
                    module_code varchar(64),
                    file_name varchar(255),
                    file_path varchar(1024) unique,
                    compressed boolean,
                    size_bytes bigint,
                    checksum varchar(128),
                    last_modified_at timestamp with time zone,
                    indexed_at timestamp with time zone,
                    status varchar(64),
                    trace_count bigint
                )
            """
        );
        Path log = tempDir.resolve("large.log");
        Files.writeString(log, "line");
        long size = Files.size(log);
        Instant modifiedAt = Files.getLastModifiedTime(log).toInstant();
        jdbcTemplate.update(
            """
                insert into analytics.log_file_index (
                    module_code, file_name, file_path, compressed, size_bytes,
                    checksum, last_modified_at, indexed_at, status, trace_count
                )
                values (?, ?, ?, false, ?, ?, ?, ?, 'SKIPPED_TOO_LARGE', 0)
            """,
            "DEFAULT",
            log.getFileName().toString(),
            log.toAbsolutePath().normalize().toString(),
            size,
            "too-large",
            Timestamp.from(modifiedAt),
            Timestamp.from(modifiedAt)
        );

        Boolean unchanged = ReflectionTestUtils.invokeMethod(logIndexService, "isUnchanged", log, size);

        assertFalse(Boolean.TRUE.equals(unchanged));
    }

    private static AnalyticsLogArchiveIndexService.IndexedTraceLookup lookup(Path log) {
        return new AnalyticsLogArchiveIndexService.IndexedTraceLookup(
            "ARCHIVE_AVAILABLE",
            1L,
            "trace-a",
            "event-a",
            "DEFAULT",
            log.getFileName().toString(),
            log.toAbsolutePath().normalize().toString(),
            false,
            Instant.parse("2026-06-04T10:00:00Z"),
            Instant.parse("2026-06-04T10:00:02Z"),
            3,
            1,
            1,
            "test",
            "CURRENT",
            List.of()
        );
    }
}
