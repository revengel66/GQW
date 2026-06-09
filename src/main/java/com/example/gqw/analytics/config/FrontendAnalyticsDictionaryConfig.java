package com.example.gqw.analytics.config;

import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.repository.StageTypeRepository;
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
    value = "app.analytics.frontend.dictionary-seed-enabled",
    havingValue = "true",
    matchIfMissing = true
)
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class FrontendAnalyticsDictionaryConfig {

    @Bean
    @Order(410)
    CommandLineRunner seedFrontendAnalyticsDictionary(
        StageTypeRepository stageTypeRepository,
        StageMetricTypeRepository stageMetricTypeRepository
    ) {
        return args -> {
            upsertStageTypes(stageTypeRepository);
            upsertMetricTypes(stageMetricTypeRepository);
        };
    }

    private void upsertStageTypes(StageTypeRepository repository) {
        if (repository.existsById("FRONTEND")) {
            return;
        }
        repository.upsert(
            "FRONTEND",
            "Фронтенд",
            "Клиентский рендер и браузерные метрики",
            true,
            true
        );
    }

    private void upsertMetricTypes(StageMetricTypeRepository repository) {
        List<MetricSeed> numeric = List.of(
            new MetricSeed("FRONTEND_TTFB_MS", "Время до первого байта (TTFB, мс)", "Время до первого байта на клиенте", SystemMetricReadingGuides.guideFor("FRONTEND_TTFB_MS"), MetricValueKind.NUMERIC, "ms"),
            new MetricSeed("FRONTEND_DOM_INTERACTIVE_MS", "Готовность DOM к взаимодействию", "Время до DOM Interactive", SystemMetricReadingGuides.guideFor("FRONTEND_DOM_INTERACTIVE_MS"), MetricValueKind.NUMERIC, "ms"),
            new MetricSeed("FRONTEND_DOM_CONTENT_LOADED_MS", "DOM загружен", "Время до DOMContentLoaded", SystemMetricReadingGuides.guideFor("FRONTEND_DOM_CONTENT_LOADED_MS"), MetricValueKind.NUMERIC, "ms"),
            new MetricSeed("FRONTEND_LOAD_EVENT_MS", "Завершение загрузки страницы", "Время до события load", SystemMetricReadingGuides.guideFor("FRONTEND_LOAD_EVENT_MS"), MetricValueKind.NUMERIC, "ms"),
            new MetricSeed("FRONTEND_TRANSFER_SIZE_BYTES", "Размер передачи (байт)", "Размер переданных данных навигации", SystemMetricReadingGuides.guideFor("FRONTEND_TRANSFER_SIZE_BYTES"), MetricValueKind.NUMERIC, "bytes"),
            new MetricSeed("FRONTEND_LCP_MS", "Отрисовка самого крупного элемента", "Largest Contentful Paint", SystemMetricReadingGuides.guideFor("FRONTEND_LCP_MS"), MetricValueKind.NUMERIC, "ms"),
            new MetricSeed("FRONTEND_INP_MS", "Задержка отклика интерфейса", "Interaction to Next Paint", SystemMetricReadingGuides.guideFor("FRONTEND_INP_MS"), MetricValueKind.NUMERIC, "ms"),
            new MetricSeed("FRONTEND_CLS_SCORE", "CLS", "Cumulative Layout Shift", SystemMetricReadingGuides.guideFor("FRONTEND_CLS_SCORE"), MetricValueKind.NUMERIC, "score"),
            new MetricSeed("FRONTEND_API_DURATION_MS", "Длительность API", "Длительность клиентского API-вызова", SystemMetricReadingGuides.guideFor("FRONTEND_API_DURATION_MS"), MetricValueKind.NUMERIC, "ms"),
            new MetricSeed("FRONTEND_RENDER_AFTER_API_MS", "Дорендер после API", "Длительность дорендера после ответа API", SystemMetricReadingGuides.guideFor("FRONTEND_RENDER_AFTER_API_MS"), MetricValueKind.NUMERIC, "ms"),
            new MetricSeed("FRONTEND_HTTP_STATUS", "HTTP-статус", "HTTP-статус клиентского запроса", SystemMetricReadingGuides.guideFor("FRONTEND_HTTP_STATUS"), MetricValueKind.NUMERIC, "code")
        );
        List<MetricSeed> text = List.of(
            new MetricSeed("FRONTEND_PAGE_URL", "URL страницы", "URL страницы на клиенте", SystemMetricReadingGuides.guideFor("FRONTEND_PAGE_URL"), MetricValueKind.TEXT, null),
            new MetricSeed("FRONTEND_NAV_TYPE", "Тип навигации", "Тип браузерной навигации", SystemMetricReadingGuides.guideFor("FRONTEND_NAV_TYPE"), MetricValueKind.TEXT, null),
            new MetricSeed("FRONTEND_API_URL", "Адрес API-запроса", "URL клиентского API-запроса", SystemMetricReadingGuides.guideFor("FRONTEND_API_URL"), MetricValueKind.TEXT, null),
            new MetricSeed("FRONTEND_API_METHOD", "HTTP-метод", "HTTP-метод клиентского API-запроса", SystemMetricReadingGuides.guideFor("FRONTEND_API_METHOD"), MetricValueKind.TEXT, null),
            new MetricSeed("FRONTEND_NETWORK_ERROR", "Сетевая ошибка", "Код/тип сетевой ошибки на клиенте", SystemMetricReadingGuides.guideFor("FRONTEND_NETWORK_ERROR"), MetricValueKind.TEXT, null),
            new MetricSeed("FRONTEND_ERROR_MESSAGE", "Текст ошибки", "Текст JavaScript ошибки", SystemMetricReadingGuides.guideFor("FRONTEND_ERROR_MESSAGE"), MetricValueKind.TEXT, null),
            new MetricSeed("FRONTEND_TRACE_ID", "Идентификатор трассировки", "Trace id, полученный клиентом из ответа backend", SystemMetricReadingGuides.guideFor("FRONTEND_TRACE_ID"), MetricValueKind.TEXT, null),
            new MetricSeed("FRONTEND_CUSTOM_ATTRS_JSON", "Пользовательские атрибуты", "JSON с data-analytics-атрибутами элемента", SystemMetricReadingGuides.guideFor("FRONTEND_CUSTOM_ATTRS_JSON"), MetricValueKind.TEXT, null)
        );
        for (MetricSeed seed : numeric) {
            upsertMetricTypeIfMissing(repository, seed);
        }
        for (MetricSeed seed : text) {
            upsertMetricTypeIfMissing(repository, seed);
        }
    }

    private void upsertMetricTypeIfMissing(StageMetricTypeRepository repository, MetricSeed seed) {
        if (repository.existsById(seed.code())) {
            return;
        }
        repository.upsert(
            seed.code(),
            seed.name(),
            seed.description(),
            seed.readingGuide(),
            seed.valueKind().name(),
            seed.unitDefault(),
            true,
            true
        );
    }

    private record MetricSeed(
        String code,
        String name,
        String description,
        String readingGuide,
        MetricValueKind valueKind,
        String unitDefault
    ) {
    }
}
