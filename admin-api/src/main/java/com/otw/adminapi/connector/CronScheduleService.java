package com.otw.adminapi.connector;

import com.otw.adminapi.common.api.ApiException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

@Service
public class CronScheduleService {
  public Instant nextRun(String schedule, ZoneId zoneId) {
    try {
      String normalized = normalize(schedule);
      ZonedDateTime next = CronExpression.parse(normalized).next(ZonedDateTime.now(zoneId).withSecond(0).withNano(0));
      if (next == null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CRON", "Unable to calculate the next run time.");
      }
      return next.toInstant();
    } catch (IllegalArgumentException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CRON", exception.getMessage());
    }
  }

  private String normalize(String schedule) {
    String[] fields = schedule.trim().split("\\s+");
    if (fields.length != 5) {
      throw new IllegalArgumentException("Cron expression must contain 5 fields.");
    }
    return "0 " + schedule.trim();
  }
}
