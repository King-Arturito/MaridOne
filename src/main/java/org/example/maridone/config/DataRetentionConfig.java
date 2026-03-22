package org.example.maridone.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "data.retention")
public class DataRetentionConfig {

    private Duration notificationMaxAge = Duration.ofDays(60);

    public Duration getNotificationMaxAge() {
        return notificationMaxAge;
    }

    public void setNotificationMaxAge(Duration notificationMaxAge) {
        this.notificationMaxAge = notificationMaxAge;
    }
}
