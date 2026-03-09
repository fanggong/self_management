package com.otw.adminapi.connector;

import java.util.Map;
import jakarta.validation.constraints.NotBlank;

public record SaveConnectorConfigurationRequest(
  @NotBlank String schedule,
  Map<String, String> config
) {
}
