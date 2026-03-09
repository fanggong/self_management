package com.otw.adminapi.sync;

import jakarta.validation.constraints.NotBlank;

public record CreateSyncJobRequest(
  @NotBlank String startAt,
  @NotBlank String endAt
) {
}
