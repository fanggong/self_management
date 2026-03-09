package com.otw.adminapi.user;

import com.otw.adminapi.auth.AuthService;
import com.otw.adminapi.common.api.ApiResult;
import com.otw.adminapi.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class ProfileController {
  private final AuthService authService;
  private final JwtService jwtService;

  public ProfileController(AuthService authService, JwtService jwtService) {
    this.authService = authService;
    this.jwtService = jwtService;
  }

  @PutMapping("/profile")
  public ApiResult<AuthUserView> updateProfile(
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody UpdateProfileRequest request
  ) {
    return ApiResult.success(
      authService.updateProfile(jwtService.toAuthenticatedUser(jwt), request),
      "Profile updated successfully."
    );
  }

  @PostMapping("/password")
  public ApiResult<Void> changePassword(
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody ChangePasswordRequest request
  ) {
    authService.changePassword(jwtService.toAuthenticatedUser(jwt), request);
    return ApiResult.success(null, "Password updated successfully.");
  }
}
