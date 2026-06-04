package com.example.gqw.analytics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventLogEntryDto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:log-index-test-" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
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
        logIndexService = new AnalyticsLogArchiveIndexService(new NamedParameterJdbcTemplate(dataSource), settingsService);
        ReflectionTestUtils.setField(logIndexService, "appLogFilePath", tempDir.resolve("logs").resolve("gqw.log").toString());
        ReflectionTestUtils.setField(logIndexService, "moduleLogDir", tempDir.resolve("logs").resolve("analytics").resolve("modules").toString());
    }

    @Test
    void archiveTraceReadRespectsMaxLinesPerTrace() throws Exception {
        settingsService.update(Map.of(
            AnalyticsRuntimeSettingsService.KEY_LOG_ARCHIVE_ALLOWED_DIRECTORY, tempDir.toString(),
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
