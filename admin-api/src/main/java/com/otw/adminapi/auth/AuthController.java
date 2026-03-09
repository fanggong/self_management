package com.otw.adminapi.auth;

import com.otw.adminapi.common.api.ApiResult;
import com.otw.adminapi.security.JwtService;
import com.otw.adminapi.user.AuthUserView;
import jakarta.validation.Valid;
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
  private final AuthService authService;
  private final JwtService jwtService;

  public AuthController(AuthService authService, JwtService jwtService) {
    this.authService = authService;
    this.jwtService = jwtService;
  }

  @PostMapping("/login")
  public ApiResult<AuthLoginView> login(@Valid @RequestBody LoginRequest request) {
    return ApiResult.success(authService.login(request));
  }

  @PostMapping("/register")
  public ApiResult<AuthUserView> register(@Valid @RequestBody RegisterRequest request) {
    return ApiResult.success(authService.register(request), "Registration successful. Please sign in.");
  }

  @PostMapping("/logout")
  public ApiResult<Void> logout() {
    return ApiResult.success(null);
  }

  @GetMapping("/me")
  public ApiResult<AuthUserView> me(@AuthenticationPrincipal Jwt jwt) {
    return ApiResult.success(authService.me(jwtService.toAuthenticatedUser(jwt)));
  }
}
