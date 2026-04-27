package org.styli.services.customer.jwt.security.jwtsecurity.controller;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.styli.services.customer.exception.ForbiddenException;
import org.styli.services.customer.jwt.security.jwtsecurity.model.*;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtGenerator;
import org.styli.services.customer.model.GuestSessions;
import org.styli.services.customer.pojo.registration.request.CustomerUpdateProfileRequest;
import org.styli.services.customer.service.ConfigService;
import org.styli.services.customer.service.CustomerV4Service;
import org.styli.services.customer.service.GuestService;
import org.styli.services.customer.utility.CommonUtility;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import java.util.Map;
import java.util.UUID;

import javax.validation.Valid;

@RestController
@RequestMapping("/rest/customer/")
public class TokenController {

    private static final Log LOGGER = LogFactory.getLog(TokenController.class);

  private JwtGenerator jwtGenerator;

  @Value("${secret.react.java.api}")
  private String secretCode;
  
  @Autowired
  ConfigService configService;

  @Autowired
  GuestService guestService;
  
  @Autowired
  CustomerV4Service customerV4Service;

  public TokenController(JwtGenerator jwtGenerator) {
    this.jwtGenerator = jwtGenerator;
  }

  @PostMapping("token")
  public String generate(@RequestBody final JwtUser jwtUser) {

    return jwtGenerator.generate(jwtUser);

  }

    private GuestSessions fetchGuestInfo(Map<String, String> headers, Integer storeId) {
        GuestSessions guestSession = null;
        String flag = ServiceConfigs.getGuestSessionsTrackingFlag();
        if(!flag.equals("true")) {
            LOGGER.info("guest_sessions_logging false to fetchGuestInfo!");
            return null;
        }
        try {
            String xSource = MapUtils.isNotEmpty(headers) ? headers.get(Constants.HEADER_X_SOURCE) : null;
            String deviceId = MapUtils.isNotEmpty(headers) ? headers.get(Constants.HEADER_DEVICE_ID) : null;
            String clientVersion = MapUtils.isNotEmpty(headers) ? headers.get(Constants.HEADER_X_CLIENT_VERSION) : null;
            if (StringUtils.isBlank(xSource) || xSource.equals(Constants.SOURCE_MSITE)) {
                LOGGER.info("x-source is blank or m-site to fetchGuestInfo!");
            } else if (StringUtils.isBlank(deviceId)) {
                LOGGER.info("device-id is blank to fetchGuestInfo!");
            } else {
                guestSession = guestService.getGuestSession(deviceId, xSource, clientVersion, storeId);
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }

        return guestSession;
    }

  /**
   * @param jwtUserInfo
   * @return JWT token This API is only for pass JWT token for Guest User
   */
  @PostMapping("token/create")
  public JwtTokenResponse generateGuest(@RequestBody final JwtUserInfo jwtUserInfo,
                                        @RequestHeader Map<String, String> headers) {

      JwtUser jwtUser = new JwtUser();
      JwtTokenResponse response = new JwtTokenResponse();

      if (ObjectUtils.isEmpty(jwtUserInfo)) {
          response.setStatus(false);
          response.setMessage("Invalid Request!");
          return response;
      }

      if (StringUtils.isBlank(secretCode)) {
          response.setStatus(false);
          response.setMessage("Invalid Code!");
          return response;
      }

      GuestSessions guestSession = fetchGuestInfo(headers, jwtUserInfo.getStoreId());
      String uuid;
      if (guestSession != null) {
          uuid = guestSession.getUuid();
          response.setGuestId(parseNullStr(guestSession.getEntityId()));
      } else {
          uuid = CommonUtility.getUuid().toString();
      }

      jwtUser.setUserId(jwtUserInfo.getEmail());
      jwtUser.setCode(secretCode);
      jwtUser.setRole(UserType.GUEST.value);
      jwtUser.setUuid(uuid);

      String jwtToken = jwtGenerator.generate(jwtUser);
      response.setJwtToken(jwtToken);
      response.setStatus(true);
      response.setMessage(Constants.SUCCESS_MSG);
      return response;

  }

    private String parseNullStr(Object val) {
        return (val == null) ? null : String.valueOf(val);
    }
  
  @GetMapping("/auth/token/validate")
  public ValidateTokenRes hello(@RequestHeader("Token") String tokenHeader) {

	  ValidateTokenRes response = new ValidateTokenRes();
	  response.setStatus(true);
	  response.setStatusCode("200");
	  response.setStatusMsg(Constants.SUCCESS_MSG);
      return response;
  }
  
 
  @PostMapping("v2/token/create")
  public JwtTokenResponse generateCustomerToken(@RequestBody final JwtUserInfo jwtUserInfo
		  , @RequestHeader(value = "authorization-token", required = false) String authorizationToken) throws ForbiddenException {

    JwtUser jwtUser = new JwtUser();
    JwtTokenResponse response = new JwtTokenResponse();

      UUID uuid = CommonUtility.getUuid();
      
      if (!configService.checkAuthorizationInternal(authorizationToken)) {
			throw new ForbiddenException();
		} else if (null != jwtUserInfo) {

            jwtUser.setUserId(jwtUserInfo.getEmail());
            jwtUser.setCode(secretCode);
            if(null !=jwtUserInfo.getCustomerId()) {
                jwtUser.setRole("user");
                jwtUser.setJwtFlag("1");
                jwtUser.setCustomerId(jwtUserInfo.getCustomerId());
            }else {
                jwtUser.setRole(UserType.GUEST.value);
                jwtUser.setUuid(uuid.toString());
            }

            String jwtToken = jwtGenerator.generate(jwtUser);
            response.setJwtToken(jwtToken);
            response.setStatus(true);
            response.setMessage(Constants.SUCCESS_MSG);

          } else {

            response.setStatus(false);
            response.setMessage("Invalid Code !!");
          }


    return response;

  }
  
  @GetMapping("/auth/token/v1/validate")
  public String validateAuthentication(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerUpdateProfileRequest updateProfileReq) {
	  
		customerV4Service.authenticateCheck(requestHeader, updateProfileReq.getCustomerId());

		return "SUCCESS";
	}
  
}
