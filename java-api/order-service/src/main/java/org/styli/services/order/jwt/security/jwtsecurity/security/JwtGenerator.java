package org.styli.services.order.jwt.security.jwtsecurity.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Component
public class JwtGenerator {
	@Value("${jwt.salt.old.secret}")
	private String jwtSaltOldSecret;

  public String generate(JwtUser jwtUser) {

    Claims claims = Jwts.claims().setSubject(jwtUser.getUserId());
    claims.put("code", String.valueOf(jwtUser.getCode()));
    claims.put("role", jwtUser.getRole());

    // Date now = new Date();
    // Date validity = new Date(now.getTime() + validityInMilliseconds);

    // return Jwts.builder()//
    // .setClaims(claims)//
    // .setIssuedAt(now)//
    // .setExpiration(validity)//
    // .signWith(SignatureAlgorithm.HS256, secretKey)//
    // .compact();
    return Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, jwtSaltOldSecret).compact();
  }
}
