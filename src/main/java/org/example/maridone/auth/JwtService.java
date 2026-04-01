package org.example.maridone.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.example.maridone.config.JwtConfig;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JwtService {

    private final JwtConfig jwtConfig;

    public JwtService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    public String generateToken(UserDetails user) {
        //claims = roles
        Map<String, Object> claims = new HashMap<>();
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        claims.put("roles", roles);
        claims.put("scope", "access");
        return generateToken(claims, user);
    }

    public String generateToken(Map<String, Object> claims, UserDetails user) {
        return buildToken(claims, user.getUsername(), jwtConfig.getExpiration());
    }

    public String generateRefreshToken(UserDetails user, boolean rememberMe) {
        long expiry = rememberMe
                ? jwtConfig.getRememberMeExpiration()
                : jwtConfig.getRefreshTokenExpiration();
        return buildToken(new HashMap<>(), user.getUsername(), expiry);
    }

    private String buildToken(Map<String, Object> claims, String username, Long expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey(jwtConfig.getSecret()))
                .compact();
    }
    private Key getKey(String secret) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    public boolean isAccessToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return "access".equals(claims.get("scope", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        try {
            return extractAllClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) getKey(jwtConfig.getSecret()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String generatePreAuthToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "pre_auth");

        //5 minutes
        return buildToken(claims, userDetails.getUsername(), 300000L);
    }

    public boolean isValidPreAuthToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String scope = claims.get("scope", String.class);
            return "pre_auth".equals(scope);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isValidVerificationToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String scope = claims.get("scope", String.class);
            return "email_verification".equals(scope);
        } catch (Exception e) {
            return false;
        }
    }

    public String generateEmailVerificationToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "email_verification");
        //1 day
        return buildToken(claims, username, 86400000L);
    }
}