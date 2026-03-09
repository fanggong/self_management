package com.otw.adminapi.user;

import java.util.UUID;

public record AuthUserView(
  UUID id,
  String displayName,
  String principal,
  String email,
  String phone,
  String avatarUrl,
  String role
) {
}
