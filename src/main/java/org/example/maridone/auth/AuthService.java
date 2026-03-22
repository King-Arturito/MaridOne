package org.example.maridone.auth;

import org.example.maridone.annotation.ExecutionTime;
import org.example.maridone.auth.token.RefreshToken;
import org.example.maridone.auth.token.RefreshTokenRepository;
import org.example.maridone.config.JwtConfig;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.core.user.UserLoginService;
import org.example.maridone.exception.notfound.AccountNotFoundException;
import org.example.maridone.exception.badrequest.InvalidRefreshTokenException;
import org.example.maridone.exception.badrequest.UnauthorizedAccessException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserLoginService userLoginService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAccountRepository userAccountRepository;
    private final JwtConfig jwtConfig;

    public AuthService(JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       UserLoginService userLoginService,
                       RefreshTokenRepository refreshTokenRepository,
                       UserAccountRepository userAccountRepository,
                       JwtConfig jwtConfig) {
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userLoginService = userLoginService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userAccountRepository = userAccountRepository;
        this.jwtConfig = jwtConfig;
    }

    @ExecutionTime
    @Transactional
    public AuthResponse authenticateUser(LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            UserAccount userAccount = userAccountRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new AccountNotFoundException("username of: " + loginRequest.getUsername() + " not found"));

            String accessToken  = jwtService.generateToken(userDetails);
            String refreshTokenString = jwtService.generateRefreshToken(userDetails, loginRequest.isRememberMe());

            RefreshToken refreshTokenEntity = new RefreshToken();
            refreshTokenEntity.setRefreshToken(refreshTokenString);
            refreshTokenEntity.setUserAccount(userAccount);

            long milliseconds = loginRequest.isRememberMe() ? jwtConfig.getRememberMeExpiration() : jwtConfig.getRefreshTokenExpiration();
            refreshTokenEntity.setExpiryDate(Instant.now().plus(milliseconds, ChronoUnit.MILLIS));

            refreshTokenRepository.save(refreshTokenEntity);

            return new AuthResponse(accessToken, refreshTokenString);

        } catch (AuthenticationException e) {
            throw new UnauthorizedAccessException("Invalid username or password");
        }

    }

    @Transactional
    public AuthResponse refreshToken(RefreshRequest request) {

        RefreshToken refreshTokenEntity = refreshTokenRepository.findByRefreshToken(request.getRefreshToken())
                .orElseThrow(InvalidRefreshTokenException::new);

        if (refreshTokenEntity.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(refreshTokenEntity);
            throw new InvalidRefreshTokenException();
        }

        UserAccount userAccount = refreshTokenEntity.getUserAccount();
        UserDetails userDetails = userLoginService.loadUserByUsername(userAccount.getUsername());

        String newAccessToken = jwtService.generateToken(userDetails);
        return new AuthResponse(newAccessToken, request.getRefreshToken());
    }

    @Transactional
    public void logoutUser(LogoutRequest request) {
        refreshTokenRepository.deleteByRefreshToken(request.getRefreshToken());
    }
}