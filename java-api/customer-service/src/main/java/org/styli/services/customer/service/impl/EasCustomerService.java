package org.styli.services.customer.service.impl;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.pojo.eas.EarnCustomerProfileResponse;
import org.styli.services.customer.pojo.eas.EarnCustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.eas.EarnOnProfileUpdateCompleteRequest;
import org.styli.services.customer.pojo.eas.EarnProfileUpdateRequest;
import org.styli.services.customer.pojo.eas.EarnResponse;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.utility.Constants;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Service
public class EasCustomerService implements ServiceConfigs.ServiceConfigsListener{

	private static final Log LOGGER = LogFactory.getLog(EasCustomerService.class);
	private static final ObjectMapper mapper = new ObjectMapper();
	private boolean stopEasProfileUpdate = false;

	@Autowired
	RestTemplate restTemplate;

	@Autowired
	Client client;

	@Value("${earn.base.url}")
	private String earnUrl;
	
	@Value("${auth.internal.header.bearer.token}")
	private String internalHeaderBearerToken;

	@PostConstruct
	public void init() {
		ServiceConfigs.addConfigListener(this);
		this.onConfigsUpdated(ServiceConfigs.getConsulServiceMap());
	}

	@PreDestroy
	public void destroy() {
		ServiceConfigs.removeConfigListener(this);
	}

	@Override
	public void onConfigsUpdated(Map<String, Object> newConfigs) {
		if (ObjectUtils.isNotEmpty(newConfigs.get("stopEasProfileUpdate"))) {
			stopEasProfileUpdate = (boolean) newConfigs.get("stopEasProfileUpdate");
		} else {
			stopEasProfileUpdate = false;
		}
	}

	public EarnCustomerProfileResponse update(EarnCustomerUpdateProfileRequest earnCustomerUpdateProfileRequest, @RequestHeader Map<String, String> requestHeader) {
		EarnCustomerProfileResponse earnCustomerProfileResponse = new EarnCustomerProfileResponse();
		try {
			LOGGER.info("stopEasProfileUpdate "+stopEasProfileUpdate);
			if (stopEasProfileUpdate) {
				return buildStopEasProfileResponse(earnCustomerProfileResponse);
			}
			CustomerEntity customerEntity = client.findByEntityId(earnCustomerUpdateProfileRequest.getCustomerId());
			if (null == earnCustomerUpdateProfileRequest.getStoreId()
					|| null == earnCustomerUpdateProfileRequest.getCustomerId()
					|| null == earnCustomerUpdateProfileRequest.getStage()) {
				earnCustomerProfileResponse.setStatus(false);
				earnCustomerProfileResponse.setStatusCode("204");
				earnCustomerProfileResponse.setStatusMsg("Invalid request, STORE, CUSTOMER OR STAGE missing!");
			} else {
				if ((earnCustomerUpdateProfileRequest.getStage() == 1
						&& null == earnCustomerUpdateProfileRequest.getGender())
						|| (earnCustomerUpdateProfileRequest.getStage() == 2
								&& (null == earnCustomerUpdateProfileRequest.getAgeGroupId()
										&& Objects.isNull(earnCustomerUpdateProfileRequest.getDob())))
						|| (earnCustomerUpdateProfileRequest.getStage() == 3
								&& null == earnCustomerUpdateProfileRequest.getMobileNumber())) {
					earnCustomerProfileResponse.setStatus(false);
					earnCustomerProfileResponse.setStatusCode("204");
					earnCustomerProfileResponse.setStatusMsg("Invalid request, gender or age group missing!");
				} else {
					earnCustomerUpdateProfileRequest.setIsVerifyMobileNumber(customerEntity.getIsPhoneNumberVerified());
					if (earnCustomerUpdateProfileRequest.getStage() == 1) {
						customerEntity.setGender(earnCustomerUpdateProfileRequest.getGender());
						client.saveAndFlushCustomerEntity(customerEntity);
						return profileUpdate(earnCustomerProfileResponse, earnCustomerUpdateProfileRequest, requestHeader);
					} else if (earnCustomerUpdateProfileRequest.getStage() == 2) {
						if (Objects.isNull(earnCustomerUpdateProfileRequest.getAgeGroupId())
								&& Objects.nonNull(earnCustomerUpdateProfileRequest.getDob())) {
							customerEntity.setDob(earnCustomerUpdateProfileRequest.getDob());
						}else {
							customerEntity.setAgeGroupId(earnCustomerUpdateProfileRequest.getAgeGroupId());
						}
						client.saveAndFlushCustomerEntity(customerEntity);
						return profileUpdate(earnCustomerProfileResponse, earnCustomerUpdateProfileRequest, requestHeader);
					} else if (earnCustomerUpdateProfileRequest.getStage() == 3) {
						customerEntity.setPhoneNumber(earnCustomerUpdateProfileRequest.getMobileNumber());
						client.saveAndFlushCustomerEntity(customerEntity);
						return mobileVerified(earnCustomerProfileResponse, earnCustomerUpdateProfileRequest, requestHeader);
					}

				}
			}

		} catch (Exception exception) {
			LOGGER.error("ErrorEarn: " + exception.getMessage());
			earnCustomerProfileResponse.setStatus(false);
			earnCustomerProfileResponse.setStatusCode("204");
			earnCustomerProfileResponse.setStatusMsg(Constants.ERROR_MSG);
		}

		return earnCustomerProfileResponse;
	}

