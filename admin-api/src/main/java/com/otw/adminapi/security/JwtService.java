package com.otw.adminapi.security;

import com.otw.adminapi.user.UserEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private static final long TOKEN_TTL_SECONDS = 60L * 60L * 24L * 7L;

  private final JwtEncoder jwtEncoder;

  public JwtService(JwtEncoder jwtEncoder) {
    this.jwtEncoder = jwtEncoder;
  }

  public String issueToken(UserEntity user) {
    Instant now = Instant.now();
    JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
    JwtClaimsSet claims = JwtClaimsSet.builder()
      .subject(user.getId().toString())
      .issuedAt(now)
      .expiresAt(now.plusSeconds(TOKEN_TTL_SECONDS))
      .claim("accountId", user.getAccountId().toString())
      .claim("principal", user.getPrincipal())
      .claim("role", user.getRole())
      .build();

    return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
  }

  public AuthenticatedUser toAuthenticatedUser(Jwt jwt) {
    return new AuthenticatedUser(
      UUID.fromString(jwt.getSubject()),
      UUID.fromString(jwt.getClaimAsString("accountId")),
      jwt.getClaimAsString("principal"),
      jwt.getClaimAsString("role")
    );
  }

  public long tokenTtlSeconds() {
    return TOKEN_TTL_SECONDS;
  }
}
