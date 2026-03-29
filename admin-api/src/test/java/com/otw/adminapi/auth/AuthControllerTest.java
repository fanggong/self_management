package com.otw.adminapi.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.otw.adminapi.common.api.ApiResult;
import com.otw.adminapi.security.JwtService;
import com.otw.adminapi.user.AuthUserView;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
  @Mock
  private AuthService authService;

  @Mock
  private JwtService jwtService;

  private AuthController controller;
  private AuthUserView userView;

  @BeforeEach
  void setUp() {
    controller = new AuthController(authService, jwtService, true);
    userView = new AuthUserView(
      UUID.randomUUID(),
      "Admin",
      "demo",
      "demo@example.invalid",
      "+1 555 010 9999",
      "",
      "admin"
    );
  }

  @Test
  void loginSetsHttpOnlySessionCookieAndReturnsUserPayload() {
    LoginRequest request = new LoginRequest("demo", "secret");
    when(authService.login(request)).thenReturn(new AuthLoginView("jwt-token", userView));
    when(jwtService.tokenTtlSeconds()).thenReturn(3600L);

    ResponseEntity<ApiResult<AuthUserView>> response = controller.login(request);

    String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertNotNull(setCookie);
    assertTrue(setCookie.contains("sm_auth_token=jwt-token"));
    assertTrue(setCookie.contains("HttpOnly"));
    assertTrue(setCookie.contains("SameSite=Lax"));
    assertTrue(setCookie.contains("Secure"));
    assertEquals(userView, response.getBody().data());
  }

  @Test
  void logoutExpiresSessionCookie() {
    ResponseEntity<ApiResult<Void>> response = controller.logout();

    String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertNotNull(setCookie);
    assertTrue(setCookie.contains("sm_auth_token="));
    assertTrue(setCookie.contains("Max-Age=0"));
    assertTrue(setCookie.contains("HttpOnly"));
    assertTrue(setCookie.contains("Secure"));
  }
}