	private EarnCustomerProfileResponse buildStopEasProfileResponse(EarnCustomerProfileResponse earnCustomerProfileResponse) {
		EarnResponse earnResponse = new EarnResponse();
		earnResponse.setCode(0);
		earnResponse.setCoins(0);
		earnCustomerProfileResponse.setStatus(true);
		earnCustomerProfileResponse.setStatusCode("200");
		earnCustomerProfileResponse.setStatusMsg(Constants.EAS_SUCCESS_MSG);
		earnCustomerProfileResponse.setEarnResponse(earnResponse);

		return earnCustomerProfileResponse;
	}

	public EarnCustomerProfileResponse profileUpdate(EarnCustomerProfileResponse earnCustomerProfileResponse,
			EarnCustomerUpdateProfileRequest earnCustomerUpdateProfileRequest, @RequestHeader Map<String, String> requestHeader) {
		try {
			EarnProfileUpdateRequest earnProfileUpdateRequest = new EarnProfileUpdateRequest();
			earnProfileUpdateRequest.setStage(earnCustomerUpdateProfileRequest.getStage());
			earnProfileUpdateRequest.setCustomerId(earnCustomerUpdateProfileRequest.getCustomerId());
			earnProfileUpdateRequest.setStoreId(earnCustomerUpdateProfileRequest.getStoreId());
			if (Objects.nonNull(earnCustomerUpdateProfileRequest.getDob())) {
				SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
				earnProfileUpdateRequest.setDob(simpleDateFormat.format(earnCustomerUpdateProfileRequest.getDob()));
			}else {
				earnProfileUpdateRequest.setDob("");
			}
			if(Objects.nonNull(earnCustomerUpdateProfileRequest.getIsVerifyMobileNumber())) {
			  earnProfileUpdateRequest.setIsVerifyMobileNumber(earnCustomerUpdateProfileRequest.getIsVerifyMobileNumber());
			}else {
			  earnProfileUpdateRequest.setIsVerifyMobileNumber(true);
			}

			HttpHeaders requestHeaders = setRequestHeaders(requestHeader);

			HttpEntity<EarnProfileUpdateRequest> requestBody = new HttpEntity<>(earnProfileUpdateRequest, requestHeaders);

			String detailsEarnUrl = earnUrl + "/api/v1/profileUpdate";
			LOGGER.info("Earn profileUpdate URL: " + detailsEarnUrl);
			LOGGER.info("Earn profileUpdate Request Body:" + mapper.writeValueAsString(requestBody.getBody()));

			ResponseEntity<EarnResponse> response = restTemplate.exchange(detailsEarnUrl, HttpMethod.POST, requestBody,
					EarnResponse.class);
			EarnResponse earnResponse = response.getBody();
			LOGGER.info("Earn profileUpdate Response Body:" + mapper.writeValueAsString(response.getBody()));
			earnCustomerProfileResponse.setStatus(true);
			earnCustomerProfileResponse.setStatusCode("200");
			earnCustomerProfileResponse.setStatusMsg("Success!");
			earnCustomerProfileResponse.setEarnResponse(earnResponse);
		} catch (Exception exception) {
			LOGGER.error("Earn onMobileVerified ErrorEarn: " + exception.getMessage());
			earnCustomerProfileResponse.setStatus(false);
			earnCustomerProfileResponse.setStatusCode("204");
			earnCustomerProfileResponse.setStatusMsg(Constants.ERROR_MSG);
		}
		return earnCustomerProfileResponse;
	}

