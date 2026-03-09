package com.otw.adminapi.connector;

import jakarta.validation.constraints.NotBlank;

public record UpdateConnectorStatusRequest(@NotBlank String status) {
}
