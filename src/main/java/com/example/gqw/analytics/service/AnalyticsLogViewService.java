package com.example.gqw.analytics.service;

import com.example.gqw.analytics.web.dto.AnalyticsApiDto.EventLogEntryDto;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsLogViewService {

    private static final DateTimeFormatter LOG_TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
        "^(?<ts>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+"
            + "(?<level>\\w+)\\s+\\[trace:(?<trace>[^\\]]*)\\]\\s+\\[event:(?<event>[^\\]]*)\\]\\s+"
            + "\\[module:(?<module>[^\\]]*)\\]\\s+---\\s+\\[(?<thread>[^\\]]*)\\]\\s+"
            + "(?<logger>[^:]+)\\s+:\\s(?<msg>.*)$"
    );

    private static final Pattern METHOD_START_PATTERN = Pattern.compile(
        "^Начато выполнение метода (?<class>[A-Za-z0-9_$]+)\\.(?<method>[A-Za-z0-9_$]+) "
            + "\\(операция='(?<op>[^']*)', слой=(?<layer>[A-Z_]+),.*$"
    );
    private static final Pattern METHOD_OK_PATTERN = Pattern.compile(
        "^Метод (?<class>[A-Za-z0-9_$]+)\\.(?<method>[A-Za-z0-9_$]+) выполнен успешно: "
            + "операция='(?<op>[^']*)', слой=(?<layer>[A-Z_]+), длительность=(?<dur>\\d+) мс.*$"
    );
    private static final Pattern BUSINESS_ERROR_PATTERN = Pattern.compile(
        "^Бизнес-ошибка в (?<class>[A-Za-z0-9_$]+)\\.(?<method>[A-Za-z0-9_$]+): "
            + "операция='(?<op>[^']*)', слой=(?<layer>[A-Z_]+), длительность=(?<dur>\\d+) мс.*$"
    );
    private static final Pattern TECH_ERROR_PATTERN = Pattern.compile(
        "^Техническая ошибка в (?<class>[A-Za-z0-9_$]+)\\.(?<method>[A-Za-z0-9_$]+): "
            + "операция='(?<op>[^']*)', слой=(?<layer>[A-Z_]+), длительность=(?<dur>\\d+) мс.*$"
    );
    private static final Pattern DETAILS_PATTERN = Pattern.compile(
        "^Детализация вызова (?<class>[A-Za-z0-9_$]+)\\.(?<method>[A-Za-z0-9_$]+): слой=(?<layer>[A-Z_]+),.*$"
    );
    private static final Pattern DB_STAGE_START_PATTERN = Pattern.compile(
        "^DB_STAGE_START stageId=(?<stageId>\\d+) method=(?<class>[A-Za-z0-9_$]+)\\.(?<method>[A-Za-z0-9_$]+).*"
    );
    private static final Pattern DB_STAGE_END_PATTERN = Pattern.compile(
        "^DB_STAGE_END stageId=(?<stageId>\\d+) method=(?<class>[A-Za-z0-9_$]+)\\.(?<method>[A-Za-z0-9_$]+) durationMs=(?<dur>\\d+).*"
    );
    private static final Pattern DB_STAGE_ERROR_PATTERN = Pattern.compile(
        "^DB_STAGE_ERROR stageId=(?<stageId>\\d+) method=(?<class>[A-Za-z0-9_$]+)\\.(?<method>[A-Za-z0-9_$]+) durationMs=(?<dur>\\d+).*"
    );
    private static final Pattern DB_CALL_OK_PATTERN = Pattern.compile(
        "^DB-вызов (?<class>[A-Za-z0-9_$]+)\\.(?<method>[A-Za-z0-9_$]+) выполнен успешно: .*длительность=(?<dur>\\d+) мс.*$"
    );
    private static final Pattern DB_CALL_ERROR_PATTERN = Pattern.compile(
        "^DB-вызов (?<class>[A-Za-z0-9_$]+)\\.(?<method>[A-Za-z0-9_$]+) завершился ошибкой: .*длительность=(?<dur>\\d+) мс.*$"
    );
    private static final Pattern HTTP_PATTERN = Pattern.compile("^HTTP (?<method>[A-Z]+) (?<path>\\S+) -> (?<code>\\d+) \\((?<dur>\\d+) ms\\)$");
    private static final Pattern SLOW_HTTP_PATTERN = Pattern.compile("^SLOW HTTP (?<method>[A-Z]+) (?<path>\\S+) -> (?<code>\\d+) \\((?<dur>\\d+) ms\\)$");

    @Value("${logging.file.name:logs/gqw.log}")
    private String appLogFilePath;

    @Value("${LOG_MODULE_DIR:logs/analytics/modules}")
    private String moduleLogDir;

    public List<EventLogEntryDto> loadTraceLogs(
        String traceId,
        String eventUid,
        String moduleCode,
        Instant eventStartedAt,
        Instant eventEndedAt
    ) {
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        Instant from = eventStartedAt == null ? null : eventStartedAt.minusSeconds(180);
        Instant to = eventEndedAt == null ? null : eventEndedAt.plusSeconds(240);

        String traceToken = "[trace:" + traceId + "]";
        Set<Path> candidates = resolveCandidateLogFiles(moduleCode, eventUid);
        List<ParsedLogLine> parsed = new ArrayList<>();
        for (Path path : candidates) {
            parsed.addAll(readTraceLinesFromFile(path, traceId, eventUid, traceToken, from, to));
        }
        if (parsed.isEmpty() && eventUid != null && !eventUid.isBlank()) {
            for (Path path : candidates) {
                parsed.addAll(readTraceLinesFromFile(path, traceId, null, traceToken, from, to));
            }
        }
        if (parsed.isEmpty()) {
            return List.of();
        }
        parsed.sort(Comparator
            .comparing(ParsedLogLine::timestamp)
            .thenComparing(ParsedLogLine::sourceFile)
            .thenComparingLong(ParsedLogLine::lineNo));

        Map<String, EventLogEntryDto> unique = new LinkedHashMap<>();
        for (ParsedLogLine line : parsed) {
            EventLogEntryDto dto = line.toDto();
            if ("SKIP".equalsIgnoreCase(dto.status())) {
                continue;
            }
            String key = dto.timestamp() + "|" + dto.level() + "|" + dto.logger() + "|" + dto.rawMessage();
            unique.putIfAbsent(key, dto);
        }
        return List.copyOf(unique.values());
    }

    private Set<Path> resolveCandidateLogFiles(String moduleCode, String eventUid) {
        Set<Path> files = new LinkedHashSet<>();
        files.add(Paths.get(appLogFilePath));
        files.add(Paths.get(moduleLogDir, "DEFAULT.log"));
        String normalizedModule = moduleCode == null ? null : moduleCode.trim().toUpperCase(Locale.ROOT);
        boolean strictEventMode = eventUid != null && !eventUid.isBlank();
        if (normalizedModule != null && !normalizedModule.isBlank()) {
            files.add(Paths.get(moduleLogDir, normalizedModule + ".log"));
        }
        if (strictEventMode && normalizedModule != null && !normalizedModule.isBlank() && !"DEFAULT".equals(normalizedModule)) {
            return files;
        }
        return files;
    }

    private List<ParsedLogLine> readTraceLinesFromFile(
        Path path,
        String expectedTraceId,
        String expectedEventUid,
        String traceToken,
        Instant from,
        Instant to
    ) {
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
            return List.of();
        }
        List<ParsedLogLine> lines = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            long lineNo = 0L;
            ParsedLogLine current = null;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                Matcher matcher = LOG_LINE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    if (current != null) {
                        lines.add(current);
                        current = null;
                    }
                    if (!line.contains(traceToken)) {
                        continue;
                    }
                    String traceId = trim(matcher.group("trace"));
                    if (!Objects.equals(expectedTraceId, traceId)) {
                        continue;
                    }
                    String eventUid = trim(matcher.group("event"));
                    if (expectedEventUid != null && !expectedEventUid.isBlank()) {
                        boolean exactEventMatch = Objects.equals(expectedEventUid, eventUid);
                        boolean fallbackHttpCandidate = (eventUid == null || eventUid.isBlank())
                            && isHttpFallbackCandidate(trim(matcher.group("logger")), matcher.group("msg"));
                        if (!exactEventMatch && !fallbackHttpCandidate) {
                            continue;
                        }
                    }
                    Instant timestamp = parseTimestamp(matcher.group("ts"));
                    if (timestamp == null) {
                        continue;
                    }
                    if (from != null && timestamp.isBefore(from)) {
                        continue;
                    }
                    if (to != null && timestamp.isAfter(to)) {
                        continue;
                    }
                    current = ParsedLogLine.fromMatcher(path.toString(), lineNo, timestamp, matcher);
                    continue;
                }
                if (current != null && !line.isBlank()) {
                    current.appendContinuation(line);
                }
            }
            if (current != null) {
                lines.add(current);
            }
        } catch (IOException ignored) {
            return List.of();
        }
        return lines;
    }

    private static Instant parseTimestamp(String raw) {
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(raw, LOG_TS_FORMATTER);
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String trim(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }

    private static String shortLogger(String logger) {
        if (logger == null || logger.isBlank()) {
            return "-";
        }
        String text = logger.trim();
        int idx = text.lastIndexOf('.');
        return idx >= 0 && idx + 1 < text.length() ? text.substring(idx + 1) : text;
    }

    private static ParsedNormalization normalizeMessage(String message, String level, String logger) {
        String safeMessage = message == null ? "" : message;
        Matcher matcher = METHOD_START_PATTERN.matcher(safeMessage);
        if (matcher.matches()) {
            return new ParsedNormalization(
                "START",
                matcher.group("layer"),
                matcher.group("class") + "." + matcher.group("method"),
                matcher.group("op"),
                null,
                "Старт метода " + matcher.group("class") + "." + matcher.group("method")
            );
        }
        matcher = METHOD_OK_PATTERN.matcher(safeMessage);
        if (matcher.matches()) {
            return new ParsedNormalization(
                "OK",
                matcher.group("layer"),
                matcher.group("class") + "." + matcher.group("method"),
                matcher.group("op"),
                parseInt(matcher.group("dur")),
                "Метод выполнен успешно"
            );
        }
        matcher = BUSINESS_ERROR_PATTERN.matcher(safeMessage);
        if (matcher.matches()) {
            return new ParsedNormalization(
                "ERROR",
                matcher.group("layer"),
                matcher.group("class") + "." + matcher.group("method"),
                matcher.group("op"),
                parseInt(matcher.group("dur")),
                "Бизнес-ошибка метода"
            );
        }
        matcher = TECH_ERROR_PATTERN.matcher(safeMessage);
        if (matcher.matches()) {
            return new ParsedNormalization(
                "ERROR",
                matcher.group("layer"),
                matcher.group("class") + "." + matcher.group("method"),
                matcher.group("op"),
                parseInt(matcher.group("dur")),
                "Техническая ошибка метода"
            );
        }
        matcher = DETAILS_PATTERN.matcher(safeMessage);
        if (matcher.matches()) {
            return new ParsedNormalization(
                "DETAIL",
                matcher.group("layer"),
                matcher.group("class") + "." + matcher.group("method"),
                null,
                null,
                "Детализация вызова"
            );
        }
        matcher = DB_STAGE_START_PATTERN.matcher(safeMessage);
        if (matcher.matches()) {
            return new ParsedNormalization(
                "START",
                "DATABASE",
                matcher.group("class") + "." + matcher.group("method"),
                "операция доступа к данным",
                null,
                "Старт DB-вызова"
            );
        }
        matcher = DB_STAGE_END_PATTERN.matcher(safeMessage);
        if (matcher.matches()) {
            return new ParsedNormalization(
                "OK",
                "DATABASE",
                matcher.group("class") + "." + matcher.group("method"),
                "операция доступа к данным",
                parseInt(matcher.group("dur")),
                "DB-вызов выполнен"
            );
        }
        matcher = DB_STAGE_ERROR_PATTERN.matcher(safeMessage);
        if (matcher.matches()) {
            return new ParsedNormalization(
                "ERROR",
                "DATABASE",
                matcher.group("class") + "." + matcher.group("method"),
                "операция доступа к данным",
                parseInt(matcher.group("dur")),
                "DB-вызов с ошибкой"
            );
        }
        matcher = DB_CALL_OK_PATTERN.matcher(safeMessage);
        if (matcher.matches()) {
            return new ParsedNormalization(
                "SKIP",
                "DATABASE",
                matcher.group("class") + "." + matcher.group("method"),
                "операция доступа к данным",
                parseInt(matcher.group("dur")),
                "DB-вызов выполнен (дубликат)"
            );
        }
        matcher = DB_CALL_ERROR_PATTERN.matcher(safeMessage);
        if (matcher.matches()) {
            return new ParsedNormalization(
                "SKIP",
                "DATABASE",
                matcher.group("class") + "." + matcher.group("method"),
                "операция доступа к данным",
                parseInt(matcher.group("dur")),
                "DB-вызов с ошибкой (дубликат)"
            );
        }
        matcher = HTTP_PATTERN.matcher(safeMessage);
        if (matcher.matches()) {
            return new ParsedNormalization(
                classifyHttpStatus(matcher.group("code"), level),
                "HTTP",
                "HTTP " + matcher.group("method"),
                null,
                parseInt(matcher.group("dur")),
                "HTTP " + matcher.group("method") + " " + matcher.group("path") + " -> " + matcher.group("code")
            );
        }
        matcher = SLOW_HTTP_PATTERN.matcher(safeMessage);
        if (matcher.matches()) {
            return new ParsedNormalization(
                "WARN",
                "HTTP",
                "HTTP " + matcher.group("method"),
                null,
                parseInt(matcher.group("dur")),
                "SLOW HTTP " + matcher.group("method") + " " + matcher.group("path") + " -> " + matcher.group("code")
            );
        }

        return new ParsedNormalization(
            normalizeByLevel(level),
            inferLayerFromLogger(logger),
            shortLogger(logger),
            null,
            null,
            safeMessage
        );
    }

    private static String classifyHttpStatus(String code, String level) {
        Integer status = parseInt(code);
        if (status == null) {
            return normalizeByLevel(level);
        }
        if (status >= 500) {
            return "ERROR";
        }
        if (status >= 400) {
            return "WARN";
        }
        return "OK";
    }

    private static String normalizeByLevel(String level) {
        String normalized = level == null ? "" : level.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ERROR" -> "ERROR";
            case "WARN" -> "WARN";
            case "TRACE" -> "DETAIL";
            case "DEBUG" -> "DEBUG";
            default -> "INFO";
        };
    }

    private static String inferLayerFromLogger(String logger) {
        if (logger == null) {
            return "UNKNOWN";
        }
        String lower = logger.toLowerCase(Locale.ROOT);
        if (lower.contains(".controller.")) {
            return "CONTROLLER";
        }
        if (lower.contains(".service.")) {
            return "SERVICE";
        }
        if (lower.contains(".repository.")) {
            return "DATABASE";
        }
        if (lower.contains("traceidfilter")) {
            return "HTTP";
        }
        return "UNKNOWN";
    }

    private static boolean isHttpFallbackCandidate(String logger, String message) {
        String safeLogger = logger == null ? "" : logger.toLowerCase(Locale.ROOT);
        String safeMessage = message == null ? "" : message;
        if (!safeLogger.contains("traceidfilter")) {
            return false;
        }
        return safeMessage.startsWith("HTTP ") || safeMessage.startsWith("SLOW HTTP ");
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ParsedNormalization(
        String status,
        String layer,
        String source,
        String operation,
        Integer durationMs,
        String message
    ) {
    }

    private static final class ParsedLogLine {
        private final String sourceFile;
        private final long lineNo;
        private final Instant timestamp;
        private final String level;
        private final String traceId;
        private final String eventUid;
        private final String moduleCode;
        private final String logger;
        private String rawMessage;
        private ParsedNormalization normalization;

        private ParsedLogLine(
            String sourceFile,
            long lineNo,
            Instant timestamp,
            String level,
            String traceId,
            String eventUid,
            String moduleCode,
            String logger,
            String rawMessage
        ) {
            this.sourceFile = sourceFile;
            this.lineNo = lineNo;
            this.timestamp = timestamp;
            this.level = level;
            this.traceId = traceId;
            this.eventUid = eventUid;
            this.moduleCode = moduleCode;
            this.logger = logger;
            this.rawMessage = rawMessage;
            this.normalization = normalizeMessage(rawMessage, level, logger);
        }

        static ParsedLogLine fromMatcher(String sourceFile, long lineNo, Instant timestamp, Matcher matcher) {
            return new ParsedLogLine(
                sourceFile,
                lineNo,
                timestamp,
                trim(matcher.group("level")),
                trim(matcher.group("trace")),
                trim(matcher.group("event")),
                trim(matcher.group("module")),
                trim(matcher.group("logger")),
                matcher.group("msg")
            );
        }

        void appendContinuation(String line) {
            rawMessage = rawMessage + "\n" + line;
            normalization = normalizeMessage(rawMessage, level, logger);
        }

        String sourceFile() {
            return sourceFile;
        }

        long lineNo() {
            return lineNo;
        }

        Instant timestamp() {
            return timestamp;
        }

        EventLogEntryDto toDto() {
            return new EventLogEntryDto(
                timestamp,
                level,
                normalization.status(),
                normalization.layer(),
                normalization.source(),
                normalization.operation(),
                normalization.durationMs(),
                normalization.message(),
                rawMessage,
                logger,
                traceId,
                eventUid,
                moduleCode
            );
        }
    }
}
