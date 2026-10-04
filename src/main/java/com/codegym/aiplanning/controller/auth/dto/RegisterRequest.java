package com.codegym.aiplanning.controller.auth.dto;

import com.codegym.aiplanning.common.validation.password.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Public local-account registration request")
public record RegisterRequest(
        @Schema(
                        description = "Email address used to sign in",
                        example = "user@example.com",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "Email is required")
                @Email(message = "Email must be valid")
                @Size(max = 254, message = "Email must not exceed 254 characters")
                String email,
        @Schema(
                        description = "Password: 8-50 characters with at least one uppercase letter and one digit, no whitespace",
                        example = "Password123",
                        accessMode = Schema.AccessMode.WRITE_ONLY,
                        minLength = 8,
                        maxLength = 50,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @ValidPassword
                String password,
        @Schema(
                        description = "User display name",
                        example = "Nguyen Van A",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "Display name is required")
                @Size(max = 150, message = "Display name must not exceed 150 characters")
                String displayName) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password='[PROTECTED]', displayName=" + displayName + "]";
    }
}
