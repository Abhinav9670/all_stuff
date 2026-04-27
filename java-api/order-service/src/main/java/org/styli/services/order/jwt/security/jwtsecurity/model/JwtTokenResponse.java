package org.styli.services.order.jwt.security.jwtsecurity.model;

import lombok.Data;

@Data
public class JwtTokenResponse {

  private boolean status;

  private String jwtToken;

  private String message;

}
