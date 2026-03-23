package com.otw.adminapi.dbt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otw.adminapi.common.api.ApiException;
import org.junit.jupiter.api.Test;

class DbtModelRunnerClientTest {
  private final DbtModelRunnerClient client = new DbtModelRunnerClient("http://dbt-runner:8090", new ObjectMapper());

  @Test
  void mapErrorResponseReturnsStructuredRunnerMessageWhenJsonExists() {
    ApiException exception = client.mapErrorResponse(404, """
      {"code":"MODEL_NOT_FOUND","message":"Model not found in the requested layer."}
      """);

    assertEquals("MODEL_NOT_FOUND", exception.getCode());
    assertEquals("Model not found in the requested layer.", exception.getMessage());
  }

  @Test
  void mapErrorResponseExplainsMissingRunnerRouteWhenHtml404IsReturned() {
    ApiException exception = client.mapErrorResponse(404, """
      <!doctype html>
      <html>
        <head><title>404 Not Found</title></head>
        <body><h1>Not Found</h1></body>
      </html>
      """);

    assertEquals("DBT_RUNNER_ROUTE_NOT_FOUND", exception.getCode());
    assertEquals(
      "dbt runner route was not found. The running dbt-runner service may be outdated; rebuild the service and retry.",
      exception.getMessage()
    );
  }
}
