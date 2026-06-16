package com.example.gqw.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.analytics.datasource")
public class AnalyticsDataSourceProperties {

    private boolean enabled = true;
    private String url = "jdbc:postgresql://localhost:5432/analytics";
    private String username = "postgres";
    private String password = "postgres";
    private String driverClassName = "org.postgresql.Driver";
    private boolean autoCreateDatabase = true;
    private String adminUrl = "";
    private String adminUsername = "";
    private String adminPassword = "";
    private int statementTimeoutSeconds = 30;
    private int idleInTransactionTimeoutSeconds = 30;
    private int lockTimeoutSeconds = 5;
    private int queryTimeoutSeconds = 30;
    private long connectionTimeoutMs = 10000;
    private long validationTimeoutMs = 5000;
    private long leakDetectionThresholdMs = 45000;
    private long maxLifetimeMs = 1800000;
    private long keepaliveTimeMs = 300000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public boolean isAutoCreateDatabase() {
        return autoCreateDatabase;
    }

    public void setAutoCreateDatabase(boolean autoCreateDatabase) {
        this.autoCreateDatabase = autoCreateDatabase;
    }

    public String getAdminUrl() {
        return adminUrl;
    }

    public void setAdminUrl(String adminUrl) {
        this.adminUrl = adminUrl;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public int getStatementTimeoutSeconds() {
        return statementTimeoutSeconds;
    }

    public void setStatementTimeoutSeconds(int statementTimeoutSeconds) {
        this.statementTimeoutSeconds = statementTimeoutSeconds;
    }

    public int getIdleInTransactionTimeoutSeconds() {
        return idleInTransactionTimeoutSeconds;
    }

    public void setIdleInTransactionTimeoutSeconds(int idleInTransactionTimeoutSeconds) {
        this.idleInTransactionTimeoutSeconds = idleInTransactionTimeoutSeconds;
    }

    public int getLockTimeoutSeconds() {
        return lockTimeoutSeconds;
    }

    public void setLockTimeoutSeconds(int lockTimeoutSeconds) {
        this.lockTimeoutSeconds = lockTimeoutSeconds;
    }

    public int getQueryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(long connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public long getValidationTimeoutMs() {
        return validationTimeoutMs;
    }

    public void setValidationTimeoutMs(long validationTimeoutMs) {
        this.validationTimeoutMs = validationTimeoutMs;
    }

    public long getLeakDetectionThresholdMs() {
        return leakDetectionThresholdMs;
    }

    public void setLeakDetectionThresholdMs(long leakDetectionThresholdMs) {
        this.leakDetectionThresholdMs = leakDetectionThresholdMs;
    }

    public long getMaxLifetimeMs() {
        return maxLifetimeMs;
    }

    public void setMaxLifetimeMs(long maxLifetimeMs) {
        this.maxLifetimeMs = maxLifetimeMs;
    }

    public long getKeepaliveTimeMs() {
        return keepaliveTimeMs;
    }

    public void setKeepaliveTimeMs(long keepaliveTimeMs) {
        this.keepaliveTimeMs = keepaliveTimeMs;
    }
}
