package org.example.maridone.auth.twofactor;

public class TwoFactorSetupResponse {
    private String qrCodeImageUri;
    private String manualSecret;

    public TwoFactorSetupResponse(String qrCodeImageUri, String manualSecret) {
        this.qrCodeImageUri = qrCodeImageUri;
        this.manualSecret = manualSecret;
    }

    public String getQrCodeImageUri() { return qrCodeImageUri; }
    public String getManualSecret() { return manualSecret; }
}