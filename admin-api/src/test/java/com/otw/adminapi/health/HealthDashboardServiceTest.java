package com.otw.adminapi.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.otw.adminapi.common.api.ApiException;
import com.otw.adminapi.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class HealthDashboardServiceTest {
  @Mock
  private JdbcTemplate jdbcTemplate;

  private HealthDashboardService service;
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    service = new HealthDashboardService(jdbcTemplate, "Asia/Shanghai");
    authenticatedUser = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "tester", "USER");
  }

  @Test
  void getSummaryReturnsNullWhenMartTableIsUnavailable() {
    when(jdbcTemplate.query(
      anyString(),
      org.mockito.ArgumentMatchers.<ResultSetExtractor<Boolean>>any(),
      eq("mart_health_dashboard_daily")
    )).thenReturn(false);

    HealthDashboardSummaryView summary = service.getSummary(authenticatedUser);

    assertNull(summary);
  }

  @Test
  void listActivitiesReturnsPagedItemsFromMartTable() {
    when(jdbcTemplate.query(
      anyString(),
      org.mockito.ArgumentMatchers.<ResultSetExtractor<Boolean>>any(),
      eq("mart_health_activity_history")
    )).thenReturn(true);
    when(jdbcTemplate.query(
      anyString(),
      org.mockito.ArgumentMatchers.<ResultSetExtractor<Integer>>any(),
      eq(authenticatedUser.accountId())
    )).thenReturn(2);
    when(jdbcTemplate.query(
      anyString(),
      org.mockito.ArgumentMatchers.<RowMapper<HealthActivityListItemView>>any(),
      eq(authenticatedUser.accountId()),
      eq(10),
      eq(0)
    )).thenReturn(List.of(
      new HealthActivityListItemView(
        UUID.randomUUID(),
        "室内骑行",
        "indoor_cycling",
        "2026-01-27 20:53:16",
        "2026-01-27 21:33:01",
        new BigDecimal("2385.79"),
        new BigDecimal("357"),
        128,
        156
      )
    ));

    HealthActivityListResponse response = service.listActivities(authenticatedUser, 1, 10, null);

    assertEquals(1, response.items().size());
    assertEquals(2, response.page().total());
    assertEquals(1, response.page().totalPages());
  }

  @Test
  void getActivityDetailThrowsNotFoundWhenMartTableIsUnavailable() {
    when(jdbcTemplate.query(
      anyString(),
      org.mockito.ArgumentMatchers.<ResultSetExtractor<Boolean>>any(),
      eq("mart_health_activity_history")
    )).thenReturn(false);

    ApiException exception = assertThrows(
      ApiException.class,
      () -> service.getActivityDetail(authenticatedUser, UUID.randomUUID())
    );

    assertEquals("HEALTH_ACTIVITY_NOT_FOUND", exception.getCode());
  }

  @Test
  void listActivitiesReturnsEmptyPageWhenMartTableIsUnavailable() {
    when(jdbcTemplate.query(
      anyString(),
      org.mockito.ArgumentMatchers.<ResultSetExtractor<Boolean>>any(),
      eq("mart_health_activity_history")
    )).thenReturn(false);

    HealthActivityListResponse response = service.listActivities(authenticatedUser, 1, 10, null);

    assertNotNull(response);
    assertEquals(0, response.items().size());
    assertEquals(0, response.page().total());
  }
}
