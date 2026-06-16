package com.example.gqw.config;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);
    private static final String JDBC_PREFIX = "jdbc:postgresql://";

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties, Environment environment) {
        boolean autoCreateDb = environment.getProperty("app.datasource.auto-create-db", Boolean.class, true);
        if (autoCreateDb) {
            ensureDatabaseExists(properties.getUrl(), properties.getUsername(), properties.getPassword());
        }
        return properties.initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
    }

    private void ensureDatabaseExists(String jdbcUrl, String username, String password) {
        if (jdbcUrl == null || !jdbcUrl.startsWith(JDBC_PREFIX)) {
            return;
        }

        ParsedPostgresUrl parsed = parsePostgresUrl(jdbcUrl);
        if (parsed.databaseName().isBlank() || "postgres".equalsIgnoreCase(parsed.databaseName())) {
            return;
        }

        String adminJdbcUrl = parsed.baseJdbcUrl() + "postgres" + parsed.querySuffix();
        try (
            Connection connection = java.sql.DriverManager.getConnection(adminJdbcUrl, username, password);
            PreparedStatement existsStatement = connection.prepareStatement(
                "SELECT 1 FROM pg_database WHERE datname = ?"
            )
        ) {
            existsStatement.setString(1, parsed.databaseName());
            try (ResultSet resultSet = existsStatement.executeQuery()) {
                if (resultSet.next()) {
                    return;
                }
            }

            String createSql = "CREATE DATABASE " + quoteIdentifier(parsed.databaseName());
            try (Statement statement = connection.createStatement()) {
                statement.execute(createSql);
                log.info("Created database '{}'", parsed.databaseName());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Cannot initialize PostgreSQL database '" + parsed.databaseName()
                    + "'. Check datasource URL, credentials and PostgreSQL server availability.",
                exception
            );
        }
    }

    private ParsedPostgresUrl parsePostgresUrl(String jdbcUrl) {
        String uriValue = jdbcUrl.substring("jdbc:".length());
        URI uri = URI.create(uriValue);
        String path = uri.getPath();
        String databaseName = path == null ? "" : path.replaceFirst("^/", "");
        String host = uri.getHost();
        int port = uri.getPort();
        StringBuilder baseUrl = new StringBuilder(JDBC_PREFIX).append(host);
        if (port > 0) {
            baseUrl.append(":").append(port).append("/");
        } else {
            baseUrl.append("/");
        }
        String querySuffix = uri.getQuery() == null ? "" : "?" + uri.getQuery();
        return new ParsedPostgresUrl(baseUrl.toString(), databaseName, querySuffix);
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record ParsedPostgresUrl(String baseJdbcUrl, String databaseName, String querySuffix) {
    }
}
