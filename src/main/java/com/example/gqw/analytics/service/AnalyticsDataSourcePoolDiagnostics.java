package com.example.gqw.analytics.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsDataSourcePoolDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsDataSourcePoolDiagnostics.class);

    private final DataSource dataSource;

    public AnalyticsDataSourcePoolDiagnostics(@Qualifier("analyticsDataSource") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean shouldSkipScheduledJob() {
        HikariPoolMXBean pool = hikariPool();
        if (pool == null) {
            return false;
        }
        int total = pool.getTotalConnections();
        int active = pool.getActiveConnections();
        int awaiting = pool.getThreadsAwaitingConnection();
        if (total <= 0) {
            return false;
        }
        return awaiting > 0 || active >= Math.max(1, total - 1);
    }

    public String snapshot() {
        HikariPoolMXBean pool = hikariPool();
        if (pool == null) {
            return "analyticsDataSource pool=unavailable";
        }
        return "analyticsDataSource active=%d idle=%d total=%d awaiting=%d"
            .formatted(
                pool.getActiveConnections(),
                pool.getIdleConnections(),
                pool.getTotalConnections(),
                pool.getThreadsAwaitingConnection()
            );
    }

    public void logSlowEndpoint(String endpoint, long totalMs) {
        log.warn("Analytics endpoint slow endpoint={} totalMs={} {}", endpoint, totalMs, snapshot());
    }

    public void logPoolFailure(String context, Throwable error) {
        log.warn(
            "Analytics datasource failure context={} error={} {}",
            context,
            error == null ? "unknown" : error.getMessage(),
            snapshot()
        );
    }

    public void logScheduledSkip(String jobName) {
        log.warn("Analytics scheduled job skipped job={} reason=analytics-pool-busy {}", jobName, snapshot());
    }

    private HikariPoolMXBean hikariPool() {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            return hikariDataSource.getHikariPoolMXBean();
        }
        return null;
    }
}
