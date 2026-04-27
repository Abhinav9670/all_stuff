package org.styli.services.customer.jwt.security.jwtsecurity.security;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.styli.services.customer.jwt.security.jwtsecurity.model.JwtUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;

@Component
@Scope("singleton")
public class JwtGenerator {
	
	@Value("${jwt.salt.old.secret}")
	private String jwtSaltOldSecret;
	
	@Value("${jwt.salt.new.secret}")
	private String jwtsaltNewSecret;

        public String generate(JwtUser jwtUser) {

                Claims claims = Jwts.claims().setSubject(jwtUser.getUserId());
                claims.put("code", String.valueOf(jwtUser.getCode()));
                claims.put("role", jwtUser.getRole());
                if(StringUtils.isNotBlank(jwtUser.getUuid())) claims.put("uuid", jwtUser.getUuid());
                if("1".equals(jwtUser.getJwtFlag()))
                        claims.put("customerId", jwtUser.getCustomerId());

                if(jwtUser.getExpiry()!=null)
                        claims.setExpiration(jwtUser.getExpiry());
                if(jwtUser.getRefreshToken()) {
                	 claims.setIssuedAt(new Date());
                     claims.setIssuer("styli");
                     claims.put("refreshtoken",jwtUser.getRefreshToken());
                }             
                return Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512,
                        ("1".equals(jwtUser.getJwtFlag()))?jwtsaltNewSecret:jwtSaltOldSecret).compact();
        }

}

