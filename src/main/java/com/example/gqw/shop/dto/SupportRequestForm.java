package com.example.gqw.shop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SupportRequestForm(
    @NotBlank String name,
    @NotBlank @Email String email,
    String phone,
    @NotBlank String message
) {
}