	public EarnCustomerProfileResponse mobileVerified(EarnCustomerProfileResponse earnCustomerProfileResponse,
			EarnCustomerUpdateProfileRequest earnCustomerUpdateProfileRequest, @RequestHeader Map<String, String> requestHeader) {
		try {
			EarnProfileUpdateRequest earnProfileUpdateRequest = new EarnProfileUpdateRequest();
			earnProfileUpdateRequest.setStage(earnCustomerUpdateProfileRequest.getStage());
			earnProfileUpdateRequest.setCustomerId(earnCustomerUpdateProfileRequest.getCustomerId());
			earnProfileUpdateRequest.setStoreId(earnCustomerUpdateProfileRequest.getStoreId());
			if(Objects.isNull(earnCustomerUpdateProfileRequest.getIsVerifyMobileNumber())) {
			  earnProfileUpdateRequest.setIsVerifyMobileNumber(true);
			}else {
			  earnProfileUpdateRequest.setIsVerifyMobileNumber(earnCustomerUpdateProfileRequest.getIsVerifyMobileNumber());
			}
			HttpHeaders requestHeaders = setRequestHeaders(requestHeader);

			HttpEntity<EarnProfileUpdateRequest> requestBody = new HttpEntity<>(earnProfileUpdateRequest, requestHeaders);

			String detailsEarnUrl = earnUrl + "/api/v1/onMobileVerified";
			LOGGER.info("Earn onMobileVerified URL: " + detailsEarnUrl);
			LOGGER.info("Earn onMobileVerified request Body:" + mapper.writeValueAsString(requestBody.getBody()));
			ResponseEntity<EarnResponse> response = restTemplate.exchange(detailsEarnUrl, HttpMethod.POST, requestBody,
					EarnResponse.class);
			EarnResponse earnResponse = response.getBody();
			LOGGER.info("Earn onMobileVerified response Body:" + mapper.writeValueAsString(response.getBody()));
			earnCustomerProfileResponse.setStatus(true);
			earnCustomerProfileResponse.setStatusCode("200");
			earnCustomerProfileResponse.setStatusMsg("Success!");
			earnCustomerProfileResponse.setEarnResponse(earnResponse);
		} catch (Exception exception) {
			LOGGER.error("Earn onMobileVerified ErrorEarn: " + exception.getMessage());
			earnCustomerProfileResponse.setStatus(false);
			earnCustomerProfileResponse.setStatusCode("204");
			earnCustomerProfileResponse.setStatusMsg(Constants.ERROR_MSG);
		}
		return earnCustomerProfileResponse;
	}

	public void profileUpdatedCompleted(CustomerEntity customerEntity, String deviceId) {

		if (customerEntity.getGender() != null || customerEntity.getAgeGroupId() != null) {
			EarnOnProfileUpdateCompleteRequest earnOnProfileUpdateCompleteRequest = new EarnOnProfileUpdateCompleteRequest();

			earnOnProfileUpdateCompleteRequest.setCustomerId(customerEntity.getEntityId());
			earnOnProfileUpdateCompleteRequest.setStoreId(customerEntity.getStoreId());
			earnOnProfileUpdateCompleteRequest.setFirstName(customerEntity.getFirstName());
			earnOnProfileUpdateCompleteRequest.setMiddleName(customerEntity.getMiddleName());
			earnOnProfileUpdateCompleteRequest.setLastName(customerEntity.getLastName());
			earnOnProfileUpdateCompleteRequest.setAgeGroupId(customerEntity.getAgeGroupId());
			earnOnProfileUpdateCompleteRequest.setGender(customerEntity.getGender());
			earnOnProfileUpdateCompleteRequest.setMobileNumber(customerEntity.getPhoneNumber());
			if (Objects.nonNull(customerEntity.getDob())) {
				SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
				earnOnProfileUpdateCompleteRequest.setDob(simpleDateFormat.format(customerEntity.getDob()));
			}else {
				earnOnProfileUpdateCompleteRequest.setDob("");
			}
			if(Objects.isNull(customerEntity.getIsPhoneNumberVerified())) {
			earnOnProfileUpdateCompleteRequest.setIsVerifyMobileNumber(true);
			}else {
				earnOnProfileUpdateCompleteRequest.setIsVerifyMobileNumber(customerEntity.getIsPhoneNumberVerified());
			}
			try {
				
				
				HttpHeaders requestHeaders = new HttpHeaders();
				requestHeaders.setContentType(MediaType.APPLICATION_JSON);
				requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
				requestHeaders.add(Constants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));
				requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
				if(StringUtils.isNotEmpty(deviceId) && StringUtils.isNotBlank(deviceId)){
					requestHeaders.set("device-id", deviceId);
				}
				HttpEntity<EarnOnProfileUpdateCompleteRequest> requestBody = new HttpEntity<>(earnOnProfileUpdateCompleteRequest, requestHeaders);

				String earnonProfileUpdateCompletedUrl = earnUrl + "/api/v1/onProfileUpdateCompleted";
				LOGGER.info("Earn onProfileUpdateCompleted url: " + earnonProfileUpdateCompletedUrl);
				LOGGER.info("Earn onProfileUpdateCompleted request Body:"
						+ mapper.writeValueAsString(requestBody.getBody()));
				restTemplate.exchange(earnonProfileUpdateCompletedUrl, HttpMethod.POST, requestBody,
						EarnOnProfileUpdateCompleteRequest.class);
			} catch (Exception exception) {
				LOGGER.error("ErrorEarn: " + exception.getMessage());
			}
		}

