package com.example.gqw.analytics.config;

import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.repository.EventAttributeTypeRepository;
import com.example.gqw.analytics.repository.EventTypeRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnProperty(
    value = "app.analytics.strict-warning.dictionary-seed-enabled",
    havingValue = "true",
    matchIfMissing = true
)
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsStrictWarningDictionaryConfig {

    public static final String STRICT_WARNING_EVENT_CODE = "ANALYTICS_STRICT_WARNING";
    public static final String ATTR_WARNING_TYPE = "ANALYTICS_WARNING_TYPE";
    public static final String ATTR_WARNING_CODE = "ANALYTICS_WARNING_CODE";
    public static final String ATTR_WARNING_REASON = "ANALYTICS_WARNING_REASON";
    public static final String ATTR_WARNING_SOURCE = "ANALYTICS_WARNING_SOURCE";
    public static final String ATTR_WARNING_CONTEXT = "ANALYTICS_WARNING_CONTEXT";
    public static final String ATTR_WARNING_EVENT_UID = "ANALYTICS_WARNING_EVENT_UID";
    public static final String ATTR_WARNING_STAGE_ID = "ANALYTICS_WARNING_STAGE_ID";

    @Bean
    @Order(415)
    CommandLineRunner seedAnalyticsStrictWarningDictionary(
        EventTypeRepository eventTypeRepository,
        EventAttributeTypeRepository attributeTypeRepository
    ) {
        return args -> {
            eventTypeRepository.upsert(
                STRICT_WARNING_EVENT_CODE,
                "Предупреждение строгой модели",
                "Диагностическое событие Analytics: неизвестный, отключённый или некорректно настроенный код справочника.",
                EventType.DEFAULT_MODULE_CODE,
                true,
                true
            );
            for (AttrSeed seed : attributeSeeds()) {
                attributeTypeRepository.upsert(
                    seed.code(),
                    seed.name(),
                    seed.description(),
                    MetricValueKind.TEXT.name(),
                    null,
                    true,
                    true
                );
            }
        };
    }

    private static List<AttrSeed> attributeSeeds() {
        return List.of(
            new AttrSeed(ATTR_WARNING_TYPE, "Тип предупреждения", "Какая часть аналитики пропустила запись: событие, модуль, этап, атрибут или метрика."),
            new AttrSeed(ATTR_WARNING_CODE, "Код", "Код справочника, который не удалось использовать."),
            new AttrSeed(ATTR_WARNING_REASON, "Причина", "Почему запись была пропущена."),
            new AttrSeed(ATTR_WARNING_SOURCE, "Источник", "Класс и метод приложения, где возникло предупреждение."),
            new AttrSeed(ATTR_WARNING_CONTEXT, "Контекст", "Дополнительные признаки запроса или операции."),
            new AttrSeed(ATTR_WARNING_EVENT_UID, "UID события", "Идентификатор пользовательского события, внутри которого возникло предупреждение."),
            new AttrSeed(ATTR_WARNING_STAGE_ID, "ID этапа", "Идентификатор этапа, если предупреждение относится к метрике или этапу.")
        );
    }

    private record AttrSeed(String code, String name, String description) {
    }
}
