package com.example.gqw.shop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
    @NotBlank String username,
    @NotBlank String fullName,
    @NotBlank @Email String email,
    @NotBlank String password,
    String phone
) {
}

