package org.example.maridone.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "default")
@Validated
public class DefaultConfig {
    private ZoneId timeZone = ZoneId.of("Asia/Manila");
    @NotEmpty(message = "No provided URL.")
    private String url;
    private String apiUrl;


    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiUrlOrDefault() {
        if (apiUrl != null && !apiUrl.isBlank()) {
            return normalizeUrl(apiUrl);
        }

        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }

        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            String authority = uri.getRawAuthority();

            if (scheme == null || authority == null || authority.isBlank()) {
                return normalizeUrl(url);
            }

            int port = uri.getPort();
            if (port == 5173 || port == 4173) {
                return normalizeUrl(scheme + "://" + uri.getHost() + ":8080");
            }

            return normalizeUrl(scheme + "://" + authority);
        } catch (IllegalArgumentException ignored) {
            return normalizeUrl(url);
        }
    }

    public ZoneId getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(ZoneId timeZone) {
        this.timeZone = timeZone;
    }

    private String normalizeUrl(String value) {
        return value == null ? null : value.trim().replaceAll("/+$", "");
    }
}
