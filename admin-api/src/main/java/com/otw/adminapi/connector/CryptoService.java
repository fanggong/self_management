package com.otw.adminapi.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otw.adminapi.common.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CryptoService {
  private static final int GCM_TAG_LENGTH = 128;
  private static final int IV_LENGTH = 12;

  private final ObjectMapper objectMapper;
  private final SecretKeySpec secretKeySpec;
  private final SecureRandom secureRandom = new SecureRandom();

  public CryptoService(ObjectMapper objectMapper, @Value("${app.connector.secret-key}") String base64Key) {
    this.objectMapper = objectMapper;
    this.secretKeySpec = new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
  }

  public String encrypt(Map<String, String> payload) {
    if (payload == null || payload.isEmpty()) {
      return null;
    }

    try {
      byte[] iv = new byte[IV_LENGTH];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      byte[] ciphertext = cipher.doFinal(objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(ciphertext);
    } catch (Exception exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONNECTOR_ENCRYPTION_ERROR", "Unable to encrypt connector configuration.");
    }
  }

  public Map<String, String> decrypt(String encryptedValue) {
    if (encryptedValue == null || encryptedValue.isBlank()) {
      return Collections.emptyMap();
    }

    try {
      String[] parts = encryptedValue.split("\\.");
      if (parts.length != 2) {
        throw new IllegalArgumentException("Encrypted connector configuration is malformed.");
      }

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
        Cipher.DECRYPT_MODE,
        secretKeySpec,
        new GCMParameterSpec(GCM_TAG_LENGTH, Base64.getDecoder().decode(parts[0]))
      );
      byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(parts[1]));
      return objectMapper.readValue(plaintext, new TypeReference<>() {});
    } catch (Exception exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONNECTOR_DECRYPTION_ERROR", "Unable to decrypt connector configuration.");
    }
  }
}
