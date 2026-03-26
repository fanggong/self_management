package com.otw.adminapi.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.otw.adminapi.common.api.ApiResult;
import com.otw.adminapi.security.AuthenticatedUser;
import com.otw.adminapi.security.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class HealthDashboardControllerTest {
  @Mock
  private HealthDashboardService healthDashboardService;

  @Mock
  private JwtService jwtService;

  private HealthDashboardController controller;
  private AuthenticatedUser authenticatedUser;
  private Jwt jwt;

  @BeforeEach
  void setUp() {
    controller = new HealthDashboardController(healthDashboardService, jwtService);
    authenticatedUser = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "tester", "USER");
    jwt = Jwt.withTokenValue("token")
      .header("alg", "HS256")
      .subject(authenticatedUser.userId().toString())
      .claim("accountId", authenticatedUser.accountId().toString())
      .claim("principal", authenticatedUser.principal())
      .claim("role", authenticatedUser.role())
      .build();
  }

  @Test
  void getSummaryReturnsApiResult() {
    HealthDashboardSummaryView summary = new HealthDashboardSummaryView(
      "2026-03-19",
      new HealthHeartRateCardView(114, 58, 96),
      new HealthWeightCardView(
        new BigDecimal("68.20"),
        new BigDecimal("22.40"),
        new BigDecimal("67.40"),
        new BigDecimal("0.80"),
        new BigDecimal("1.19")
      ),
      new HealthCaloriesCardView(new BigDecimal("1411"), new BigDecimal("157")),
      new HealthStressCardView(new BigDecimal("24"), 1200L, 1800L, 900L, 3600L)
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(healthDashboardService.getSummary(authenticatedUser)).thenReturn(summary);

    ApiResult<HealthDashboardSummaryView> result = controller.getSummary(jwt);

    assertEquals(true, result.success());
    assertEquals(summary, result.data());
  }

  @Test
  void getHeartRateCardReturnsApiResult() {
    LocalDate date = LocalDate.of(2026, 3, 19);
    HealthCardResponseView<HealthHeartRateCardView> response = new HealthCardResponseView<>(
      "2026-03-19",
      new HealthHeartRateCardView(114, 58, 96)
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(healthDashboardService.getHeartRateCard(authenticatedUser, date)).thenReturn(response);

    ApiResult<HealthCardResponseView<HealthHeartRateCardView>> result = controller.getHeartRateCard(jwt, date);

    assertEquals(true, result.success());
    assertEquals(response, result.data());
  }

  @Test
  void getHeartRateCardWithoutDateReturnsApiResult() {
    HealthCardResponseView<HealthHeartRateCardView> response = new HealthCardResponseView<>(
      "2026-03-18",
      new HealthHeartRateCardView(134, 69, 87)
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(healthDashboardService.getHeartRateCard(authenticatedUser, null)).thenReturn(response);

    ApiResult<HealthCardResponseView<HealthHeartRateCardView>> result = controller.getHeartRateCard(jwt, null);

    assertEquals(true, result.success());
    assertEquals(response, result.data());
  }

  @Test
  void getWeightCardReturnsApiResult() {
    LocalDate date = LocalDate.of(2026, 3, 19);
    HealthCardResponseView<HealthWeightCardView> response = new HealthCardResponseView<>(
      "2026-03-19",
      new HealthWeightCardView(
        new BigDecimal("68.20"),
        new BigDecimal("22.40"),
        new BigDecimal("67.40"),
        new BigDecimal("0.80"),
        new BigDecimal("1.19")
      )
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(healthDashboardService.getWeightCard(authenticatedUser, date)).thenReturn(response);

    ApiResult<HealthCardResponseView<HealthWeightCardView>> result = controller.getWeightCard(jwt, date);

    assertEquals(true, result.success());
    assertEquals(response, result.data());
  }

  @Test
  void getCaloriesCardReturnsApiResult() {
    LocalDate date = LocalDate.of(2026, 3, 19);
    HealthCardResponseView<HealthCaloriesCardView> response = new HealthCardResponseView<>(
      "2026-03-19",
      new HealthCaloriesCardView(new BigDecimal("1411"), new BigDecimal("157"))
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(healthDashboardService.getCaloriesCard(authenticatedUser, date)).thenReturn(response);

    ApiResult<HealthCardResponseView<HealthCaloriesCardView>> result = controller.getCaloriesCard(jwt, date);

    assertEquals(true, result.success());
    assertEquals(response, result.data());
  }

  @Test
  void getStressCardReturnsApiResult() {
    LocalDate date = LocalDate.of(2026, 3, 19);
    HealthCardResponseView<HealthStressCardView> response = new HealthCardResponseView<>(
      "2026-03-19",
      new HealthStressCardView(new BigDecimal("24"), 1200L, 1800L, 900L, 3600L)
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(healthDashboardService.getStressCard(authenticatedUser, date)).thenReturn(response);

    ApiResult<HealthCardResponseView<HealthStressCardView>> result = controller.getStressCard(jwt, date);

    assertEquals(true, result.success());
    assertEquals(response, result.data());
  }

  @Test
  void listActivitiesReturnsApiResult() {
    HealthActivityListResponse response = new HealthActivityListResponse(
      List.of(new HealthActivityListItemView(
        UUID.randomUUID(),
        "室内骑行",
        "indoor_cycling",
        "2026-01-27 20:53:16",
        "2026-01-27 21:33:01",
        new BigDecimal("2385.79"),
        new BigDecimal("357"),
        128,
        156
      )),
      new HealthActivityPageView(1, 10, 1, 1)
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(healthDashboardService.listActivities(authenticatedUser, 1, 10, null)).thenReturn(response);

    ApiResult<HealthActivityListResponse> result = controller.listActivities(jwt, 1, 10, null);

    assertEquals(true, result.success());
    assertEquals(response, result.data());
  }

  @Test
  void getActivityDetailReturnsApiResult() {
    UUID activityRecordId = UUID.randomUUID();
    HealthActivityDetailView response = new HealthActivityDetailView(
      activityRecordId,
      "室内骑行",
      "indoor_cycling",
      List.of(new HealthDetailFieldView("Activity Name", "室内骑行")),
      List.of(new HealthDetailFieldView("Distance", "23.50 km")),
      List.of(new HealthDetailFieldView("Average HR", "128 bpm")),
      List.of(new HealthDetailFieldView("Start Time", "2026-01-27 20:53:16"))
    );
    when(jwtService.toAuthenticatedUser(jwt)).thenReturn(authenticatedUser);
    when(healthDashboardService.getActivityDetail(authenticatedUser, activityRecordId)).thenReturn(response);

    ApiResult<HealthActivityDetailView> result = controller.getActivityDetail(jwt, activityRecordId);

    assertEquals(true, result.success());
    assertEquals(response, result.data());
  }
}
