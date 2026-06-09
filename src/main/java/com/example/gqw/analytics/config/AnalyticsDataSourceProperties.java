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
}
