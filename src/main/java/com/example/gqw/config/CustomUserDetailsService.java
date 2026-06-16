package com.example.gqw.config;

import com.example.gqw.shop.entity.ShopUser;
import com.example.gqw.shop.repository.ShopUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final ShopUserRepository userRepository;

    public CustomUserDetailsService(ShopUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        ShopUser user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        String role = Boolean.TRUE.equals(user.getIsAdmin()) ? "ROLE_ADMIN" : "ROLE_USER";
        return User.builder()
            .username(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(new SimpleGrantedAuthority(role))
            .disabled(!Boolean.TRUE.equals(user.getIsEnabled()))
            .build();
    }
}

