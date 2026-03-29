package com.otw.adminapi.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CryptoServiceTest {
  private static final String CONNECTOR_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

  @Test
  void encryptAndDecryptRoundTripsConnectorPayload() {
    CryptoService cryptoService = new CryptoService(new ObjectMapper(), CONNECTOR_KEY);
    String ciphertext = cryptoService.encrypt(Map.of("username", "alice", "password", "secret-password"));

    Map<String, String> decrypted = cryptoService.decrypt(ciphertext);

    assertEquals("alice", decrypted.get("username"));
    assertEquals("secret-password", decrypted.get("password"));
  }

  @Test
  void decryptReturnsEmptyMapForBlankCiphertext() {
    CryptoService cryptoService = new CryptoService(new ObjectMapper(), CONNECTOR_KEY);
    assertEquals(Collections.emptyMap(), cryptoService.decrypt(""));
  }
}
