package org.example.maridone.auth;

public class AuthResponse {
    //preauth if 2fa is required
    private String accessToken;
    private String refreshToken;
    private boolean requires2fa;

    public AuthResponse(String accessToken, String refreshToken, boolean requires2fa) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.requires2fa = requires2fa;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public boolean isRequires2fa() { return requires2fa; }
}