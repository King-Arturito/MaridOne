package org.example.maridone.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
@ConfigurationProperties(prefix = "default")
public class DefaultProperties {
    private ZoneId timeZone = ZoneId.of("Asia/Manila");


    public ZoneId getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(ZoneId timeZone) {
        this.timeZone = timeZone;
    }
}
