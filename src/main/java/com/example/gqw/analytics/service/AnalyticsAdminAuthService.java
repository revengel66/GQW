package com.example.gqw.analytics.service;

import com.example.gqw.analytics.entity.AnalyticsAdminUser;
import com.example.gqw.analytics.repository.AnalyticsAdminUserRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsAdminAuthService {

    public static final String SESSION_KEY_AUTH = "analyticsAdminAuthenticated";
    public static final String SESSION_KEY_USER_ID = "analyticsAdminUserId";
    public static final String SESSION_KEY_USERNAME = "analyticsAdminUsername";

    private final AnalyticsAdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AnalyticsAdminAuthService(
        AnalyticsAdminUserRepository adminUserRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public boolean isSetupComplete() {
        return adminUserRepository.count() > 0;
    }

    @Transactional
    public AnalyticsAdminUser registerInitial(String usernameRaw, String passwordRaw) {
        if (isSetupComplete()) {
            throw new IllegalStateException("Учётная запись аналитики уже создана");
        }
        String username = normalizeUsername(usernameRaw);
        String password = normalizePassword(passwordRaw);
        AnalyticsAdminUser user = new AnalyticsAdminUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        return adminUserRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AnalyticsAdminUser authenticate(String usernameRaw, String passwordRaw) {
        String username = normalizeUsername(usernameRaw);
        String password = normalizePassword(passwordRaw);
        AnalyticsAdminUser user = adminUserRepository.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new IllegalArgumentException("Неверный логин или пароль"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Неверный логин или пароль");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public AnalyticsAdminUser requireById(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Пользователь аналитики не найден");
        }
        return adminUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Пользователь аналитики не найден"));
    }

    @Transactional
    public AnalyticsAdminUser updateCredentials(
        Long userId,
        String newUsernameRaw,
        String newPasswordRaw
    ) {
        AnalyticsAdminUser user = requireById(userId);
        String newUsername = normalizeUsername(newUsernameRaw);
        String newPassword = normalizePassword(newPasswordRaw);
        if (!user.getUsername().equalsIgnoreCase(newUsername)
            && adminUserRepository.existsByUsernameIgnoreCase(newUsername)) {
            throw new IllegalArgumentException("Логин уже используется");
        }
        user.setUsername(newUsername);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        return adminUserRepository.save(user);
    }

    private static String normalizeUsername(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Логин обязателен");
        }
        String username = value.trim().toLowerCase(Locale.ROOT);
        if (username.length() < 3 || username.length() > 96) {
            throw new IllegalArgumentException("Логин должен быть от 3 до 96 символов");
        }
        return username;
    }

    private static String normalizePassword(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Пароль обязателен");
        }
        String password = value.trim();
        if (password.length() < 6 || password.length() > 120) {
            throw new IllegalArgumentException("Пароль должен быть от 6 до 120 символов");
        }
        return password;
    }
}

