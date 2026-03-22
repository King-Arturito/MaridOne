package org.example.maridone.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("holiday")
public class HolidayConfig {

    private boolean enabled = false;
    private String apiKey = "";
    private String country = "PH";
    private String baseUrl = "https://calendarific.com/api/v2/holidays";
    private String cacheDir = ".cache/holidays";
    private String overrideFile = ".cache/holidays/holiday-overrides.json";
    private Duration refreshAfter = Duration.ofDays(365);
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCacheDir() {
        return cacheDir;
    }

    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    public String getOverrideFile() {
        return overrideFile;
    }

    public void setOverrideFile(String overrideFile) {
        this.overrideFile = overrideFile;
    }

    public Duration getRefreshAfter() {
        return refreshAfter;
    }

    public void setRefreshAfter(Duration refreshAfter) {
        this.refreshAfter = refreshAfter;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
