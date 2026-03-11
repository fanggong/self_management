package com.otw.adminapi.common.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class DateTimeFormats {
  public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private DateTimeFormats() {
  }

  public static String formatNullable(Instant instant, ZoneId zoneId) {
    if (instant == null) {
      return "";
    }

    return DATETIME_FORMATTER.format(LocalDateTime.ofInstant(instant, zoneId));
  }

  public static String formatNullableOrNull(Instant instant, ZoneId zoneId) {
    if (instant == null) {
      return null;
    }

    return DATETIME_FORMATTER.format(LocalDateTime.ofInstant(instant, zoneId));
  }

  public static Instant parseRequired(String value, ZoneId zoneId) {
    return LocalDateTime.parse(value, DATETIME_FORMATTER).atZone(zoneId).toInstant();
  }
}
