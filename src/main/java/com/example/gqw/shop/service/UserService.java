package com.example.gqw.shop.service;

import com.example.gqw.shop.dto.RegisterRequest;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.CartItemRepository;
import com.example.gqw.shop.repository.ShopUserRepository;
import com.example.gqw.shop.repository.WishlistItemRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final ShopUserRepository userRepository;
    private final CartItemRepository cartItemRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
        ShopUserRepository userRepository,
        CartItemRepository cartItemRepository,
        WishlistItemRepository wishlistItemRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.cartItemRepository = cartItemRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ShopUser register(RegisterRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        String fullName = request.fullName() == null ? "" : request.fullName().trim();
        String email = request.email() == null ? "" : request.email().trim();
        String phone = request.phone() == null ? null : request.phone().trim();

        if (username.isBlank()) {
            throw new IllegalArgumentException("Логин обязателен");
        }
        if (fullName.isBlank()) {
            throw new IllegalArgumentException("ФИО обязательно");
        }
        if (email.isBlank()) {
            throw new IllegalArgumentException("Email обязателен");
        }

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Пользователь с таким логином уже существует");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }
        if (request.password() == null || request.password().length() < 6) {
            throw new IllegalArgumentException("Пароль должен содержать минимум 6 символов");
        }
        ShopUser user = new ShopUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setIsAdmin(false);
        user.setIsEnabled(true);
        return userRepository.save(user);
    }

    @Transactional
    public ShopUser updateProfile(
        ShopUser user,
        String fullName,
        String phone,
        String email,
        String addressStreet,
        String addressHouse,
        String addressApartment,
        String addressEntrance,
        String addressFloor,
        String addressIntercom
    ) {
        updateContacts(user, fullName, phone, email);
        return updateAddress(
            user,
            addressStreet,
            addressHouse,
            addressApartment,
            addressEntrance,
            addressFloor,
            addressIntercom
        );
    }

    @Transactional
    public ShopUser updateContacts(ShopUser user, String fullName, String phone, String email) {
        String normalizedFullName = fullName == null ? "" : fullName.trim();
        if (normalizedFullName.isBlank()) {
            throw new IllegalArgumentException("ФИО обязательно");
        }
        String normalizedEmail = email == null ? "" : email.trim();
        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Email обязателен");
        }
        userRepository.findByEmailIgnoreCase(normalizedEmail)
            .filter(existing -> !existing.getId().equals(user.getId()))
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Этот email уже используется");
            });

        user.setFullName(normalizedFullName);
        user.setPhone(normalizeNullable(phone));
        user.setEmail(normalizedEmail);
        return userRepository.save(user);
    }

    @Transactional
    public ShopUser updateAddress(
        ShopUser user,
        String addressStreet,
        String addressHouse,
        String addressApartment,
        String addressEntrance,
        String addressFloor,
        String addressIntercom
    ) {
        String street = normalizeNullable(addressStreet);
        String house = normalizeNullable(addressHouse);
        String apartment = normalizeNullable(addressApartment);
        String entrance = normalizeNullable(addressEntrance);
        String floor = normalizeNullable(addressFloor);
        String intercom = normalizeNullable(addressIntercom);
        user.setAddressStreet(street);
        user.setAddressHouse(house);
        user.setAddressApartment(apartment);
        user.setAddressEntrance(entrance);
        user.setAddressFloor(floor);
        user.setAddressIntercom(intercom);
        user.setAddress(buildLegacyAddress(street, house, apartment, entrance, floor, intercom));
        return userRepository.save(user);
    }

    @Transactional
    public ShopUser markReviewRepliesSeen(ShopUser user, Instant seenAt) {
        if (user == null) {
            throw new IllegalArgumentException("Пользователь не найден");
        }
        user.setReviewRepliesSeenAt(seenAt == null ? Instant.now() : seenAt);
        return userRepository.save(user);
    }

    @Transactional
    public ShopUser deleteAccount(ShopUser user) {
        if (user == null) {
            throw new IllegalArgumentException("Пользователь не найден");
        }
        if (Boolean.TRUE.equals(user.getIsAdmin())) {
            throw new IllegalArgumentException("Администратор не может удалить свой аккаунт");
        }

        // Удаляем пользовательские коллекции, которые не нужны после деактивации.
        cartItemRepository.deleteAll(cartItemRepository.findByUser(user));
        wishlistItemRepository.deleteAll(wishlistItemRepository.findByUser(user));

        String suffix = user.getId() + "_" + Instant.now().getEpochSecond();
        String generatedUsername = ("deleted_" + suffix).toLowerCase();
        if (generatedUsername.length() > 64) {
            generatedUsername = generatedUsername.substring(0, 64);
        }
        while (userRepository.existsByUsernameIgnoreCase(generatedUsername)) {
            generatedUsername = ("deleted_" + suffix + "_" + UUID.randomUUID().toString().substring(0, 8)).toLowerCase();
            if (generatedUsername.length() > 64) {
                generatedUsername = generatedUsername.substring(0, 64);
            }
        }

        String generatedEmail = ("deleted_" + suffix + "@deleted.local").toLowerCase();
        if (generatedEmail.length() > 128) {
            generatedEmail = generatedEmail.substring(0, 128);
        }
        while (userRepository.existsByEmailIgnoreCase(generatedEmail)) {
            generatedEmail = ("deleted_" + suffix + "_" + UUID.randomUUID().toString().substring(0, 8) + "@deleted.local").toLowerCase();
            if (generatedEmail.length() > 128) {
                generatedEmail = generatedEmail.substring(0, 128);
            }
        }

        user.setUsername(generatedUsername);
        user.setEmail(generatedEmail);
        user.setFullName("Удалённый пользователь");
        user.setPhone(null);
        user.setAddress(null);
        user.setAddressStreet(null);
        user.setAddressHouse(null);
        user.setAddressApartment(null);
        user.setAddressEntrance(null);
        user.setAddressFloor(null);
        user.setAddressIntercom(null);
        user.setIsEnabled(false);
        user.setReviewRepliesSeenAt(Instant.now());
        user.setPasswordHash(passwordEncoder.encode("deleted-account-" + UUID.randomUUID()));
        return userRepository.save(user);
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String buildLegacyAddress(
        String street,
        String house,
        String apartment,
        String entrance,
        String floor,
        String intercom
    ) {
        StringBuilder sb = new StringBuilder();
        appendAddressPart(sb, street, "");
        appendAddressPart(sb, house, "д. ");
        appendAddressPart(sb, apartment, "кв. ");
        appendAddressPart(sb, entrance, "подъезд ");
        appendAddressPart(sb, floor, "этаж ");
        appendAddressPart(sb, intercom, "домофон ");
        return sb.isEmpty() ? null : sb.toString();
    }

    private static void appendAddressPart(StringBuilder sb, String value, String prefix) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        sb.append(prefix).append(value);
    }
}

