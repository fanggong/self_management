package com.otw.adminapi.auth;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
  @NotBlank String displayName,
  @NotBlank String principal,
  @NotBlank String password,
  @NotBlank String confirmPassword
) {
}
