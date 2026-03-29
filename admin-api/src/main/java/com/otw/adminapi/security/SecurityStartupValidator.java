package com.otw.adminapi.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class SecurityStartupValidator {
  static final String JWT_SECRET_PLACEHOLDER = "REPLACE_WITH_A_STRONG_JWT_SECRET_BEFORE_DEPLOYMENT";
  static final String PUBLISHED_JWT_SECRET_SHA256 = "8155b62907ead5659687e2b16f8a453091296da82904b14c75ecaf5e23d69f7c";
  static final String CONNECTOR_SECRET_KEY_PLACEHOLDER = "UkVQTEFDRV9XSVRIXzMyX0JZVEVfQUVTX0tFWV9OT1c=";
  static final String PUBLISHED_CONNECTOR_SECRET_KEY_SHA256 = "97f52fab4e515dc1d849dc2ecbb77f65e8ca1dcdb8588afa57168e9bcd4137ed";
  static final String INTERNAL_API_TOKEN_PLACEHOLDER = "REPLACE_WITH_A_RANDOM_INTERNAL_API_TOKEN_NOW";
  static final String PUBLISHED_INTERNAL_API_TOKEN_SHA256 = "2a44e4449440bc59034a53b62e4bb5838f881f06e84e22a52201f80bc19e24df";

  private final String jwtSecret;
  private final String connectorSecretKey;
  private final String internalApiToken;

  public SecurityStartupValidator(
    @Value("${app.security.jwt-secret}") String jwtSecret,
    @Value("${app.connector.secret-key}") String connectorSecretKey,
    @Value("${app.connector.internal-token}") String internalApiToken
  ) {
    this.jwtSecret = jwtSecret;
    this.connectorSecretKey = connectorSecretKey;
    this.internalApiToken = internalApiToken;
  }

  @PostConstruct
  void validate() {
    validateJwtSecret();
    validateConnectorSecretKey();
    validateInternalApiToken();
  }

  private void validateJwtSecret() {
    if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.length() < 32) {
      throw new IllegalStateException("JWT_SECRET must be configured with a value of at least 32 characters.");
    }
    if (JWT_SECRET_PLACEHOLDER.equals(jwtSecret) || PUBLISHED_JWT_SECRET_SHA256.equals(sha256(jwtSecret))) {
      throw new IllegalStateException("JWT_SECRET must not reuse the tracked placeholder or the published default value.");
    }
  }

  private void validateConnectorSecretKey() {
    if (connectorSecretKey == null || connectorSecretKey.isBlank()) {
      throw new IllegalStateException("CONNECTOR_SECRET_KEY must be configured with a base64-encoded 32-byte AES key.");
    }
    if (CONNECTOR_SECRET_KEY_PLACEHOLDER.equals(connectorSecretKey) || PUBLISHED_CONNECTOR_SECRET_KEY_SHA256.equals(sha256(connectorSecretKey))) {
      throw new IllegalStateException("CONNECTOR_SECRET_KEY must not reuse the tracked placeholder or the published default value.");
    }

    try {
      byte[] decoded = Base64.getDecoder().decode(connectorSecretKey);
      if (decoded.length != 32) {
        throw new IllegalStateException("CONNECTOR_SECRET_KEY must decode to exactly 32 bytes.");
      }
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("CONNECTOR_SECRET_KEY must be valid base64 and decode to 32 bytes.", exception);
    }
  }

  private void validateInternalApiToken() {
    if (internalApiToken == null || internalApiToken.isBlank() || internalApiToken.length() < 24) {
      throw new IllegalStateException("INTERNAL_API_TOKEN must be configured with a value of at least 24 characters.");
    }
    if (INTERNAL_API_TOKEN_PLACEHOLDER.equals(internalApiToken) || PUBLISHED_INTERNAL_API_TOKEN_SHA256.equals(sha256(internalApiToken))) {
      throw new IllegalStateException("INTERNAL_API_TOKEN must not reuse the tracked placeholder or the published default value.");
    }
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to validate secret fingerprints.", exception);
    }
  }
}
