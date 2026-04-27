package org.styli.services.customer.service.impl;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.helper.EmailHelper;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.customer.jwt.security.jwtsecurity.model.UserType;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtGenerator;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.request.CustomerQueryReq;
import org.styli.services.customer.pojo.registration.request.TokenPasswordRequest;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerLoginV4Response;
import org.styli.services.customer.pojo.registration.response.CustomerRestPassResponse;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.pojo.registration.response.PasswordResetResponse;
import org.styli.services.customer.redis.TtlMode;
import org.styli.services.customer.repository.StaticComponents;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.service.PasswordV2Service;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.MailPatternConfigs;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.styli.services.customer.utility.pojo.config.Stores;

/**
 * Created on 06-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Component
@Scope("singleton")
public class PasswordV2ServiceImpl
    implements PasswordV2Service,
        ServiceConfigs.ServiceConfigsListener,
        MailPatternConfigs.MailPatternListener {

  private static final Log LOGGER = LogFactory.getLog(PasswordV2ServiceImpl.class);
  private static final String KEY_FORGOT_PASSWORD = "forgotPassword";

  @Autowired CustomerEntityRepository customerEntityRepository;

  @Autowired StaticComponents staticComponents;

  @Autowired EmailHelper emailHelper;

  @Autowired JwtGenerator jwtGenerator;

  @Autowired JwtValidator jwtValidator;

  @Autowired PasswordHelper passwordHelper;


  
  @Autowired
  Client client;

  @Value("${customer.jwt.flag}")
  String jwtFlag;

  @Value("${template.host.url}")
  String templateHostUrl;
  
    @Autowired
	IosSigninHelper iosSigninHelper;

  private String resetPasswordUrl = "";
  private Map<String, String> forgotPasswordMailPatterns = new LinkedHashMap<>();
  private Map<String, String> forgotPasswordSubject = new LinkedHashMap<>();

  @PostConstruct
  public void init() {
    ServiceConfigs.addConfigListener(this);
    MailPatternConfigs.addMailPatternListener(this);
    checkConsulInitialization();
  }

  @PreDestroy
  public void destroy() {
    ServiceConfigs.removeConfigListener(this);
    MailPatternConfigs.removeMailPatternListener(this);
  }

  @Override
  public void onConfigsUpdated(Map<String, Object> newConfigs) {
    resetPasswordUrl = ServiceConfigs.getUrl(KEY_FORGOT_PASSWORD);
  }

  @Override
  public void onPatternsUpdated(Map<String, Object> newConfigs) {
    forgotPasswordMailPatterns = MailPatternConfigs.getMailPatternMap(KEY_FORGOT_PASSWORD);
    forgotPasswordSubject = MailPatternConfigs.getMailPatternMap(KEY_FORGOT_PASSWORD + "Subject");
  }

  @Override
  public CustomerRestPassResponse forgotPassword(
      Map<String, String> requestHeader, CustomerQueryReq request) throws CustomerException {
    CustomerRestPassResponse response = new CustomerRestPassResponse();

    if (!EmailHelper.validateEmail(request.getUseridentifier())) {
      throw new CustomerException("400", "Invalid Email address!");
    }

    List<Stores> stores = Constants.getStoresList();
    Stores store =
        stores.stream()
            .filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
            .findAny()
            .orElse(null);
    if (store == null || StringUtils.isEmpty(store.getStoreLanguage())) {
      response.setStatus(false);
      response.setStatusCode("201");
      response.setStatusMsg("Store not found!");
      return response;
    }
    CustomerEntity customerEntity =
        customerEntityRepository.findByEmail(request.getUseridentifier().toLowerCase());

    if (customerEntity == null || customerEntity.getEntityId() == null) {
      response.setStatus(false);
      response.setStatusCode("202");
      response.setStatusMsg("User does not exists for this email!");
      return response;
    }
    String fullName = "";
    if (StringUtils.isNotEmpty(customerEntity.getFirstName()))
      fullName = fullName + customerEntity.getFirstName();

    if (StringUtils.isNotEmpty(customerEntity.getLastName()))
      fullName = fullName + " " + customerEntity.getLastName();
    checkConsulInitialization();

    if (StringUtils.isEmpty(resetPasswordUrl)) {
      throw new CustomerException("202", "could not get reset password url!");
    }

    String langCode = "en";
    if(StringUtils.isNotEmpty(store.getStoreLanguage()) && StringUtils.isNotBlank(store.getStoreLanguage()) && store.getStoreLanguage().equalsIgnoreCase("ar_SA")){
      langCode ="ar";
    }


    String content = forgotPasswordMailPatterns.get(langCode);

    String stringUrl= getStringUrl(templateHostUrl, langCode, store.getWebsiteCode());
    LOGGER.info("string url for forget password "+ stringUrl);
    if (StringUtils.isEmpty(content)) {
      throw new CustomerException("202", "could not get mail content!");
    }

    String subject = forgotPasswordSubject.get(langCode);
    if (StringUtils.isEmpty(subject)) {
      throw new CustomerException("202", "could not get mail subject!");
    }

    String code = null;
    if(ObjectUtils.isNotEmpty(customerEntity.getPasswordHash())) {
    	code = DigestUtils.sha256Hex(customerEntity.getPasswordHash());
    } else {
    	if(null != customerEntity.getEntityId())
    		code = DigestUtils.sha256Hex(customerEntity.getEntityId().toString());
    }
    String token = createToken(request.getUseridentifier(), code);

    if (StringUtils.isEmpty(token)) {
      throw new CustomerException("203", "could not create valid token!");
    }


    String finalResetUrl =
        resetPasswordUrl.replace("{{lang-code}}", langCode).replace("{{token}}", token);
    content =
        content.replace("{{full-name}}", fullName).replace("{{password-link}}", finalResetUrl).replace("{{storeLink}}", stringUrl);

    boolean success =
        emailHelper.sendEmail(
            request.getUseridentifier(), fullName, content, EmailHelper.CONTENT_TYPE_HTML, subject, langCode);
    if (!success) {
      throw new CustomerException("204", "Failed to send email!");
    }
    response.setStatus(true);
    response.setStatusCode("200");
    response.setStatusMsg("Success!");
    PasswordResetResponse passRes = new PasswordResetResponse();
    passRes.setValue(true);
    response.setResponse(passRes);
    return response;
  }

  @Override
  public CustomerRestPassResponse resetTokenPassword(
      Map<String, String> requestHeader, TokenPasswordRequest request) {
    CustomerRestPassResponse response = new CustomerRestPassResponse();

    if (request == null) {
      response.setStatus(false);
      response.setStatusCode("400");
      response.setStatusMsg("Bad Request!");
      return response;
    }
    if (StringUtils.isEmpty(request.getNewPassword()) || request.getNewPassword().length() < 6) {
      response.setStatus(false);
      response.setStatusCode("401");
      response.setStatusMsg("Invalid new password!");
      return response;
    }

    JwtUser jwtUser = jwtValidator.validate(request.getToken());
    if (jwtUser == null
        || jwtUser.getExpiry() == null
        || StringUtils.isEmpty(jwtUser.getUserId())
        || StringUtils.isEmpty(jwtUser.getCode())) {
      response.setStatus(false);
      response.setStatusCode("201");
      response.setStatusMsg("Invalid request token!");
      return response;
    }

    long now = Instant.now().toEpochMilli();
    long expiry = jwtUser.getExpiry().getTime();
    if (now > expiry) {
      response.setStatus(false);
      response.setStatusCode("202");
      response.setStatusMsg("Request token has expired!");
      return response;
    }

    CustomerEntity customerEntity;
    try {
      customerEntity = customerEntityRepository.findByEmail(jwtUser.getUserId().toLowerCase());
    } catch (Exception e) {
      customerEntity = null;
    }
    if (customerEntity == null || customerEntity.getEntityId() == null) {
      response.setStatus(false);
      response.setStatusCode("203");
      response.setStatusMsg("This user does not exists");
      return response;
    }

    String code = null;
    if(ObjectUtils.isNotEmpty(customerEntity.getPasswordHash())) {
    	code = DigestUtils.sha256Hex(customerEntity.getPasswordHash());
    } else {
    	if(null != customerEntity.getEntityId())
    		code = DigestUtils.sha256Hex(customerEntity.getEntityId().toString());
    }
    if (!jwtUser.getCode().equalsIgnoreCase(code)) {
      response.setStatus(false);
      response.setStatusCode("204");
      response.setStatusMsg("Invalid code found in token!");
      return response;
    }

    try {
      customerEntity.setPasswordHash(passwordHelper.getSha256Hash(request.getNewPassword(), null));
      customerEntityRepository.save(customerEntity);

      PasswordResetResponse passwordRes = new PasswordResetResponse();
      passwordRes.setValue(true);
      response.setStatus(true);
      response.setStatusCode("200");
      response.setStatusMsg("Password Changed Successfully!!");
      response.setResponse(passwordRes);
    } catch (Exception e) {
      ErrorType error = new ErrorType();
      error.setErrorCode("205");
      error.setErrorMessage(e.getMessage());
      response.setError(error);
      response.setStatus(false);
      response.setStatusCode("500");
      response.setStatusMsg("ERROR !!");
      return response;
    }

    return response;
  }

  private void checkConsulInitialization() {

    if (StringUtils.isEmpty(resetPasswordUrl)) {
      onConfigsUpdated(ServiceConfigs.getConsulServiceMap());
    }

    if (MapUtils.isEmpty(forgotPasswordMailPatterns) || MapUtils.isEmpty(forgotPasswordSubject)) {
      onPatternsUpdated(MailPatternConfigs.getConsulMailPatternMap());
    }
  }

  private String createToken(String email, String code) {
    String result = "";
    if (StringUtils.isEmpty(email) || StringUtils.isEmpty(code)) return result;
    try {
      Instant now = Instant.now();
      TtlMode ttlMode = TtlMode.RESET_PASSWORD;
      long interval = ttlMode.getTimeUnit().toMillis(ttlMode.getValue());
      long expiryMilli = now.toEpochMilli() + interval;
      JwtUser jwtUser = new JwtUser();
      jwtUser.setUserId(email);
      jwtUser.setCode(code);
      jwtUser.setExpiry(Date.from(Instant.ofEpochMilli(expiryMilli)));
      jwtUser.setRole(UserType.GUEST.value);
      result = jwtGenerator.generate(jwtUser);
    } catch (Exception e) {
      LOGGER.error(e);
    }

    return result;
  }

@Override
public CustomerLoginV4Response refreshToken(Map<String, String> requestHeader, CustomerLoginV4Request customerLoginRequest) {
	
	boolean validateFlag = false;
	String refreshToken = null;
	
	String tokenId = null;
	
	CustomerEntity customer ;
	customer = client.findByEmail(customerLoginRequest.getUseridentifier());
	
	if(null != customer) {
		
		tokenId = customer.getRefreshToken();
	}
	
	CustomerLoginV4Response customerLoginRes = new CustomerLoginV4Response();
	try {
	    final boolean isWeb = (MapUtils.isNotEmpty(requestHeader) &&
                org.apache.commons.lang3.StringUtils.isNotEmpty(requestHeader.get("x-source")) &&
                "msite".equals(requestHeader.get("x-source")));
		validateFlag = iosSigninHelper.appleAuth(tokenId,customerLoginRequest,customerLoginRes
				,refreshToken,true, isWeb);
		if(validateFlag) {
			
			customerLoginRes.setStatus(true); 
			customerLoginRes.setStatusCode("200");
			customerLoginRes.setStatusMsg("Token refreshed successfully!");
		}else {
			if (null != customer) {customer.setRefreshToken(null);}
			client.saveAndFlushCustomerEntity(customer);
			customerLoginRes.setStatus(true); 
			customerLoginRes.setStatusCode("401");
			customerLoginRes.setStatusMsg("Something went wrong!");
		}
	} catch (Exception e) {
		
		LOGGER.error("exception during refresh token:"+e.getMessage());
	}
	
	return customerLoginRes;
}
  public static String getStringUrl(String templateHostUrl,String storeCode, String websiteCode){

    return templateHostUrl+websiteCode+'/'+storeCode;

  }

}
