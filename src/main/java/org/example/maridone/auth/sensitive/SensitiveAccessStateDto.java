package org.example.maridone.auth.sensitive;

public class SensitiveAccessStateDto {
    private boolean twoFactorEnabled;
    private boolean sensitiveAccessActive;
    private String sensitiveAccessExpiresAt;

    public SensitiveAccessStateDto() {
    }

    public SensitiveAccessStateDto(boolean twoFactorEnabled, boolean sensitiveAccessActive, String sensitiveAccessExpiresAt) {
        this.twoFactorEnabled = twoFactorEnabled;
        this.sensitiveAccessActive = sensitiveAccessActive;
        this.sensitiveAccessExpiresAt = sensitiveAccessExpiresAt;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public void setTwoFactorEnabled(boolean twoFactorEnabled) {
        this.twoFactorEnabled = twoFactorEnabled;
    }

    public boolean isSensitiveAccessActive() {
        return sensitiveAccessActive;
    }

    public void setSensitiveAccessActive(boolean sensitiveAccessActive) {
        this.sensitiveAccessActive = sensitiveAccessActive;
    }

    public String getSensitiveAccessExpiresAt() {
        return sensitiveAccessExpiresAt;
    }

    public void setSensitiveAccessExpiresAt(String sensitiveAccessExpiresAt) {
        this.sensitiveAccessExpiresAt = sensitiveAccessExpiresAt;
    }
}
