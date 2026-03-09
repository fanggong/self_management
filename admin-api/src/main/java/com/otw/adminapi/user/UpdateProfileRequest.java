package com.otw.adminapi.user;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
  @NotBlank String displayName,
  String email,
  String phone,
  String avatarUrl
) {
}
