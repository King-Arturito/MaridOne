package org.example.maridone.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "default")
@Validated
public class DefaultConfig {
    private ZoneId timeZone = ZoneId.of("Asia/Manila");
    @NotEmpty(message = "No provided URL.")
    private String url;


    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public ZoneId getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(ZoneId timeZone) {
        this.timeZone = timeZone;
    }
}
