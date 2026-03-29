package com.otw.adminapi.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecurityStartupValidatorTest {
  @Test
  void validateRejectsTrackedPlaceholders() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
      SecurityStartupValidator.JWT_SECRET_PLACEHOLDER,
      SecurityStartupValidator.CONNECTOR_SECRET_KEY_PLACEHOLDER,
      SecurityStartupValidator.INTERNAL_API_TOKEN_PLACEHOLDER
    );

    IllegalStateException error = assertThrows(IllegalStateException.class, validator::validate);
    assertTrue(error.getMessage().contains("tracked placeholder"));
  }

  @Test
  void validateRejectsPublishedDefaults() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
      "0ea96cef2a345c46af1aa6318958d60228fa1903c1f1776" + "fb8b3fa9bcc2dbc3b",
      "ZpbWkYr/Lhf2an422+7liSjKuN4KkbOEx9" + "f2oll8Nv4=",
      "6f988cfd1b0c448b182082f4a4f79273" + "70a07699c0258d2e"
    );

    IllegalStateException error = assertThrows(IllegalStateException.class, validator::validate);
    assertTrue(error.getMessage().contains("published default value"));
  }

  @Test
  void validateAcceptsCustomSecrets() {
    SecurityStartupValidator validator = new SecurityStartupValidator(
      "custom-jwt-secret-that-is-longer-than-thirty-two-characters",
      "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
      "custom-internal-token-1234567890"
    );

    assertDoesNotThrow(validator::validate);
  }
}
