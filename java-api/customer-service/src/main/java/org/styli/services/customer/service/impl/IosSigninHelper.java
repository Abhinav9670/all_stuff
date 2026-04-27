package org.styli.services.customer.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.pojo.LoginCredentials;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.response.CustomerLoginV4Response;
import org.styli.services.customer.pojo.response.IdTokenPayload;
import org.styli.services.customer.pojo.response.TokenResponse;
import org.styli.services.customer.utility.Constants;

import com.google.gson.Gson;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;

import io.jsonwebtoken.io.Decoders;


@Component
public class IosSigninHelper {

    private static final String REFRESH_TOKEN_KEY = "refresh_token";


	private static final String CONTENT_TYPE = "Content-Type";


	private static final String CLIENT_SECRET_KEY = "client_secret";


	private static final String CLIENT_ID_KEY = "client_id";


	private static final Log LOGGER = LogFactory.getLog(IosSigninHelper.class);


    @Value("${ios.siginin.client_secret}")
    String isoClientSecret;

    @Value("${ios.siginin.web.client_secret}")
	String isoClientSecretWeb;
    
    @Autowired
	@Qualifier("restTemplateBuilder")
	private RestTemplate restTemplate;


	public boolean appleAuth(
			String authorizationCode,
			CustomerLoginV4Request customerLoginRequest,
			CustomerLoginV4Response customerLoginRes,
			String refreshToken ,
			boolean isrefreshToken,
			boolean isWeb) throws Exception {

		boolean flag = false;
//		String APPLE_AUTH_URL ="https://appleid.apple.com/auth/token";
//		String CLIENT_ID = "com.stylishop.stylidev"; //todo
		String APPLE_AUTH_URL =null;
		String CLIENT_ID = null; 
		String CLIENT_SECRET = null;
		if(null != org.styli.services.customer.utility.Constants.loginCredentials) {
			LoginCredentials loginCredentials = org.styli.services.customer.utility.Constants.loginCredentials;
			 APPLE_AUTH_URL = loginCredentials.getAppleAuthUrl();
				 CLIENT_ID = isWeb? loginCredentials.getIosWebCleintId() : loginCredentials.getIosCleintId();

		}
		CLIENT_SECRET = isWeb? isoClientSecretWeb : isoClientSecret;
		HttpResponse<String> response = null;
		if(isrefreshToken) {
			LOGGER.info("refresh token:"+authorizationCode);
			LOGGER.info("APPLE_AUTH_URL:"+APPLE_AUTH_URL);
			 response = Unirest.post(APPLE_AUTH_URL)
						.header(CONTENT_TYPE, "application/x-www-form-urlencoded").field(CLIENT_ID_KEY, CLIENT_ID)
						.field(CLIENT_SECRET_KEY, CLIENT_SECRET).field("grant_type", REFRESH_TOKEN_KEY)
						.field(REFRESH_TOKEN_KEY, authorizationCode).asString();
		}else {
			response = Unirest.post(APPLE_AUTH_URL)
					.header(CONTENT_TYPE, "application/x-www-form-urlencoded").field(CLIENT_ID_KEY, CLIENT_ID)
					.field(CLIENT_SECRET_KEY, CLIENT_SECRET).field("grant_type", "authorization_code")
					.field("code", authorizationCode).asString();
		}
		 

		LOGGER.info("CLIENT_SECRET:"+CLIENT_SECRET);
		LOGGER.info("response Mapper:"+response);
		LOGGER.info("apple auth response body:"+ response.getBody());
		TokenResponse tokenResponse = new Gson().fromJson(response.getBody(), TokenResponse.class);
		LOGGER.info("tokenResponse" + tokenResponse);
		String idToken = tokenResponse.getId_token();
		if (null != idToken) {
			String payload = idToken.split("\\.")[1];// 0 is header we ignore it for now
			String decoded = new String(Decoders.BASE64.decode(payload));

			IdTokenPayload idTokenPayload = new Gson().fromJson(decoded, IdTokenPayload.class);

			LOGGER.info("idTokenPayload" + idTokenPayload);
			
//			if(null != idTokenPayload && StringUtils.isNotEmpty(idTokenPayload.getEmail())
//					
//					&& !idTokenPayload.getEmail().equalsIgnoreCase(customerLoginRequest.getUseridentifier())) {
//				
//				return false;
//			}
			if(null != idTokenPayload && StringUtils.isNotEmpty(idTokenPayload.getAud())
					
					&& !idTokenPayload.getAud().equalsIgnoreCase(CLIENT_ID)) {
				
				return false;
			}

		} else {

			return flag;
		}

		customerLoginRes.setResponseToken(tokenResponse.getRefresh_token());
		flag = true;
		return flag;
	}

	/**
	 * Revoke the apple authentication token
	 * @param token
	 * @return
	 */
	public boolean revokeToken(String token) {
		try {
			String iosCleintId = Constants.loginCredentials.getIosCleintId();
			String url = Constants.loginCredentials.getAppleBaseUrl() + "/revoke";
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
			MultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
			payload.add(CLIENT_ID_KEY, iosCleintId);
			payload.add(CLIENT_SECRET_KEY, isoClientSecret);
			payload.add("token", token);
			LOGGER.info("### Apple Revoke Request :  " + new Gson().toJson(payload) + "URL : " + url);
			HttpEntity<MultiValueMap<String, String>> formEntity = new HttpEntity<>(payload, headers);
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, formEntity, String.class);
			LOGGER.info("## Apple auth revoke API success. Response : " + response.getBody());
			return response.getStatusCode() == HttpStatus.OK;
		} catch (Exception e) {
			LOGGER.error("Error in revoking apple token : " + token + " Error : " + e);
		}
		return false;
	}
    
}
