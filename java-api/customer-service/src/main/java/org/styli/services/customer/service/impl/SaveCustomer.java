package org.styli.services.customer.service.impl;

import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.model.CustomerLogs;
import org.styli.services.customer.model.GuestSessions;
import org.styli.services.customer.model.SequenceCustomerEntity;
import org.styli.services.customer.pojo.GetQuotePreferredPaymentMethodRequest;
import org.styli.services.customer.pojo.GetQuotePreferredPaymentMethodResponse;
import org.styli.services.customer.pojo.PreferredPaymentMethod;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.request.CustomerV4Registration;
import org.styli.services.customer.pojo.registration.response.*;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.repository.SequenceCustomerEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerLogsRepository;
import org.styli.services.customer.repository.Customer.GuestSessionsRepository;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.utility.CommonUtility;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.styli.services.customer.service.OtpService;


@Component
public class SaveCustomer {

	private static final Log LOGGER = LogFactory.getLog(SaveCustomer.class);
	
    Map<Integer, String> attributeMap;
    
    @Value("${region}")
    String region;
    
    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;
    
    @Value("${auth.internal.header.bearer.token}")
	private String internalHeaderBearerToken;

	@Autowired
	CustomerEntityRepository customerEntityRepository;

    @Autowired
    SequenceCustomerEntityRepository sequenceCustomerEntityRepository;
    
    @Autowired
    OtpService otpService;
    
    @Autowired
    Client client;
    
    @Value("${quote.service.base.url}")
	String quoteServiceBaseUrl;
    
    @Autowired
    private ObjectMapper mapper;	    

	public CustomerV4RegistrationResponse saveCustomer(final CustomerV4Registration customerInfoRequest,
													   Map<String, String> requestHeader,
													   final PasswordHelper passwordHelper,
													   final Client client, String jwtFlag)
			throws CustomerException {

        final CustomerV4RegistrationResponse response = new CustomerV4RegistrationResponse();
        final CustomerV4RegistrationResponseBody customerResponse = new CustomerV4RegistrationResponseBody();

        if (null != customerInfoRequest.getCustomerInfo()
                && null != customerInfoRequest.getCustomerInfo().getCustomer()) {

            if(!checkIfEmailAndPasswordMissing(customerInfoRequest, response) && ObjectUtils.isNotEmpty(response)){

				if(ObjectUtils.isNotEmpty(response)) {
					return response;
				}

			}
			//Check Email already exists or not
			if (StringUtils.isNotEmpty(customerInfoRequest.getCustomerInfo().getCustomer().getEmail())) {
				CustomerEntity customerByEmail = client
						.findByEmail(customerInfoRequest.getCustomerInfo().getCustomer().getEmail());
				if (null !=customerByEmail) {
					response.setStatus(false);
					response.setStatusCode("201");
					response.setStatusMsg(Constants.EMAIL_ALREADY_EXISTS);
					return response;
				}
			}


            if (StringUtils.isNotEmpty(customerInfoRequest.getCustomerInfo().getCustomer().getPhone())) {

            	CustomerEntity customerByPhone = client.findByPhoneNumber(customerInfoRequest.getCustomerInfo().getCustomer().getPhone());

                if (null !=customerByPhone) {

                    response.setStatus(false);
                    response.setStatusCode("206");
                    response.setStatusMsg("Mobile Number is Already Registered !!");

                    return response;
                }
            }

            setAndSaveCustomer(customerInfoRequest, requestHeader, passwordHelper, client, jwtFlag, response, customerResponse);

        } else {

            response.setStatus(false);
            response.setStatusCode("204");
            response.setStatusMsg("Invalid Input Request");
        }

        if (!ObjectUtils.isNotEmpty(response)) {
            response.setStatus(false);
            response.setStatusCode("204");
            response.setStatusMsg("Something Went Wrong");
        }
        return response;

    }

