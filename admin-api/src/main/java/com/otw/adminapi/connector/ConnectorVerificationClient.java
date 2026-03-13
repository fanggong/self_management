package com.otw.adminapi.connector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otw.adminapi.common.api.ApiException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ConnectorVerificationClient {
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public ConnectorVerificationClient(
    @Value("${app.connector.internal-base-url:http://sync-worker:8081}") String internalBaseUrl,
    ObjectMapper objectMapper
  ) {
    this.restClient = RestClient.builder().baseUrl(internalBaseUrl).build();
    this.objectMapper = objectMapper;
  }

  public void verifyGarminConnection(Map<String, String> config) {
    verify("/internal/connectors/garmin-connect/verify", config, "Garmin Connect");
  }

  public void verifyMedicalReportConnection(Map<String, String> config) {
    verify("/internal/connectors/medical-report/verify", config, "Medical Report");
  }

  private void verify(String path, Map<String, String> config, String connectorName) {
    try {
      String requestBody = objectMapper.writeValueAsString(Map.of("config", config));

      VerificationResponse response = restClient.post()
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestBody)
        .retrieve()
        .body(VerificationResponse.class);

      if (response == null) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "CONNECTOR_VERIFICATION_FAILED", connectorName + " verification returned an empty response.");
      }

      if (!response.success()) {
        throw new ApiException(HttpStatus.BAD_REQUEST, response.code(), response.message());
      }
    } catch (ApiException exception) {
      throw exception;
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new ApiException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "CONNECTOR_VERIFICATION_REQUEST_ERROR",
        "Unable to prepare connector verification request."
      );
    } catch (RestClientResponseException exception) {
      throw mapResponseException(exception);
    } catch (RestClientException exception) {
      throw new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "CONNECTOR_VERIFICATION_UNAVAILABLE",
        "Connector verification is temporarily unavailable."
      );
    }
  }

  private ApiException mapResponseException(RestClientResponseException exception) {
    try {
      VerificationResponse response = objectMapper.readValue(exception.getResponseBodyAsString(), VerificationResponse.class);
      if (response != null && response.message() != null && !response.message().isBlank()) {
        HttpStatus status = exception.getStatusCode().is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
        return new ApiException(status, response.code(), response.message());
      }
    } catch (Exception ignored) {
      // Fall back to generic mapping when the downstream response is not valid JSON.
    }

    HttpStatus status = exception.getStatusCode().is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
    return new ApiException(status, "CONNECTOR_VERIFICATION_FAILED", "Connector verification failed.");
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record VerificationResponse(boolean success, String code, String message) {
  }
}
