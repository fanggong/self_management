package com.otw.adminapi.auth;

import com.otw.adminapi.common.api.ApiResult;
import com.otw.adminapi.security.JwtService;
import com.otw.adminapi.user.AuthUserView;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  public static final String AUTH_COOKIE_NAME = "sm_auth_token";

  private final AuthService authService;
  private final JwtService jwtService;
  private final boolean cookieSecure;

  public AuthController(
    AuthService authService,
    JwtService jwtService,
    @Value("${app.security.cookie-secure:true}") boolean cookieSecure
  ) {
    this.authService = authService;
    this.jwtService = jwtService;
    this.cookieSecure = cookieSecure;
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResult<AuthUserView>> login(@Valid @RequestBody LoginRequest request) {
    AuthLoginView loginView = authService.login(request);
    return ResponseEntity.ok()
      .header(HttpHeaders.SET_COOKIE, buildSessionCookie(loginView.token(), jwtService.tokenTtlSeconds()).toString())
      .body(ApiResult.success(loginView.user()));
  }

  @PostMapping("/register")
  public ApiResult<AuthUserView> register(@Valid @RequestBody RegisterRequest request) {
    return ApiResult.success(authService.register(request), "Registration successful. Please sign in.");
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResult<Void>> logout() {
    return ResponseEntity.ok()
      .header(HttpHeaders.SET_COOKIE, buildSessionCookie("", 0).toString())
      .body(ApiResult.success(null));
  }

  @GetMapping("/me")
  public ApiResult<AuthUserView> me(@AuthenticationPrincipal Jwt jwt) {
    return ApiResult.success(authService.me(jwtService.toAuthenticatedUser(jwt)));
  }

  private ResponseCookie buildSessionCookie(String value, long maxAgeSeconds) {
    return ResponseCookie.from(AUTH_COOKIE_NAME, value)
      .httpOnly(true)
      .secure(cookieSecure)
      .sameSite("Lax")
      .path("/")
      .maxAge(Duration.ofSeconds(maxAgeSeconds))
      .build();
  }
}