	private boolean checkIfEmailAndPasswordMissing(final CustomerV4Registration customerInfoRequest,
			final CustomerV4RegistrationResponse response) {
		
		boolean valid = true;
		if (StringUtils.isEmpty(customerInfoRequest.getCustomerInfo().getCustomer().getEmail())) {

		    response.setStatus(false);
		    response.setStatusCode("201");
		    response.setStatusMsg("Email ID Is Missing!!");
		    valid = false;
			return valid;

		}
		if (null != customerInfoRequest.getCustomerInfo()
		        && StringUtils.isEmpty(customerInfoRequest.getCustomerInfo().getPassword())) {

		    response.setStatus(false);
		    response.setStatusCode("201");
		    response.setStatusMsg("Password Is Missing!!");
		    valid = false;
			return valid;

		}
		if (null != customerInfoRequest.getCustomerInfo()
		        && null != customerInfoRequest.getCustomerInfo().getCustomer() && !CommonUtility
		                .isValidEmailAddress(customerInfoRequest.getCustomerInfo().getCustomer().getEmail())) {

		    response.setStatus(false);
		    response.setStatusCode("300");
			response.setStatusMsg("Invalid Email ID");
			valid = false;
		}
		return valid;
	}

	private void setAndSaveCustomer(final CustomerV4Registration customerInfoRequest, Map<String, String> requestHeader,
			final PasswordHelper passwordHelper, final Client client, String jwtFlag,
			final CustomerV4RegistrationResponse response, final CustomerV4RegistrationResponseBody customerResponse)
			throws CustomerException {

		String minAppVersion = ServiceConfigs.getMinAppVersionReqdForOtpFeature();

		final CustomerEntity customerByEmail = client
				.findByEmail(customerInfoRequest.getCustomerInfo().getCustomer().getEmail());

		//For Phone
		List<Integer> storeIds = ServiceConfigs.getStoreIdsForOtpFeature();
		LOGGER.info("add/address: Store IDs enabled for OTP feature: " + storeIds);
		Integer storeIdFromPayload = customerInfoRequest.getCustomerInfo().getCustomer().getStoreId();

		boolean isStoreValid = storeIds.contains(storeIdFromPayload);
		boolean isEmailOtpInRegistrationEnabled = ServiceConfigs.getIsEmailOtpInRegistrationEnabled(storeIdFromPayload);

		LOGGER.info("setAndSaveCustomer : isStoreValid" + isStoreValid);
		LOGGER.info("setAndSaveCustomer : storeIdFromPayload" + storeIdFromPayload);
		LOGGER.info("setAndSaveCustomer : otp changes to show?"
				+ (isVersionGreaterOrEqual(customerInfoRequest.getClientVersion(), minAppVersion)
						&& "true".equalsIgnoreCase(customerInfoRequest.getIsSignUpOtpEnabled())));

		try {
			if (storeIdFromPayload != null) {

				if ("true".equalsIgnoreCase(customerInfoRequest.getIsSignUpOtpEnabled())
						&& (isVersionGreaterOrEqual(customerInfoRequest.getClientVersion(), minAppVersion)
								|| customerInfoRequest.getSource().equalsIgnoreCase(Constants.SOURCE_MSITE))) {

					String mobileNumber = null, email = null;
					Boolean mobileVerificationStatus = false, emailVerificationStatus = false;

					if (customerInfoRequest != null && customerInfoRequest.getCustomerInfo() != null
							&& customerInfoRequest.getCustomerInfo().getCustomer() != null) {

						if (customerInfoRequest.getCustomerInfo().getCustomer().getPhone() != null) {
							mobileNumber = customerInfoRequest.getCustomerInfo().getCustomer().getPhone();
							mobileVerificationStatus = otpService.getVerificationStatusFromRedis(mobileNumber);
						}

						if (customerInfoRequest.getCustomerInfo().getCustomer().getEmail() != null) {
							email = customerInfoRequest.getCustomerInfo().getCustomer().getEmail();
							emailVerificationStatus = otpService.getVerificationStatusFromRedis(email);
						}
					}

					if (isStoreValid && (mobileVerificationStatus == null || Boolean.FALSE.equals(mobileVerificationStatus))) {
						response.setStatus(false);
						response.setStatusCode("213");
						response.setStatusMsg("OTP validation failed for mobile");
						return;
					}

					if (isEmailOtpInRegistrationEnabled && (emailVerificationStatus == null || Boolean.FALSE.equals(emailVerificationStatus))) {
						response.setStatus(false);
						response.setStatusCode("214");
						response.setStatusMsg("OTP validation failed for email");
						return;
					}

					if (Boolean.TRUE.equals(mobileVerificationStatus) || Boolean.TRUE.equals(emailVerificationStatus)) {
						createAndSaveCustomerEntity(customerInfoRequest, requestHeader, passwordHelper, client, jwtFlag,
								response, customerResponse, null);
						return;
					}
				}

			}
		} catch (Exception e) {
			LOGGER.info("setAndSaveCustomer : Exception occured" + e);
			response.setStatus(false);
			response.setStatusCode("500");
			response.setStatusMsg("An error occurred while saving the customer");
			return;
		}

		if (customerByEmail == null) {
			LOGGER.info("setAndSaveCustomer : User not registered!");
			createAndSaveCustomerEntity(customerInfoRequest, requestHeader, passwordHelper, client, jwtFlag, response,
					customerResponse, customerByEmail);
		} else {
			response.setStatus(false);
			response.setStatusCode("201");
			response.setStatusMsg(Constants.EMAIL_ALREADY_EXISTS);
		}
	}

