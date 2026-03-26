package com.otw.adminapi.dbt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RunDbtModelsRequest(
  @NotBlank String layer,
  @NotBlank String selectionType,
  @NotEmpty List<@NotBlank String> modelNames
) {
}
