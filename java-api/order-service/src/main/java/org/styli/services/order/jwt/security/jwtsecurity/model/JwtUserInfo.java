package org.styli.services.order.jwt.security.jwtsecurity.model;

import lombok.Data;

@Data
public class JwtUserInfo {

  private String email;
  private String code;
}
