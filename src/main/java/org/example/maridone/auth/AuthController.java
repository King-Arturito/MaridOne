package org.example.maridone.auth;

import org.example.maridone.auth.twofactor.TwoFactorService;
import org.example.maridone.auth.twofactor.TwoFactorSetupResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final TwoFactorService twoFactorService;

    public AuthController(AuthService authService, TwoFactorService twoFactorService) {
        this.authService = authService;
        this.twoFactorService = twoFactorService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> authenticateUser(@RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.authenticateUser(loginRequest));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<String> logout(@RequestBody LogoutRequest request) {
        authService.logoutUser(request);
        return ResponseEntity.ok("Successfully logged out");
    }

    @PostMapping("/auth/verify-2fa")
    public ResponseEntity<AuthResponse> verifyTwoFactorAuth(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody String code) {
        //skips 'Bearer '
        String preAuthToken = authorizationHeader.substring(7);
        return ResponseEntity.ok(authService.verify2fa(preAuthToken, code));
    }

    @PostMapping("/auth/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestBody String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok("Email successfully verified");
    }

    @PostMapping("/settings/send-email-verification")
    public ResponseEntity<Void> sendEmailVerification(Authentication authentication) {
        return ResponseEntity.ok(authService.sendEmailVerification(authentication.getName()));
    }

    @PostMapping("/auth/2fa/send-login-code")
    public ResponseEntity<?> sendTwoFactorLoginCode(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(authService.sendTwoFactorLoginCode(authHeader));
    }
    @PostMapping("/settings/2fa/disable")
    public ResponseEntity<Void> disable2fa(Authentication authentication) {
        return ResponseEntity.ok(twoFactorService.disable2fa(authentication.getName()));
    }

    @PostMapping("/settings/2fa/enable")
    public ResponseEntity<TwoFactorSetupResponse> setup2fa(Authentication authentication) {
        return ResponseEntity.ok(twoFactorService.generateSetupDetails(authentication.getName()));
    }

    @PostMapping("/settings/2fa/confirm")
    public ResponseEntity<String> confirm2fa(
            Authentication authentication,
            @RequestBody String code) {

        twoFactorService.confirmAndEnable2fa(authentication.getName(), code);
        return ResponseEntity.ok("Two-Factor Authentication successfully enabled.");
    }
}