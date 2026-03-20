package com.otw.adminapi.medical;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otw.adminapi.common.api.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MedicalReportWorkerClient {
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public MedicalReportWorkerClient(
    @Value("${app.connector.internal-base-url:http://sync-worker:8081}") String internalBaseUrl,
    ObjectMapper objectMapper
  ) {
    this.restClient = RestClient.builder().baseUrl(internalBaseUrl).build();
    this.objectMapper = objectMapper;
  }

  public ParsedPayload parseReport(WorkerParseRequest request) {
    try {
      String requestBody = objectMapper.writeValueAsString(Map.of(
        "accountId", request.accountId(),
        "connectorConfigId", request.connectorConfigId(),
        "config", request.config(),
        "recordNumber", request.recordNumber(),
        "reportDate", request.reportDate(),
        "institution", request.institution(),
        "fileName", request.fileName(),
        "fileBase64", request.fileBase64()
      ));

      WorkerParseResponse response = restClient.post()
        .uri("/internal/connectors/medical-report/parse")
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestBody)
        .retrieve()
        .body(WorkerParseResponse.class);

      if (response == null) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "REPORT_PARSE_FAILED", "Medical report parse returned an empty response.");
      }
      if (!response.success()) {
        throw new ApiException(HttpStatus.BAD_REQUEST, response.code(), response.message());
      }
      if (response.data() == null) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "REPORT_PARSE_FAILED", "Medical report parse result is empty.");
      }

      WorkerParseData data = response.data();
      List<ParsedSection> sections = data.sections() == null
        ? List.of()
        : data.sections().stream()
          .map(section -> new ParsedSection(
            normalize(section.sectionKey()),
            normalize(section.examiner()),
            normalize(section.examDate()),
            section.items() == null
              ? List.of()
              : section.items().stream()
                .map(item -> new ParsedItem(
                  normalize(item.itemKey()),
                  normalize(item.result()),
                  normalize(item.referenceValue()),
                  normalize(item.unit()),
                  normalize(item.abnormalFlag())
                ))
                .toList()
          ))
          .toList();

      return new ParsedPayload(sections);
    } catch (ApiException exception) {
      throw exception;
    } catch (JsonProcessingException exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_PARSE_REQUEST_ERROR", "Unable to prepare medical report parse request.");
    } catch (RestClientResponseException exception) {
      throw mapResponseException(exception);
    } catch (RestClientException exception) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "MODEL_CONNECTION_ERROR", "Unable to reach model parsing service right now.");
    }
  }

  private ApiException mapResponseException(RestClientResponseException exception) {
    try {
      WorkerParseResponse response = objectMapper.readValue(exception.getResponseBodyAsString(), WorkerParseResponse.class);
      if (response != null && response.message() != null && !response.message().isBlank()) {
        HttpStatus status = exception.getStatusCode().is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
        return new ApiException(status, response.code(), response.message());
      }
    } catch (Exception ignored) {
      // Fall back to generic mapping when the downstream response body cannot be parsed.
    }

    HttpStatus status = exception.getStatusCode().is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
    return new ApiException(status, "REPORT_PARSE_FAILED", "Unable to parse medical report content.");
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  public record WorkerParseRequest(
    UUID accountId,
    UUID connectorConfigId,
    Map<String, String> config,
    String recordNumber,
    String reportDate,
    String institution,
    String fileName,
    String fileBase64
  ) {
  }

  public record ParsedPayload(List<ParsedSection> sections) {
  }

  public record ParsedSection(
    String sectionKey,
    String examiner,
    String examDate,
    List<ParsedItem> items
  ) {
  }

  public record ParsedItem(
    String itemKey,
    String result,
    String referenceValue,
    String unit,
    String abnormalFlag
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record WorkerParseResponse(
    boolean success,
    String code,
    String message,
    WorkerParseData data
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record WorkerParseData(
    List<WorkerParseSection> sections
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record WorkerParseSection(
    String sectionKey,
    String examiner,
    String examDate,
    List<WorkerParseItem> items
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record WorkerParseItem(
    String itemKey,
    String result,
    String referenceValue,
    String unit,
    String abnormalFlag
  ) {
  }
}
