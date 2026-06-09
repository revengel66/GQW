package com.example.gqw.analytics.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableConfigurationProperties(AnalyticsDataSourceProperties.class)
public class AnalyticsPersistenceConfig {

    @Bean(name = "analyticsDataSource")
    @ConditionalOnProperty(value = "app.analytics.datasource.enabled", havingValue = "true")
    DataSource analyticsDataSource(AnalyticsDataSourceProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setPoolName("analyticsDataSource");
        return dataSource;
    }

    @Bean(name = "analyticsDataSource")
    @ConditionalOnProperty(value = "app.analytics.datasource.enabled", havingValue = "false", matchIfMissing = true)
    DataSource legacyAnalyticsDataSource(@Qualifier("dataSource") DataSource dataSource) {
        return dataSource;
    }

    @Bean(name = "analyticsJdbcTemplate")
    JdbcTemplate analyticsJdbcTemplate(@Qualifier("analyticsDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "analyticsNamedParameterJdbcTemplate")
    NamedParameterJdbcTemplate analyticsNamedParameterJdbcTemplate(
        @Qualifier("analyticsDataSource") DataSource dataSource
    ) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean(name = {"transactionManager", "analyticsTransactionManager"})
    @Primary
    @ConditionalOnProperty(value = "app.analytics.datasource.enabled", havingValue = "false", matchIfMissing = true)
    PlatformTransactionManager legacyAnalyticsTransactionManager(
        EntityManagerFactory entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    static Map<String, Object> jpaProperties(Environment environment) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(
            "hibernate.hbm2ddl.auto",
            environment.getProperty("spring.jpa.hibernate.ddl-auto", "update")
        );
        properties.put(
            "hibernate.hbm2ddl.create_namespaces",
            environment.getProperty("spring.jpa.properties.hibernate.hbm2ddl.create_namespaces", "true")
        );
        properties.put(
            "hibernate.jdbc.time_zone",
            environment.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone", "UTC")
        );
        properties.put(
            "hibernate.physical_naming_strategy",
            environment.getProperty(
                "spring.jpa.hibernate.naming.physical-strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"
            )
        );
        return properties;
    }

    static HibernateJpaVendorAdapter jpaVendorAdapter() {
        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setGenerateDdl(true);
        return adapter;
    }

    @Configuration
    @ConditionalOnProperty(value = "app.analytics.datasource.enabled", havingValue = "true")
    @EnableJpaRepositories(
        basePackages = "${app.analytics.host.repository-packages:com.example.gqw.repository}",
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager"
    )
    static class BusinessJpaConfig {

        @Bean(name = "entityManagerFactory")
        @Primary
        LocalContainerEntityManagerFactoryBean entityManagerFactory(
            @Qualifier("dataSource") DataSource dataSource,
            Environment environment
        ) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(environment.getProperty(
                "app.analytics.host.entity-packages",
                String[].class,
                new String[] {"com.example.gqw.entity"}
            ));
            factory.setJpaVendorAdapter(jpaVendorAdapter());
            factory.setJpaPropertyMap(jpaProperties(environment));
            factory.setPersistenceUnitName("business");
            return factory;
        }

        @Bean(name = "transactionManager")
        @Primary
        PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory
        ) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }

    @Configuration
    @ConditionalOnProperty(value = "app.analytics.datasource.enabled", havingValue = "true")
    @EnableJpaRepositories(
        basePackages = "com.example.gqw.analytics.repository",
        entityManagerFactoryRef = "analyticsEntityManagerFactory",
        transactionManagerRef = "analyticsTransactionManager"
    )
    static class AnalyticsJpaConfig {

        @Bean(name = "analyticsEntityManagerFactory")
        LocalContainerEntityManagerFactoryBean analyticsEntityManagerFactory(
            @Qualifier("analyticsDataSource") DataSource dataSource,
            Environment environment
        ) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.example.gqw.analytics.entity");
            factory.setJpaVendorAdapter(jpaVendorAdapter());
            factory.setJpaPropertyMap(jpaProperties(environment));
            factory.setPersistenceUnitName("analytics");
            return factory;
        }

        @Bean(name = "analyticsTransactionManager")
        PlatformTransactionManager analyticsTransactionManager(
            @Qualifier("analyticsEntityManagerFactory") EntityManagerFactory entityManagerFactory
        ) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }
}
