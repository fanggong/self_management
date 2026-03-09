package com.otw.adminapi.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, UUID accountId, String principal, String role) {
}
