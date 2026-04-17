package org.example.maridone.auth.sensitive;

public class SensitiveAccessVerifyResponse {
    private String sensitiveAccessToken;
    private String expiresAt;
    private String verificationMethod;

    public SensitiveAccessVerifyResponse() {
    }

    public SensitiveAccessVerifyResponse(String sensitiveAccessToken, String expiresAt, String verificationMethod) {
        this.sensitiveAccessToken = sensitiveAccessToken;
        this.expiresAt = expiresAt;
        this.verificationMethod = verificationMethod;
    }

    public String getSensitiveAccessToken() {
        return sensitiveAccessToken;
    }

    public void setSensitiveAccessToken(String sensitiveAccessToken) {
        this.sensitiveAccessToken = sensitiveAccessToken;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getVerificationMethod() {
        return verificationMethod;
    }

    public void setVerificationMethod(String verificationMethod) {
        this.verificationMethod = verificationMethod;
    }
}
