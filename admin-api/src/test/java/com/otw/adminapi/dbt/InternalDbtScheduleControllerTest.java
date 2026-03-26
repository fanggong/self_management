package com.otw.adminapi.dbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.otw.adminapi.common.api.ApiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalDbtScheduleControllerTest {
  @Mock
  private DbtModelService dbtModelService;

  private InternalDbtScheduleController controller;

  @BeforeEach
  void setUp() {
    controller = new InternalDbtScheduleController(dbtModelService);
  }

  @Test
  void runDefaultMartsScheduleReturnsApiResult() {
    DefaultScheduledMartsRunResultView response = new DefaultScheduledMartsRunResultView(2, 1, 5, 1);
    when(dbtModelService.runDefaultMartsSchedule()).thenReturn(response);

    ApiResult<DefaultScheduledMartsRunResultView> result = controller.runDefaultMartsSchedule();

    assertEquals(true, result.success());
    assertEquals(response, result.data());
    verify(dbtModelService).runDefaultMartsSchedule();
  }
}
