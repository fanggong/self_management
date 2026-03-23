package com.otw.adminapi.dbt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RunDbtModelsByScopeRequest(
  @NotBlank String layer,
  @NotBlank String scopeType,
  @NotEmpty List<@NotBlank String> scopeValues
) {
}
