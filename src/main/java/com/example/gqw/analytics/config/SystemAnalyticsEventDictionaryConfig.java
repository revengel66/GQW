package com.example.gqw.analytics.config;

import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.repository.EventTypeRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnProperty(
    value = "app.analytics.system-events-seed-enabled",
    havingValue = "true",
    matchIfMissing = true
)
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class SystemAnalyticsEventDictionaryConfig {

    @Bean
    @Order(401)
    CommandLineRunner seedSystemAnalyticsEvents(EventTypeRepository repository) {
        return args -> {
            for (EventSeed seed : eventSeeds()) {
                repository.upsert(
                    seed.code(),
                    seed.name(),
                    seed.description(),
                    EventType.DEFAULT_MODULE_CODE,
                    true,
                    true
                );
            }
        };
    }

    private static List<EventSeed> eventSeeds() {
        return List.of(
            new EventSeed(
                "FRONTEND_PAGE_LOAD",
                "Загрузка страницы",
                "Системное frontend-событие загрузки страницы и базовых browser timing метрик."
            ),
            new EventSeed(
                "FRONTEND_WEB_VITALS",
                "Web Vitals",
                "Системное frontend-событие Core Web Vitals: LCP, INP, CLS и связанные показатели."
            ),
            new EventSeed(
                "FRONTEND_JS_ERROR",
                "JavaScript ошибка",
                "Системное frontend-событие ошибки JavaScript в браузере."
            ),
            new EventSeed(
                "FRONTEND_API_CALL",
                "Frontend API-вызов",
                "Системное frontend-событие клиентского API-вызова: URL, метод, статус и длительность."
            ),
            new EventSeed(
                "HTTP_REQUEST_ERROR",
                "HTTP request error",
                "Системное событие технической ошибки HTTP-запроса: статус ответа, путь и traceId."
            )
        );
    }

    private record EventSeed(String code, String name, String description) {
    }
}
