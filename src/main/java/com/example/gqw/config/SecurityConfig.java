package com.example.gqw.config;

import com.example.gqw.analytics.service.LoginAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final LoginAnalyticsService loginAnalyticsService;

    public SecurityConfig(LoginAnalyticsService loginAnalyticsService) {
        this.loginAnalyticsService = loginAnalyticsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(registry -> registry
                .requestMatchers(
                    "/",
                    "/catalog/**",
                    "/category/**",
                    "/product/**",
                    "/cart/**",
                    "/register",
                    "/login",
                    "/error",
                    "/css/**",
                    "/js/**",
                    "/shop/**",
                    "/admin/css/**",
                    "/admin/js/**",
                    "/admin/img/**",
                    "/analytics/css/**",
                    "/analytics/js/**",
                    "/analytics/img/**",
                    "/analytics-admin/**",
                    "/img/**",
                    "/images/**",
                    "/uploads/**"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/analytics/api/**").hasRole("ADMIN")
                .requestMatchers("/review/**").permitAll()
                .requestMatchers("/account/**", "/checkout/**").authenticated()
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler((request, response, authentication) -> {
                    safeTrackLoginSuccess(request);
                    boolean admin = authentication.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
                    response.sendRedirect(admin ? "/admin" : "/");
                })
                .failureHandler((request, response, exception) -> {
                    safeTrackLoginFailure(request, exception);
                    response.sendRedirect("/login?error");
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/analytics/frontend/**")
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void safeTrackLoginSuccess(HttpServletRequest request) {
        try {
            loginAnalyticsService.trackSuccess(request);
        } catch (RuntimeException ignored) {
            // Security flow should not fail because of analytics issues.
        }
    }

    private void safeTrackLoginFailure(HttpServletRequest request, AuthenticationException exception) {
        try {
            loginAnalyticsService.trackFailure(request, exception);
        } catch (RuntimeException ignored) {
            // Security flow should not fail because of analytics issues.
        }
    }
}

