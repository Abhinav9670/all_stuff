package org.styli.services.order.jwt.security.jwtsecurity.controller;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtTokenResponse;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtUserInfo;
import org.styli.services.order.jwt.security.jwtsecurity.model.UserType;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtGenerator;

@RestController
@RequestMapping("/rest/order/")
public class TokenController {

  private JwtGenerator jwtGenerator;

  @Value("${secret.react.java.api}")
  private String secretCode;

  public TokenController(JwtGenerator jwtGenerator) {
    this.jwtGenerator = jwtGenerator;
  }

  @PostMapping("token")
  public String generate(@RequestBody final JwtUser jwtUser) {

    return jwtGenerator.generate(jwtUser);

  }

  /**
   * @param jwtUserInfo
   * @return JWT token This API is only for pass JWT token for Guest User
   */
  @PostMapping("token/create")
  public JwtTokenResponse generateGuest(@RequestBody final JwtUserInfo jwtUserInfo) {

    JwtUser jwtUser = new JwtUser();
    JwtTokenResponse response = new JwtTokenResponse();

    if (null != jwtUserInfo) {

      if (StringUtils.isNotBlank(secretCode)) {

        jwtUser.setUserId(jwtUserInfo.getEmail());
        jwtUser.setCode(secretCode);
        jwtUser.setRole(UserType.GUEST.value);

        String jwtToken = jwtGenerator.generate(jwtUser);
        response.setJwtToken(jwtToken);
        response.setStatus(true);
        response.setMessage("SUCCESS");

      } else {

        response.setStatus(false);
        response.setMessage("Invalid Code !!");
      }

    } else {

      response.setStatus(false);
      response.setMessage("Invalid Request");
    }

    return response;

  }
}
