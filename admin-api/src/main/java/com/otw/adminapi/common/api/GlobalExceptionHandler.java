package com.otw.adminapi.common.api;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResult<Void>> handleApiException(ApiException exception) {
    return ResponseEntity.status(exception.getStatus())
      .body(ApiResult.failure(exception.getMessage(), exception.getCode()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResult<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
    String message = exception.getBindingResult().getAllErrors().stream()
      .map(error -> error instanceof FieldError fieldError ? fieldError.getField() + ": " + error.getDefaultMessage() : error.getDefaultMessage())
      .findFirst()
      .orElse("Validation failed.");
    return ResponseEntity.badRequest().body(ApiResult.failure(message, "VALIDATION_ERROR"));
  }

  @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
  public ResponseEntity<ApiResult<Void>> handleValidation(Exception exception) {
    return ResponseEntity.badRequest().body(ApiResult.failure(exception.getMessage(), "VALIDATION_ERROR"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception exception) {
    log.error("Unhandled API exception", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(ApiResult.failure("Unexpected server error.", "INTERNAL_SERVER_ERROR"));
  }
}
