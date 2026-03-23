package com.otw.adminapi.dbt;

import jakarta.validation.constraints.NotBlank;

public record RunDbtModelRequest(
  @NotBlank String layer,
  @NotBlank String modelName
) {
}
