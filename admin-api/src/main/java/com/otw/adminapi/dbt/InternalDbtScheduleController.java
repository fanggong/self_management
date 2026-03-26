package com.otw.adminapi.dbt;

import com.otw.adminapi.common.api.ApiResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/dbt/default-schedules")
public class InternalDbtScheduleController {
  private final DbtModelService dbtModelService;

  public InternalDbtScheduleController(DbtModelService dbtModelService) {
    this.dbtModelService = dbtModelService;
  }

  @PostMapping("/marts/run")
  public ApiResult<DefaultScheduledMartsRunResultView> runDefaultMartsSchedule() {
    return ApiResult.success(dbtModelService.runDefaultMartsSchedule());
  }
}
