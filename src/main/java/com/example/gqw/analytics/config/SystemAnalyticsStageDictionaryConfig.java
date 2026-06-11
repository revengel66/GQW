package com.example.gqw.analytics.config;

import com.example.gqw.analytics.repository.StageTypeRepository;
import com.example.gqw.analytics.entity.StageType;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnProperty(
    value = "app.analytics.system-stages-seed-enabled",
    havingValue = "true",
    matchIfMissing = true
)
@ConditionalOnProperty(value = "app.startup.runners-enabled", havingValue = "true", matchIfMissing = true)
public class SystemAnalyticsStageDictionaryConfig {

    @Bean
    @Order(405)
    CommandLineRunner seedSystemAnalyticsStages(StageTypeRepository repository) {
        return args -> {
            for (StageSeed seed : stageSeeds()) {
                repository.upsert(
                    seed.code(),
                    seed.name(),
                    seed.description(),
                    true,
                    true
                );
            }
            demoteOptionalCustomStage(repository, "PERSISTENCE");
        };
    }

    private static List<StageSeed> stageSeeds() {
        return List.of(
            new StageSeed("CONTROLLER", "Контроллер", "Обработка HTTP-запроса контроллером."),
            new StageSeed("SERVICE", "Сервис", "Бизнес-логика приложения."),
            new StageSeed("DATABASE", "База данных", "Операции чтения и записи через репозитории."),
            new StageSeed("FRONTEND", "Фронтенд", "Клиентский рендер и браузерные метрики.")
        );
    }

    private static void demoteOptionalCustomStage(StageTypeRepository repository, String code) {
        StageType stageType = repository.findById(code).orElse(null);
        if (stageType == null || !Boolean.TRUE.equals(stageType.getIsSystem())) {
            return;
        }
        stageType.setIsSystem(false);
        repository.save(stageType);
    }

    private record StageSeed(String code, String name, String description) {
    }
}
