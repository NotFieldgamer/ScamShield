package com.verity.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 320, message = "email must be at most 320 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 200, message = "password must be between 8 and 200 characters")
        String password) {
}