	private void createAndSaveCustomerEntity(final CustomerV4Registration customerInfoRequest,
			Map<String, String> requestHeader, final PasswordHelper passwordHelper, final Client client, String jwtFlag,
			final CustomerV4RegistrationResponse response, final CustomerV4RegistrationResponseBody customerResponse,
			final CustomerEntity existingCustomer) {
		
		LOGGER.info("createAndSaveCustomerEntity : Inside createAndSaveCustomerEntity");
		
		try {
			CustomerEntity customerEntity = (existingCustomer == null) ? new CustomerEntity() : existingCustomer;
			
			setCustomerInfo(customerInfoRequest, customerEntity);
		    setCreatedIn(customerInfoRequest, customerEntity);

			customerEntity.setGroupId(1);
			customerEntity.setWebsiteId(customerInfoRequest.getCustomerInfo().getCustomer().getWebsiteId());
			customerEntity.setStoreId(customerInfoRequest.getCustomerInfo().getCustomer().getStoreId());
			customerEntity.setCreatedAt(new Date());
			customerEntity.setUpdatedAt(new Date());
			customerEntity.setIsActive(1);
			customerEntity.setDisableGroupChange(0);
			customerEntity.setClientSource(requestHeader.get(Constants.HEADER_X_SOURCE));

			customerEntity.setPhoneNumber(customerInfoRequest.getCustomerInfo().getCustomer().getPhone());
			customerEntity.setAgeGroupId(customerInfoRequest.getCustomerInfo().getCustomer().getAgeGroupId());
			customerEntity.setIsPhoneNumberVerified(false);

			if ("1".equals(jwtFlag)) {
				customerEntity.setJwtToken(1);
			}

			if (customerInfoRequest.getCustomerInfo().getCustomer().getIsUserConsentProvided() != null) {
				customerEntity.setIsUserConsentProvided(
						customerInfoRequest.getCustomerInfo().getCustomer().getIsUserConsentProvided());
				customerEntity.setUserConsentDate(new Date());
			}

			setPasswordHashForCustomer(customerInfoRequest, passwordHelper, customerEntity);

		    saveCustomerEntity(customerInfoRequest, passwordHelper, client, customerResponse, customerEntity, requestHeader);

			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("SUCCESS");
			response.setResponse(customerResponse);
		} catch (Exception e) {	
			LOGGER.error("createAndSaveCustomerEntity : Error saving customer entity: {}" + e.getMessage());
			response.setStatus(false);
			response.setStatusCode("500");
			response.setStatusMsg("An error occurred while saving the customer");
			return;
		}
	}

