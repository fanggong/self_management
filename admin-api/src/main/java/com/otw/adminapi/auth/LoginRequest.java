package com.otw.adminapi.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
  @NotBlank String principal,
  @NotBlank String password
) {
}
