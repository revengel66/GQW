package com.example.gqw.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.gqw.analytics.entity.MetricValueKind;
import com.example.gqw.analytics.repository.EventAttributeTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ShopAnalyticsDictionaryConfigTest {

    @Test
    void seedsProductSlugAsShopAttribute() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EventAttributeTypeRepository repository = mock(EventAttributeTypeRepository.class);

        new ShopAnalyticsDictionaryConfig(jdbcTemplate, repository).run();

        verify(repository).upsert(
            "PRODUCT_SLUG",
            "Slug \u0442\u043E\u0432\u0430\u0440\u0430",
            "Slug \u0442\u043E\u0432\u0430\u0440\u0430.",
            MetricValueKind.TEXT.name(),
            null,
            false,
            true
        );
    }
}
