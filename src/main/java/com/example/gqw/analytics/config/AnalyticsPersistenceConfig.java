package com.example.gqw.analytics.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
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
    @ConditionalOnProperty(value = "app.analytics.datasource.enabled", havingValue = "true", matchIfMissing = true)
    DataSource analyticsDataSource(AnalyticsDataSourceProperties properties) {
        ensureAnalyticsDatabaseExists(properties);
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setPoolName("analyticsDataSource");
        return dataSource;
    }

    @Bean(name = "analyticsDataSource")
    @ConditionalOnProperty(value = "app.analytics.datasource.enabled", havingValue = "false")
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
    @ConditionalOnProperty(value = "app.analytics.datasource.enabled", havingValue = "false")
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
    @ConditionalOnProperty(value = "app.analytics.datasource.enabled", havingValue = "true", matchIfMissing = true)
    @EnableJpaRepositories(
        basePackages = "${app.analytics.host.repository-packages:com.example.gqw.shop.repository,com.example.gqw.admin.repository}",
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
                new String[] {"com.example.gqw.shop.entity", "com.example.gqw.admin.entity"}
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
    @ConditionalOnProperty(value = "app.analytics.datasource.enabled", havingValue = "true", matchIfMissing = true)
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

    private static void ensureAnalyticsDatabaseExists(AnalyticsDataSourceProperties properties) {
        if (!properties.isAutoCreateDatabase() || !isPostgresJdbcUrl(properties.getUrl())) {
            return;
        }
        String databaseName = extractPostgresDatabaseName(properties.getUrl());
        if (!hasText(databaseName)) {
            return;
        }
        String adminUrl = hasText(properties.getAdminUrl())
            ? properties.getAdminUrl()
            : derivePostgresAdminUrl(properties.getUrl());
        String adminUsername = hasText(properties.getAdminUsername())
            ? properties.getAdminUsername()
            : properties.getUsername();
        String adminPassword = hasText(properties.getAdminPassword())
            ? properties.getAdminPassword()
            : properties.getPassword();
        try (Connection connection = DriverManager.getConnection(adminUrl, adminUsername, adminPassword)) {
            if (databaseExists(connection, databaseName)) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                try {
                    statement.execute("CREATE DATABASE " + quoteIdentifier(databaseName));
                } catch (SQLException createEx) {
                    if (!"42P04".equals(createEx.getSQLState())) {
                        throw createEx;
                    }
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(
                "Cannot create analytics database '%s'. Create it manually or disable app.analytics.datasource.auto-create-database."
                    .formatted(databaseName),
                ex
            );
        }
    }

    private static boolean databaseExists(Connection connection, String databaseName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select 1 from pg_database where datname = ?")) {
            statement.setString(1, databaseName);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean isPostgresJdbcUrl(String url) {
        return url != null && url.startsWith("jdbc:postgresql://");
    }

    private static String extractPostgresDatabaseName(String url) {
        String mainPart = withoutQuery(url);
        int slashIndex = mainPart.lastIndexOf('/');
        if (slashIndex < "jdbc:postgresql://".length() || slashIndex == mainPart.length() - 1) {
            return "";
        }
        return mainPart.substring(slashIndex + 1);
    }

    private static String derivePostgresAdminUrl(String url) {
        String mainPart = withoutQuery(url);
        String queryPart = queryPart(url);
        int slashIndex = mainPart.lastIndexOf('/');
        if (slashIndex < "jdbc:postgresql://".length()) {
            return url;
        }
        return mainPart.substring(0, slashIndex + 1) + "postgres" + queryPart;
    }

    private static String withoutQuery(String url) {
        int queryIndex = url.indexOf('?');
        return queryIndex >= 0 ? url.substring(0, queryIndex) : url;
    }

    private static String queryPart(String url) {
        int queryIndex = url.indexOf('?');
        return queryIndex >= 0 ? url.substring(queryIndex) : "";
    }

    private static String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
