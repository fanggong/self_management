package com.otw.adminapi.connector;

import com.otw.adminapi.common.api.ApiResult;
import com.otw.adminapi.security.JwtService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ConnectorController {
  private final ConnectorService connectorService;
  private final JwtService jwtService;

  public ConnectorController(ConnectorService connectorService, JwtService jwtService) {
    this.connectorService = connectorService;
    this.jwtService = jwtService;
  }

  @GetMapping("/connectors/catalog")
  public ApiResult<List<ConnectorCatalogItemView>> catalog() {
    return ApiResult.success(connectorService.listCatalog());
  }

  @GetMapping("/users/me/connectors")
  public ApiResult<List<ConnectorRecordView>> list(@AuthenticationPrincipal Jwt jwt) {
    return ApiResult.success(connectorService.listConnectors(jwtService.toAuthenticatedUser(jwt)));
  }

  @PostMapping("/users/me/connectors/{connectorId}/connection-test")
  public ApiResult<Void> testConnection(
    @AuthenticationPrincipal Jwt jwt,
    @PathVariable String connectorId,
    @RequestBody TestConnectionRequest request
  ) {
    connectorService.testConnection(jwtService.toAuthenticatedUser(jwt), connectorId, request);
    return ApiResult.success(null, connectorService.getConnectorName(connectorId) + " connection verified successfully.");
  }

  @PutMapping("/users/me/connectors/{connectorId}/configuration")
  public ApiResult<ConnectorRecordView> saveConfiguration(
    @AuthenticationPrincipal Jwt jwt,
    @PathVariable String connectorId,
    @Valid @RequestBody SaveConnectorConfigurationRequest request
  ) {
    ConnectorRecordView data = connectorService.saveConfiguration(jwtService.toAuthenticatedUser(jwt), connectorId, request);
    return ApiResult.success(data, data.name() + " configuration saved.");
  }

  @PatchMapping("/users/me/connectors/{connectorId}/status")
  public ApiResult<ConnectorRecordView> updateStatus(
    @AuthenticationPrincipal Jwt jwt,
    @PathVariable String connectorId,
    @Valid @RequestBody UpdateConnectorStatusRequest request
  ) {
    ConnectorRecordView data = connectorService.updateStatus(jwtService.toAuthenticatedUser(jwt), connectorId, request);
    return ApiResult.success(data, data.name() + " is now " + data.status() + ".");
  }
}