	public void processGuestSessionData(Map<String, String> headers,
										Customer customerEntity,
										GuestSessionsRepository guestSessionsRepository,
										CustomerLogsRepository customerLogsRepository,
										String action) {

		String flag = ServiceConfigs.getGuestSessionsTrackingFlag();
		if(!flag.equals("true")) {
			LOGGER.info("guest_sessions_logging false to processGuestSessionData!");
		} else {
			try {
				String deviceId = MapUtils.isNotEmpty(headers) ? headers.get(Constants.HEADER_DEVICE_ID) : null;
				if (StringUtils.isNotBlank(deviceId)) {
					LOGGER.info("deviceId available to processGuestSessionData!");
					GuestSessions guestSession = guestSessionsRepository.findByDeviceId(deviceId);
					if (ObjectUtils.isNotEmpty(guestSession)) {
						LOGGER.info("guestSession available to processGuestSessionData!");
						CustomerLogs customerLog = new CustomerLogs();
						customerLog.setCustomerId(customerEntity.getCustomerId());
						customerLog.setGuestId(guestSession.getEntityId());
						customerLog.setDeviceId(deviceId);
						customerLog.setAction(action);
						customerLog.setCreatedAt(new Timestamp(new Date().getTime()));
						customerLogsRepository.saveAndFlush(customerLog);
					}
				}
			} catch (Exception e) {
				LOGGER.error("could not save guest session data : " + e.getMessage());
			}
		}
	}

	public CustomerEntity saveCustomer(final CustomerV4Registration customerInfoRequest,
									   Map<String, String> requestHeader,
									   final PasswordHelper passwordHelper,
									   final Client client,
									   String jwtFlag,
									   final CustomerV4RegistrationResponse response,
									   final CustomerV4RegistrationResponseBody customerResponse,
									   CustomerLoginV4Request customerLoginRequest,String refreshToken) throws CustomerException {

		final CustomerEntity customerByEmail = client
				.findByEmail(customerInfoRequest.getCustomerInfo().getCustomer().getEmail());

		CustomerEntity customerEntity = new CustomerEntity();
		if (null == customerByEmail) {

			setCustomerInfo(customerInfoRequest, customerEntity);
			setCreatedIn(customerInfoRequest, customerEntity);
			customerEntity.setGroupId(1);
			customerEntity.setWebsiteId(customerInfoRequest.getCustomerInfo().getCustomer().getWebsiteId());
			customerEntity.setStoreId(customerInfoRequest.getCustomerInfo().getCustomer().getStoreId());
			customerEntity.setCreatedAt(new Date());
			customerEntity.setUpdatedAt(new Date());
			customerEntity.setIsActive(1);
			customerEntity.setDisableGroupChange(0);
			customerEntity.setClientSource(requestHeader.get(Constants.HEADER_X_SOURCE));
			customerEntity.setFirstName(customerInfoRequest.getCustomerInfo().getCustomer().getFirstName());

			if(customerLoginRequest.getLoginType().value.equalsIgnoreCase("GOOGLELOGIN")) {
				customerEntity.setSignedUpUsing(1);
				customerEntity.setSignedInNowUsing(1);
			} else if(customerLoginRequest.getLoginType().value.equalsIgnoreCase("APPLELOGIN")) {
				customerEntity.setSignedUpUsing(2);
				customerEntity.setSignedInNowUsing(2);
				customerEntity.setRefreshToken(refreshToken);
			} else if(customerLoginRequest.getLoginType().value.equalsIgnoreCase("WHATSAPPLOGIN")) {
				customerEntity.setSignedUpUsing(3);
			}
			customerEntity.setLastSignedInTimestamp(new Date());
			customerEntity.setRefreshToken(refreshToken);
			customerEntity.setPhoneNumber(customerLoginRequest.getPhoneNumber());
			customerEntity.setAgeGroupId(customerInfoRequest.getCustomerInfo().getCustomer().getAgeGroupId());

			if (null != jwtFlag && "1".equals(jwtFlag)) {
				customerEntity.setJwtToken(1);
			}
			if(StringUtils.isNotBlank(refreshToken)) {
				
				customerEntity.setPhoneNumber(customerLoginRequest.getPhoneNumber());
			}

			setPasswordHashForCustomer(customerInfoRequest, passwordHelper, customerEntity);

			saveCustomerEntity(customerInfoRequest, passwordHelper, client, customerResponse, customerEntity,requestHeader);

			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("SUCCESS");
			response.setResponse(customerResponse);

		} else {
			String message = null;
			message = Constants.EMAIL_ALREADY_EXISTS;
			response.setStatus(false);
			response.setStatusCode("201");
			response.setStatusMsg(message);
		}

		return customerEntity;
	}
	

