package org.styli.services.order.jwt.security.jwtsecurity.security;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.styli.services.order.exception.BadRequestException;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtAuthenticationToken;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtUserDetails;
import org.styli.services.order.utility.Constants;

@Component
public class JwtAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider {

  @Autowired
  private JwtValidator validator;

  @Override
  protected void additionalAuthenticationChecks(UserDetails userDetails,
      UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken) throws AuthenticationException {
    // To be implemented
  }

  @Override
  protected UserDetails retrieveUser(String username,
      UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken) throws AuthenticationException {

    JwtAuthenticationToken jwtAuthenticationToken = (JwtAuthenticationToken) usernamePasswordAuthenticationToken;
    String token = jwtAuthenticationToken.getToken();

    JwtUser jwtUser = validator.validate(token);

//
//    if(jwtUser != null && jwtUser.getRefreshToken() &&  jwtUser.getExpiry() != null && jwtUser.getExpiry().before(new Date())) {
//
//    	throw new BadRequestException("403", "Exception", Constants.HEADDER_INVALID_JWT_TOKEN_EXPIRED_MESSAGE);
//
//    }else 
    	
	if (jwtUser != null && jwtUser.getErrorFlag()) {

		throw new BadRequestException("401", "Exception", jwtUser.getJwtError());
	}

    List<GrantedAuthority> grantedAuthorities = AuthorityUtils.commaSeparatedStringToAuthorityList(jwtUser.getRole());
    return new JwtUserDetails(jwtUser.getUserId(), jwtUser.getCode(), token, grantedAuthorities);
  }

  @Override
  public boolean supports(Class<?> aClass) {
    return (JwtAuthenticationToken.class.isAssignableFrom(aClass));
  }
}
