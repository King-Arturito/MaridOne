package org.example.maridone.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.maridone.core.user.UserAccount;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.core.user.UserLoginService;
import org.example.maridone.enums.AccountStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private static final String FORCED_LOGOUT_HEADER = "X-Session-Ended-Reason";
    private static final String PASSWORD_CHANGE_REQUIRED_HEADER = "X-Password-Change-Required";
    private static final String PORTAL_ACCESS_REMOVED_REASON = "portal-access-removed";

    private final JwtService jwtService;
    private final UserLoginService userLoginService;
    private final UserAccountRepository userAccountRepository;

    public JwtFilter(
            JwtService jwtService,
            UserLoginService userLoginService,
            UserAccountRepository userAccountRepository
    ) {
        this.jwtService = jwtService;
        this.userLoginService = userLoginService;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            username = jwtService.extractUsername(jwt);
        } catch (RuntimeException e) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userLoginService.loadUserByUsername(username);
            if (jwtService.isTokenValid(jwt, userDetails) && jwtService.isAccessToken(jwt)) {
                UserAccount userAccount = userAccountRepository.findByUsername(username).orElse(null);
                if (userAccount != null
                        && userAccount.getAccountStatus() == AccountStatus.INACTIVE
                        && !isLogoutAllowedPath(request)) {
                    SecurityContextHolder.clearContext();
                    response.setHeader(FORCED_LOGOUT_HEADER, PORTAL_ACCESS_REMOVED_REASON);
                    response.sendError(
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "Your portal access has been removed. Logging out now."
                    );
                    return;
                }

                if (userAccount != null
                        && Boolean.TRUE.equals(userAccount.getPasswordChangeRequired())
                        && !isPasswordChangeAllowedPath(request)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setHeader(PASSWORD_CHANGE_REQUIRED_HEADER, "true");
                    response.setContentType("application/json");
                    response.getWriter().write("""
                            {"code":"PASSWORD_CHANGE_REQUIRED","message":"You must change your temporary password before continuing.","status":403}
                            """);
                    return;
                }

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPasswordChangeAllowedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/user/password/change".equals(path)
                || "/api/auth/logout".equals(path)
                || "/api/auth/refresh".equals(path);
    }

    private boolean isLogoutAllowedPath(HttpServletRequest request) {
        return "/api/auth/logout".equals(request.getRequestURI());
    }
}
