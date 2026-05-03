package org.example.maridone.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "default")
@Validated
public class DefaultConfig {
    public static final Logger log = LoggerFactory.getLogger(DefaultConfig.class);

    @NotNull
    private URI url;

    @NotNull
    private URI apiUrl;

    private ZoneId timeZone = ZoneId.of("Asia/Manila");

    @PostConstruct
    public void logConfig() {
        log.info("AppConfig initialized:");
        log.info(" - Frontend URL: {}", url);
        log.info(" - API URL: {}", apiUrl);
        log.info(" - Timezone: {}", timeZone);
    }

    public URI getUrl() {
        return url;
    }

    public void setUrl(URI url) {
        this.url = url;
    }

    public URI getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(URI apiUrl) {
        this.apiUrl = apiUrl;
    }

    public ZoneId getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(ZoneId timeZone) {
        this.timeZone = timeZone;
    }
}