		return;
	}
	
	public HttpHeaders getRequestHeaders(@RequestHeader Map<String, String> requestHeader) {
		
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
	
		for (Map.Entry<String, String> entry : requestHeader.entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue();
            if ("Token".equalsIgnoreCase(k) && null != v && v.length() > 3) {
            	requestHeaders.add(Constants.HEADDER_X_HEADER_TOKEN, v);
            	LOGGER.info("Earn Token: " + v);
            }
            if ("X-Header-Token".equalsIgnoreCase(k) && null != v && v.length() > 3) {
        		requestHeaders.add(Constants.HEADDER_X_TOKEN, v);
        		LOGGER.info("Earn X-Header-Token: " + v);
            }
        }
		return requestHeaders;
	}

	public String getAuthorization(String authToken) {
		String token = null;
		if (StringUtils.isNotEmpty(authToken)) {
			if (authToken.contains(",")) {
				List<String> authTokenList = Arrays.asList(authToken.split(","));
				if (CollectionUtils.isNotEmpty(authTokenList)) {
					token = authTokenList.get(0);
				}
			}
		}
		return token;
	}
	
	public boolean checkAuthorization(String intenalAuthorizationToken) {
		
		boolean statusFlag = false;
		LOGGER.info("intenalAuthorizationToken:"+intenalAuthorizationToken);
		if(StringUtils.isNotEmpty(intenalAuthorizationToken) && StringUtils.isNotBlank(internalHeaderBearerToken)) {
			
			String intenalToken = internalHeaderBearerToken;
			
			if(intenalToken.contains(",")) {
				List<String> authTokenList = Arrays.asList(intenalToken.split(","));
				if(CollectionUtils.isNotEmpty(authTokenList) && authTokenList.contains(intenalAuthorizationToken)) {
					statusFlag = true;
					return statusFlag;
				}
			}
		}
		
		return statusFlag;
	}

	public HttpHeaders setRequestHeaders(@RequestHeader Map<String, String> requestHeader){
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.add("content-type", "application/json");
		if(StringUtils.isNotEmpty(requestHeader.get(Constants.HEADER_X_SOURCE)) && StringUtils.isNotBlank(requestHeader.get(Constants.HEADER_X_SOURCE)) ){
			requestHeaders.add("x-source", requestHeader.get(Constants.HEADER_X_SOURCE));
		}
		if(StringUtils.isNotEmpty(requestHeader.get(Constants.HEADER_X_SOURCE_LARGE)) && StringUtils.isNotBlank(requestHeader.get(Constants.HEADER_X_SOURCE_LARGE))){
			requestHeaders.add("x-source", requestHeader.get(Constants.HEADER_X_SOURCE_LARGE));
		}
		if(StringUtils.isNotEmpty(requestHeader.get(Constants.Token)) && StringUtils.isNotBlank(requestHeader.get(Constants.Token) )){
			requestHeaders.add("token", requestHeader.get(Constants.Token));
		}

		if(StringUtils.isNotEmpty(requestHeader.get(Constants.Token_small)) && StringUtils.isNotBlank(requestHeader.get(Constants.Token_small) )){
			requestHeaders.add("token", requestHeader.get(Constants.Token_small));
		}

		if(StringUtils.isNotEmpty(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION)) && StringUtils.isNotBlank(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION) )){
			requestHeaders.add("x-client-version", requestHeader.get(Constants.HEADER_X_CLIENT_VERSION));
		}

		if(StringUtils.isNotEmpty(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION_CAPITAL)) && StringUtils.isNotBlank(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION_CAPITAL) )){
			requestHeaders.add("x-client-version", requestHeader.get(Constants.HEADER_X_CLIENT_VERSION_CAPITAL));
		}

		if(StringUtils.isNotEmpty(requestHeader.get(Constants.HEADDER_X_TOKEN)) && StringUtils.isNotBlank(requestHeader.get(Constants.HEADDER_X_TOKEN) )){
			requestHeaders.add("x-header-token", requestHeader.get(Constants.HEADDER_X_TOKEN));
		}
		if(StringUtils.isNotEmpty(requestHeader.get(Constants.HEADDER_X_TOKEN_SMALL)) && StringUtils.isNotBlank(requestHeader.get(Constants.HEADDER_X_TOKEN_SMALL) )){
			requestHeaders.add("x-header-token", requestHeader.get(Constants.HEADDER_X_TOKEN_SMALL));
		}

		if(StringUtils.isNotEmpty(requestHeader.get(Constants.deviceId)) && StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))){
			requestHeaders.set("device-id", requestHeader.get(Constants.deviceId));
		}
		return requestHeaders;
	}
}
