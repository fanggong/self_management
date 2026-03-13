package com.otw.adminapi.auth;

import com.otw.adminapi.common.api.ApiException;
import com.otw.adminapi.connector.ConnectorConfigEntity;
import com.otw.adminapi.connector.ConnectorConfigRepository;
import com.otw.adminapi.connector.ConnectorService;
import com.otw.adminapi.connector.CronScheduleService;
import com.otw.adminapi.security.AuthenticatedUser;
import com.otw.adminapi.security.JwtService;
import com.otw.adminapi.user.AccountEntity;
import com.otw.adminapi.user.AccountRepository;
import com.otw.adminapi.user.AuthUserView;
import com.otw.adminapi.user.ChangePasswordRequest;
import com.otw.adminapi.user.UpdateProfileRequest;
import com.otw.adminapi.user.UserEntity;
import com.otw.adminapi.user.UserRepository;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {
  private static final String DEFAULT_CONNECTOR_CATEGORY = "health";
  private static final String DEFAULT_GARMIN_SCHEDULE = "0 2 * * *";
  private static final String DEFAULT_MEDICAL_REPORT_SCHEDULE = "-";

  private final UserRepository userRepository;
  private final AccountRepository accountRepository;
  private final ConnectorConfigRepository connectorConfigRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final CronScheduleService cronScheduleService;
  private final ZoneId zoneId;

  public AuthService(
    UserRepository userRepository,
    AccountRepository accountRepository,
    ConnectorConfigRepository connectorConfigRepository,
    PasswordEncoder passwordEncoder,
    JwtService jwtService,
    CronScheduleService cronScheduleService,
    @Value("${app.timezone}") String timezone
  ) {
    this.userRepository = userRepository;
    this.accountRepository = accountRepository;
    this.connectorConfigRepository = connectorConfigRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.cronScheduleService = cronScheduleService;
    this.zoneId = ZoneId.of(timezone);
  }

  @Transactional(readOnly = true)
  public AuthLoginView login(LoginRequest request) {
    UserEntity user = userRepository.findByPrincipalIgnoreCase(request.principal().trim())
      .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Incorrect username or password. Please try again."));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Incorrect username or password. Please try again.");
    }

    return new AuthLoginView(jwtService.issueToken(user), toUserView(user));
  }

  public AuthUserView register(RegisterRequest request) {
    String principal = request.principal().trim().toLowerCase(Locale.ROOT);
    if (!request.password().equals(request.confirmPassword())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "Passwords do not match.");
    }

    if (request.password().length() < 6) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Password must be at least 6 characters.");
    }

    if (userRepository.existsByPrincipalIgnoreCase(principal)) {
      throw new ApiException(HttpStatus.CONFLICT, "PRINCIPAL_CONFLICT", "This username already exists. Please choose another one.");
    }

    AccountEntity account = new AccountEntity();
    account.setName(request.displayName().trim() + " Account");
    accountRepository.save(account);

    UserEntity user = new UserEntity();
    user.setAccountId(account.getId());
    user.setPrincipal(principal);
    user.setDisplayName(request.displayName().trim());
    user.setEmail("");
    user.setPhone("");
    user.setAvatarUrl("");
    user.setRole("MEMBER");
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    userRepository.save(user);

    connectorConfigRepository.save(createDefaultConnector(account.getId(), ConnectorService.GARMIN_CONNECT_ID));
    connectorConfigRepository.save(createDefaultConnector(account.getId(), ConnectorService.MEDICAL_REPORT_ID));

    return toUserView(user);
  }

  @Transactional(readOnly = true)
  public AuthUserView me(AuthenticatedUser authenticatedUser) {
    UserEntity user = requireUser(authenticatedUser);
    return toUserView(user);
  }

  public AuthUserView updateProfile(AuthenticatedUser authenticatedUser, UpdateProfileRequest request) {
    UserEntity user = requireUser(authenticatedUser);
    if (request.displayName().trim().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Nickname is required.");
    }

    user.setDisplayName(request.displayName().trim());
    user.setEmail(request.email() == null ? "" : request.email().trim());
    user.setPhone(request.phone() == null ? "" : request.phone().trim());
    user.setAvatarUrl(request.avatarUrl() == null ? "" : request.avatarUrl().trim());
    return toUserView(userRepository.save(user));
  }

  public void changePassword(AuthenticatedUser authenticatedUser, ChangePasswordRequest request) {
    UserEntity user = requireUser(authenticatedUser);
    if (!request.newPassword().equals(request.confirmPassword())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "Passwords do not match.");
    }

    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CREDENTIALS", "Current password is incorrect.");
    }

    if (request.newPassword().length() < 6) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "New password must be at least 6 characters.");
    }

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);
  }

  private UserEntity requireUser(AuthenticatedUser authenticatedUser) {
    return userRepository.findByIdAndAccountId(authenticatedUser.userId(), authenticatedUser.accountId())
      .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Your session has expired."));
  }

  private ConnectorConfigEntity createDefaultConnector(java.util.UUID accountId, String connectorId) {
    ConnectorConfigEntity connectorConfig = new ConnectorConfigEntity();
    connectorConfig.setAccountId(accountId);
    connectorConfig.setConnectorId(connectorId);
    connectorConfig.setCategory(DEFAULT_CONNECTOR_CATEGORY);
    connectorConfig.setStatus("not_configured");

    if (ConnectorService.MEDICAL_REPORT_ID.equals(connectorId)) {
      connectorConfig.setSchedule(DEFAULT_MEDICAL_REPORT_SCHEDULE);
      connectorConfig.setNextRunAt(null);
      return connectorConfig;
    }

    connectorConfig.setSchedule(DEFAULT_GARMIN_SCHEDULE);
    connectorConfig.setNextRunAt(cronScheduleService.nextRun(DEFAULT_GARMIN_SCHEDULE, zoneId));
    return connectorConfig;
  }

  public static AuthUserView toUserView(UserEntity user) {
    return new AuthUserView(
      user.getId(),
      user.getDisplayName(),
      user.getPrincipal(),
      user.getEmail(),
      user.getPhone(),
      user.getAvatarUrl(),
      user.getRole().toLowerCase(Locale.ROOT)
    );
  }
}