	private void setCustomerInfo(final CustomerV4Registration customerInfoRequest, CustomerEntity customerEntity) {
		if (null != customerInfoRequest.getCustomerInfo()
				&& null != customerInfoRequest.getCustomerInfo().getCustomer()) {

			customerEntity.setEmail(customerInfoRequest.getCustomerInfo().getCustomer().getEmail().toLowerCase());
			customerEntity.setFirstName(customerInfoRequest.getCustomerInfo().getCustomer().getFirstName());
			customerEntity.setLastName(customerInfoRequest.getCustomerInfo().getCustomer().getLastName());
			customerEntity.setGender(customerInfoRequest.getCustomerInfo().getCustomer().getGender());
		}
	}

	private void setCreatedIn(final CustomerV4Registration customerInfoRequest, CustomerEntity customerEntity) {
		if (customerInfoRequest.getCustomerInfo().getCustomer().getWebsiteId() == 1) {

		    customerEntity.setCreatedIn("English");

		} else if (customerInfoRequest.getCustomerInfo().getCustomer().getWebsiteId() == 2) {

		    customerEntity.setCreatedIn("Arabic");
		}
	}

	private void setPasswordHashForCustomer(final CustomerV4Registration customerInfoRequest,
											final PasswordHelper passwordHelper, CustomerEntity customerEntity) {
		try {
			if (StringUtils.isNotBlank(customerInfoRequest.getCustomerInfo().getPassword()))
				customerEntity.setPasswordHash(
						passwordHelper.getSha256Hash(customerInfoRequest.getCustomerInfo().getPassword(), null));

		} catch (final NoSuchAlgorithmException e) {
			LOGGER.error("Error in password has for customer : " + e);
		}
	}

