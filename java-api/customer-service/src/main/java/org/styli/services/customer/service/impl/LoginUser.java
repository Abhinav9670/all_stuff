package org.styli.services.customer.service.impl;

import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.helper.LoginCapchaHelper;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.pojo.account.CustomerStatus;
import org.styli.services.customer.pojo.limiter.TokenBucketObject;
import org.styli.services.customer.pojo.registration.request.CustomerInfoRequest;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.request.CustomerV4Registration;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerLoginV4Response;
import org.styli.services.customer.pojo.registration.response.CustomerV4RegistrationResponse;
import org.styli.services.customer.pojo.registration.response.CustomerV4RegistrationResponseBody;
import org.styli.services.customer.pojo.registration.response.CustomerV4Response;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.pojo.registration.response.LoginHistory;
import org.styli.services.customer.pojo.whatsapp.WhatsappSignupBucketObject;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.service.OtpService;
import org.styli.services.customer.service.WhatsappService;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.styli.services.customer.service.impl.OtpServiceImpl;

@Component
public class LoginUser {

	//@Autowired
	//GoogleSigninHelper googleSigninHelper;


	@Value("${secret.react.java.api}")
	private String secretReactJavaApi;
	
	@Autowired
	IosSigninHelper iosSigninHelper;

	@Autowired
    OtpService otpService;
	
	@Autowired
    OtpServiceImpl otpServiceImpl;

	@Autowired
	SaveCustomer saveCustomer1;

	Map<Integer, String> attributeMap;
	private static final Log LOGGER = LogFactory.getLog(LoginUser.class);

	public CustomerLoginV4Response login(CustomerLoginV4Request customerLoginRequest,
			Map<String, String> requestHeader,
			PasswordHelper passwordHelper,
			Client client,
			String secretReactJavaApi,
			String jwtFlag,
			LoginCapchaHelper loginCapchaHelper,
			GoogleSigninHelper googleSigninHelper,
			WhatsappService whatsappService,
			SaveCustomer saveCustomer) throws CustomerException {
		CustomerLoginV4Response customerLoginRes = new CustomerLoginV4Response();
		CustomerV4Response response = new CustomerV4Response();

		CustomerEntity customer = client.findByEmail(customerLoginRequest.getUseridentifier());

		if(customerLoginRequest != null && customerLoginRequest.getLoginType() != null) {
			attributeMap = client.getAttrMap();

			// if (null != customerLoginRequest.getLoginType() && customerLoginRequest.getLoginType().value.equalsIgnoreCase(GOOGLELOGIN)) {
			if (Constants.GOOGLELOGIN.equalsIgnoreCase(customerLoginRequest.getLoginType().value)) {

				try {
					handleRequestForGoogleLogin(customerLoginRequest, passwordHelper, client, jwtFlag,
							customerLoginRes, response, requestHeader, googleSigninHelper, saveCustomer);
				} catch (CustomerException e) {

					LOGGER.error("exception occoured during google login:" + e.getMessage());
				}

				// } else if (null != customerLoginRequest.getLoginType() && customerLoginRequest.getLoginType().value.equalsIgnoreCase(APPLELOGIN)) {
			} else if (Constants.APPLELOGIN.equalsIgnoreCase(customerLoginRequest.getLoginType().value)) {

				try {
					handleRequestForAppleLogin(customerLoginRequest, passwordHelper, client, jwtFlag,
							customerLoginRes, response, requestHeader, saveCustomer);
				} catch (CustomerException e) {

					LOGGER.error("exception occoured during apple login:" + e.getMessage());
				}

			} else if (Constants.WHATSAPPLOGIN.equalsIgnoreCase(customerLoginRequest.getLoginType().value)) {

				try {
					handleRequestForWhatsappLogin(customerLoginRequest, passwordHelper, client, jwtFlag,
							customerLoginRes, response, requestHeader, whatsappService, saveCustomer);
				} catch (CustomerException e) {

					LOGGER.error("exception occoured during whatsapp login:" + e.getMessage());
				}

			} else if (Constants.EMAIL.equalsIgnoreCase(customerLoginRequest.getLoginType().value)) {

				customerLoginRes = handleRequestForEmail(customerLoginRequest, passwordHelper, client, jwtFlag,
						customerLoginRes, response, loginCapchaHelper, requestHeader);

			} else if (Constants.MOBILE.equalsIgnoreCase(customerLoginRequest.getLoginType().value)) {

				handleRequestForMobile(customerLoginRequest, passwordHelper, client, secretReactJavaApi, jwtFlag,
						customerLoginRes, response, requestHeader);

			}

			if (customer != null && response.getCustomer() != null) {
				response.getCustomer().setIsInfluencer(Boolean.TRUE.equals(customer.getIsInfluencer()));
			}

			customerLoginRes.setResponse(response);

		}else {

			customerLoginRes.setStatus(false);
			customerLoginRes.setStatusCode("204");
			customerLoginRes.setStatusMsg("Bad Request!!");

			return customerLoginRes;

		}
		return customerLoginRes;

	}

