package org.example.maridone.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("jwt")
@Component
@Validated
public class JwtProperties {
    @NotEmpty(message = "No provided JWT Secret Key.")
    private String secret;
    //1 day
    @NotNull(message = "JWT Token Expiration is null. Don't modify the environment to get the default = 24 hours.")
    @Positive
    private Long expiration = 86400000L;

    @NotNull(message = "Refresh Token Expiration is null. Don't modify the environment to get the default = 24 hours.")
    @Positive
    private Long refreshTokenExpiration = 86400000L;

    @NotNull(message = "Remember Me Expiration is null. Don't modify the environment to get the default = 30 days")
    @Positive
    private Long rememberMeExpiration = 2592000000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Long getExpiration() {
        return expiration;
    }

    public void setExpiration(Long expiration) {
        this.expiration = expiration;
    }

    public Long getRememberMeExpiration() {
        return rememberMeExpiration;
    }

    public void setRememberMeExpiration(Long rememberMeExpiration) {
        this.rememberMeExpiration = rememberMeExpiration;
    }

    public Long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public void setRefreshTokenExpiration(Long refreshTokenExpiration) {
        this.refreshTokenExpiration = refreshTokenExpiration;
    }
}
