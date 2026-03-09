package com.otw.adminapi.common.api;

public record ApiResult<T>(boolean success, T data, String message, String code) {
  public static <T> ApiResult<T> success(T data) {
    return new ApiResult<>(true, data, null, null);
  }

  public static <T> ApiResult<T> success(T data, String message) {
    return new ApiResult<>(true, data, message, null);
  }

  public static <T> ApiResult<T> failure(String message, String code) {
    return new ApiResult<>(false, null, message, code);
  }
}