	private void saveCustomerEntity(final CustomerV4Registration customerInfoRequest,
			final PasswordHelper passwordHelper, final Client client,
			final CustomerV4RegistrationResponseBody customerResponse, CustomerEntity customerEntity,Map<String, String> requestHeader)
			throws CustomerException {
		CustomerEntity customerCreatedObject = null ;
		boolean refreshTokenFlag = Constants.validateRefershTokenEnable(requestHeader);
		try {
			
			
			Boolean isEmailVerified = otpService.getVerificationStatusFromRedis(customerInfoRequest.getCustomerInfo().getCustomer().getEmail());
			LOGGER.info("Inside saveCustomerEntity : isEmailVerified:  " +isEmailVerified + "for email : " +customerInfoRequest.getCustomerInfo().getCustomer().getEmail());
			Boolean isMobileVerified = otpService.getVerificationStatusFromRedis(customerInfoRequest.getCustomerInfo().getCustomer().getPhone());
			LOGGER.info("Inside saveCustomerEntity : isMobileVerified:  " +isMobileVerified + "for mobile : " +customerInfoRequest.getCustomerInfo().getCustomer().getPhone());
			
			if (Boolean.TRUE.equals(isMobileVerified)) {
		        customerEntity.setIsMobileVerified(true);
		    }
		    if (Boolean.TRUE.equals(isEmailVerified)){
		        customerEntity.setIsEmailVerified(true);
		    }
			
				
			if (StringUtils.isNotBlank(region) && region.equalsIgnoreCase("GCC")) {

				SequenceCustomerEntity sequenceCustomerEntity = new SequenceCustomerEntity();
				sequenceCustomerEntity = sequenceCustomerEntityRepository.saveAndFlush(sequenceCustomerEntity);

				customerEntity.setEntityId(sequenceCustomerEntity.getSequenceValue().intValue());
				customerEntity.setId(sequenceCustomerEntity.getSequenceValue().intValue());

				LOGGER.info("Inside GCC loop " + customerEntity.getId());

			} else if (StringUtils.isNotBlank(region) && region.equalsIgnoreCase("IN")) {
				Integer incremntId = getRegistrerIncId();
				if (Objects.nonNull(incremntId)) {
					customerEntity.setEntityId(incremntId);
					customerEntity.setId(incremntId);
				} else {
					LOGGER.error("increment id is null!");
					throw new CustomerException("500", "incremnt id is null");
				}
			}

			try {
				customerCreatedObject = client.saveAndFlushMongoCustomerDocument(customerEntity);
				LOGGER.info("After mongo save " + customerEntity.getId());
			} catch (Exception e) {
				LOGGER.info("Exception in saving to mongo " + e.getStackTrace());
			}
			final Customer savedCustomer;
			
		    if (customerCreatedObject != null) {
		    	savedCustomer = setSavedCustomerInfo(customerCreatedObject,
			            customerInfoRequest.getCustomerInfo().getCustomer().getPhone(),
			            customerInfoRequest.getCustomerInfo().getCustomer().getAgeGroupId());
		    	String userId = null;
				Boolean refereshTokenMatchingFlag= false;
		    	if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
						|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
					userId = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
					refereshTokenMatchingFlag= true;
				}else {
					userId = customerInfoRequest.getCustomerInfo().getCustomer().getEmail();
					refreshTokenFlag = false;
				}

				if (refereshTokenMatchingFlag) {
					Set<LoginHistory> loginHistories = new HashSet<>();
					if(customerCreatedObject.getLoginHistories() != null && !customerCreatedObject.getLoginHistories().isEmpty()){
						loginHistories = customerCreatedObject.getLoginHistories();
					}

					LocalDate currentDate = LocalDate.now(ZoneId.of("UTC"));
					LocalDate expiryDate = currentDate.plusDays(Constants.refreshTokenExpireTimeInDays());

					String refreshToken = passwordHelper.generateRefreshToken();
					customerCreatedObject = saveCustomerLoginHistory(loginHistories, expiryDate, refreshToken, customerCreatedObject, userId, client);
					if(customerCreatedObject == null){
						throw new CustomerException("400", "Customer Saved Without Login History");
					}

					savedCustomer.setRefreshToken(refreshToken);

				}
				client.saveAndFlushCustomerEntity(customerCreatedObject);
		    	savedCustomer.setJwtToken(
			            passwordHelper.generateToken(userId,
			                    String.valueOf(new Date().getTime()),savedCustomer.getCustomerId(),refreshTokenFlag));
		    	savedCustomer.setIsMobileVerified(isMobileVerified);
		    	LOGGER.info("Inside saveCustomerEntity : Setting MobileVerified in DB to  :  " +isMobileVerified + "for mobile : " +customerInfoRequest.getCustomerInfo().getCustomer().getPhone());
		    	savedCustomer.setIsEmailVerified(isEmailVerified);
		    	LOGGER.info("Inside saveCustomerEntity : Setting EmailVerified in DB to :  " +isEmailVerified + "for email : " +customerInfoRequest.getCustomerInfo().getCustomer().getEmail());
			    customerResponse.setCustomer(savedCustomer);
		    }
		   
		    

		} catch (final DataAccessException dataException) {

		    throw new CustomerException("400", dataException.getMessage());
		}
	}

	private Integer getRegistrerIncId() {
		Integer incrementId = null;
		try {
			HttpHeaders requestHeaders = new HttpHeaders();
			requestHeaders.setContentType(MediaType.APPLICATION_JSON);
			requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
			requestHeaders.add(Constants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));

			String customerGccBaseUrl = null;
			if (null != ServiceConfigs.consulServiceMap.get("gcc_customer_service_base_url")) {
				customerGccBaseUrl = (String) ServiceConfigs.consulServiceMap.get("gcc_customer_service_base_url");
			}
			HttpEntity<Object> requestBody = new HttpEntity<>(requestHeaders);
			String url = customerGccBaseUrl + "/rest/customer/oms/registration/incrementid";
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestBody, String.class);
			if (response.getStatusCode() == HttpStatus.OK) {
				String incrementIdString = response.getBody();
				if (null != incrementIdString) {
					incrementId = Integer.parseInt(incrementIdString);
				}
			}
		} catch (RestClientException e) {
			LOGGER.error("exception occoured during get increment id:" + e.getMessage());
		}
		LOGGER.info("incremnt id:" + incrementId);
		return incrementId;
	}

	/**
     * @param savedCustomer
     * @param phone
     * @return
     */
    private Customer setSavedCustomerInfo(final CustomerEntity savedCustomer, final String phone,
            final Integer ageFGroup) {

        final Customer customer = new Customer();
        customer.setCustomerId(savedCustomer.getEntityId());
        customer.setFirstName(savedCustomer.getFirstName());
        customer.setLastName(savedCustomer.getLastName());
        customer.setEmail(savedCustomer.getEmail());
        customer.setMobileNumber(phone);
        customer.setGroupId(savedCustomer.getGroupId());
        customer.setStoreId(savedCustomer.getStoreId());
        customer.setCreatedIn(savedCustomer.getCreatedIn());
        customer.setCreatedAt(savedCustomer.getCreatedAt().toString());
        customer.setUpdatedAt(savedCustomer.getUpdatedAt().toString());
        customer.setGender(savedCustomer.getGender());
        customer.setDob(savedCustomer.getDob());
        customer.setAgeGroupId(ageFGroup);

		customer.setSignedInNowUsing(savedCustomer.getSignedInNowUsing());
		customer.setPasswordAvailable(ObjectUtils.isNotEmpty(savedCustomer.getPasswordHash()));

        return customer;
    }
    
	public String getAuthorization(String authToken) {
		String token = null;
		if (StringUtils.isNotEmpty(authToken) && authToken.contains(",")) {
			List<String> authTokenList = Arrays.asList(authToken.split(","));
			if (CollectionUtils.isNotEmpty(authTokenList)) {
				token = authTokenList.get(0);
			}
		}
		return token;
	}
	
	public boolean isVersionGreaterOrEqual(String clientVersion, String baseVersion) {
	    if (clientVersion == null || baseVersion == null) {
	        return false;
	    }

	    String[] clientParts = clientVersion.split("\\.");
	    String[] baseParts = baseVersion.split("\\.");

	    int length = Math.max(clientParts.length, baseParts.length);
	    for (int i = 0; i < length; i++) {
	        int clientPart = i < clientParts.length ? Integer.parseInt(clientParts[i]) : 0;
	        int basePart = i < baseParts.length ? Integer.parseInt(baseParts[i]) : 0;

	        if (clientPart > basePart) {
	            return true;
	        } else if (clientPart < basePart) {
	            return false;
	        }
	    }

	    return true; 
	}

	public CustomerEntity saveCustomerLoginHistory(Set<LoginHistory> loginHistories, LocalDate expiryDate, String refreshToken, CustomerEntity customerEntity, String deviceId, final Client client) {
		CustomerEntity finalCustomerEntity = null;
		try {
			LOGGER.info("save customer login history data " + refreshToken + "deviceId " + deviceId);
			if (StringUtils.isNotBlank(refreshToken) && StringUtils.isNotEmpty(refreshToken) && StringUtils.isNotEmpty(deviceId) && StringUtils.isNotBlank(refreshToken)) {
				for (int i = 0; i < 2; i++) {
					Set<LoginHistory> loginHistories1 = new HashSet<>();
					if (loginHistories != null && !loginHistories.isEmpty()) {
						loginHistories1 = loginHistories;
					}

					// Build the new login history
					LoginHistory newLoginHistory = LoginHistory.builder()
							.deviceId(deviceId)
							.refreshToken(refreshToken)
							.expiryDate(expiryDate)
							.build();

					// Add the new login history
					loginHistories1.add(newLoginHistory);
					LOGGER.info("All login history data " + loginHistories1);
					customerEntity.setLoginHistories(loginHistories1);

					// Save and fetch the customer entity
					CustomerEntity savedCustomerEntity = client.saveAndFlushCustomerEntity(customerEntity);
					try {
						LOGGER.info("Introducing a delay before fetching the saved customer entity...");
						Thread.sleep(Constants.CUSTOMER_TOKEN_DELAY_IN_MILLISECONDS.intValue());  // Delay of (500 milliseconds)
					} catch (InterruptedException e) {
						LOGGER.error("Error during delay: " + e.getMessage());
						Thread.currentThread().interrupt();  // Restore interrupted state
					}
					CustomerEntity fetchedCustomerEntity = customerEntityRepository.findByEntityId(savedCustomerEntity.getEntityId());
					LOGGER.info("Fetched Customer data " + fetchedCustomerEntity);

					// Check if the login history exists for the current deviceId
					if (fetchedCustomerEntity != null && fetchedCustomerEntity.getLoginHistories() != null) {
						Optional<LoginHistory> existingHistory = fetchedCustomerEntity.getLoginHistories().stream()
								.filter(his -> his != null && Objects.nonNull(his.getDeviceId()) && his.getDeviceId().equals(deviceId))
								.findFirst();

						// If history exists, assign the final entity and break the loop
						if (existingHistory.isPresent()) {
							finalCustomerEntity = fetchedCustomerEntity;
							break;  // Stop the loop after a successful save
						}
					}

					LOGGER.info("Attempt " + (i + 1) + " to save login history complete.");
				}
			}
		} catch (Exception ex) {
			LOGGER.info("error " + ex.getMessage());
			return finalCustomerEntity;
		}
		return finalCustomerEntity;
	}
	
	public void savePreferredPaymentMethodToCustomerEntity(Integer customerId, String paymentMethod, Integer storeId) {
	    CustomerEntity fetchedCustomerEntity = customerEntityRepository.findByEntityId(customerId);
	    LOGGER.info("Fetched Customer data: " +fetchedCustomerEntity);

	    if (fetchedCustomerEntity == null) {
	        LOGGER.info("Customer not found with ID: " +customerId);
	        return;
	    }

	    sendPreferredPaymentToQuote(customerId, paymentMethod,storeId);
	    updatePreferredPaymentForStore(fetchedCustomerEntity, paymentMethod,storeId);
	}
	
	private void sendPreferredPaymentToQuote(Integer customerId, String paymentMethod, Integer storeId) {
	    try {
	    	
	        HttpHeaders headers = new HttpHeaders();
	        headers.setContentType(MediaType.APPLICATION_JSON);
	        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
	        headers.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

	        if (internalHeaderBearerToken != null && internalHeaderBearerToken.contains(",")) {
	            String firstToken = internalHeaderBearerToken.split(",")[0];
	            headers.add("authorization-token", firstToken);
	        }

	        GetQuotePreferredPaymentMethodRequest payload = new GetQuotePreferredPaymentMethodRequest();
	        payload.setCustomerId(customerId);
	        payload.setPreferredPayment(paymentMethod);
	        payload.setStoreId(storeId);

	        HttpEntity<GetQuotePreferredPaymentMethodRequest> request = new HttpEntity<>(payload, headers);
	        String url = quoteServiceBaseUrl + "/rest/quote/save/preferred-payment";

	        LOGGER.info("Preferred Payment Quote URL:" +url);
	        LOGGER.info("Request Body for setting Preferred Payment: " +mapper.writeValueAsString(payload));

	        ResponseEntity<GetQuotePreferredPaymentMethodResponse> response = restTemplate.exchange(
	            url, HttpMethod.POST, request, GetQuotePreferredPaymentMethodResponse.class);

	        LOGGER.info("Preferred Payment Response fetched successfully!");
	        LOGGER.info("Preferred Payment Response Body: " + mapper.writeValueAsString(response.getBody()));
	    } catch (Exception e) {
	        LOGGER.error("Error updating preferred payment for customer" + e);
	    }
	}

	private void updatePreferredPaymentForStore(CustomerEntity customerEntity, String paymentMethod, Integer storeId) {
	    try {
	        List<PreferredPaymentMethod> preferredMethods = customerEntity.getPreferredPaymentMethod();
	        
	        if (preferredMethods == null) {
	            preferredMethods = new ArrayList<>();
	        }

	        boolean updated = false;

	        for (PreferredPaymentMethod method : preferredMethods) {
	        	if (storeId != null && storeId.equals(method.getStoreId())) {
	                method.setMethod(paymentMethod);
	                updated = true;
	                break;
	            }
	        }

	        if (!updated) {
	            PreferredPaymentMethod newMethod = new PreferredPaymentMethod();
	            newMethod.setStoreId(storeId);
	            newMethod.setMethod(paymentMethod);
	            preferredMethods.add(newMethod);
	        }

	        customerEntity.setPreferredPaymentMethod(preferredMethods);
	        client.saveAndFlushCustomerEntity(customerEntity); 

	    } catch (Exception e) {
	        LOGGER.error("Error updating preferred payment method for Customer Entity", e);
	    }
	}

}
