package com.otw.adminapi.dbt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.otw.adminapi.common.api.ApiException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class DbtModelRunnerClient {
  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final String runnerBaseUrl;

  public DbtModelRunnerClient(
    @Value("${app.dbt.runner-base-url:http://dbt-runner:8090}") String runnerBaseUrl,
    ObjectMapper objectMapper
  ) {
    this.restClient = RestClient.builder().baseUrl(runnerBaseUrl).build();
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newHttpClient();
    this.runnerBaseUrl = runnerBaseUrl;
  }

  public List<RunnerModelItem> listModels(String layer, String search) {
    try {
      RunnerModelListResponse response = restClient.get()
        .uri(uriBuilder -> uriBuilder.path("/models")
          .queryParam("layer", layer)
          .queryParamIfPresent("search", search == null || search.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(search.trim()))
          .build())
        .retrieve()
        .body(RunnerModelListResponse.class);

      if (response == null) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "DBT_MODEL_LIST_FAILED", "dbt model list returned an empty response.");
      }
      if (!response.success()) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, response.code(), response.message());
      }
      return response.items() == null
        ? List.of()
        : response.items().stream()
          .map(item -> new RunnerModelItem(
            item.name(),
            item.layer(),
            item.description(),
            item.connectorId(),
            item.domainKey(),
            item.lastRunCompletedAt()
          ))
          .toList();
    } catch (ApiException exception) {
      throw exception;
    } catch (RestClientResponseException exception) {
      throw mapErrorResponse(exception);
    } catch (RestClientException exception) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "DBT_RUNNER_UNAVAILABLE", "dbt runner is temporarily unavailable.");
    }
  }

  public RunnerModelDetail getModelDetail(String layer, String modelName) {
    try {
      RunnerModelDetailResponse response = restClient.get()
        .uri("/models/{layer}/{modelName}", layer, modelName)
        .retrieve()
        .body(RunnerModelDetailResponse.class);

      if (response == null || response.item() == null) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "DBT_MODEL_DETAIL_FAILED", "dbt model detail returned an empty response.");
      }
      if (!response.success()) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, response.code(), response.message());
      }

      return new RunnerModelDetail(
        response.item().name(),
        response.item().layer(),
        normalizeNullable(response.item().description()),
        normalizeNullable(response.item().connectorId()),
        normalizeNullable(response.item().domainKey()),
        normalizeNullable(response.item().schemaName()),
        normalizeNullable(response.item().relationName()),
        response.item().columns() == null
          ? List.of()
          : response.item().columns().stream()
            .map(column -> new RunnerModelColumn(
              normalizeNullable(column.name()),
              normalizeNullable(column.type()),
              normalizeNullable(column.description())
            ))
            .toList()
      );
    } catch (ApiException exception) {
      throw exception;
    } catch (RestClientResponseException exception) {
      throw mapErrorResponse(exception);
    } catch (RestClientException exception) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "DBT_RUNNER_UNAVAILABLE", "dbt runner is temporarily unavailable.");
    }
  }

  public RunnerRunResponse runModel(String layer, String modelName) {
    try {
      String requestBody = objectMapper.writeValueAsString(Map.of("layer", layer, "modelName", modelName));

      return restClient.post()
        .uri("/models/run")
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestBody)
        .exchange((clientRequest, clientResponse) -> {
          String responseBody = StreamUtils.copyToString(clientResponse.getBody(), StandardCharsets.UTF_8);
          RunnerRunPayload payload = responseBody.isBlank()
            ? new RunnerRunPayload(false, null, null, null, null, null, null, null, null)
            : objectMapper.readValue(responseBody, RunnerRunPayload.class);

          return new RunnerRunResponse(
            clientResponse.getStatusCode().value(),
            Boolean.TRUE.equals(payload.success()),
            payload.returncode(),
            normalize(payload.stdout()),
            normalize(payload.stderr()),
            normalizeNullable(payload.startedAt()),
            normalizeNullable(payload.finishedAt()),
            normalizeNullable(payload.code()),
            normalizeNullable(payload.message()),
            payload.executedModels() == null
              ? List.of()
              : payload.executedModels().stream()
                .map(item -> new RunnerExecutedModel(
                  normalizeNullable(item.uniqueId()),
                  normalizeNullable(item.name()),
                  normalizeNullable(item.layer()),
                  normalizeNullable(item.status()),
                  normalizeNullable(item.message()),
                  normalizeNullable(item.relationName()),
                  item.executionTimeSeconds(),
                  normalizeNullable(item.completedAt())
                ))
                .toList()
          );
        });
    } catch (JsonProcessingException exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DBT_MODEL_RUN_REQUEST_ERROR", "Unable to prepare dbt model run request.");
    } catch (RestClientResponseException exception) {
      throw mapErrorResponse(exception);
    } catch (RestClientException exception) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "DBT_RUNNER_UNAVAILABLE", "dbt runner is temporarily unavailable.");
    } catch (Exception exception) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "DBT_MODEL_RUN_FAILED", "Unable to execute dbt model run.");
    }
  }

  public RunnerModelRunStream openModelRunStream(String layer, String modelName) {
    try {
      String requestBody = objectMapper.writeValueAsString(Map.of("layer", layer, "modelName", modelName));
      HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(runnerBaseUrl + "/models/run/stream"))
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .header("Accept", "application/x-ndjson, application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
        .build();

      HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      String contentType = response.headers().firstValue("content-type").orElse("");
      if (response.statusCode() != 200 || !contentType.toLowerCase(Locale.ROOT).contains("application/x-ndjson")) {
        try (InputStream responseBody = response.body()) {
          String body = new String(responseBody.readAllBytes(), StandardCharsets.UTF_8);
          throw mapErrorResponse(response.statusCode(), body);
        }
      }

      return new RunnerModelRunStream(response.body(), objectMapper);
    } catch (JsonProcessingException exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DBT_MODEL_RUN_REQUEST_ERROR", "Unable to prepare dbt model run request.");
    } catch (ApiException exception) {
      throw exception;
    } catch (IOException exception) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "DBT_RUNNER_UNAVAILABLE", "dbt runner is temporarily unavailable.");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ApiException(HttpStatus.BAD_GATEWAY, "DBT_RUNNER_UNAVAILABLE", "dbt runner request was interrupted.");
    } catch (Exception exception) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "DBT_MODEL_RUN_FAILED", "Unable to open dbt model run stream.");
    }
  }

  private ApiException mapErrorResponse(RestClientResponseException exception) {
    return mapErrorResponse(exception.getStatusCode().value(), exception.getResponseBodyAsString());
  }

  ApiException mapErrorResponse(int statusCode, String responseBody) {
    HttpStatus status = HttpStatus.resolve(statusCode);
    HttpStatus resolvedStatus = status == null ? HttpStatus.BAD_GATEWAY : status;

    try {
      RunnerErrorResponse response = objectMapper.readValue(normalize(responseBody), RunnerErrorResponse.class);
      if (response != null && response.message() != null && !response.message().isBlank()) {
        return new ApiException(resolvedStatus, normalizeNullable(response.code()), response.message());
      }
    } catch (Exception ignored) {
      // Fall back to a readable message when the downstream response body is not structured JSON.
    }

    if (statusCode == 404 && looksLikeHtml(responseBody)) {
      return new ApiException(
        resolvedStatus,
        "DBT_RUNNER_ROUTE_NOT_FOUND",
        "dbt runner route was not found. The running dbt-runner service may be outdated; rebuild the service and retry."
      );
    }

    String responseSummary = summarizeResponseBody(responseBody);
    if (responseSummary != null) {
      return new ApiException(
        resolvedStatus,
        "DBT_RUNNER_REQUEST_FAILED",
        "dbt runner request failed with HTTP " + statusCode + ": " + responseSummary
      );
    }

    return new ApiException(
      resolvedStatus,
      "DBT_RUNNER_REQUEST_FAILED",
      "dbt runner request failed with HTTP " + statusCode + "."
    );
  }

  private boolean looksLikeHtml(String value) {
    String normalized = normalizeNullable(value);
    if (normalized == null) {
      return false;
    }

    String lowerCased = normalized.toLowerCase();
    return lowerCased.contains("<html") || lowerCased.contains("<!doctype html") || lowerCased.contains("<title>");
  }

  private String summarizeResponseBody(String value) {
    String normalized = normalizeNullable(value);
    if (normalized == null) {
      return null;
    }

    String summary = normalized
      .replaceAll("<[^>]+>", " ")
      .replaceAll("\\s+", " ")
      .trim();
    if (summary.isEmpty()) {
      return null;
    }

    int maxLength = 180;
    return summary.length() <= maxLength ? summary : summary.substring(0, maxLength - 3) + "...";
  }

  private String normalize(String value) {
    return value == null ? "" : value;
  }

  private String normalizeNullable(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  public record RunnerModelItem(
    String name,
    String layer,
    String description,
    String connectorId,
    String domainKey,
    String lastRunCompletedAt
  ) {
  }

  public record RunnerModelDetail(
    String name,
    String layer,
    String description,
    String connectorId,
    String domainKey,
    String schemaName,
    String relationName,
    List<RunnerModelColumn> columns
  ) {
  }

  public record RunnerModelColumn(
    String name,
    String type,
    String description
  ) {
  }

  public record RunnerRunResponse(
    int statusCode,
    boolean success,
    Integer returncode,
    String stdout,
    String stderr,
    String startedAt,
    String finishedAt,
    String code,
    String message,
    List<RunnerExecutedModel> executedModels
  ) {
  }

  public record RunnerExecutedModel(
    String uniqueId,
    String name,
    String layer,
    String status,
    String message,
    String relationName,
    Double executionTimeSeconds,
    String completedAt
  ) {
  }

  public record RunnerLogEvent(
    String stream,
    String text,
    String timestamp
  ) {
  }

  public static final class RunnerModelRunStream implements AutoCloseable {
    private final BufferedReader reader;
    private final ObjectMapper objectMapper;

    private RunnerModelRunStream(InputStream inputStream, ObjectMapper objectMapper) {
      this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
      this.objectMapper = objectMapper;
    }

    public RunnerRunResponse consume(Consumer<RunnerLogEvent> onLog) throws IOException {
      RunnerRunResponse finalResponse = null;
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }

        JsonNode node = objectMapper.readTree(line);
        String type = textValue(node, "type");
        if ("log".equals(type)) {
          if (onLog != null) {
            onLog.accept(new RunnerLogEvent(
              textValue(node, "stream"),
              textValue(node, "text"),
              textValue(node, "timestamp")
            ));
          }
          continue;
        }

        if ("run_finished".equals(type)) {
          finalResponse = new RunnerRunResponse(
            intValue(node, "statusCode", 500),
            booleanValue(node, "success"),
            nullableIntValue(node, "returncode"),
            normalize(textValue(node, "stdout")),
            normalize(textValue(node, "stderr")),
            normalizeNullable(textValue(node, "startedAt")),
            normalizeNullable(textValue(node, "finishedAt")),
            normalizeNullable(textValue(node, "code")),
            normalizeNullable(textValue(node, "message")),
            parseExecutedModels(node.get("executedModels"))
          );
        }
      }

      if (finalResponse == null) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "DBT_RUNNER_STREAM_INCOMPLETE", "dbt runner stream ended without a final result.");
      }

      return finalResponse;
    }

    @Override
    public void close() throws IOException {
      reader.close();
    }

    private List<RunnerExecutedModel> parseExecutedModels(JsonNode node) {
      if (node == null || !node.isArray()) {
        return List.of();
      }

      java.util.ArrayList<RunnerExecutedModel> items = new java.util.ArrayList<>();
      for (JsonNode item : node) {
        items.add(new RunnerExecutedModel(
          normalizeNullable(textValue(item, "uniqueId")),
          normalizeNullable(textValue(item, "name")),
          normalizeNullable(textValue(item, "layer")),
          normalizeNullable(textValue(item, "status")),
          normalizeNullable(textValue(item, "message")),
          normalizeNullable(textValue(item, "relationName")),
          item.hasNonNull("executionTimeSeconds") ? item.get("executionTimeSeconds").doubleValue() : null,
          normalizeNullable(textValue(item, "completedAt"))
        ));
      }
      return items;
    }

    private String textValue(JsonNode node, String field) {
      JsonNode value = node == null ? null : node.get(field);
      return value == null || value.isNull() ? null : value.asText();
    }

    private int intValue(JsonNode node, String field, int defaultValue) {
      JsonNode value = node == null ? null : node.get(field);
      return value == null || value.isNull() ? defaultValue : value.asInt(defaultValue);
    }

    private Integer nullableIntValue(JsonNode node, String field) {
      JsonNode value = node == null ? null : node.get(field);
      return value == null || value.isNull() ? null : value.asInt();
    }

    private boolean booleanValue(JsonNode node, String field) {
      JsonNode value = node == null ? null : node.get(field);
      return value != null && !value.isNull() && value.asBoolean(false);
    }

    private String normalize(String value) {
      return value == null ? "" : value;
    }

    private String normalizeNullable(String value) {
      if (value == null) {
        return null;
      }
      String normalized = value.trim();
      return normalized.isEmpty() ? null : normalized;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RunnerModelListResponse(
    boolean success,
    List<RunnerModelItemPayload> items,
    String code,
    String message
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RunnerModelItemPayload(
    String name,
    String layer,
    String description,
    String connectorId,
    String domainKey,
    String lastRunCompletedAt
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RunnerModelDetailResponse(
    boolean success,
    RunnerModelDetailPayload item,
    String code,
    String message
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RunnerModelDetailPayload(
    String name,
    String layer,
    String description,
    String connectorId,
    String domainKey,
    String schemaName,
    String relationName,
    List<RunnerModelColumnPayload> columns
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RunnerModelColumnPayload(
    String name,
    String type,
    String description
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RunnerRunPayload(
    Boolean success,
    Integer returncode,
    String stdout,
    String stderr,
    String startedAt,
    String finishedAt,
    String code,
    String message,
    List<RunnerExecutedModelPayload> executedModels
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RunnerExecutedModelPayload(
    String uniqueId,
    String name,
    String layer,
    String status,
    String message,
    String relationName,
    Double executionTimeSeconds,
    String completedAt
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RunnerErrorResponse(
    String code,
    String message
  ) {
  }
}
