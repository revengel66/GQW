package com.example.gqw.analytics.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.gqw.analytics.entity.EventType;
import com.example.gqw.analytics.repository.EventTypeRepository;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = {
    AnalyticsPersistenceConfig.class,
    AnalyticsSeparateDataSourceTest.BusinessDataSourceConfig.class
})
@TestPropertySource(properties = {
    "app.analytics.datasource.enabled=true",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.analytics.datasource.url=jdbc:h2:mem:analytics-ds-test;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS analytics",
    "app.analytics.datasource.username=sa",
    "app.analytics.datasource.password=",
    "app.analytics.datasource.driver-class-name=org.h2.Driver"
})
class AnalyticsSeparateDataSourceTest {

    private final DataSource businessDataSource;
    private final DataSource analyticsDataSource;
    private final JdbcTemplate businessJdbcTemplate;
    private final JdbcTemplate analyticsJdbcTemplate;
    private final EventTypeRepository eventTypeRepository;

    @Autowired
    AnalyticsSeparateDataSourceTest(
        @Qualifier("dataSource") DataSource businessDataSource,
        @Qualifier("analyticsDataSource") DataSource analyticsDataSource,
        @Qualifier("analyticsJdbcTemplate") JdbcTemplate analyticsJdbcTemplate,
        EventTypeRepository eventTypeRepository
    ) {
        this.businessDataSource = businessDataSource;
        this.analyticsDataSource = analyticsDataSource;
        this.businessJdbcTemplate = new JdbcTemplate(businessDataSource);
        this.analyticsJdbcTemplate = analyticsJdbcTemplate;
        this.eventTypeRepository = eventTypeRepository;
    }

    @Test
    void analyticsRepositoryUsesAnalyticsDataSource() {
        assertThat(analyticsDataSource).isNotSameAs(businessDataSource);

        EventType type = new EventType();
        type.setCode("SEPARATE_DS_TEST");
        type.setName("Separate datasource test");
        type.setDescription("Test event type");
        type.setModuleCode(EventType.DEFAULT_MODULE_CODE);
        type.setIsSystem(false);
        type.setIsActive(true);

        eventTypeRepository.saveAndFlush(type);

        Long analyticsCount = analyticsJdbcTemplate.queryForObject(
            "select count(*) from analytics.event_type where code = 'SEPARATE_DS_TEST'",
            Long.class
        );
        assertThat(analyticsCount).isEqualTo(1L);

        Long businessTableCount = businessJdbcTemplate.queryForObject(
            """
                select count(*)
                from INFORMATION_SCHEMA.TABLES
                where lower(TABLE_SCHEMA) = 'analytics'
                  and lower(TABLE_NAME) = 'event_type'
                """,
            Long.class
        );
        assertThat(businessTableCount).isZero();
    }

    @Configuration
    static class BusinessDataSourceConfig {

        @Bean(name = "dataSource")
        @Primary
        DataSource dataSource() {
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(
                "jdbc:h2:mem:business-ds-test;MODE=PostgreSQL;DATABASE_TO_UPPER=false;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS shop"
            );
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setPoolName("businessDataSource");
            return dataSource;
        }
    }
}
