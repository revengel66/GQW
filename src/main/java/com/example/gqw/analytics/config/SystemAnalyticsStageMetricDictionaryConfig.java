package com.example.gqw.analytics.config;

import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.repository.StageMetricTypeRepository;
import com.example.gqw.analytics.support.SystemMetricReadingGuides;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnProperty(
    value = "app.analytics.system-stage-metrics-seed-enabled",
    havingValue = "true",
    matchIfMissing = true
)
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class SystemAnalyticsStageMetricDictionaryConfig {

    @Bean
    @Order(406)
    CommandLineRunner seedSystemAnalyticsStageMetrics(StageMetricTypeRepository repository) {
        return args -> {
            for (MetricSeed seed : metricSeeds()) {
                repository.upsert(
                    seed.code(),
                    seed.name(),
                    seed.description(),
                    SystemMetricReadingGuides.guideFor(seed.code()),
                    seed.valueKind().name(),
                    seed.unitDefault(),
                    true,
                    true
                );
            }
        };
    }

    private static List<MetricSeed> metricSeeds() {
        return List.of(
            new MetricSeed("DB_QUERY_COUNT", "Количество SQL-запросов", "Количество SQL-запросов.", MetricValueKind.NUMERIC, "count"),
            new MetricSeed("RESPONSE_SIZE_BYTES", "Полный размер ответа (байт)", "Размер HTTP-ответа.", MetricValueKind.NUMERIC, "bytes"),
            new MetricSeed("RETRY_COUNT", "Повторные попытки", "Количество повторных попыток.", MetricValueKind.NUMERIC, "count"),
            new MetricSeed("ERROR_CODE", "Код ошибки", "Код ошибки этапа.", MetricValueKind.TEXT, null),
            new MetricSeed("ERROR_CLASS", "Класс ошибки", "Класс ошибки: VALIDATION, BUSINESS, SYSTEM.", MetricValueKind.TEXT, null),
            new MetricSeed("ITEM_COUNT", "Количество элементов", "Количество элементов в операции.", MetricValueKind.NUMERIC, "count"),
            new MetricSeed("PAYLOAD_SIZE_BYTES", "Размер входных данных (байт)", "Размер входного payload.", MetricValueKind.NUMERIC, "bytes"),
            new MetricSeed("VALIDATION_ERROR_COUNT", "Ошибки валидации", "Количество ошибок валидации.", MetricValueKind.NUMERIC, "count")
        );
    }

    private record MetricSeed(
        String code,
        String name,
        String description,
        MetricValueKind valueKind,
        String unitDefault
    ) {
    }
}
