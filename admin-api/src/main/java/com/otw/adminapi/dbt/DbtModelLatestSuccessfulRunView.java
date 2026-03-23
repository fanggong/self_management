package com.otw.adminapi.dbt;

import java.time.Instant;

public interface DbtModelLatestSuccessfulRunView {
  String getModelName();

  String getLayer();

  Instant getCompletedAt();
}
