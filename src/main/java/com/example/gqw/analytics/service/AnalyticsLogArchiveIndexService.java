package com.example.gqw.analytics.service;

import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventLogEntryDto;
import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventLogExcerptDto;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsLogArchiveIndexService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsLogArchiveIndexService.class);
    private static final DateTimeFormatter LOG_TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Pattern ARCHIVE_FILE_PATTERN = Pattern.compile(
        "^(?<module>[A-Za-z0-9_-]+)\\.(?<date>\\d{4}-\\d{2}-\\d{2})\\.(?<sequence>\\d+)\\.log\\.gz$"
    );
    private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
        "^(?<ts>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+"
            + "(?<level>\\w+)\\s+\\[trace:(?<trace>[^\\]]*)\\]\\s+\\[event:(?<event>[^\\]]*)\\]\\s+"
            + "\\[module:(?<module>[^\\]]*)\\]\\s+---\\s+\\[(?<thread>[^\\]]*)\\]\\s+"
            + "(?<logger>[^:]+)\\s+:\\s(?<msg>.*)$"
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AnalyticsRuntimeSettingsService runtimeSettingsService;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Instant lastScheduledRunAt = Instant.EPOCH;
    private volatile Instant lastCleanupRunAt = Instant.EPOCH;

    @Value("${logging.file.name:logs/gqw.log}")
    private String appLogFilePath;

    @Value("${LOG_MODULE_DIR:logs/analytics/modules}")
    private String moduleLogDir;

    public AnalyticsLogArchiveIndexService(
        NamedParameterJdbcTemplate jdbcTemplate,
        AnalyticsRuntimeSettingsService runtimeSettingsService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.runtimeSettingsService = runtimeSettingsService;
        this.clock = Clock.systemUTC();
    }

    @Scheduled(cron = "0 * * * * *")
    public void scheduledIndexTick() {
        if (!runtimeSettingsService.getBoolean(AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_ENABLED, true)) {
            return;
        }
        int intervalMinutes = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_INTERVAL_MINUTES,
            10,
            1,
            1440
        );
        Instant now = Instant.now(clock);
        if (now.isBefore(lastScheduledRunAt.plus(Duration.ofMinutes(intervalMinutes)))) {
            return;
        }
        lastScheduledRunAt = now;
        indexAvailableFilesNow();
    }

    @Scheduled(cron = "30 * * * * *")
    public void scheduledRetentionTick() {
        if (!runtimeSettingsService.getBoolean(AnalyticsRuntimeSettingsService.KEY_LOG_RETENTION_CLEANUP_ENABLED, false)) {
            return;
        }
        int intervalHours = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_LOG_RETENTION_INTERVAL_HOURS,
            24,
            1,
            720
        );
        Instant now = Instant.now(clock);
        if (now.isBefore(lastCleanupRunAt.plus(Duration.ofHours(intervalHours)))) {
            return;
        }
        lastCleanupRunAt = now;
        cleanupOldLogsNow();
    }

    @Transactional
    public LogIndexRunResult indexAvailableFilesNow() {
        if (!running.compareAndSet(false, true)) {
            return new LogIndexRunResult(0, 0, 0, 0, List.of("log index already running"));
        }
        try {
            if (!tableExists("analytics.log_file_index")) {
                return new LogIndexRunResult(0, 0, 0, 0, List.of("log index schema is not available"));
            }
            int batchSize = runtimeSettingsService.getInt(
                AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_BATCH_SIZE,
                20,
                1,
                500
            );
            long maxFileBytes = runtimeSettingsService.getInt(
                AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_MAX_FILE_SIZE_MB,
                128,
                1,
                2048
            ) * 1024L * 1024L;
            List<Path> candidates = discoverCandidateFiles();
            logScanRoots(candidates);
            int discovered = candidates.size();
            int indexed = 0;
            int skipped = 0;
            int errors = 0;
            List<String> notes = new ArrayList<>();
            for (Path candidate : candidates) {
                if (indexed + errors >= batchSize) {
                    break;
                }
                try {
                    if (!Files.isRegularFile(candidate)) {
                        logParsedFile(candidate, false, "not a regular file");
                        skipped++;
                        continue;
                    }
                    logParsedFile(candidate, true, null);
                    long size = Files.size(candidate);
                    if (size > maxFileBytes) {
                        upsertFileError(candidate, size, "SKIPPED_TOO_LARGE", "file is larger than configured limit");
                        errors++;
                        continue;
                    }
                    if (isUnchanged(candidate, size)) {
                        skipped++;
                        continue;
                    }
                    indexFile(candidate, size);
                    indexed++;
                } catch (RuntimeException | IOException ex) {
                    logParsedFile(candidate, false, ex.getMessage());
                    try {
                        upsertFileError(candidate, safeSize(candidate), "INDEX_ERROR", ex.getMessage());
                    } catch (RuntimeException ignored) {
                        // keep the indexing batch moving
                    }
                    errors++;
                }
            }
            cleanupExpiredIndex();
            LogIndexDiagnostics diagnostics = diagnostics();
            log.info(
                "[LOG_INDEX_DEBUG] index summary filesDiscovered={} filesIndexed={} filesPending={} filesSkippedTooLarge={} filesIndexError={} filesMissing={} traceRowsCount={} excerptRowsCount={} lastIndexStartedAt={} lastIndexFinishedAt={} lastError={}",
                discovered,
                diagnostics.indexedFiles(),
                diagnostics.pendingFiles(),
                diagnostics.skippedTooLargeFiles(),
                diagnostics.indexErrorFiles(),
                diagnostics.missingFiles(),
                diagnostics.traceLinks(),
                diagnostics.excerptRows(),
                null,
                Instant.now(clock),
                diagnostics.lastError()
            );
            notes.add("discovered=" + discovered);
            notes.add("indexed=" + indexed);
            notes.add("skipped=" + skipped);
            notes.add("errors=" + errors);
            return new LogIndexRunResult(discovered, indexed, skipped, errors, notes);
        } finally {
            running.set(false);
        }
    }

    @Transactional(readOnly = true)
    public IndexedTraceLookup findIndexedTrace(String traceId, String eventUid, String moduleCode) {
        String normalizedTrace = trim(traceId);
        if (normalizedTrace == null || !tableExists("analytics.log_trace_index")) {
            return IndexedTraceLookup.notFound();
        }
        List<IndexedTraceLookup> rows = jdbcTemplate.query(
            """
                select
                    ti.trace_id,
                    ti.event_id,
                    ti.module_code,
                    ti.first_ts,
                    ti.last_ts,
                    ti.line_count,
                    ti.error_count,
                    ti.warn_count,
                    ti.summary,
                    fi.id as file_id,
                    fi.file_name,
                    fi.file_path,
                    fi.compressed,
                    fi.from_ts,
                    fi.to_ts,
                    fi.status
                from analytics.log_trace_index ti
                join analytics.log_file_index fi on fi.id = ti.file_id
                where ti.trace_id = :traceId
                  and (:moduleCode is null or ti.module_code = :moduleCode)
                order by
                    case when :eventUid is not null and ti.event_id = :eventUid then 0 else 1 end,
                    ti.last_ts desc nulls last,
                    ti.id desc
                limit 1
            """,
            new MapSqlParameterSource()
                .addValue("traceId", normalizedTrace)
                .addValue("eventUid", trim(eventUid))
                .addValue("moduleCode", normalizeModule(moduleCode)),
            (rs, rowNum) -> new IndexedTraceLookup(
                "ARCHIVE_AVAILABLE",
                rs.getLong("file_id"),
                rs.getString("trace_id"),
                rs.getString("event_id"),
                rs.getString("module_code"),
                rs.getString("file_name"),
                rs.getString("file_path"),
                rs.getBoolean("compressed"),
                toInstant(rs.getTimestamp("from_ts")),
                toInstant(rs.getTimestamp("to_ts")),
                rs.getLong("line_count"),
                rs.getLong("error_count"),
                rs.getLong("warn_count"),
                rs.getString("summary"),
                rs.getString("status"),
                List.of()
            )
        );
        if (rows.isEmpty()) {
            return IndexedTraceLookup.notFound();
        }
        IndexedTraceLookup found = rows.getFirst();
        List<EventLogExcerptDto> excerpts = loadExcerpts(found.fileId(), normalizedTrace, trim(eventUid));
        String status = isArchiveReadable(found) ? "ARCHIVE_AVAILABLE" : "ARCHIVE_INDEX_ONLY";
        return found.withStatusAndExcerpts(status, excerpts);
    }

    public List<EventLogEntryDto> loadTraceLinesFromArchive(IndexedTraceLookup lookup, String traceId, String eventUid) {
        if (lookup == null || !isArchiveReadable(lookup)) {
            return List.of();
        }
        if (!runtimeSettingsService.getBoolean(AnalyticsRuntimeSettingsService.KEY_LOG_ARCHIVE_READ_ENABLED, true)) {
            return List.of();
        }
        int maxLines = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_LOG_ARCHIVE_READ_MAX_LINES,
            500,
            10,
            10000
        );
        int maxLinesPerTrace = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_MAX_LINES_PER_TRACE,
            500,
            1,
            10000
        );
        int effectiveMaxLines = Math.min(maxLines, maxLinesPerTrace);
        Path path = Paths.get(lookup.filePath()).normalize();
        if (!isAllowedPath(path) || !Files.isRegularFile(path)) {
            return List.of();
        }
        List<EventLogEntryDto> rows = new ArrayList<>();
        String expectedTrace = trim(traceId);
        String expectedEvent = trim(eventUid);
        try (BufferedReader reader = newLogReader(path)) {
            String line;
            long lineNo = 0L;
            while ((line = reader.readLine()) != null && rows.size() < effectiveMaxLines) {
                lineNo++;
                Matcher matcher = LOG_LINE_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }
                String parsedTrace = trim(matcher.group("trace"));
                if (!Objects.equals(expectedTrace, parsedTrace)) {
                    continue;
                }
                String parsedEvent = trim(matcher.group("event"));
                if (expectedEvent != null && parsedEvent != null && !Objects.equals(expectedEvent, parsedEvent)) {
                    continue;
                }
                Instant timestamp = parseTimestamp(matcher.group("ts"));
                rows.add(toDto(timestamp, matcher, path.toString(), lineNo));
            }
        } catch (IOException ignored) {
            return List.of();
        }
        rows.sort(Comparator.comparing(EventLogEntryDto::timestamp, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    @Transactional(readOnly = true)
    public LogIndexDiagnostics diagnostics() {
        LogFileCounts fileCounts = countFilesOnDisk();
        if (!tableExists("analytics.log_file_index")) {
            return new LogIndexDiagnostics(
                fileCounts.currentFiles(),
                fileCounts.archiveFiles(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                "SCHEMA_MISSING",
                "schema missing",
                0
            );
        }
        Map<String, Long> db = jdbcTemplate.query(
            """
                select
                    count(*) as indexed_files,
                    count(*) filter (where status in ('MISSING', 'DELETED')) as missing_files,
                    coalesce(sum(trace_count), 0) as trace_links,
                    count(*) filter (where status = 'SKIPPED_TOO_LARGE') as skipped_too_large,
                    count(*) filter (where status = 'INDEX_ERROR') as index_errors,
                    max(indexed_at) as last_indexed_at
                from analytics.log_file_index
            """,
            rs -> {
                Map<String, Long> result = new HashMap<>();
                if (rs.next()) {
                    result.put("indexed_files", rs.getLong("indexed_files"));
                    result.put("missing_files", rs.getLong("missing_files"));
                    result.put("trace_links", rs.getLong("trace_links"));
                    result.put("skipped_too_large", rs.getLong("skipped_too_large"));
                    result.put("index_errors", rs.getLong("index_errors"));
                    Timestamp last = rs.getTimestamp("last_indexed_at");
                    result.put("last_indexed_at", last == null ? 0L : last.toInstant().toEpochMilli());
                }
                return result;
            }
        );
        long excerptRows = tableExists("analytics.log_problem_excerpt")
            ? queryLong("select count(*) from analytics.log_problem_excerpt")
            : 0L;
        String lastError = readLastIndexError();
        long indexedFiles = db.getOrDefault("indexed_files", 0L);
        long pendingFiles = Math.max(0L, fileCounts.currentFiles() + fileCounts.archiveFiles() - indexedFiles);
        Long lastEpoch = db.get("last_indexed_at");
        Instant lastIndexed = lastEpoch == null || lastEpoch <= 0 ? null : Instant.ofEpochMilli(lastEpoch);
        long indexErrors = db.getOrDefault("index_errors", 0L);
        long skippedTooLarge = db.getOrDefault("skipped_too_large", 0L);
        return new LogIndexDiagnostics(
            fileCounts.currentFiles(),
            fileCounts.archiveFiles(),
            indexedFiles,
            pendingFiles,
            db.getOrDefault("trace_links", 0L),
            db.getOrDefault("missing_files", 0L),
            skippedTooLarge,
            indexErrors,
            excerptRows,
            lastIndexed,
            indexErrors > 0 || skippedTooLarge > 0 ? "WARN" : "OK",
            lastError,
            countRetentionCandidates(false).candidates()
        );
    }

    private void indexFile(Path path, long sizeBytes) throws IOException {
        String checksum = sha256(path);
        Instant lastModified = Files.getLastModifiedTime(path).toInstant();
        boolean compressed = path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz");
        String moduleCode = resolveModuleCode(path);
        FileScan scan = scanFile(path, moduleCode);
        String status = compressed ? "ARCHIVED" : "CURRENT";
        Long fileId = upsertFile(path, moduleCode, compressed, sizeBytes, checksum, lastModified, status, scan);
        jdbcTemplate.update(
            "delete from analytics.log_problem_excerpt where file_id = :fileId",
            Map.of("fileId", fileId)
        );
        jdbcTemplate.update(
            "delete from analytics.log_trace_index where file_id = :fileId",
            Map.of("fileId", fileId)
        );
        for (TraceScan trace : scan.traces().values()) {
            insertTrace(fileId, moduleCode, trace);
            for (ProblemExcerpt excerpt : trace.excerpts()) {
                insertExcerpt(fileId, moduleCode, trace, excerpt);
            }
        }
    }

    private FileScan scanFile(Path path, String fallbackModule) throws IOException {
        Map<String, TraceScan> traces = new LinkedHashMap<>();
        long lineCount = 0L;
        long errorCount = 0L;
        long warnCount = 0L;
        Instant firstTs = null;
        Instant lastTs = null;
        int maxExcerpts = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_MAX_EXCERPTS_PER_TRACE,
            8,
            1,
            100
        );
        int excerptMaxLength = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_EXCERPT_MAX_LENGTH,
            800,
            120,
            4096
        );
        Set<String> includeLevels = parseIncludeLevels(runtimeSettingsService.getText(
            AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_INCLUDE_LEVELS,
            "WARN,ERROR,SLOW"
        ));
        long linesWithTrace = 0L;
        long parseErrors = 0L;
        long startedAt = System.nanoTime();
        try (BufferedReader reader = newLogReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                Matcher matcher = LOG_LINE_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    if (line.contains("[trace:")) {
                        parseErrors++;
                    }
                    continue;
                }
                linesWithTrace++;
                Instant timestamp = parseTimestamp(matcher.group("ts"));
                if (timestamp != null) {
                    firstTs = firstTs == null || timestamp.isBefore(firstTs) ? timestamp : firstTs;
                    lastTs = lastTs == null || timestamp.isAfter(lastTs) ? timestamp : lastTs;
                }
                String level = normalizeLevel(matcher.group("level"), matcher.group("msg"));
                if ("ERROR".equals(level)) {
                    errorCount++;
                }
                if ("WARN".equals(level)) {
                    warnCount++;
                }
                String traceId = trim(matcher.group("trace"));
                if (traceId == null) {
                    continue;
                }
                String eventId = trim(matcher.group("event"));
                String module = normalizeModule(trim(matcher.group("module")));
                if (module == null) {
                    module = fallbackModule;
                }
                String traceModule = module;
                TraceScan trace = traces.computeIfAbsent(traceId, key -> new TraceScan(traceId, eventId, traceModule));
                trace.addLine(timestamp, level, shortLogger(matcher.group("logger")));
                boolean slow = String.valueOf(matcher.group("msg")).startsWith("SLOW ");
                if ((includeLevels.contains(level) || (slow && includeLevels.contains("SLOW")))
                    && trace.excerpts().size() < maxExcerpts) {
                    trace.excerpts().add(new ProblemExcerpt(
                        timestamp,
                        level,
                        shortLogger(matcher.group("logger")),
                        abbreviate(matcher.group("msg"), 160),
                        abbreviate(line, excerptMaxLength),
                        lineCount
                    ));
                }
            }
        }
        if (isArchiveLog(path)) {
            log.info(
                "[LOG_INDEX_DEBUG] gzip read fileName={} opened={} linesRead={} linesWithTrace={} tracesFound={} errorsFound={} parseErrors={} durationMs={}",
                path.getFileName(),
                true,
                lineCount,
                linesWithTrace,
                traces.size(),
                errorCount,
                parseErrors,
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
            );
        }
        return new FileScan(firstTs, lastTs, lineCount, errorCount, warnCount, traces);
    }

    private Long upsertFile(
        Path path,
        String moduleCode,
        boolean compressed,
        long sizeBytes,
        String checksum,
        Instant lastModified,
        String status,
        FileScan scan
    ) {
        return jdbcTemplate.queryForObject(
            """
                insert into analytics.log_file_index (
                    module_code, file_name, file_path, compressed, from_ts, to_ts,
                    line_count, size_bytes, checksum, last_modified_at, indexed_at,
                    expires_at, status, error_count, warn_count, trace_count
                )
                values (
                    :moduleCode, :fileName, :filePath, :compressed, :fromTs, :toTs,
                    :lineCount, :sizeBytes, :checksum, :lastModifiedAt, now(),
                    now() + (:retentionDays * interval '1 day'), :status, :errorCount, :warnCount, :traceCount
                )
                on conflict (file_path)
                do update set
                    module_code = excluded.module_code,
                    file_name = excluded.file_name,
                    compressed = excluded.compressed,
                    from_ts = excluded.from_ts,
                    to_ts = excluded.to_ts,
                    line_count = excluded.line_count,
                    size_bytes = excluded.size_bytes,
                    checksum = excluded.checksum,
                    last_modified_at = excluded.last_modified_at,
                    indexed_at = now(),
                    expires_at = excluded.expires_at,
                    status = excluded.status,
                    error_count = excluded.error_count,
                    warn_count = excluded.warn_count,
                    trace_count = excluded.trace_count
                returning id
            """,
            new MapSqlParameterSource()
                .addValue("moduleCode", moduleCode)
                .addValue("fileName", path.getFileName().toString())
                .addValue("filePath", path.toAbsolutePath().normalize().toString())
                .addValue("compressed", compressed)
                .addValue("fromTs", scan.fromTs() == null ? null : Timestamp.from(scan.fromTs()))
                .addValue("toTs", scan.toTs() == null ? null : Timestamp.from(scan.toTs()))
                .addValue("lineCount", scan.lineCount())
                .addValue("sizeBytes", sizeBytes)
                .addValue("checksum", checksum)
                .addValue("lastModifiedAt", Timestamp.from(lastModified))
                .addValue("retentionDays", runtimeSettingsService.getInt(
                    AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_RETENTION_DAYS,
                    180,
                    7,
                    3650
                ))
                .addValue("status", status)
                .addValue("errorCount", scan.errorCount())
                .addValue("warnCount", scan.warnCount())
                .addValue("traceCount", scan.traces().size()),
            Long.class
        );
    }

    private void insertTrace(Long fileId, String moduleCode, TraceScan trace) {
        jdbcTemplate.update(
            """
                insert into analytics.log_trace_index (
                    trace_id, event_id, module_code, file_id, first_ts, last_ts,
                    line_count, error_count, warn_count, has_error, top_sources, summary, indexed_at
                )
                values (
                    :traceId, :eventId, :moduleCode, :fileId, :firstTs, :lastTs,
                    :lineCount, :errorCount, :warnCount, :hasError, :topSources, :summary, now()
                )
            """,
            new MapSqlParameterSource()
                .addValue("traceId", trace.traceId())
                .addValue("eventId", trace.eventId())
                .addValue("moduleCode", normalizeModule(trace.moduleCode()) == null ? moduleCode : normalizeModule(trace.moduleCode()))
                .addValue("fileId", fileId)
                .addValue("firstTs", trace.firstTs() == null ? null : Timestamp.from(trace.firstTs()))
                .addValue("lastTs", trace.lastTs() == null ? null : Timestamp.from(trace.lastTs()))
                .addValue("lineCount", trace.lineCount())
                .addValue("errorCount", trace.errorCount())
                .addValue("warnCount", trace.warnCount())
                .addValue("hasError", trace.errorCount() > 0)
                .addValue("topSources", abbreviate(String.join(", ", trace.sources()), 1024))
                .addValue("summary", trace.summary())
        );
    }

    private void insertExcerpt(Long fileId, String moduleCode, TraceScan trace, ProblemExcerpt excerpt) {
        jdbcTemplate.update(
            """
                insert into analytics.log_problem_excerpt (
                    trace_id, event_id, module_code, file_id, timestamp,
                    level, source, message_short, excerpt, line_number
                )
                values (
                    :traceId, :eventId, :moduleCode, :fileId, :timestamp,
                    :level, :source, :messageShort, :excerpt, :lineNumber
                )
            """,
            new MapSqlParameterSource()
                .addValue("traceId", trace.traceId())
                .addValue("eventId", trace.eventId())
                .addValue("moduleCode", normalizeModule(trace.moduleCode()) == null ? moduleCode : normalizeModule(trace.moduleCode()))
                .addValue("fileId", fileId)
                .addValue("timestamp", excerpt.timestamp() == null ? null : Timestamp.from(excerpt.timestamp()))
                .addValue("level", excerpt.level())
                .addValue("source", excerpt.source())
                .addValue("messageShort", excerpt.messageShort())
                .addValue("excerpt", excerpt.excerpt())
                .addValue("lineNumber", excerpt.lineNumber())
        );
    }

    private void upsertFileError(Path path, long sizeBytes, String status, String message) {
        jdbcTemplate.update(
            """
                insert into analytics.log_file_index (
                    module_code, file_name, file_path, compressed, size_bytes,
                    checksum, last_modified_at, indexed_at, status, trace_count
                )
                values (
                    :moduleCode, :fileName, :filePath, :compressed, :sizeBytes,
                    :checksum, :lastModifiedAt, now(), :status, 0
                )
                on conflict (file_path)
                do update set
                    size_bytes = excluded.size_bytes,
                    checksum = excluded.checksum,
                    last_modified_at = excluded.last_modified_at,
                    indexed_at = now(),
                    status = excluded.status
            """,
            new MapSqlParameterSource()
                .addValue("moduleCode", resolveModuleCode(path))
                .addValue("fileName", path.getFileName().toString())
                .addValue("filePath", path.toAbsolutePath().normalize().toString())
                .addValue("compressed", path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz"))
                .addValue("sizeBytes", sizeBytes)
                .addValue("checksum", abbreviate(message, 128))
                .addValue("lastModifiedAt", Timestamp.from(safeLastModified(path)))
                .addValue("status", status)
        );
    }

    private boolean isUnchanged(Path path, long size) {
        if (!tableExists("analytics.log_file_index")) {
            return false;
        }
        List<Boolean> rows = jdbcTemplate.query(
            """
                select size_bytes = :sizeBytes and last_modified_at = :lastModifiedAt and status <> 'INDEX_ERROR' as unchanged
                from analytics.log_file_index
                where file_path = :filePath
            """,
            new MapSqlParameterSource()
                .addValue("filePath", path.toAbsolutePath().normalize().toString())
                .addValue("sizeBytes", size)
                .addValue("lastModifiedAt", Timestamp.from(safeLastModified(path))),
            (rs, rowNum) -> rs.getBoolean("unchanged")
        );
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.getFirst());
    }

    private List<EventLogExcerptDto> loadExcerpts(Long fileId, String traceId, String eventUid) {
        if (fileId == null || !tableExists("analytics.log_problem_excerpt")) {
            return List.of();
        }
        return jdbcTemplate.query(
            """
                select timestamp, level, source, message_short, excerpt, line_number
                from analytics.log_problem_excerpt
                where file_id = :fileId
                  and trace_id = :traceId
                  and (:eventUid is null or event_id is null or event_id = :eventUid)
                order by timestamp nulls last, line_number
            """,
            new MapSqlParameterSource()
                .addValue("fileId", fileId)
                .addValue("traceId", traceId)
                .addValue("eventUid", eventUid),
            (rs, rowNum) -> new EventLogExcerptDto(
                toInstant(rs.getTimestamp("timestamp")),
                rs.getString("level"),
                rs.getString("source"),
                rs.getString("message_short"),
                rs.getString("excerpt"),
                rs.getLong("line_number")
            )
        );
    }

    private void cleanupExpiredIndex() {
        if (!tableExists("analytics.log_file_index")) {
            return;
        }
        jdbcTemplate.update(
            "delete from analytics.log_file_index where expires_at is not null and expires_at < now()",
            Map.of()
        );
    }

    @Transactional
    public LogRetentionCleanupResult cleanupOldLogsNow() {
        long startedAt = System.nanoTime();
        boolean enabled = runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_LOG_RETENTION_CLEANUP_ENABLED,
            false
        );
        boolean safeMode = runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_LOG_RETENTION_SAFE_MODE_ENABLED,
            true
        );
        int currentRetentionDays = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_LOG_RETENTION_CURRENT_DAYS,
            14,
            1,
            3650
        );
        int archiveRetentionDays = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_LOG_RETENTION_ARCHIVE_DAYS,
            90,
            1,
            3650
        );
        int indexRetentionDays = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_LOG_RETENTION_INDEX_DAYS,
            180,
            1,
            3650
        );
        int batchSize = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_LOG_RETENTION_DELETE_BATCH_SIZE,
            100,
            1,
            10000
        );
        boolean archiveAfterIndexedOnly = runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_LOG_RETENTION_ARCHIVE_INDEXED_ONLY,
            true
        );
        RetentionCandidates candidateSnapshot = countRetentionCandidates(archiveAfterIndexedOnly);
        int deletedFiles = 0;
        int skippedActive = 0;
        int skippedNotIndexed = 0;
        List<String> notes = new ArrayList<>();

        if (enabled && !safeMode) {
            Instant now = Instant.now(clock);
            for (Path path : discoverCandidateFiles()) {
                if (deletedFiles >= batchSize) {
                    break;
                }
                if (!isRetentionCandidate(path, now, currentRetentionDays, archiveRetentionDays)) {
                    continue;
                }
                if (isActiveLog(path)) {
                    skippedActive++;
                    continue;
                }
                if (archiveAfterIndexedOnly && isArchiveLog(path) && !isSuccessfullyIndexed(path)) {
                    skippedNotIndexed++;
                    continue;
                }
                try {
                    Files.deleteIfExists(path);
                    markFileDeleted(path);
                    deletedFiles++;
                } catch (IOException ex) {
                    notes.add(path.getFileName() + ": " + ex.getMessage());
                }
            }
        }
        int deletedIndexRows = deleteExpiredIndexRows(indexRetentionDays, enabled && !safeMode, batchSize);
        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        log.info(
            "[LOG_RETENTION] cleanup currentRetentionDays={} archiveRetentionDays={} indexRetentionDays={} candidates={} deletedFiles={} skippedActive={} skippedNotIndexed={} deletedIndexRows={} durationMs={} dryRun={} safeMode={}",
            currentRetentionDays,
            archiveRetentionDays,
            indexRetentionDays,
            candidateSnapshot.candidates(),
            deletedFiles,
            skippedActive,
            skippedNotIndexed,
            deletedIndexRows,
            durationMs,
            !enabled || safeMode,
            safeMode
        );
        return new LogRetentionCleanupResult(
            enabled,
            safeMode,
            currentRetentionDays,
            archiveRetentionDays,
            indexRetentionDays,
            candidateSnapshot.filesFound(),
            candidateSnapshot.candidates(),
            deletedFiles,
            skippedActive,
            skippedNotIndexed,
            deletedIndexRows,
            durationMs,
            notes
        );
    }

    private List<Path> discoverCandidateFiles() {
        boolean includeCurrent = runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_CURRENT_LOGS_ENABLED,
            true
        );
        boolean includeArchives = runtimeSettingsService.getBoolean(
            AnalyticsRuntimeSettingsService.KEY_LOG_INDEX_ARCHIVES_ENABLED,
            true
        );
        Set<Path> roots = new LinkedHashSet<>();
        Path appLog = Paths.get(appLogFilePath).toAbsolutePath().normalize();
        roots.add(appLog.getParent() == null ? Paths.get(".").toAbsolutePath().normalize() : appLog.getParent());
        roots.add(Paths.get(moduleLogDir).toAbsolutePath().normalize());
        roots.add(Paths.get(moduleLogDir, "archive").toAbsolutePath().normalize());
        roots.add(Paths.get("logs", "archive").toAbsolutePath().normalize());
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root, 2, FileVisitOption.FOLLOW_LINKS)) {
                stream.filter(Files::isRegularFile)
                    .filter(path -> isAllowedPath(path.toAbsolutePath().normalize()))
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return (includeCurrent && name.endsWith(".log")) || (includeArchives && name.endsWith(".log.gz"));
                    })
                    .forEach(files::add);
            } catch (IOException ignored) {
                // skip unreadable roots
            }
        }
        files.sort(Comparator
            .comparing((Path path) -> isCurrentLog(path) ? 0 : 1)
            .thenComparing((Path path) -> safeLastModified(path), Comparator.reverseOrder())
            .thenComparing(path -> path.toAbsolutePath().normalize().toString()));
        return files.stream().distinct().toList();
    }

    private void logScanRoots(List<Path> candidates) {
        Path appLog = Paths.get(appLogFilePath).toAbsolutePath().normalize();
        List<Path> roots = List.of(
            appLog.getParent() == null ? Paths.get(".").toAbsolutePath().normalize() : appLog.getParent(),
            Paths.get(moduleLogDir).toAbsolutePath().normalize(),
            Paths.get(moduleLogDir, "archive").toAbsolutePath().normalize(),
            Paths.get("logs", "archive").toAbsolutePath().normalize()
        );
        long current = candidates.stream().filter(this::isCurrentLog).count();
        long archives = candidates.stream().filter(this::isArchiveLog).count();
        List<String> samples = candidates.stream()
            .filter(this::isArchiveLog)
            .limit(8)
            .map(path -> path.getFileName().toString())
            .toList();
        for (Path root : roots) {
            log.info(
                "[LOG_INDEX_DEBUG] scan roots configuredLogDirectory={} resolvedAbsolutePath={} exists={} readable={} currentLogFilesFound={} archiveLogFilesFound={} archivePatternsUsed={} sampleArchiveFiles={}",
                root,
                root.toAbsolutePath().normalize(),
                Files.exists(root),
                Files.isReadable(root),
                current,
                archives,
                "*.log.gz, <MODULE>.yyyy-MM-dd.<sequence>.log.gz, gqw.yyyy-MM-dd.<sequence>.log.gz",
                samples
            );
        }
    }

    private void logParsedFile(Path path, boolean accepted, String rejectReason) {
        String fileName = path == null || path.getFileName() == null ? "" : path.getFileName().toString();
        Matcher archiveMatcher = ARCHIVE_FILE_PATTERN.matcher(fileName);
        boolean archivePattern = archiveMatcher.matches();
        String moduleCode = archivePattern ? normalizeModule(archiveMatcher.group("module")) : resolveModuleCode(path);
        String timestampOrSequence = archivePattern
            ? archiveMatcher.group("date") + "#" + archiveMatcher.group("sequence")
            : "";
        log.debug(
            "[LOG_INDEX_DEBUG] file parsed fileName={} matchedPattern={} moduleCode={} timestamp/sequence={} compressed={} accepted={} rejected={} rejectReason={}",
            fileName,
            archivePattern ? "module-date-sequence-log-gz" : (isCurrentLog(path) ? "current-log" : "generic-log-gz"),
            moduleCode,
            timestampOrSequence,
            isArchiveLog(path),
            accepted,
            !accepted,
            rejectReason
        );
    }

    private boolean isCurrentLog(Path path) {
        String name = path == null || path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".log") && !name.endsWith(".log.gz");
    }

    private boolean isArchiveLog(Path path) {
        String name = path == null || path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".log.gz");
    }

    public List<String> findArchiveCandidatesForDate(String moduleCode, Instant eventTimestamp, int limit) {
        if (eventTimestamp == null) {
            return List.of();
        }
        LocalDate eventDate = eventTimestamp.atZone(ZoneId.systemDefault()).toLocalDate();
        String normalizedModule = normalizeModule(moduleCode);
        return discoverCandidateFiles().stream()
            .filter(this::isArchiveLog)
            .filter(path -> archiveFileDate(path).map(eventDate::equals).orElse(false))
            .filter(path -> normalizedModule == null || normalizedModule.equals(resolveModuleCode(path)) || "DEFAULT".equals(resolveModuleCode(path)))
            .limit(Math.max(1, limit))
            .map(path -> path.getFileName().toString())
            .toList();
    }

    private RetentionCandidates countRetentionCandidates(boolean archiveAfterIndexedOnly) {
        Instant now = Instant.now(clock);
        int currentRetentionDays = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_LOG_RETENTION_CURRENT_DAYS,
            14,
            1,
            3650
        );
        int archiveRetentionDays = runtimeSettingsService.getInt(
            AnalyticsRuntimeSettingsService.KEY_LOG_RETENTION_ARCHIVE_DAYS,
            90,
            1,
            3650
        );
        int filesFound = 0;
        int candidates = 0;
        for (Path path : discoverCandidateFiles()) {
            filesFound++;
            if (!isRetentionCandidate(path, now, currentRetentionDays, archiveRetentionDays)) {
                continue;
            }
            if (isActiveLog(path)) {
                continue;
            }
            if (archiveAfterIndexedOnly && isArchiveLog(path) && !isSuccessfullyIndexed(path)) {
                continue;
            }
            candidates++;
        }
        return new RetentionCandidates(filesFound, candidates);
    }

    private boolean isRetentionCandidate(Path path, Instant now, int currentRetentionDays, int archiveRetentionDays) {
        if (path == null || !isAllowedPath(path) || (!isCurrentLog(path) && !isArchiveLog(path))) {
            return false;
        }
        Instant lastModified = safeLastModified(path);
        int retentionDays = isArchiveLog(path) ? archiveRetentionDays : currentRetentionDays;
        return lastModified.plus(Duration.ofDays(retentionDays)).isBefore(now);
    }

    private boolean isActiveLog(Path path) {
        if (path == null || !isCurrentLog(path)) {
            return false;
        }
        Path absolute = path.toAbsolutePath().normalize();
        Path appLog = Paths.get(appLogFilePath).toAbsolutePath().normalize();
        if (absolute.equals(appLog)) {
            return true;
        }
        Path moduleRoot = Paths.get(moduleLogDir).toAbsolutePath().normalize();
        return absolute.getParent() != null
            && absolute.getParent().equals(moduleRoot)
            && absolute.getFileName().toString().matches("[A-Za-z0-9_-]+\\.log");
    }

    private boolean isSuccessfullyIndexed(Path path) {
        if (!tableExists("analytics.log_file_index")) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from analytics.log_file_index
                where file_path = :filePath
                  and status in ('ARCHIVED', 'CURRENT', 'ARCHIVE_INDEX_ONLY')
            """,
            Map.of("filePath", path.toAbsolutePath().normalize().toString()),
            Long.class
        );
        return count != null && count > 0;
    }

    private void markFileDeleted(Path path) {
        if (!tableExists("analytics.log_file_index")) {
            return;
        }
        jdbcTemplate.update(
            """
                update analytics.log_file_index
                set status = 'DELETED', indexed_at = now()
                where file_path = :filePath
            """,
            Map.of("filePath", path.toAbsolutePath().normalize().toString())
        );
    }

    private int deleteExpiredIndexRows(int indexRetentionDays, boolean deleteRows, int batchSize) {
        if (!tableExists("analytics.log_file_index")) {
            return 0;
        }
        if (!deleteRows) {
            Long count = jdbcTemplate.queryForObject(
                "select count(*) from analytics.log_file_index where indexed_at < now() - (:days * interval '1 day')",
                Map.of("days", indexRetentionDays),
                Long.class
            );
            return count == null ? 0 : Math.toIntExact(Math.min(count, batchSize));
        }
        return jdbcTemplate.update(
            """
                delete from analytics.log_file_index
                where id in (
                    select id
                    from analytics.log_file_index
                    where indexed_at < now() - (:days * interval '1 day')
                    order by indexed_at
                    limit :batchSize
                )
            """,
            new MapSqlParameterSource()
                .addValue("days", indexRetentionDays)
                .addValue("batchSize", batchSize)
        );
    }

    private long queryLong(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Map.of(), Long.class);
        return value == null ? 0L : value;
    }

    private String readLastIndexError() {
        if (!tableExists("analytics.log_file_index")) {
            return null;
        }
        List<String> rows = jdbcTemplate.query(
            """
                select file_name || ': ' || coalesce(checksum, status) as error_text
                from analytics.log_file_index
                where status in ('INDEX_ERROR', 'SKIPPED_TOO_LARGE')
                order by indexed_at desc nulls last
                limit 1
            """,
            Map.of(),
            (rs, rowNum) -> rs.getString("error_text")
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private java.util.Optional<LocalDate> archiveFileDate(Path path) {
        if (path == null || path.getFileName() == null) {
            return java.util.Optional.empty();
        }
        Matcher matcher = ARCHIVE_FILE_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(LocalDate.parse(matcher.group("date")));
        } catch (RuntimeException ex) {
            return java.util.Optional.empty();
        }
    }

    private LogFileCounts countFilesOnDisk() {
        List<Path> files = discoverCandidateFiles();
        long current = files.stream().filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".log")).count();
        long archives = files.stream().filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".log.gz")).count();
        return new LogFileCounts(current, archives);
    }

    private BufferedReader newLogReader(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz")) {
            input = new GZIPInputStream(new BufferedInputStream(input));
        }
        return new BufferedReader(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
    }

    private EventLogEntryDto toDto(Instant timestamp, Matcher matcher, String filePath, long lineNo) {
        String level = trim(matcher.group("level"));
        String message = matcher.group("msg");
        String status = normalizeLevel(level, message);
        Integer duration = parseDuration(message);
        return new EventLogEntryDto(
            timestamp,
            level,
            status,
            inferLayer(matcher.group("logger"), message),
            shortLogger(matcher.group("logger")),
            null,
            duration,
            message,
            message,
            trim(matcher.group("logger")),
            trim(matcher.group("trace")),
            trim(matcher.group("event")),
            trim(matcher.group("module"))
        );
    }

    private boolean isArchiveReadable(IndexedTraceLookup lookup) {
        if (lookup == null || lookup.filePath() == null) {
            return false;
        }
        Path path = Paths.get(lookup.filePath()).normalize();
        return isAllowedPath(path) && Files.isRegularFile(path);
    }

    private boolean isAllowedPath(Path path) {
        Path allowed = Paths.get(runtimeSettingsService.getText(
            AnalyticsRuntimeSettingsService.KEY_LOG_ARCHIVE_ALLOWED_DIRECTORY,
            "logs"
        )).toAbsolutePath().normalize();
        Path absolute = path.toAbsolutePath().normalize();
        return absolute.startsWith(allowed);
    }

    private String resolveModuleCode(Path path) {
        String fileName = path.getFileName().toString();
        String base = fileName;
        if (base.endsWith(".log.gz")) {
            base = base.substring(0, base.length() - ".log.gz".length());
        } else if (base.endsWith(".log")) {
            base = base.substring(0, base.length() - ".log".length());
        }
        int dateIdx = base.indexOf('.');
        if (dateIdx > 0) {
            base = base.substring(0, dateIdx);
        }
        String normalized = normalizeModule(base);
        if (normalized == null || "GQW".equals(normalized)) {
            return "DEFAULT";
        }
        return normalized;
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(OutputStreamDiscard.INSTANCE);
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            return Files.size(path) + ":" + safeLastModified(path).toEpochMilli();
        }
    }

    private static Instant parseTimestamp(String raw) {
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(raw, LOG_TS_FORMATTER);
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String normalizeLevel(String level, String message) {
        String safeMessage = message == null ? "" : message;
        if (safeMessage.startsWith("SLOW ")) {
            return "WARN";
        }
        String normalized = level == null ? "" : level.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ERROR" -> "ERROR";
            case "WARN" -> "WARN";
            case "DEBUG" -> "DEBUG";
            case "TRACE" -> "DETAIL";
            default -> "INFO";
        };
    }

    private static String inferLayer(String logger, String message) {
        String safeMessage = message == null ? "" : message;
        if (safeMessage.startsWith("HTTP ") || safeMessage.startsWith("SLOW HTTP ")) {
            return "HTTP";
        }
        String lower = logger == null ? "" : logger.toLowerCase(Locale.ROOT);
        if (lower.contains(".controller.")) {
            return "CONTROLLER";
        }
        if (lower.contains(".service.")) {
            return "SERVICE";
        }
        if (lower.contains(".repository.")) {
            return "DATABASE";
        }
        return "UNKNOWN";
    }

    private static Integer parseDuration(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?:duration|durationMs)=?(\\d+)|\\((\\d+) ms\\)").matcher(message);
        if (!matcher.find()) {
            return null;
        }
        String raw = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String shortLogger(String logger) {
        if (logger == null || logger.isBlank()) {
            return "-";
        }
        String text = logger.trim();
        int idx = text.lastIndexOf('.');
        return idx >= 0 && idx + 1 < text.length() ? text.substring(idx + 1) : text;
    }

    private static Set<String> parseIncludeLevels(String raw) {
        Set<String> result = new LinkedHashSet<>();
        for (String item : String.valueOf(raw == null ? "" : raw).split(",")) {
            String normalized = item.trim().toUpperCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        if (result.isEmpty()) {
            result.add("WARN");
            result.add("ERROR");
            result.add("SLOW");
        }
        return result;
    }

    private static String normalizeModule(String moduleCode) {
        String normalized = trim(moduleCode);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String trim(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }

    private static String abbreviate(String text, int maxLength) {
        String value = text == null ? "" : text.trim();
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return 0L;
        }
    }

    private static Instant safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ex) {
            return Instant.EPOCH;
        }
    }

    private boolean tableExists(String regclass) {
        Boolean exists = jdbcTemplate.queryForObject(
            "select to_regclass(:regclass) is not null",
            Map.of("regclass", regclass),
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    public record LogIndexRunResult(
        int discoveredFiles,
        int indexedFiles,
        int skippedFiles,
        int errorFiles,
        List<String> notes
    ) {
    }

    public record LogIndexDiagnostics(
        long currentFiles,
        long archiveFiles,
        long indexedFiles,
        long pendingFiles,
        long traceLinks,
        long missingFiles,
        long skippedTooLargeFiles,
        long indexErrorFiles,
        long excerptRows,
        Instant lastIndexedAt,
        String status,
        String lastError,
        long cleanupCandidates
    ) {
    }

    public record LogRetentionCleanupResult(
        boolean enabled,
        boolean safeMode,
        int currentRetentionDays,
        int archiveRetentionDays,
        int indexRetentionDays,
        int filesFound,
        int candidates,
        int deletedFiles,
        int skippedActive,
        int skippedNotIndexed,
        int deletedIndexRows,
        long durationMs,
        List<String> notes
    ) {
    }

    public record IndexedTraceLookup(
        String status,
        Long fileId,
        String traceId,
        String eventId,
        String moduleCode,
        String fileName,
        String filePath,
        boolean compressed,
        Instant fromTs,
        Instant toTs,
        long lineCount,
        long errorCount,
        long warnCount,
        String summary,
        String fileStatus,
        List<EventLogExcerptDto> excerpts
    ) {
        static IndexedTraceLookup notFound() {
            return new IndexedTraceLookup("NOT_FOUND", null, null, null, null, null, null, false, null, null, 0, 0, 0, null, null, List.of());
        }

        IndexedTraceLookup withStatusAndExcerpts(String nextStatus, List<EventLogExcerptDto> nextExcerpts) {
            return new IndexedTraceLookup(
                nextStatus,
                fileId,
                traceId,
                eventId,
                moduleCode,
                fileName,
                filePath,
                compressed,
                fromTs,
                toTs,
                lineCount,
                errorCount,
                warnCount,
                summary,
                fileStatus,
                nextExcerpts == null ? List.of() : nextExcerpts
            );
        }
    }

    private record FileScan(
        Instant fromTs,
        Instant toTs,
        long lineCount,
        long errorCount,
        long warnCount,
        Map<String, TraceScan> traces
    ) {
    }

    private static final class TraceScan {
        private final String traceId;
        private final String eventId;
        private final String moduleCode;
        private final Set<String> sources = new LinkedHashSet<>();
        private final List<ProblemExcerpt> excerpts = new ArrayList<>();
        private Instant firstTs;
        private Instant lastTs;
        private long lineCount;
        private long errorCount;
        private long warnCount;

        private TraceScan(String traceId, String eventId, String moduleCode) {
            this.traceId = traceId;
            this.eventId = eventId;
            this.moduleCode = moduleCode;
        }

        void addLine(Instant timestamp, String level, String source) {
            lineCount++;
            if (timestamp != null) {
                firstTs = firstTs == null || timestamp.isBefore(firstTs) ? timestamp : firstTs;
                lastTs = lastTs == null || timestamp.isAfter(lastTs) ? timestamp : lastTs;
            }
            if ("ERROR".equals(level)) {
                errorCount++;
            }
            if ("WARN".equals(level)) {
                warnCount++;
            }
            if (source != null && !source.isBlank() && sources.size() < 8) {
                sources.add(source);
            }
        }

        String summary() {
            return "lines=" + lineCount + ", warn=" + warnCount + ", error=" + errorCount;
        }

        String traceId() { return traceId; }
        String eventId() { return eventId; }
        String moduleCode() { return moduleCode; }
        Set<String> sources() { return sources; }
        List<ProblemExcerpt> excerpts() { return excerpts; }
        Instant firstTs() { return firstTs; }
        Instant lastTs() { return lastTs; }
        long lineCount() { return lineCount; }
        long errorCount() { return errorCount; }
        long warnCount() { return warnCount; }
    }

    private record ProblemExcerpt(
        Instant timestamp,
        String level,
        String source,
        String messageShort,
        String excerpt,
        long lineNumber
    ) {
    }

    private record LogFileCounts(long currentFiles, long archiveFiles) {
    }

    private record RetentionCandidates(int filesFound, int candidates) {
    }

    private static final class OutputStreamDiscard extends java.io.OutputStream {
        private static final OutputStreamDiscard INSTANCE = new OutputStreamDiscard();
        @Override
        public void write(int b) {
            // discard
        }
    }
}
