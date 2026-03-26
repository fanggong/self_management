package com.otw.adminapi.health;

import com.otw.adminapi.common.api.ApiException;
import com.otw.adminapi.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthDashboardService {
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final JdbcTemplate jdbcTemplate;
  private final ZoneId appZoneId;

  public HealthDashboardService(
    JdbcTemplate jdbcTemplate,
    @Value("${app.timezone:Asia/Shanghai}") String appTimezone
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.appZoneId = ZoneId.of(appTimezone);
  }

  public HealthDashboardSummaryView getSummary(AuthenticatedUser authenticatedUser) {
    if (!isMartTableAvailable("mart_health_dashboard_daily")) {
      return null;
    }

    LocalDate yesterday = LocalDate.now(appZoneId).minusDays(1);
    return jdbcTemplate.query(
      """
        WITH current_summary AS (
          SELECT
            metric_date,
            max_heart_rate,
            resting_heart_rate,
            average_heart_rate,
            weight_kg,
            bmi,
            bmr_kilocalories,
            active_kilocalories,
            average_stress_level,
            low_stress_duration,
            medium_stress_duration,
            high_stress_duration,
            rest_stress_duration
          FROM marts.mart_health_dashboard_daily
          WHERE account_id = ?
            AND metric_date <= ?
          ORDER BY metric_date DESC
          LIMIT 1
        )
        SELECT
          current_summary.metric_date,
          current_summary.max_heart_rate,
          current_summary.resting_heart_rate,
          current_summary.average_heart_rate,
          current_summary.weight_kg,
          current_summary.bmi,
          previous_weight.previous_weight_kg,
          current_summary.bmr_kilocalories,
          current_summary.active_kilocalories,
          current_summary.average_stress_level,
          current_summary.low_stress_duration,
          current_summary.medium_stress_duration,
          current_summary.high_stress_duration,
          current_summary.rest_stress_duration
        FROM current_summary
        LEFT JOIN LATERAL (
          SELECT candidate.weight_kg AS previous_weight_kg
          FROM marts.mart_health_dashboard_daily AS candidate
          WHERE candidate.account_id = ?
            AND candidate.metric_date < current_summary.metric_date
            AND candidate.weight_kg IS NOT NULL
          ORDER BY candidate.metric_date DESC
          LIMIT 1
        ) AS previous_weight ON true
      """,
      rs -> rs.next() ? mapSummaryRow(rs) : null,
      authenticatedUser.accountId(),
      yesterday,
      authenticatedUser.accountId()
    );
  }

  public HealthCardResponseView<HealthHeartRateCardView> getHeartRateCard(
    AuthenticatedUser authenticatedUser,
    LocalDate date
  ) {
    LocalDate requestedDate = date != null ? validateRequestedDate(date) : null;
    String formattedDate = formatDate(requestedDate);

    if (!isMartTableAvailable("mart_health_dashboard_daily")) {
      return new HealthCardResponseView<>(formattedDate, null);
    }

    if (requestedDate == null) {
      return jdbcTemplate.query(
        """
          SELECT
            metric_date,
            max_heart_rate,
            resting_heart_rate,
            average_heart_rate
          FROM marts.mart_health_dashboard_daily
          WHERE account_id = ?
            AND metric_date <= ?
            AND (
              max_heart_rate IS NOT NULL
              OR resting_heart_rate IS NOT NULL
              OR average_heart_rate IS NOT NULL
            )
          ORDER BY metric_date DESC
          LIMIT 1
        """,
        rs -> {
          if (!rs.next()) {
            return new HealthCardResponseView<HealthHeartRateCardView>(null, null);
          }

          return new HealthCardResponseView<>(
            formatDate(rs.getObject("metric_date", LocalDate.class)),
            new HealthHeartRateCardView(
              getNullableInteger(rs, "max_heart_rate"),
              getNullableInteger(rs, "resting_heart_rate"),
              getNullableInteger(rs, "average_heart_rate")
            )
          );
        },
        authenticatedUser.accountId(),
        latestAllowedDate()
      );
    }

    HealthHeartRateCardView card = jdbcTemplate.query(
      """
        SELECT
          max_heart_rate,
          resting_heart_rate,
          average_heart_rate
        FROM marts.mart_health_dashboard_daily
        WHERE account_id = ?
          AND metric_date = ?
        LIMIT 1
      """,
      rs -> {
        if (!rs.next()) {
          return null;
        }

        HealthHeartRateCardView view = new HealthHeartRateCardView(
          getNullableInteger(rs, "max_heart_rate"),
          getNullableInteger(rs, "resting_heart_rate"),
          getNullableInteger(rs, "average_heart_rate")
        );
        return hasHeartRateData(view) ? view : null;
      },
      authenticatedUser.accountId(),
      requestedDate
    );

    return new HealthCardResponseView<>(formattedDate, card);
  }

  public HealthCardResponseView<HealthWeightCardView> getWeightCard(
    AuthenticatedUser authenticatedUser,
    LocalDate date
  ) {
    LocalDate requestedDate = date != null ? validateRequestedDate(date) : null;
    String formattedDate = formatDate(requestedDate);

    if (!isMartTableAvailable("mart_health_dashboard_daily")) {
      return new HealthCardResponseView<>(formattedDate, null);
    }

    if (requestedDate == null) {
      return jdbcTemplate.query(
        """
          WITH selected_summary AS (
            SELECT
              metric_date,
              weight_kg,
              bmi
            FROM marts.mart_health_dashboard_daily
            WHERE account_id = ?
              AND metric_date <= ?
              AND (weight_kg IS NOT NULL OR bmi IS NOT NULL)
            ORDER BY metric_date DESC
            LIMIT 1
          )
          SELECT
            selected_summary.metric_date,
            selected_summary.weight_kg,
            selected_summary.bmi,
            previous_weight.previous_weight_kg
          FROM selected_summary
          LEFT JOIN LATERAL (
            SELECT candidate.weight_kg AS previous_weight_kg
            FROM marts.mart_health_dashboard_daily AS candidate
            WHERE candidate.account_id = ?
              AND candidate.metric_date < selected_summary.metric_date
              AND candidate.weight_kg IS NOT NULL
            ORDER BY candidate.metric_date DESC
            LIMIT 1
          ) AS previous_weight ON true
        """,
        rs -> {
          if (!rs.next()) {
            return new HealthCardResponseView<HealthWeightCardView>(null, null);
          }

          return new HealthCardResponseView<>(
            formatDate(rs.getObject("metric_date", LocalDate.class)),
            buildWeightCard(
              rs.getBigDecimal("weight_kg"),
              rs.getBigDecimal("bmi"),
              rs.getBigDecimal("previous_weight_kg")
            )
          );
        },
        authenticatedUser.accountId(),
        latestAllowedDate(),
        authenticatedUser.accountId()
      );
    }

    HealthWeightCardView card = jdbcTemplate.query(
      """
        WITH selected_summary AS (
          SELECT
            metric_date,
            weight_kg,
            bmi
          FROM marts.mart_health_dashboard_daily
          WHERE account_id = ?
            AND metric_date = ?
          LIMIT 1
        )
        SELECT
          selected_summary.weight_kg,
          selected_summary.bmi,
          previous_weight.previous_weight_kg
        FROM selected_summary
        LEFT JOIN LATERAL (
          SELECT candidate.weight_kg AS previous_weight_kg
          FROM marts.mart_health_dashboard_daily AS candidate
          WHERE candidate.account_id = ?
            AND candidate.metric_date < selected_summary.metric_date
            AND candidate.weight_kg IS NOT NULL
          ORDER BY candidate.metric_date DESC
          LIMIT 1
        ) AS previous_weight ON true
      """,
      rs -> {
        if (!rs.next()) {
          return null;
        }

        HealthWeightCardView view = buildWeightCard(
          rs.getBigDecimal("weight_kg"),
          rs.getBigDecimal("bmi"),
          rs.getBigDecimal("previous_weight_kg")
        );
        return hasWeightData(view) ? view : null;
      },
      authenticatedUser.accountId(),
      requestedDate,
      authenticatedUser.accountId()
    );

    return new HealthCardResponseView<>(formattedDate, card);
  }

  public HealthCardResponseView<HealthCaloriesCardView> getCaloriesCard(
    AuthenticatedUser authenticatedUser,
    LocalDate date
  ) {
    LocalDate requestedDate = date != null ? validateRequestedDate(date) : null;
    String formattedDate = formatDate(requestedDate);

    if (!isMartTableAvailable("mart_health_dashboard_daily")) {
      return new HealthCardResponseView<>(formattedDate, null);
    }

    if (requestedDate == null) {
      return jdbcTemplate.query(
        """
          SELECT
            metric_date,
            bmr_kilocalories,
            active_kilocalories
          FROM marts.mart_health_dashboard_daily
          WHERE account_id = ?
            AND metric_date <= ?
            AND (
              bmr_kilocalories IS NOT NULL
              OR active_kilocalories IS NOT NULL
            )
          ORDER BY metric_date DESC
          LIMIT 1
        """,
        rs -> {
          if (!rs.next()) {
            return new HealthCardResponseView<HealthCaloriesCardView>(null, null);
          }

          return new HealthCardResponseView<>(
            formatDate(rs.getObject("metric_date", LocalDate.class)),
            new HealthCaloriesCardView(
              rs.getBigDecimal("bmr_kilocalories"),
              rs.getBigDecimal("active_kilocalories")
            )
          );
        },
        authenticatedUser.accountId(),
        latestAllowedDate()
      );
    }

    HealthCaloriesCardView card = jdbcTemplate.query(
      """
        SELECT
          bmr_kilocalories,
          active_kilocalories
        FROM marts.mart_health_dashboard_daily
        WHERE account_id = ?
          AND metric_date = ?
        LIMIT 1
      """,
      rs -> {
        if (!rs.next()) {
          return null;
        }

        HealthCaloriesCardView view = new HealthCaloriesCardView(
          rs.getBigDecimal("bmr_kilocalories"),
          rs.getBigDecimal("active_kilocalories")
        );
        return hasCaloriesData(view) ? view : null;
      },
      authenticatedUser.accountId(),
      requestedDate
    );

    return new HealthCardResponseView<>(formattedDate, card);
  }

  public HealthCardResponseView<HealthStressCardView> getStressCard(
    AuthenticatedUser authenticatedUser,
    LocalDate date
  ) {
    LocalDate requestedDate = date != null ? validateRequestedDate(date) : null;
    String formattedDate = formatDate(requestedDate);

    if (!isMartTableAvailable("mart_health_dashboard_daily")) {
      return new HealthCardResponseView<>(formattedDate, null);
    }

    if (requestedDate == null) {
      return jdbcTemplate.query(
        """
          SELECT
            metric_date,
            average_stress_level,
            low_stress_duration,
            medium_stress_duration,
            high_stress_duration,
            rest_stress_duration
          FROM marts.mart_health_dashboard_daily
          WHERE account_id = ?
            AND metric_date <= ?
            AND (
              average_stress_level IS NOT NULL
              OR low_stress_duration IS NOT NULL
              OR medium_stress_duration IS NOT NULL
              OR high_stress_duration IS NOT NULL
              OR rest_stress_duration IS NOT NULL
            )
          ORDER BY metric_date DESC
          LIMIT 1
        """,
        rs -> {
          if (!rs.next()) {
            return new HealthCardResponseView<HealthStressCardView>(null, null);
          }

          return new HealthCardResponseView<>(
            formatDate(rs.getObject("metric_date", LocalDate.class)),
            new HealthStressCardView(
              rs.getBigDecimal("average_stress_level"),
              getNullableLong(rs, "low_stress_duration"),
              getNullableLong(rs, "medium_stress_duration"),
              getNullableLong(rs, "high_stress_duration"),
              getNullableLong(rs, "rest_stress_duration")
            )
          );
        },
        authenticatedUser.accountId(),
        latestAllowedDate()
      );
    }

    HealthStressCardView card = jdbcTemplate.query(
      """
        SELECT
          average_stress_level,
          low_stress_duration,
          medium_stress_duration,
          high_stress_duration,
          rest_stress_duration
        FROM marts.mart_health_dashboard_daily
        WHERE account_id = ?
          AND metric_date = ?
        LIMIT 1
      """,
      rs -> {
        if (!rs.next()) {
          return null;
        }

        HealthStressCardView view = new HealthStressCardView(
          rs.getBigDecimal("average_stress_level"),
          getNullableLong(rs, "low_stress_duration"),
          getNullableLong(rs, "medium_stress_duration"),
          getNullableLong(rs, "high_stress_duration"),
          getNullableLong(rs, "rest_stress_duration")
        );
        return hasStressData(view) ? view : null;
      },
      authenticatedUser.accountId(),
      requestedDate
    );

    return new HealthCardResponseView<>(formattedDate, card);
  }

  public HealthActivityListResponse listActivities(
    AuthenticatedUser authenticatedUser,
    Integer page,
    Integer pageSize,
    String search
  ) {
    int resolvedPage = page != null && page > 0 ? page : 1;
    int resolvedPageSize = pageSize != null && pageSize > 0 ? pageSize : 10;
    String normalizedSearch = normalizeSearch(search);

    if (!isMartTableAvailable("mart_health_activity_history")) {
      return new HealthActivityListResponse(List.of(), new HealthActivityPageView(resolvedPage, resolvedPageSize, 0, 0));
    }

    Integer total;
    if (normalizedSearch == null) {
      total = jdbcTemplate.query(
        """
          SELECT count(*)::int
          FROM marts.mart_health_activity_history
          WHERE account_id = ?
        """,
        rs -> rs.next() ? rs.getInt(1) : 0,
        authenticatedUser.accountId()
      );
    } else {
      total = jdbcTemplate.query(
        """
          SELECT count(*)::int
          FROM marts.mart_health_activity_history
          WHERE account_id = ?
            AND coalesce(activity_name, '') ILIKE ?
        """,
        rs -> rs.next() ? rs.getInt(1) : 0,
        authenticatedUser.accountId(),
        toSearchPattern(normalizedSearch)
      );
    }
    int totalRecords = total != null ? total : 0;
    int totalPages = totalRecords == 0 ? 0 : (int) Math.ceil(totalRecords / (double) resolvedPageSize);
    int offset = (resolvedPage - 1) * resolvedPageSize;

    List<HealthActivityListItemView> items;
    if (normalizedSearch == null) {
      items = jdbcTemplate.query(
        """
          SELECT
            activity_record_id,
            activity_name,
            activity_type,
            start_time_gmt,
            end_time_gmt,
            duration_seconds,
            calories_kilocalories,
            average_hr,
            max_hr
          FROM marts.mart_health_activity_history
          WHERE account_id = ?
          ORDER BY start_time_gmt DESC NULLS LAST, activity_record_id DESC
          LIMIT ?
          OFFSET ?
        """,
        (rs, rowNum) -> new HealthActivityListItemView(
          rs.getObject("activity_record_id", UUID.class),
          rs.getString("activity_name"),
          rs.getString("activity_type"),
          formatDateTime(rs.getObject("start_time_gmt", OffsetDateTime.class)),
          formatDateTime(rs.getObject("end_time_gmt", OffsetDateTime.class)),
          rs.getBigDecimal("duration_seconds"),
          rs.getBigDecimal("calories_kilocalories"),
          getNullableInteger(rs, "average_hr"),
          getNullableInteger(rs, "max_hr")
        ),
        authenticatedUser.accountId(),
        resolvedPageSize,
        offset
      );
    } else {
      items = jdbcTemplate.query(
        """
          SELECT
            activity_record_id,
            activity_name,
            activity_type,
            start_time_gmt,
            end_time_gmt,
            duration_seconds,
            calories_kilocalories,
            average_hr,
            max_hr
          FROM marts.mart_health_activity_history
          WHERE account_id = ?
            AND coalesce(activity_name, '') ILIKE ?
          ORDER BY start_time_gmt DESC NULLS LAST, activity_record_id DESC
          LIMIT ?
          OFFSET ?
        """,
        (rs, rowNum) -> new HealthActivityListItemView(
          rs.getObject("activity_record_id", UUID.class),
          rs.getString("activity_name"),
          rs.getString("activity_type"),
          formatDateTime(rs.getObject("start_time_gmt", OffsetDateTime.class)),
          formatDateTime(rs.getObject("end_time_gmt", OffsetDateTime.class)),
          rs.getBigDecimal("duration_seconds"),
          rs.getBigDecimal("calories_kilocalories"),
          getNullableInteger(rs, "average_hr"),
          getNullableInteger(rs, "max_hr")
        ),
        authenticatedUser.accountId(),
        toSearchPattern(normalizedSearch),
        resolvedPageSize,
        offset
      );
    }

    return new HealthActivityListResponse(
      items,
      new HealthActivityPageView(resolvedPage, resolvedPageSize, totalRecords, totalPages)
    );
  }

  public HealthActivityDetailView getActivityDetail(AuthenticatedUser authenticatedUser, UUID activityRecordId) {
    if (!isMartTableAvailable("mart_health_activity_history")) {
      throw new ApiException(HttpStatus.NOT_FOUND, "HEALTH_ACTIVITY_NOT_FOUND", "Activity not found.");
    }

    HealthActivityDetailView detail = jdbcTemplate.query(
      """
        SELECT
          activity_record_id,
          activity_name,
          activity_type,
          start_time_gmt,
          end_time_gmt,
          start_time_local,
          time_zone_id,
          source_record_date,
          duration_seconds,
          moving_duration_seconds,
          elapsed_duration_seconds,
          distance_meters,
          calories_kilocalories,
          bmr_kilocalories,
          average_hr,
          max_hr,
          average_speed_meters_per_second,
          max_speed_meters_per_second,
          elevation_gain_meters,
          elevation_loss_meters,
          lap_count,
          training_effect_label,
          aerobic_training_effect,
          anaerobic_training_effect,
          hr_time_in_zone_1,
          hr_time_in_zone_2,
          hr_time_in_zone_3,
          hr_time_in_zone_4,
          hr_time_in_zone_5,
          location_name,
          start_latitude,
          start_longitude,
          end_latitude,
          end_longitude,
          manufacturer,
          owner_display_name,
          owner_full_name,
          sport_type_id,
          privacy_type_key
        FROM marts.mart_health_activity_history
        WHERE account_id = ?
          AND activity_record_id = ?
      """,
      rs -> rs.next() ? mapActivityDetailRow(rs) : null,
      authenticatedUser.accountId(),
      activityRecordId
    );

    if (detail == null) {
      throw new ApiException(HttpStatus.NOT_FOUND, "HEALTH_ACTIVITY_NOT_FOUND", "Activity not found.");
    }

    return detail;
  }

  private boolean isMartTableAvailable(String tableName) {
    Boolean available = jdbcTemplate.query(
      """
        SELECT EXISTS (
          SELECT 1
          FROM information_schema.tables
          WHERE table_schema = 'marts'
            AND table_name = ?
        )
      """,
      rs -> rs.next() && rs.getBoolean(1),
      tableName
    );
    return Boolean.TRUE.equals(available);
  }

  private String normalizeSearch(String search) {
    if (search == null) {
      return null;
    }

    String trimmed = search.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String toSearchPattern(String search) {
    return "%" + search + "%";
  }

  private HealthDashboardSummaryView mapSummaryRow(ResultSet rs) throws SQLException {
    LocalDate summaryDate = rs.getObject("metric_date", LocalDate.class);
    return new HealthDashboardSummaryView(
      summaryDate != null ? summaryDate.format(DATE_FORMATTER) : null,
      new HealthHeartRateCardView(
        getNullableInteger(rs, "max_heart_rate"),
        getNullableInteger(rs, "resting_heart_rate"),
        getNullableInteger(rs, "average_heart_rate")
      ),
      buildWeightCard(
        rs.getBigDecimal("weight_kg"),
        rs.getBigDecimal("bmi"),
        rs.getBigDecimal("previous_weight_kg")
      ),
      new HealthCaloriesCardView(
        rs.getBigDecimal("bmr_kilocalories"),
        rs.getBigDecimal("active_kilocalories")
      ),
      new HealthStressCardView(
        rs.getBigDecimal("average_stress_level"),
        getNullableLong(rs, "low_stress_duration"),
        getNullableLong(rs, "medium_stress_duration"),
        getNullableLong(rs, "high_stress_duration"),
        getNullableLong(rs, "rest_stress_duration")
      )
    );
  }

  private HealthWeightCardView buildWeightCard(
    BigDecimal currentWeightKg,
    BigDecimal bmi,
    BigDecimal previousWeightKg
  ) {
    BigDecimal weightDeltaKg = null;
    BigDecimal weightDeltaPercent = null;

    if (currentWeightKg != null && previousWeightKg != null && previousWeightKg.compareTo(BigDecimal.ZERO) != 0) {
      weightDeltaKg = currentWeightKg.subtract(previousWeightKg).setScale(2, RoundingMode.HALF_UP);
      weightDeltaPercent = weightDeltaKg
        .multiply(BigDecimal.valueOf(100))
        .divide(previousWeightKg, 2, RoundingMode.HALF_UP);
    }

    return new HealthWeightCardView(
      currentWeightKg,
      bmi,
      previousWeightKg,
      weightDeltaKg,
      weightDeltaPercent
    );
  }

  private LocalDate validateRequestedDate(LocalDate date) {
    if (date == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Date is required.");
    }

    LocalDate yesterday = latestAllowedDate();
    if (date.isAfter(yesterday)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Date must be on or before yesterday.");
    }

    return date;
  }

  private LocalDate latestAllowedDate() {
    return LocalDate.now(appZoneId).minusDays(1);
  }

  private boolean hasHeartRateData(HealthHeartRateCardView view) {
    return view != null && (view.highest() != null || view.resting() != null || view.average() != null);
  }

  private boolean hasWeightData(HealthWeightCardView view) {
    return view != null && (view.weightKg() != null || view.bmi() != null);
  }

  private boolean hasCaloriesData(HealthCaloriesCardView view) {
    return view != null && (view.restingBurn() != null || view.activeBurn() != null);
  }

  private boolean hasStressData(HealthStressCardView view) {
    return view != null && (
      view.overall() != null ||
      view.lowDurationSeconds() != null ||
      view.mediumDurationSeconds() != null ||
      view.highDurationSeconds() != null ||
      view.restDurationSeconds() != null
    );
  }

  private HealthActivityDetailView mapActivityDetailRow(ResultSet rs) throws SQLException {
    List<HealthDetailFieldView> basics = new ArrayList<>();
    addField(basics, "Activity Name", rs.getString("activity_name"));
    addField(basics, "Activity Type", humanizeIdentifier(rs.getString("activity_type")));
    addField(basics, "Source Date", formatDate(rs.getObject("source_record_date", LocalDate.class)));
    addField(basics, "Owner", firstNonBlank(rs.getString("owner_full_name"), rs.getString("owner_display_name")));
    addField(basics, "Manufacturer", rs.getString("manufacturer"));
    addField(basics, "Privacy", humanizeIdentifier(rs.getString("privacy_type_key")));

    List<HealthDetailFieldView> performance = new ArrayList<>();
    addField(performance, "Duration", formatDuration(rs.getBigDecimal("duration_seconds")));
    addField(performance, "Moving Duration", formatDuration(rs.getBigDecimal("moving_duration_seconds")));
    addField(performance, "Elapsed Duration", formatDuration(rs.getBigDecimal("elapsed_duration_seconds")));
    addField(performance, "Distance", formatKilometers(rs.getBigDecimal("distance_meters")));
    addField(performance, "Calories", formatKilocalories(rs.getBigDecimal("calories_kilocalories")));
    addField(performance, "Resting Burn", formatKilocalories(rs.getBigDecimal("bmr_kilocalories")));
    addField(performance, "Avg Speed", formatKilometersPerHour(rs.getBigDecimal("average_speed_meters_per_second")));
    addField(performance, "Max Speed", formatKilometersPerHour(rs.getBigDecimal("max_speed_meters_per_second")));
    addField(performance, "Elevation Gain", formatMeters(rs.getBigDecimal("elevation_gain_meters")));
    addField(performance, "Elevation Loss", formatMeters(rs.getBigDecimal("elevation_loss_meters")));
    addField(performance, "Lap Count", formatInteger(getNullableInteger(rs, "lap_count")));
    addField(performance, "Training Effect", rs.getString("training_effect_label"));
    addField(performance, "Aerobic Effect", formatDecimal(rs.getBigDecimal("aerobic_training_effect"), 1));
    addField(performance, "Anaerobic Effect", formatDecimal(rs.getBigDecimal("anaerobic_training_effect"), 1));

    List<HealthDetailFieldView> heartRate = new ArrayList<>();
    addField(heartRate, "Average HR", formatBpm(getNullableInteger(rs, "average_hr")));
    addField(heartRate, "Max HR", formatBpm(getNullableInteger(rs, "max_hr")));
    addField(heartRate, "Zone 1", formatDuration(rs.getBigDecimal("hr_time_in_zone_1")));
    addField(heartRate, "Zone 2", formatDuration(rs.getBigDecimal("hr_time_in_zone_2")));
    addField(heartRate, "Zone 3", formatDuration(rs.getBigDecimal("hr_time_in_zone_3")));
    addField(heartRate, "Zone 4", formatDuration(rs.getBigDecimal("hr_time_in_zone_4")));
    addField(heartRate, "Zone 5", formatDuration(rs.getBigDecimal("hr_time_in_zone_5")));

    List<HealthDetailFieldView> locationAndTiming = new ArrayList<>();
    addField(locationAndTiming, "Start Time", formatDateTime(rs.getObject("start_time_gmt", OffsetDateTime.class)));
    addField(locationAndTiming, "End Time", formatDateTime(rs.getObject("end_time_gmt", OffsetDateTime.class)));
    addField(locationAndTiming, "Start Time (Local)", rs.getString("start_time_local"));
    addField(locationAndTiming, "Time Zone", rs.getString("time_zone_id"));
    addField(locationAndTiming, "Location", rs.getString("location_name"));
    addField(locationAndTiming, "Start Coordinates", formatCoordinates(rs.getBigDecimal("start_latitude"), rs.getBigDecimal("start_longitude")));
    addField(locationAndTiming, "End Coordinates", formatCoordinates(rs.getBigDecimal("end_latitude"), rs.getBigDecimal("end_longitude")));
    addField(locationAndTiming, "Sport Type Id", formatInteger(getNullableInteger(rs, "sport_type_id")));

    return new HealthActivityDetailView(
      rs.getObject("activity_record_id", UUID.class),
      rs.getString("activity_name"),
      rs.getString("activity_type"),
      basics,
      performance,
      heartRate,
      locationAndTiming
    );
  }

  private static Integer getNullableInteger(ResultSet rs, String columnLabel) throws SQLException {
    int value = rs.getInt(columnLabel);
    return rs.wasNull() ? null : value;
  }

  private static Long getNullableLong(ResultSet rs, String columnLabel) throws SQLException {
    long value = rs.getLong(columnLabel);
    return rs.wasNull() ? null : value;
  }

  private void addField(List<HealthDetailFieldView> target, String label, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    target.add(new HealthDetailFieldView(label, value));
  }

  private String formatDate(LocalDate value) {
    return value != null ? value.format(DATE_FORMATTER) : null;
  }

  private String formatDateTime(OffsetDateTime value) {
    if (value == null) {
      return null;
    }
    return value.atZoneSameInstant(appZoneId).format(DATE_TIME_FORMATTER);
  }

  private String formatDecimal(BigDecimal value, int scale) {
    if (value == null) {
      return null;
    }
    return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
  }

  private String formatKilocalories(BigDecimal value) {
    String formatted = formatDecimal(value, 0);
    return formatted != null ? formatted + " kcal" : null;
  }

  private String formatKilometers(BigDecimal meters) {
    if (meters == null) {
      return null;
    }
    return formatDecimal(meters.divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP), 2) + " km";
  }

  private String formatKilometersPerHour(BigDecimal metersPerSecond) {
    if (metersPerSecond == null) {
      return null;
    }
    BigDecimal kilometersPerHour = metersPerSecond.multiply(BigDecimal.valueOf(3.6));
    return formatDecimal(kilometersPerHour, 1) + " km/h";
  }

  private String formatMeters(BigDecimal value) {
    String formatted = formatDecimal(value, 1);
    return formatted != null ? formatted + " m" : null;
  }

  private String formatDuration(BigDecimal secondsValue) {
    if (secondsValue == null) {
      return null;
    }
    long totalSeconds = secondsValue.setScale(0, RoundingMode.HALF_UP).longValue();
    long hours = totalSeconds / 3600;
    long minutes = (totalSeconds % 3600) / 60;
    long seconds = totalSeconds % 60;
    if (hours > 0) {
      return "%dh %02dm %02ds".formatted(hours, minutes, seconds);
    }
    if (minutes > 0) {
      return "%dm %02ds".formatted(minutes, seconds);
    }
    return "%ds".formatted(seconds);
  }

  private String formatBpm(Integer value) {
    return value != null ? value + " bpm" : null;
  }

  private String formatInteger(Integer value) {
    return value != null ? String.valueOf(value) : null;
  }

  private String formatCoordinates(BigDecimal latitude, BigDecimal longitude) {
    if (latitude == null || longitude == null) {
      return null;
    }
    return formatDecimal(latitude, 5) + ", " + formatDecimal(longitude, 5);
  }

  private String humanizeIdentifier(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.replace('_', ' ').trim();
    return normalized.isEmpty()
      ? null
      : normalized.substring(0, 1).toUpperCase() + normalized.substring(1);
  }

  private String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    if (second != null && !second.isBlank()) {
      return second;
    }
    return null;
  }
}