	private void handleRequestForAppleLogin(CustomerLoginV4Request customerLoginRequest, PasswordHelper passwordHelper,
			Client client, String jwtFlag, CustomerLoginV4Response customerLoginRes, CustomerV4Response response,
			Map<String, String> requestHeader, SaveCustomer saveCustomer) throws CustomerException {

		String isSignUpOtpEnabled = ServiceConfigs.getSignUpOtpEnabled();
		Boolean otpVerificationRequired = "true".equalsIgnoreCase(isSignUpOtpEnabled);
		Boolean isMobileVerified = false;
		Customer savedCustomer = new Customer();
		String tokenId = null;
		if (null != customerLoginRequest.getSocialLoginDetails()
				&& null != customerLoginRequest.getSocialLoginDetails().getTokenId()) {
			tokenId = customerLoginRequest.getSocialLoginDetails().getTokenId();
		}

		boolean validateFlag = false;
		String refreshToken = null;
		boolean refreshTokenFlag = Constants.validateRefershTokenEnable(requestHeader);
		final boolean isWeb = (MapUtils.isNotEmpty(requestHeader) &&
				StringUtils.isNotEmpty(requestHeader.get("x-source")) &&
				"msite".equals(requestHeader.get("x-source")));
		try {
			validateFlag = iosSigninHelper.appleAuth(tokenId, customerLoginRequest, customerLoginRes, refreshToken,
					false, isWeb);
		} catch (Exception e1) {
			LOGGER.error("exception occoured during iso login validation");
		}
		if (validateFlag) {

			LOGGER.info("ios refresh token:" + customerLoginRes.getResponseToken());
			refreshToken = customerLoginRes.getResponseToken();
			CustomerEntity customer;
			customer = client.findByEmail(customerLoginRequest.getUseridentifier());

			if (null == customer) {
				String blockRegistration = ServiceConfigs.isRegistrationBlocked();
				if (null != blockRegistration && "true".equals(blockRegistration)) {
					ErrorType error = new ErrorType();
					error.setErrorCode("500");
					error.setErrorMessage(Constants.REGISTRATION_BLOCKED);
					customerLoginRes.setError(error);
					customerLoginRes.setStatus(false);
					customerLoginRes.setStatusCode("500");
					customerLoginRes.setStatusMsg(Constants.ERROR_MSG);
					throw new CustomerException("500", Constants.REGISTRATION_BLOCKED);
				}
				org.styli.services.customer.pojo.registration.request.Customer customerObject = new org.styli.services.customer.pojo.registration.request.Customer();
				/** registration flow **/

				if (StringUtils.isNotBlank(customerLoginRequest.getFullName())
						&& customerLoginRequest.getFullName().contains(" ")) {

					String stripFullName = customerLoginRequest.getFullName().replaceAll(Constants.CHARACTERFILETR, "");

					String[] nameArr = stripFullName.split(" ");
					savedCustomer.setFirstName(nameArr[0]);
					savedCustomer.setLastName(nameArr[nameArr.length - 1]);

				} else if (StringUtils.isNotBlank(customerLoginRequest.getFullName())) {
					savedCustomer.setFirstName(customerLoginRequest.getFullName());
				} else if (StringUtils.isNoneEmpty(customerLoginRequest.getUseridentifier())) {

					String[] firstName = customerLoginRequest.getUseridentifier().split("@");

					if (ArrayUtils.isNotEmpty(firstName)
							&& StringUtils.isNotBlank(firstName[0])) {
						savedCustomer.setFirstName(firstName[0]);
					} else {
						savedCustomer.setFirstName("iOS User");
					}
					savedCustomer.setLastName(".");
				} else {

					savedCustomer.setFirstName("iOS User");
					savedCustomer.setLastName(".");
				}

				CustomerV4Registration customerV4Registration = new CustomerV4Registration();
				CustomerInfoRequest request = new CustomerInfoRequest();
				request.setPassword("");
				customerObject.setWebsiteId(customerLoginRequest.getWebsiteId());
				customerObject.setEmail(customerLoginRequest.getUseridentifier());
				customerObject.setFirstName(savedCustomer.getFirstName());
				customerObject.setLastName(savedCustomer.getLastName());
				if (null != customerLoginRequest.getStoreId()) {
					customerObject.setStoreId(customerLoginRequest.getStoreId());
				}
				request.setCustomer(customerObject);
				customerV4Registration.setCustomerInfo(request);
				final CustomerV4RegistrationResponse finalresponse = new CustomerV4RegistrationResponse();
				final CustomerV4RegistrationResponseBody customerResponse = new CustomerV4RegistrationResponseBody();
				customer = saveCustomer.saveCustomer(customerV4Registration,
						requestHeader,
						passwordHelper,
						client,
						jwtFlag,
						finalresponse,
						customerResponse,
						customerLoginRequest, refreshToken);

				if (ObjectUtils.isNotEmpty(customer)) {
					customer.setSocilaLoginType(2);
					customer.setRefreshToken(customerLoginRes.getResponseToken());
					if (customerLoginRequest.getIsUserConsentProvided() != null) {
						customer.setIsUserConsentProvided(customerLoginRequest.getIsUserConsentProvided());
						customer.setUserConsentDate(new Date());
					}
					if (customerLoginRequest.getPhoneNumber() != null) {
		                isMobileVerified = otpService.getVerificationStatusFromRedis(customerLoginRequest.getPhoneNumber());
		            }
					customer = updateMobileVerified(customer,isMobileVerified);
			        customer.setIsEmailVerified(true);
					client.saveAndFlushCustomerEntity(customer);

				}
				savedCustomer = setSavedCustomerInfo(customer, customerLoginRequest.getUseridentifier(),
						customerLoginRequest.getAgeGroupId());
				String userId = null;
				Boolean refereshTokenMatchingFlag= false;
				if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
						|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
					userId = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
					refereshTokenMatchingFlag= true;
				}else {
					userId = savedCustomer.getEmail();
					refreshTokenFlag = false;
				}
				savedCustomer.setJwtToken(passwordHelper.generateToken(userId,
						String.valueOf(new Date().getTime()), savedCustomer.getCustomerId(),refreshTokenFlag));
				if (refereshTokenMatchingFlag) {
					String refreshLoginToken = passwordHelper.generateRefreshToken();
					Set<LoginHistory> loginHistories = customer.getLoginHistories();

					LocalDate currentDate = LocalDate.now(ZoneId.of("UTC"));
					LocalDate expiryDate = currentDate.plusDays(Constants.refreshTokenExpireTimeInDays());

					customer = saveCustomer.saveCustomerLoginHistory(loginHistories, expiryDate, refreshToken, customer, userId, client);
					if(customer == null) {
						throw new CustomerException("400", "Customer Saved Without Login History");
					}

					savedCustomer.setRefreshToken(refreshLoginToken);

				}
				client.saveAndFlushCustomerEntity(customer);
				response.setCustomer(savedCustomer);
				response.setRegistrationResponse(true);
				customerLoginRes.setResponse(response);
				customerLoginRes.setStatus(true);
				customerLoginRes.setStatusCode("200");
				customerLoginRes.setStatusMsg(Constants.SUCCESSFUL_LOGIN);
			} else {

				if (null != customerLoginRequest.getAgeGroupId()) {

					customer.setAgeGroupId(customerLoginRequest.getAgeGroupId());

				}
				if (null != customerLoginRequest.getGender()) {

					customer.setGender(customerLoginRequest.getGender());
				}

				if ("1".equals(jwtFlag)) {

					customer.setJwtToken(1);
				}
				customer.setSocilaLoginType(1);

				if (customerLoginRequest.getLoginType().value.equalsIgnoreCase(Constants.GOOGLELOGIN)) {
					customer.setSignedInNowUsing(1);
				} else if (customerLoginRequest.getLoginType().value.equalsIgnoreCase(Constants.APPLELOGIN)) {
					customer.setSignedInNowUsing(2);
				}
				customer.setLastSignedInTimestamp(new Date());
				customer.setRefreshToken(refreshToken);
				if (customerLoginRequest.getIsUserConsentProvided() != null) {
					customer.setIsUserConsentProvided(customerLoginRequest.getIsUserConsentProvided());
					customer.setUserConsentDate(new Date());
				}
				if (customerLoginRequest.getPhoneNumber() != null) {
	                isMobileVerified = otpService.getVerificationStatusFromRedis(customerLoginRequest.getPhoneNumber());
	            }
				customer = updateMobileVerified(customer,isMobileVerified);
	            customer.setIsEmailVerified(true);
				client.saveAndFlushCustomerEntity(customer);
				savedCustomer = setSavedCustomerInfo(customer, customerLoginRequest.getUseridentifier(),
						customerLoginRequest.getAgeGroupId());
				String userId = null;
				Boolean refereshTokenMatchingFlag= false;
				if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
						|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
					userId = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
					refereshTokenMatchingFlag=true;
				}else {
					userId = savedCustomer.getEmail();
					refreshTokenFlag = false;
				}
				if (refereshTokenMatchingFlag) {
					String refreshLoginToken = null;
					Set<LoginHistory> loginHistories = customer.getLoginHistories();
					Optional<LoginHistory> isTokenExists = loginHistories.stream()
							.filter(his -> Objects.nonNull(his.getDeviceId())
									&& his.getDeviceId().equals(requestHeader.get(Constants.deviceId)))
							.findFirst();
					LocalDate currentDate = LocalDate.now(ZoneId.of("UTC"));
					LocalDate expiryDate = currentDate.plusDays(Constants.refreshTokenExpireTimeInDays());

					String deviceId=null;
					if (isTokenExists.isPresent()) {
						refreshLoginToken = isTokenExists.get().getRefreshToken();
						LoginHistory loginHistory1 = isTokenExists.get();
						if(ObjectUtils.isNotEmpty(loginHistory1)) {
							if(loginHistory1.getExpiryDate()!= null) {
								LocalDate loginExpiryDate = loginHistory1.getExpiryDate();
								if (ChronoUnit.DAYS.between(loginExpiryDate, currentDate) < Constants.refreshTokenExpireTimeInDays()) {
									loginHistory1.setExpiryDate(expiryDate);
									customer.setLoginHistories(loginHistories);
								}
							}else {
								loginHistory1.setExpiryDate(expiryDate);
								loginHistories.add(loginHistory1);
								customer.setLoginHistories(loginHistories);
							}
						}

					} else {
						refreshLoginToken = passwordHelper.generateRefreshToken();
						customer = saveCustomer.saveCustomerLoginHistory(loginHistories, expiryDate, refreshLoginToken, customer, userId, client);
						if(customer == null){
							throw new CustomerException("400", "Customer Saved Without Login History");
						}
					}

					savedCustomer.setRefreshToken(refreshLoginToken);

				}
				client.saveAndFlushCustomerEntity(customer);
				savedCustomer.setJwtToken(passwordHelper.generateToken(userId,
						String.valueOf(new Date().getTime()), savedCustomer.getCustomerId(),refreshTokenFlag));
				if(customer.getIsActive().equals(CustomerStatus.DELETE_REQUESTED.getValue())) {
					savedCustomer.setDeleteRequested(true);
				}
				response.setRegistrationResponse(true);
				response.setCustomer(savedCustomer);
				customerLoginRes.setResponse(response);
				customerLoginRes.setStatus(true);
				customerLoginRes.setStatusCode("200");
				customerLoginRes.setStatusMsg(Constants.SUCCESSFUL_LOGIN);
			}

		} else {

			customerLoginRes.setStatusCode("211");
			customerLoginRes.setStatusMsg(Constants.INVALID_TOKEN);
			customerLoginRes.setStatus(false);

		}
	}

	private CustomerLoginV4Response handleRequestForGoogleLogin(CustomerLoginV4Request customerLoginRequest,
			PasswordHelper passwordHelper, Client client, String jwtFlag, CustomerLoginV4Response customerLoginRes,
			CustomerV4Response response, Map<String, String> requestHeader, GoogleSigninHelper googleSigninHelper,
			SaveCustomer saveCustomer) throws CustomerException {

		String isSignUpOtpEnabled = ServiceConfigs.getSignUpOtpEnabled();
		Boolean otpVerificationRequired = "true".equalsIgnoreCase(isSignUpOtpEnabled);
		Boolean isMobileVerified = false;
		Customer savedCustomer = new Customer();
		String tokenId = null;
		if (null != customerLoginRequest.getSocialLoginDetails()
				&& null != customerLoginRequest.getSocialLoginDetails().getTokenId()) {
			tokenId = customerLoginRequest.getSocialLoginDetails().getTokenId();
		}
		boolean validateFlag = googleSigninHelper.validateGoogleSignin(customerLoginRequest, tokenId, savedCustomer);
		// validateFlag = true;
		if (validateFlag) {

			boolean refreshTokenFlag = Constants.validateRefershTokenEnable(requestHeader);
			CustomerEntity customer ;
			customer = client.findByEmail(customerLoginRequest.getUseridentifier());

			if (null == customer) {
				String blockRegistration = ServiceConfigs.isRegistrationBlocked();
				if (null != blockRegistration && "true".equals(blockRegistration)) {
					CustomerLoginV4Response errResponse = new CustomerLoginV4Response();
					ErrorType error = new ErrorType();
					error.setErrorCode("500");
					error.setErrorMessage(Constants.REGISTRATION_BLOCKED);
					errResponse.setError(error);
					errResponse.setStatus(false);
					errResponse.setStatusCode("500");
					errResponse.setStatusMsg(Constants.ERROR_MSG);
					return errResponse;
				}
				org.styli.services.customer.pojo.registration.request.Customer customerObject = new org.styli.services.customer.pojo.registration.request.Customer();
				/** registration flow **/
				CustomerV4Registration customerV4Registration = new CustomerV4Registration();
				CustomerInfoRequest request = new CustomerInfoRequest();
				request.setPassword("");
				customerObject.setWebsiteId(customerLoginRequest.getWebsiteId());
				customerObject.setEmail(customerLoginRequest.getUseridentifier());
				customerObject.setFirstName(savedCustomer.getFirstName());
				customerObject.setLastName(savedCustomer.getLastName());
				if (null != customerLoginRequest.getStoreId()) {

					customerObject.setStoreId(customerLoginRequest.getStoreId());
				}
				request.setCustomer(customerObject);
				customerV4Registration.setCustomerInfo(request);
				final CustomerV4RegistrationResponse finalresponse = new CustomerV4RegistrationResponse();
				final CustomerV4RegistrationResponseBody customerResponse = new CustomerV4RegistrationResponseBody();
				customer = saveCustomer.saveCustomer(customerV4Registration,
						requestHeader,
						passwordHelper,
						client,
						jwtFlag,
						finalresponse,
						customerResponse,
						customerLoginRequest, null);

			        if (ObjectUtils.isNotEmpty(customer)) {
			            customer.setSocilaLoginType(1);
			            if (customerLoginRequest.getIsUserConsentProvided() != null) {
			                customer.setIsUserConsentProvided(customerLoginRequest.getIsUserConsentProvided());
			                customer.setUserConsentDate(new Date());
			            }
			            if (customerLoginRequest.getPhoneNumber() != null) {
			                isMobileVerified = otpService.getVerificationStatusFromRedis(customerLoginRequest.getPhoneNumber());
			            }
						customer = updateMobileVerified(customer,isMobileVerified);
				        customer.setIsEmailVerified(true);
			            client.saveAndFlushCustomerEntity(customer);
			        }
				savedCustomer = setSavedCustomerInfo(customer, customerLoginRequest.getUseridentifier(),
						customerLoginRequest.getAgeGroupId());
				String userId = null;
				boolean refereshTokenMatchingFlag= false;
				if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
						|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
					userId = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
					refereshTokenMatchingFlag= true;
				}else {
					userId = savedCustomer.getEmail();
					refreshTokenFlag = false;
				}
				savedCustomer.setJwtToken(passwordHelper.generateToken(userId,
						String.valueOf(new Date().getTime()), savedCustomer.getCustomerId(),refreshTokenFlag));

				if (refereshTokenMatchingFlag) {
					String refreshToken = passwordHelper.generateRefreshToken();
					Set<LoginHistory> loginHistories = customer.getLoginHistories();

					LocalDate currentDate = LocalDate.now(ZoneId.of("UTC"));
					LocalDate expiryDate = currentDate.plusDays(Constants.refreshTokenExpireTimeInDays());

					customer = saveCustomer.saveCustomerLoginHistory(loginHistories, expiryDate, refreshToken, customer, userId, client);
					if(customer == null){
						customerLoginRes.setStatus(false);
						customerLoginRes.setStatusCode("400");
						customerLoginRes.setStatusMsg("Customer Saved Without Login History");
						return customerLoginRes;
					}

					savedCustomer.setRefreshToken(refreshToken);

				}
				client.saveAndFlushCustomerEntity(customer);
				response.setCustomer(savedCustomer);
				response.setRegistrationResponse(true);
				customerLoginRes.setResponse(response);
				customerLoginRes.setStatus(true);
				customerLoginRes.setStatusCode("200");
				customerLoginRes.setStatusMsg(Constants.SUCCESSFUL_LOGIN);
			} else {
				if (null != customerLoginRequest.getAgeGroupId()) {

					customer.setAgeGroupId(customerLoginRequest.getAgeGroupId());

				}
				if (null != customerLoginRequest.getGender()) {

					customer.setGender(customerLoginRequest.getGender());
				}

				if ("1".equals(jwtFlag)) {

					customer.setJwtToken(1);
				}
				customer.setSocilaLoginType(1);

				if (customerLoginRequest.getLoginType().value.equalsIgnoreCase(Constants.GOOGLELOGIN)) {
					customer.setSignedInNowUsing(1);
				} else if (customerLoginRequest.getLoginType().value.equalsIgnoreCase(Constants.APPLELOGIN)) {
					customer.setSignedInNowUsing(2);
				}
				customer.setLastSignedInTimestamp(new Date());
				if (customerLoginRequest.getIsUserConsentProvided() != null) {
					customer.setIsUserConsentProvided(customerLoginRequest.getIsUserConsentProvided());
					customer.setUserConsentDate(new Date());
				}
				if (customerLoginRequest.getPhoneNumber() != null) {
		            isMobileVerified = otpService.getVerificationStatusFromRedis(customerLoginRequest.getPhoneNumber());
		        }
				customer = updateMobileVerified(customer,isMobileVerified);
		        customer.setIsEmailVerified(true);
		        if (customerLoginRequest.getPhoneNumber() != null && !customerLoginRequest.getPhoneNumber().trim().isEmpty()) {
		            customer.setPhoneNumber(customerLoginRequest.getPhoneNumber());
		        }
				client.saveAndFlushCustomerEntity(customer);
				savedCustomer = setSavedCustomerInfo(customer, customerLoginRequest.getUseridentifier(),
						customerLoginRequest.getAgeGroupId());
				String userId = null;
				boolean refereshTokenMatchingFlag= false;
				if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
						|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
					userId = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
					refereshTokenMatchingFlag = true;
				}else {
					userId = savedCustomer.getEmail();
					refreshTokenFlag = false;
				}

				savedCustomer.setJwtToken(passwordHelper.generateToken(userId,
						String.valueOf(new Date().getTime()), savedCustomer.getCustomerId(),refreshTokenFlag));
//				savedCustomer.setJwtToken(passwordHelper.generateToken(userId,
//						String.valueOf(new Date().getTime()), savedCustomer.getCustomerId(),refreshTokenFlag));
				if(customer.getIsActive().equals(CustomerStatus.DELETE_REQUESTED.getValue())) {
					savedCustomer.setDeleteRequested(true);
				}
				if (refereshTokenMatchingFlag) {
					String refreshToken = null;
					Set<LoginHistory> loginHistories = customer.getLoginHistories();
					Optional<LoginHistory> isTokenExists = loginHistories.stream()
							.filter(his -> Objects.nonNull(his.getDeviceId())
									&& his.getDeviceId().equals(requestHeader.get(Constants.deviceId)))
							.findFirst();
					LocalDate currentDate = LocalDate.now(ZoneId.of("UTC"));
					LocalDate expiryDate = currentDate.plusDays(Constants.refreshTokenExpireTimeInDays());

					if (isTokenExists.isPresent()) {
						refreshToken = isTokenExists.get().getRefreshToken();
						LoginHistory loginHistory1 = isTokenExists.get();
						if(ObjectUtils.isNotEmpty(loginHistory1)) {
							if(loginHistory1.getExpiryDate()!= null) {
								LocalDate loginExpiryDate = loginHistory1.getExpiryDate();
								if (ChronoUnit.DAYS.between(loginExpiryDate, currentDate) < Constants.refreshTokenExpireTimeInDays()) {
									loginHistory1.setExpiryDate(expiryDate);
									customer.setLoginHistories(loginHistories);
								}
							}else {
								loginHistory1.setExpiryDate(expiryDate);
								loginHistories.add(loginHistory1);
								customer.setLoginHistories(loginHistories);
							}
						}

					} else {
						refreshToken = passwordHelper.generateRefreshToken();
						customer = saveCustomer.saveCustomerLoginHistory(loginHistories, expiryDate, refreshToken, customer, userId, client);
						if(customer == null){
							customerLoginRes.setStatus(false);
							customerLoginRes.setStatusCode("400");
							customerLoginRes.setStatusMsg("Customer Saved Without Login History");
							return customerLoginRes;
						}
					}

					savedCustomer.setRefreshToken(refreshToken);

				}
				client.saveAndFlushCustomerEntity(customer);
				response.setRegistrationResponse(true);
				response.setCustomer(savedCustomer);
				customerLoginRes.setResponse(response);
				customerLoginRes.setStatus(true);
				customerLoginRes.setStatusCode("200");
				customerLoginRes.setStatusMsg(Constants.SUCCESSFUL_LOGIN);
			}

		} else {

			customerLoginRes.setStatusCode("211");
			customerLoginRes.setStatusMsg(Constants.INVALID_TOKEN);
			customerLoginRes.setStatus(false);

		}

		return customerLoginRes;
	}

	private CustomerLoginV4Response handleRequestForWhatsappLogin(CustomerLoginV4Request customerLoginRequest,
			PasswordHelper passwordHelper,
			Client client, String jwtFlag,
			CustomerLoginV4Response customerLoginRes,
			CustomerV4Response response,
			Map<String, String> requestHeader,
			WhatsappService whatsappService,
			SaveCustomer saveCustomer) throws CustomerException {
		WhatsappSignupBucketObject payloadObject;
		boolean refreshTokenFlag = Constants.validateRefershTokenEnable(requestHeader);
		if(ObjectUtils.isNotEmpty(customerLoginRequest.getSocialLoginDetails()) &&
				( payloadObject = whatsappService.getValidPayloadFromToken(
						customerLoginRequest.getSocialLoginDetails().getTokenId()) ) != null &&
				ObjectUtils.isNotEmpty(payloadObject.getMobileNo())) {
			final String mobileNumber = payloadObject.getMobileNo();
			CustomerEntity customer;
			try {
				customer = client.findByPhoneNumber(mobileNumber);
			} catch (Exception e) {
				LOGGER.error("handleRequestForWhatsappLogin find customer error : " + e.getMessage());
				CustomerLoginV4Response errResponse = new CustomerLoginV4Response();
				errResponse.setStatus(false);
				errResponse.setStatusCode("202");
				errResponse.setStatusMsg("Error in finding customer!");
				return errResponse;
			}

			Customer savedCustomer;
			if (customer == null) {
				/** registration flow **/
				String blockRegistration = ServiceConfigs.isRegistrationBlocked();
				if (null != blockRegistration && "true".equals(blockRegistration)) {
					CustomerLoginV4Response errResponse = new CustomerLoginV4Response();
					ErrorType error = new ErrorType();
					error.setErrorCode("500");
					error.setErrorMessage(Constants.REGISTRATION_BLOCKED);
					errResponse.setError(error);
					errResponse.setStatus(false);
					errResponse.setStatusCode("500");
					errResponse.setStatusMsg(Constants.ERROR_MSG);
					return errResponse;
				}

				final String email = "w_" + mobileNumber
						.replace(" ", "")
						.replace("-", "")
						.replace("+", "") + "@stylishop.com";

				final String firstName = (ObjectUtils.isNotEmpty(payloadObject.getFirstName()))
						? payloadObject.getFirstName()
						: "";
				final String lastName = (ObjectUtils.isNotEmpty(payloadObject.getLastName()))
						? payloadObject.getLastName()
						: "";

				org.styli.services.customer.pojo.registration.request.Customer customerObject = new org.styli.services.customer.pojo.registration.request.Customer();
				customerLoginRequest.setPhoneNumber(mobileNumber);
				CustomerV4Registration customerV4Registration = new CustomerV4Registration();
				CustomerInfoRequest request = new CustomerInfoRequest();
				request.setPassword("");
				customerObject.setWebsiteId(customerLoginRequest.getWebsiteId());
				customerObject.setEmail(email);
				customerObject.setFirstName(firstName);
				customerObject.setLastName(lastName);
				customerObject.setPhone(mobileNumber);
				if (null != customerLoginRequest.getStoreId()) {
					customerObject.setStoreId(customerLoginRequest.getStoreId());
				}
				request.setCustomer(customerObject);
				customerV4Registration.setCustomerInfo(request);
				final CustomerV4RegistrationResponse finalresponse = new CustomerV4RegistrationResponse();
				final CustomerV4RegistrationResponseBody customerResponse = new CustomerV4RegistrationResponseBody();
				customer = saveCustomer.saveCustomer(customerV4Registration,
						requestHeader,
						passwordHelper,
						client,
						jwtFlag,
						finalresponse,
						customerResponse,
						customerLoginRequest,
						null);

				if (ObjectUtils.isNotEmpty(customer)) {
					if (null != customerLoginRequest.getAgeGroupId()) {
						customer.setAgeGroupId(customerLoginRequest.getAgeGroupId());
					}
					if (null != customerLoginRequest.getGender()) {
						customer.setGender(customerLoginRequest.getGender());
					}
					customer.setSocilaLoginType(3);
					customer.setSignedInNowUsing(3);
					customer.setLastSignedInTimestamp(new Date());
					if (customerLoginRequest.getIsUserConsentProvided() != null) {
						customer.setIsUserConsentProvided(customerLoginRequest.getIsUserConsentProvided());
						customer.setUserConsentDate(new Date());
					}
				}
				savedCustomer = setSavedCustomerInfo(customer, customerLoginRequest.getUseridentifier(),
						customerLoginRequest.getAgeGroupId());
				String userId = null;
				Boolean refereshTokenMatchingFlag= false;
				if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
						|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
					userId = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
					refereshTokenMatchingFlag= true;
				}else {
					userId = savedCustomer.getEmail();
					refreshTokenFlag = false;
				}
				savedCustomer.setJwtToken(passwordHelper.generateToken(userId,
						String.valueOf(new Date().getTime()), savedCustomer.getCustomerId(),refreshTokenFlag));
				if (refereshTokenMatchingFlag) {
					String refreshToken = refreshToken = passwordHelper.generateRefreshToken();
					Set<LoginHistory> loginHistories = customer.getLoginHistories();

					LocalDate currentDate = LocalDate.now(ZoneId.of("UTC"));
					LocalDate expiryDate = currentDate.plusDays(Constants.refreshTokenExpireTimeInDays());

					customer = saveCustomer.saveCustomerLoginHistory(loginHistories, expiryDate, refreshToken, customer, userId, client);
					if(customer == null){
						customerLoginRes.setStatus(false);
						customerLoginRes.setStatusCode("400");
						customerLoginRes.setStatusMsg("Customer Saved Without Login History");
						return customerLoginRes;
					}


					savedCustomer.setRefreshToken(refreshToken);

				}
				client.saveAndFlushCustomerEntity(customer);
				// Remove token from cache
				whatsappService.clearToken(customerLoginRequest.getSocialLoginDetails().getTokenId());

				response.setCustomer(savedCustomer);
				response.setRegistrationResponse(true);
				customerLoginRes.setResponse(response);
				customerLoginRes.setStatus(true);
				customerLoginRes.setStatusCode("200");
				customerLoginRes.setStatusMsg(Constants.SUCCESSFUL_LOGIN);
			} else {

				if (null != customerLoginRequest.getAgeGroupId()) {
					customer.setAgeGroupId(customerLoginRequest.getAgeGroupId());
				}
				if (null != customerLoginRequest.getGender()) {
					customer.setGender(customerLoginRequest.getGender());
				}

				if ("1".equals(jwtFlag)) {
					customer.setJwtToken(1);
				}
				customer.setSocilaLoginType(3);
				customer.setSignedInNowUsing(3);
				customer.setSignedUpUsing(3);
				customer.setLastSignedInTimestamp(new Date());


				savedCustomer = setSavedCustomerInfo(customer, customerLoginRequest.getUseridentifier(),
						customerLoginRequest.getAgeGroupId());
				String userId = null;
				Boolean refereshTokenMatchingFlag= false;
				if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
						|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
					userId = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
					refereshTokenMatchingFlag = true;
				}else {
					userId = savedCustomer.getEmail();
					refreshTokenFlag = false;
				}
				if (refereshTokenMatchingFlag) {
					String refreshToken = null;
					Set<LoginHistory> loginHistories = customer.getLoginHistories();
					Optional<LoginHistory> isTokenExists = loginHistories.stream()
							.filter(his -> Objects.nonNull(his.getDeviceId())
									&& his.getDeviceId().equals(requestHeader.get(Constants.deviceId)))
							.findFirst();
					LocalDate currentDate = LocalDate.now(ZoneId.of("UTC"));
					LocalDate expiryDate = currentDate.plusDays(Constants.refreshTokenExpireTimeInDays());

					if (isTokenExists.isPresent()) {
						refreshToken = isTokenExists.get().getRefreshToken();
						LoginHistory loginHistory1 = isTokenExists.get();
						if(ObjectUtils.isNotEmpty(loginHistory1)) {
							if(loginHistory1.getExpiryDate()!= null) {
								LocalDate loginExpiryDate = loginHistory1.getExpiryDate();
								if (ChronoUnit.DAYS.between(loginExpiryDate, currentDate) < Constants.refreshTokenExpireTimeInDays()) {
									loginHistory1.setExpiryDate(expiryDate);
									customer.setLoginHistories(loginHistories);
								}
							}else {
								loginHistory1.setExpiryDate(expiryDate);
								loginHistories.add(loginHistory1);
								customer.setLoginHistories(loginHistories);
							}
						}

					} else {
						refreshToken = passwordHelper.generateRefreshToken();
						customer = saveCustomer.saveCustomerLoginHistory(loginHistories, expiryDate, refreshToken, customer, userId, client);
						if(customer == null){
							customerLoginRes.setStatus(false);
							customerLoginRes.setStatusCode("400");
							customerLoginRes.setStatusMsg("Customer Saved Without Login History");
							return customerLoginRes;
						}
					}

					savedCustomer.setRefreshToken(refreshToken);

				}
				savedCustomer.setJwtToken(passwordHelper.generateToken(userId,
						String.valueOf(new Date().getTime()), savedCustomer.getCustomerId(),refreshTokenFlag));
				if(customer.getIsActive().equals(CustomerStatus.DELETE_REQUESTED.getValue())) {
					savedCustomer.setDeleteRequested(true);
				}
				if(customerLoginRequest.getIsUserConsentProvided() != null){
					customer.setIsUserConsentProvided(customerLoginRequest.getIsUserConsentProvided());
					customer.setUserConsentDate(new Date());
				}
				try {
					client.saveAndFlushCustomerEntity(customer);
				} catch (Exception e) {
					customerLoginRes.setStatusCode("211");
					customerLoginRes.setStatusMsg(e.getMessage());
					customerLoginRes.setStatus(false);
					return customerLoginRes;
				}
				savedCustomer = setSavedCustomerInfo(customer, customerLoginRequest.getUseridentifier(),
						customerLoginRequest.getAgeGroupId());
				savedCustomer.setJwtToken(passwordHelper.generateToken(savedCustomer.getEmail(),
						String.valueOf(new Date().getTime()), savedCustomer.getCustomerId(), refreshTokenFlag));
				if (customer.getIsActive().equals(CustomerStatus.DELETE_REQUESTED.getValue())) {
					savedCustomer.setDeleteRequested(true);
				}

				// Remove token from cache
				whatsappService.clearToken(customerLoginRequest.getSocialLoginDetails().getTokenId());

				response.setCustomer(savedCustomer);
				customerLoginRes.setResponse(response);
				customerLoginRes.setStatus(true);
				customerLoginRes.setStatusCode("200");
				customerLoginRes.setStatusMsg(Constants.SUCCESSFUL_LOGIN);
			}

		} else {
			customerLoginRes.setStatusCode("211");
			customerLoginRes.setStatusMsg(Constants.INVALID_TOKEN);
			customerLoginRes.setStatus(false);
		}

		return customerLoginRes;

	}

	private CustomerLoginV4Response handleRequestForEmail(CustomerLoginV4Request customerLoginRequest,
			PasswordHelper passwordHelper, Client client, String jwtFlag,
			CustomerLoginV4Response customerLoginRes,
			CustomerV4Response response,
			LoginCapchaHelper loginCapchaHelper,
			Map<String, String> requestHeader) {
		CustomerEntity customer;
		customer = client.findByEmail(customerLoginRequest.getUseridentifier());
		LOGGER.info("validate/otp : handleRequestForEmail:");
		if (null != customer) {
			customer.setSignedInNowUsing(0);
			String hash = customer.getPasswordHash();
			final String DELIMITER = "\\:";
			String hashVersion = null;

			if (null != hash) {
				hashVersion = hash.split(DELIMITER)[2];
			} else {

				customerLoginRes.setStatus(false);
				customerLoginRes.setStatusCode("209");
				customerLoginRes.setStatusMsg("There is no passwpord set for this user!");
				return customerLoginRes;
			}

			customerLoginRes = processDifferentHashVersions(customerLoginRequest, passwordHelper, client, jwtFlag,
					customerLoginRes, response, customer, hash, hashVersion, loginCapchaHelper, requestHeader);
			response.setRegistrationResponse(true);
			customerLoginRes.setResponse(response);

		} else {

			customerLoginRes.setStatus(false);
			customerLoginRes.setStatusCode("201");
			customerLoginRes.setStatusMsg("Invalid User ID!");

		}

		return customerLoginRes;
	}

	private void handleRequestForMobile(CustomerLoginV4Request customerLoginRequest, PasswordHelper passwordHelper,
										Client client, String secretReactJavaApi,
										String jwtFlag,
										CustomerLoginV4Response customerLoginRes,
										CustomerV4Response response,Map<String, String> requestHeader) {
		boolean isInternalLogin = true;
		if (null != customerLoginRequest.getPassword()
				&& !customerLoginRequest.getPassword().equals(secretReactJavaApi)) {
			customerLoginRes.setStatus(false);
			customerLoginRes.setStatusCode("203");
			customerLoginRes.setStatusMsg("Wrong Credentials");
			isInternalLogin = false;
		}

		try {
			boolean processCheckForMobile = true;
			// If request contains password , validate password
			// If valid password continue process request for mobile
			LOGGER.info("In handleRequestForMobile : isInternalLogin : "+isInternalLogin);
			if (!isInternalLogin && StringUtils.isNotBlank(customerLoginRequest.getPassword()) && StringUtils.isNotEmpty(customerLoginRequest.getPassword())) {
				processCheckForMobile = handleRequestMobileWithPassword(customerLoginRequest, passwordHelper, client, jwtFlag,
						customerLoginRes, response, requestHeader);
			}
			LOGGER.info("In handleRequestForMobile : processCheckForMobile : "+processCheckForMobile);
			if (processCheckForMobile) {
				processRequestForMobile(customerLoginRequest, passwordHelper, client, jwtFlag, customerLoginRes, response,requestHeader);
				response.setRegistrationResponse(true);
			}
		} catch (DataAccessException de) {

			LOGGER.error("exception occoured during data fetch for login mobile number:" + de.getMessage());
			customerLoginRes.setStatus(false);
			customerLoginRes.setStatusCode("208");
			customerLoginRes.setStatusMsg("exception occured during login.Please check logs");
		}
	}

	private boolean handleRequestMobileWithPassword(CustomerLoginV4Request customerLoginRequest,
														  PasswordHelper passwordHelper, Client client, String jwtFlag,
														  CustomerLoginV4Response customerLoginRes,
														  CustomerV4Response response,
														  Map<String, String> requestHeader) {
		boolean isPasswordValid = false;
		try {
			CustomerEntity customer;
			customer = client.findByPhoneNumber(customerLoginRequest.getUseridentifier());
			LOGGER.info("validate password : handleRequestForMobile:");
			if (null != customer) {
				customer.setSignedInNowUsing(0);
				String hash = customer.getPasswordHash();
				final String DELIMITER = "\\:";
				String hashVersion = null;

				if (null != hash) {
					hashVersion = hash.split(DELIMITER)[2];
				} else {
					customerLoginRes.setStatus(false);
					customerLoginRes.setStatusCode("209");
					customerLoginRes.setStatusMsg("There is no passwpord set for this user!");
				}
				if (null != hashVersion && hashVersion.equals("1")) {
					String createdHash = passwordHelper.getSha256Hash(customerLoginRequest.getPassword(),
							getCustomerSalt(customer));
					if (createdHash.equalsIgnoreCase(hash)) {
						isPasswordValid = true;
					} else {
						customerLoginRes.setStatus(true);
						customerLoginRes.setStatusCode("201");
						customerLoginRes.setStatusMsg("Invalid Password!");
					}
				} else if (null != hashVersion && hashVersion.equals("2")) {
					String createdHash = passwordHelper.getArgon2Id13Hash(customerLoginRequest.getPassword(),
							getCustomerSalt(customer));
					if (createdHash.equalsIgnoreCase(customer.getPasswordHash())) {
						isPasswordValid = true;
					} else {
						customerLoginRes.setStatus(true);
						customerLoginRes.setStatusCode("201");
						customerLoginRes.setStatusMsg("Invalid Password!");
					}
				} else {
					ErrorType error = new ErrorType();
					error.setErrorCode("207");
					error.setErrorMessage("Something Went Wrong !!");
					customerLoginRes.setStatus(false);
					customerLoginRes.setStatusCode("207");
					customerLoginRes.setStatusMsg("Error in Password !! ");
					customerLoginRes.setError(error);
				}
			} else {
				customerLoginRes.setStatus(false);
				customerLoginRes.setStatusCode("201");
				customerLoginRes.setStatusMsg("Invalid User ID!");

			}
		} catch (NoSuchAlgorithmException exception) {

		ErrorType error = new ErrorType();

		error.setErrorCode("400");
		error.setErrorMessage(exception.getMessage());

		customerLoginRes.setStatus(false);
		customerLoginRes.setStatusCode("204");
		customerLoginRes.setStatusMsg("ERROR");
		customerLoginRes.setError(error);
	}

		return isPasswordValid;
	}

	private CustomerLoginV4Response processDifferentHashVersions(CustomerLoginV4Request customerLoginRequest,
			PasswordHelper passwordHelper, Client client, String jwtFlag, CustomerLoginV4Response customerLoginRes,
			CustomerV4Response response,
			CustomerEntity customer,
			String hash,
			String hashVersion,
			LoginCapchaHelper loginCapchaHelper,
			Map<String, String> requestHeader) {
		if (null != hashVersion && hashVersion.equals("1")) {

			customerLoginRes = processhashVersionOne(customerLoginRequest, passwordHelper, client, jwtFlag,
					response, customer, hash, loginCapchaHelper, requestHeader);

		} else if (null != hashVersion && hashVersion.equals("2")) {

			processHashVersionTwo(customerLoginRequest, passwordHelper, client, jwtFlag, customerLoginRes,
					response, customer, loginCapchaHelper, requestHeader);
		} else {

			ErrorType error = new ErrorType();

			error.setErrorCode("207");
			error.setErrorMessage("Something Went Wrong !!");

			customerLoginRes.setStatus(false);
			customerLoginRes.setStatusCode("207");
			customerLoginRes.setStatusMsg("Error in Password !! ");
			customerLoginRes.setError(error);

		}
		return customerLoginRes;
	}

	private void processRequestForMobile(CustomerLoginV4Request customerLoginRequest, PasswordHelper passwordHelper,
										 Client client, String jwtFlag, CustomerLoginV4Response customerLoginRes,
										 CustomerV4Response response,Map<String, String> requestHeader) {
		CustomerEntity customer;
		customer = client.findByPhoneNumber(customerLoginRequest.getUseridentifier());

		if (null != customer) {
			
		    saveCustomerEntityForMobile(customerLoginRequest, passwordHelper, client, jwtFlag, customerLoginRes,
					response, customer,requestHeader);

		} else {

			ErrorType error = new ErrorType();

			error.setErrorCode("201");
			error.setErrorMessage("User not found");

			customerLoginRes.setStatus(false);
			customerLoginRes.setStatusCode("201");
			customerLoginRes.setStatusMsg("User not found");
			customerLoginRes.setError(error);
		}
	}

	private void processHashVersionTwo(CustomerLoginV4Request customerLoginRequest, PasswordHelper passwordHelper,
			Client client, String jwtFlag,
			CustomerLoginV4Response customerLoginRes,
			CustomerV4Response response,
			CustomerEntity customer,
			LoginCapchaHelper loginCapchaHelper,
			Map<String, String> requestHeader) {

		LOGGER.info("validate/otp : processHashVersionTwo:");
		LOGGER.info("validate/otp : getIsOtpVerified:" + customerLoginRequest.getIsOtpVerified());
		String createdHash = passwordHelper.getArgon2Id13Hash(customerLoginRequest.getPassword(),
				getCustomerSalt(customer));

		if (createdHash.equalsIgnoreCase(customer.getPasswordHash())
				|| Boolean.TRUE.equals(customerLoginRequest.getIsOtpVerified())) {

			saveCustomerEntityForHashVersionTwo(customerLoginRequest, passwordHelper, client, jwtFlag,
					customerLoginRes, response, customer,requestHeader);

		} else {

			customerLoginRes.setStatus(true);
			customerLoginRes.setStatusCode("201");
			customerLoginRes.setStatusMsg("Invalid Password!");

			String source = null != requestHeader ? requestHeader.get(Constants.HEADER_X_SOURCE) : null;

			if (null != source
					&& ((Constants.SOURCE_MSITE.equals(source)
							&& "true".equals(ServiceConfigs.getRecaptchaEnabledForMSite()))
							|| ((Constants.SOURCE_MOBILE_ANDROID.equals(source)
									|| Constants.SOURCE_MOBILE_IOS.equals(source))
									&& "true".equals(ServiceConfigs.getRecaptchaEnabledForMobile())))) {

				TokenBucketObject tokenBucketObject = loginCapchaHelper.getCapchaBucketObject(customerLoginRequest,
						requestHeader);

				if (loginCapchaHelper.needsReCapcha(tokenBucketObject)) {
					customerLoginRes.setStatus(true);
					customerLoginRes.setStatusCode("301");
					customerLoginRes.setStatusMsg("Recaptcha needed!");
				}
			}
		}
	}

	private CustomerLoginV4Response processhashVersionOne(CustomerLoginV4Request customerLoginRequest,
			PasswordHelper passwordHelper,
			Client client, String jwtFlag,
			CustomerV4Response response,
			CustomerEntity customer,
			String hash,
			LoginCapchaHelper loginCapchaHelper,
			Map<String, String> requestHeader) {

		LOGGER.info("validate/otp : processhashVersionOne:");
		LOGGER.info("validate/otp : getIsOtpVerified:" + customerLoginRequest.getIsOtpVerified());

		CustomerLoginV4Response customerLoginRes = new CustomerLoginV4Response();
		try {
			String createdHash = passwordHelper.getSha256Hash(customerLoginRequest.getPassword(),
					getCustomerSalt(customer));

			if (createdHash.equalsIgnoreCase(hash) || Boolean.TRUE.equals(customerLoginRequest.getIsOtpVerified())) {

				saveCustomerEntity(customerLoginRequest, passwordHelper, client, jwtFlag, customerLoginRes,
						response, customer,requestHeader);

			} else {

				customerLoginRes.setStatus(true);
				customerLoginRes.setStatusCode("201");
				customerLoginRes.setStatusMsg("Invalid Password!");

				String source = null != requestHeader ? requestHeader.get(Constants.HEADER_X_SOURCE) : null;

				if (null != source
						&& ((Constants.SOURCE_MSITE.equals(source)
								&& "true".equals(ServiceConfigs.getRecaptchaEnabledForMSite()))
								|| ((Constants.SOURCE_MOBILE_ANDROID.equals(source)
										|| Constants.SOURCE_MOBILE_IOS.equals(source))
										&& "true".equals(ServiceConfigs.getRecaptchaEnabledForMobile())))) {

					TokenBucketObject tokenBucketObject = loginCapchaHelper.getCapchaBucketObject(customerLoginRequest,
							requestHeader);

					if (loginCapchaHelper.needsReCapcha(tokenBucketObject)) {
						customerLoginRes.setStatus(true);
						customerLoginRes.setStatusCode("301");
						customerLoginRes.setStatusMsg("Recaptcha needed!");
					}
				}
			}

		} catch (NoSuchAlgorithmException exception) {

			ErrorType error = new ErrorType();

			error.setErrorCode("400");
			error.setErrorMessage(exception.getMessage());

			customerLoginRes.setStatus(false);
			customerLoginRes.setStatusCode("204");
			customerLoginRes.setStatusMsg("ERROR");
			customerLoginRes.setError(error);
		}
		return customerLoginRes;
	}

	private String getCustomerSalt(CustomerEntity customer) {
		String hash = customer.getPasswordHash();

		final String DELIMITER = "\\:";

		String customerSalt = null;

		if (null != hash) {

			customerSalt = hash.split(DELIMITER)[1];
		}
		return customerSalt;
	}

	private void saveCustomerEntityForMobile(CustomerLoginV4Request customerLoginRequest, PasswordHelper passwordHelper,
											 Client client,
											 String jwtFlag,
											 CustomerLoginV4Response customerLoginRes,
											 CustomerV4Response response,
											 CustomerEntity customer,Map<String, String> requestHeader) {
		Customer savedCustomer;
		boolean refreshTokenFlag = Constants.validateRefershTokenEnable(requestHeader);
		if (null != customerLoginRequest.getAgeGroupId()) {

			customer.setAgeGroupId(customerLoginRequest.getAgeGroupId());

		}
		if (null != customerLoginRequest.getGender()) {

			customer.setGender(customerLoginRequest.getGender());
		}

		if ("1".equals(jwtFlag)) {

			customer.setJwtToken(1);
		}
		if (customerLoginRequest.getIsUserConsentProvided() != null) {
			customer.setIsUserConsentProvided(customerLoginRequest.getIsUserConsentProvided());
			customer.setUserConsentDate(new Date());
		}
		customer.setIsPhoneNumberVerified(true);
		client.saveAndFlushCustomerEntity(customer);

		savedCustomer = setSavedCustomerInfo(customer, customerLoginRequest.getUseridentifier(),
		        customerLoginRequest.getAgeGroupId());
		String userId = null;
		Boolean refereshTokenMatchingFlag= false;
		if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
				|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
			userId = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
			refereshTokenMatchingFlag= true;
		}else {
			userId = savedCustomer.getEmail();
			refreshTokenFlag = false;
		}
		if (refereshTokenMatchingFlag) {
			String refreshToken = null;
			Set<LoginHistory> loginHistories = customer.getLoginHistories();
			Optional<LoginHistory> isTokenExists = loginHistories.stream()
					.filter(his -> Objects.nonNull(his.getDeviceId())
							&& his.getDeviceId().equals(requestHeader.get(Constants.deviceId)))
					.findFirst();
			LocalDate currentDate = LocalDate.now(ZoneId.of("UTC"));
			LocalDate expiryDate = currentDate.plusDays(Constants.refreshTokenExpireTimeInDays());

			if (isTokenExists.isPresent()) {
				refreshToken = isTokenExists.get().getRefreshToken();
				LoginHistory loginHistory1 = isTokenExists.get();
				if(ObjectUtils.isNotEmpty(loginHistory1)) {
					if(loginHistory1.getExpiryDate()!= null) {
						LocalDate loginExpiryDate = loginHistory1.getExpiryDate();
						if (ChronoUnit.DAYS.between(loginExpiryDate, currentDate) < Constants.refreshTokenExpireTimeInDays()) {
							loginHistory1.setExpiryDate(expiryDate);
							customer.setLoginHistories(loginHistories);
						}
					}else {
						customer = saveCustomer1.saveCustomerLoginHistory(loginHistories, expiryDate, refreshToken, customer, userId, client);

						if(customer == null) {
							customerLoginRes.setStatus(false);
							customerLoginRes.setStatusCode("400");
							customerLoginRes.setStatusMsg("Customer Saved Without Login History");
						}
					}
				}

			} else {
				refreshToken = passwordHelper.generateRefreshToken();
				customer = saveCustomer1.saveCustomerLoginHistory(loginHistories, expiryDate, refreshToken, customer, userId, client);

				if(customer == null) {
					customerLoginRes.setStatus(false);
					customerLoginRes.setStatusCode("400");
					customerLoginRes.setStatusMsg("Customer Saved Without Login History");
				}
			}

			savedCustomer.setRefreshToken(refreshToken);


		}
		client.saveAndFlushCustomerEntity(customer);
		savedCustomer.setJwtToken(passwordHelper.generateToken(userId,
		        String.valueOf(new Date().getTime()),savedCustomer.getCustomerId(),refreshTokenFlag));
		response.setCustomer(savedCustomer);
		
		String password = customerLoginRequest.getPassword();
		LOGGER.info("saveCustomerEntityForMobile : password : " + password);

		CustomerEntity customerEntity = null;
		if (password == null || StringUtils.isEmpty(password) || password.equalsIgnoreCase(secretReactJavaApi)) {

			String mobile = customerLoginRequest.getUseridentifier();
			LOGGER.info("saveCustomerEntityForMobile : Fetching CustomerEntity for mobile: " + mobile);


			customerEntity = client.findByPhoneNumber(mobile);
			if (Objects.nonNull(customerEntity)) {
				LOGGER.info("saveCustomerEntityForMobile : Updating mobileVerified to true for customer: " +customer.getEntityId());
				customerEntity.setIsMobileVerified(true);
				LOGGER.info("Checking for same phone number in address to make it verified in DB ");
				otpServiceImpl.validateAndMarkMobileVerifiedInAddressDB(customer.getEntityId(),mobile);
				client.saveAndFlushCustomerEntity(customerEntity);
				LOGGER.info("saveCustomerEntityForMobile : CustomerEntity updated successfully for mobile: " + mobile);
			} else {
				LOGGER.info("saveCustomerEntityForMobile : No CustomerEntity found for mobile: " + mobile);
			}
		}

		customerLoginRes.setResponse(response);
		if (response.getCustomer() != null && customerEntity != null) {
			Boolean isInfluencer = Boolean.TRUE.equals(customerEntity.getIsInfluencer());
			LOGGER.info("Setting Influencer flag for login user: {}" +isInfluencer);
			response.getCustomer().setIsInfluencer(isInfluencer);
		}
		customerLoginRes.setStatus(true);
		customerLoginRes.setStatusCode("200");
		customerLoginRes.setStatusMsg("LoggedIn Successfully!!");
	}

	private void saveCustomerEntityForHashVersionTwo(CustomerLoginV4Request customerLoginRequest,
			PasswordHelper passwordHelper, Client client, String jwtFlag, CustomerLoginV4Response customerLoginRes,
			CustomerV4Response response, CustomerEntity customer,Map<String, String> requestHeader) {
		
		boolean refreshTokenFlag = Constants.validateRefershTokenEnable(requestHeader);
		if (null != customerLoginRequest.getAgeGroupId()) {

			customer.setAgeGroupId(customerLoginRequest.getAgeGroupId());

		}
		if (null != customerLoginRequest.getGender()) {

			customer.setGender(customerLoginRequest.getGender());
		}

		if (customerLoginRequest.getIsUserConsentProvided() != null) {
			customer.setIsUserConsentProvided(customerLoginRequest.getIsUserConsentProvided());
			customer.setUserConsentDate(new Date());
		}

		if ("1".equals(jwtFlag)) {
			customer.setJwtToken(1);
		}
		String password = customerLoginRequest.getPassword();
		LOGGER.info("saveCustomerEntity : password : " + password);

		if (password == null || password.equalsIgnoreCase(secretReactJavaApi)) {
		    String email = customerLoginRequest.getUseridentifier();
		    LOGGER.info("saveCustomerEntity : Fetching CustomerEntity for email: {}" + email);

		    CustomerEntity customerEntity = client.findByEmail(email);

		    if (Objects.nonNull(customerEntity)) {
		        LOGGER.info("saveCustomerEntity : CustomerEntity found. Updating emailVerified to true for email: {}" + email);
		        customer.setIsEmailVerified(true);
		        LOGGER.info("saveCustomerEntity : CustomerEntity updated successfully for email: {}" + email);
		    } else {
		        LOGGER.info("saveCustomerEntity : No CustomerEntity found for email: {}" + email);
		    }
		} else {
		    LOGGER.info("saveCustomerEntity : Password is not null or does not match secretReactJavaApi. No DB changes performed.");
		}

		client.saveAndFlushCustomerEntity(customer);

		Customer savedCustomer = getSavedCustomerInfo(customer,
		        customerLoginRequest.getAgeGroupId());
		String userId = null;
		if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
				|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
			userId = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
		}else {
			userId = savedCustomer.getEmail();
			refreshTokenFlag = false;
		}
		savedCustomer.setJwtToken(passwordHelper.generateToken(userId,
		        String.valueOf(new Date().getTime()),savedCustomer.getCustomerId(),refreshTokenFlag));
		
		if(customer.getIsActive().equals(CustomerStatus.DELETE_REQUESTED.getValue())) {
			savedCustomer.setDeleteRequested(true);
		}

		response.setCustomer(savedCustomer);

		customerLoginRes.setResponse(response);
		customerLoginRes.setStatus(true);
		customerLoginRes.setStatusCode("200");
		customerLoginRes.setStatusMsg("Logged In Successfully!!");
	}

	private void saveCustomerEntity(CustomerLoginV4Request customerLoginRequest, PasswordHelper passwordHelper,
			Client client, String jwtFlag, CustomerLoginV4Response customerLoginRes, CustomerV4Response response,
			CustomerEntity customer,Map<String, String> requestHeader) {
		// success
		Customer savedCustomer = null;
		boolean refreshTokenFlag = Constants.validateRefershTokenEnable(requestHeader);
		if (null != customerLoginRequest.getAgeGroupId()) {

			customer.setAgeGroupId(customerLoginRequest.getAgeGroupId());
		}
		if (null != customerLoginRequest.getGender()) {
			customer.setGender(customerLoginRequest.getGender());
		}
		if (customerLoginRequest.getIsUserConsentProvided() != null) {
			customer.setIsUserConsentProvided(customerLoginRequest.getIsUserConsentProvided());
			customer.setUserConsentDate(new Date());
		}
		if ("1".equals(jwtFlag)) {
			customer.setJwtToken(1);
		}
		
		String password1 = customerLoginRequest.getPassword();
		LOGGER.info("saveCustomerEntity : password : " + password1);

		if (password1 == null || password1.equalsIgnoreCase(secretReactJavaApi)) {
		    String email = customerLoginRequest.getUseridentifier();
		    LOGGER.info("saveCustomerEntity : Fetching CustomerEntity for email: {}" + email);

		    CustomerEntity customerEntity = client.findByEmail(email);

		    if (Objects.nonNull(customerEntity)) {
		        LOGGER.info("saveCustomerEntity : CustomerEntity found. Updating emailVerified to true for email: {}" + email);
		        customer.setIsEmailVerified(true);
		        LOGGER.info("saveCustomerEntity : CustomerEntity updated successfully for email: {}" + email);
		    } else {
		        LOGGER.info("saveCustomerEntity : No CustomerEntity found for email: {}" + email);
		    }
		} else {
		    LOGGER.info("saveCustomerEntity : Password is not null or does not match secretReactJavaApi. No DB changes performed.");
		}
		
		client.saveAndFlushCustomerEntity(customer);

		savedCustomer = getSavedCustomerInfo(customer, customerLoginRequest.getAgeGroupId());
		String userId = null;
		boolean refereshTokenMatchingFlag= false;
		LOGGER.info("deviceId:"+requestHeader.get(Constants.deviceId));
		if(refreshTokenFlag && null != requestHeader && (StringUtils.isNotBlank(requestHeader.get(Constants.deviceId))
				|| StringUtils.isNotBlank(requestHeader.get(Constants.DeviceId)))) {
			userId = null != requestHeader.get(Constants.deviceId) ? requestHeader.get(Constants.deviceId):requestHeader.get(Constants.DeviceId);
			refereshTokenMatchingFlag = true;
		}else {
			userId = customerLoginRequest.getUseridentifier();
			refreshTokenFlag = false;
		}
		savedCustomer.setJwtToken(passwordHelper.generateToken(
				userId, String.valueOf(new Date().getTime()),savedCustomer.getCustomerId(),refreshTokenFlag));
		
		if (refereshTokenMatchingFlag) {
			String refreshToken = null;
			Set<LoginHistory> loginHistories = customer.getLoginHistories();
			Optional<LoginHistory> isTokenExists = loginHistories.stream()
					.filter(his -> Objects.nonNull(his.getDeviceId())
							&& his.getDeviceId().equals(requestHeader.get(Constants.deviceId)))
					.findFirst();
			LocalDate currentDate = LocalDate.now(ZoneId.of("UTC"));
			LocalDate expiryDate = currentDate.plusDays(Constants.refreshTokenExpireTimeInDays());

			if (isTokenExists.isPresent()) {
				refreshToken = isTokenExists.get().getRefreshToken();
				LoginHistory loginHistory1 = isTokenExists.get();
				if(ObjectUtils.isNotEmpty(loginHistory1)) {
					if(loginHistory1.getExpiryDate()!= null) {
						LocalDate loginExpiryDate = loginHistory1.getExpiryDate();
						if (ChronoUnit.DAYS.between(loginExpiryDate, currentDate) < Constants.refreshTokenExpireTimeInDays()) {
							loginHistory1.setExpiryDate(expiryDate);
							customer.setLoginHistories(loginHistories);
						}
					}else {
						loginHistory1.setExpiryDate(expiryDate);
						loginHistories.add(loginHistory1);
						customer.setLoginHistories(loginHistories);
					}
				}

			} else {
				refreshToken = passwordHelper.generateRefreshToken();
				customer = saveCustomer1.saveCustomerLoginHistory(loginHistories, expiryDate, refreshToken, customer, userId, client);

				if(customer == null) {
					customerLoginRes.setStatus(false);
					customerLoginRes.setStatusCode("400");
					customerLoginRes.setStatusMsg("Customer Saved Without Login History");
				}

			}

			savedCustomer.setRefreshToken(refreshToken);

		}
		client.saveAndFlushCustomerEntity(customer);
		
		if(customer.getIsActive().equals(CustomerStatus.DELETE_REQUESTED.getValue())) {
			savedCustomer.setDeleteRequested(true);
		}

		response.setCustomer(savedCustomer);

		customerLoginRes.setResponse(response);
		customerLoginRes.setStatus(true);
		customerLoginRes.setStatusCode("200");
		customerLoginRes.setStatusMsg("Logged In Successfully!!");
	}

    /**
     * @param savedCustomer
     * @param ageGroupId
     * @return
     */
    public Customer getSavedCustomerInfo(CustomerEntity savedCustomer, Integer ageGroupId) {

		Customer customer = new Customer();
		customer.setCustomerId(savedCustomer.getEntityId());
		customer.setFirstName(savedCustomer.getFirstName());
		customer.setLastName(savedCustomer.getLastName());
		customer.setEmail(savedCustomer.getEmail());
		customer.setMobileNumber(savedCustomer.getPhoneNumber());
		customer.setGroupId(savedCustomer.getGroupId());
		customer.setCreatedIn(savedCustomer.getCreatedIn());
		customer.setCreatedAt(savedCustomer.getCreatedAt() != null ? savedCustomer.getCreatedAt().toString(): "");
		customer.setUpdatedAt(savedCustomer.getUpdatedAt() != null ? savedCustomer.getUpdatedAt().toString(): "");
		customer.setGender(savedCustomer.getGender());
		customer.setDob(savedCustomer.getDob());
		customer.setAgeGroupId(ageGroupId);

		customer.setStoreId(savedCustomer.getStoreId());

		customer.setSignedInNowUsing(savedCustomer.getSignedInNowUsing());
		customer.setPasswordAvailable(ObjectUtils.isNotEmpty(savedCustomer.getPasswordHash()));
		
		// Mobile number removed flag
		customer.setIsMobileNumberRemoved(Optional.ofNullable(savedCustomer.getIsMobileNumberRemoved()).orElse(false));

		if (null != savedCustomer.getWhatsappOptn()) {
			if (savedCustomer.getWhatsappOptn() == 1) {
				customer.setWhatsAppoptn(true);
			} else
				customer.setWhatsAppoptn(false);
		} else {
			customer.setWhatsAppoptn(false);
		}

		return customer;
	}

	/**
	 * @param savedCustomer
	 * @param phone
	 * @return
	 */
	private Customer setSavedCustomerInfo(CustomerEntity savedCustomer, String phone, Integer ageFGroup) {

		Customer customer = new Customer();
		DateFormat dtFormarmater = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		customer.setCustomerId(savedCustomer.getEntityId());
		customer.setFirstName(savedCustomer.getFirstName());
		customer.setLastName(savedCustomer.getLastName());
		customer.setEmail(savedCustomer.getEmail());
		customer.setMobileNumber(savedCustomer.getPhoneNumber());
		customer.setGroupId(savedCustomer.getGroupId());
		customer.setStoreId(savedCustomer.getStoreId());
		customer.setCreatedIn(savedCustomer.getCreatedIn());
		customer.setCreatedAt(dtFormarmater.format(savedCustomer.getCreatedAt()));
		customer.setOtpCreatedAt(savedCustomer.getCreatedAt().toString());
		customer.setUpdatedAt(dtFormarmater.format(savedCustomer.getUpdatedAt()));
		customer.setGender(savedCustomer.getGender());
		customer.setDob(savedCustomer.getDob());
		customer.setAgeGroupId(ageFGroup);

		customer.setNeedAlternateEmail(StringUtils.isNotEmpty(customer.getEmail())
				&& customer.getEmail().matches(Constants.WHATSAPP_EMAIL_REGEX));
		if (savedCustomer.getIsActive().equals(CustomerStatus.DELETE_REQUESTED.getValue())) {
			customer.setDeleteRequested(true);
		}
		customer.setSignedInNowUsing(savedCustomer.getSignedInNowUsing());
		customer.setPasswordAvailable(ObjectUtils.isNotEmpty(savedCustomer.getPasswordHash()));

		return customer;
	}

	private  CustomerEntity  updateMobileVerified(CustomerEntity customer,boolean isMobileVerified) {
		//First time loggin user
		if(null == customer.getIsMobileVerified()) {
			customer.setIsMobileVerified(isMobileVerified);
		} //second time loggin user
		else if(!customer.getIsMobileVerified()) {
			customer.setIsMobileVerified(isMobileVerified);
		}

		return customer;
	}

}
