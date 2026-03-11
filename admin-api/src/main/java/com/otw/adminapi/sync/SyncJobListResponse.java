package com.otw.adminapi.sync;

import java.util.List;

public record SyncJobListResponse(
  List<SyncJobListItemView> items,
  SyncJobListPageView page,
  SyncJobFacetsView facets
) {
}
