package com.example.gqw.shop.service;

import com.example.gqw.shop.dto.RegisterRequest;
import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.ShopUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final ShopUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(ShopUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ShopUser register(RegisterRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new IllegalArgumentException("Пользователь с таким логином уже существует");
        }
        ShopUser user = new ShopUser();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setIsAdmin(false);
        user.setIsEnabled(true);
        return userRepository.save(user);
    }

    @Transactional
    public ShopUser updateProfile(ShopUser user, String fullName, String phone, String email) {
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEmail(email);
        return userRepository.save(user);
    }
}

