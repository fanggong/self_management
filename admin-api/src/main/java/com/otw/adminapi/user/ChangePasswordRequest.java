package com.otw.adminapi.user;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
  @NotBlank String currentPassword,
  @NotBlank String newPassword,
  @NotBlank String confirmPassword
) {
}
