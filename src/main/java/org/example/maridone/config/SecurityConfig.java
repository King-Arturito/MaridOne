package org.example.maridone.config;

import org.example.maridone.auth.JwtFilter;
import org.example.maridone.auth.JwtService;
import org.example.maridone.core.user.UserAccountRepository;
import org.example.maridone.core.user.UserLoginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final DefaultConfig defaultConfig;
    private final JwtService jwtService;
    private final UserLoginService userLoginService;
    private final UserAccountRepository userAccountRepository;

    public SecurityConfig(
            DefaultConfig defaultConfig,
            JwtService jwtService,
            UserLoginService userLoginService,
            UserAccountRepository userAccountRepository
    ) {
        this.defaultConfig = defaultConfig;
        this.jwtService = jwtService;
        this.userLoginService = userLoginService;
        this.userAccountRepository = userAccountRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public SecurityFilterChain customFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            var authentication = org.springframework.security.core.context.SecurityContextHolder
                                    .getContext()
                                    .getAuthentication();
                            log.warn(
                                    "Access denied for {} {} as {} with authorities {}",
                                    request.getMethod(),
                                    request.getRequestURI(),
                                    authentication != null ? authentication.getName() : "anonymous",
                                    authentication != null ? authentication.getAuthorities() : "none"
                            );
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("""
                                    {"code":"ACCESS_DENIED","message":"You do not have permission to perform this action.","status":403}
                                    """);
                        }))
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers("/actuator/health").permitAll()
                                .requestMatchers("/api/auth/**").permitAll()
                                .requestMatchers("/api/**").authenticated()
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(logout -> logout.logoutSuccessUrl(defaultConfig.getUrl().toString()))
                .addFilterBefore(jwtFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean JwtFilter jwtFilter() {
        return new JwtFilter(jwtService, userLoginService, userAccountRepository);
    }
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setAllowedOrigins(List.of(defaultConfig.getUrl().toString()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
