package com.otw.adminapi.connector;

public record ConnectorConfigFieldView(
  String key,
  String label,
  String type,
  String placeholder,
  boolean required,
  String autocomplete
) {
}
