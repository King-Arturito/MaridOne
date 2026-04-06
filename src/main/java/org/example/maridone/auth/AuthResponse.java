package org.example.maridone.auth;

public class AuthResponse {
    //preauth if 2fa is required
    private String accessToken;
    private String refreshToken;
    private boolean requires2fa;
    private boolean requiresPasswordChange;

    public AuthResponse(String accessToken, String refreshToken, boolean requires2fa, boolean requiresPasswordChange) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.requires2fa = requires2fa;
        this.requiresPasswordChange = requiresPasswordChange;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public boolean isRequires2fa() { return requires2fa; }
    public boolean isRequiresPasswordChange() { return requiresPasswordChange; }
}
