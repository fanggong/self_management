package com.otw.adminapi.connector;

import java.util.List;

public record ConnectorCatalogItemView(
  String id,
  String name,
  String category,
  List<ConnectorConfigFieldView> fields
) {
}
