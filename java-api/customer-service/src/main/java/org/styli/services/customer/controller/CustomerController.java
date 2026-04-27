package org.styli.services.customer.controller;

import java.util.Map;
import java.util.Objects;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.styli.services.customer.config.KafkaAsyncService;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.pojo.*;
import org.styli.services.customer.pojo.GenericApiResponse;
import org.styli.services.customer.pojo.account.AccountDeleteResponse;
import org.styli.services.customer.pojo.account.AccountDeleteTaskUpdateRequest;
import org.styli.services.customer.pojo.account.AccountDeletionEligibleRequest;
import org.styli.services.customer.pojo.account.AccountDeletionEligibleResponse;
import org.styli.services.customer.pojo.account.AccountDeletionOTPRequest;
import org.styli.services.customer.pojo.account.AccountDeletionRequest;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.address.response.CustomerAddreesResponse;
import org.styli.services.customer.pojo.address.response.NonServiceableAddressDTO;
import org.styli.services.customer.pojo.card.request.CreateCardRequest;
import org.styli.services.customer.pojo.card.request.DeleteCardRequest;
import org.styli.services.customer.pojo.card.response.CustomerCardsResponseDTO;
import org.styli.services.customer.pojo.epsilon.request.LinkShukranRequest;
import org.styli.services.customer.pojo.epsilon.request.ShukranEnrollmentRequest;
import org.styli.services.customer.pojo.epsilon.request.UpgradeShukranTierActivityRequest;
import org.styli.services.customer.pojo.epsilon.response.BuildUpgradeShukranTierActivityResponse;
import org.styli.services.customer.pojo.epsilon.response.DeleteShukranResponse;
import org.styli.services.customer.pojo.epsilon.response.EnrollmentResponse;
import org.styli.services.customer.pojo.nationalid.NationalIdValidationData;
import org.styli.services.customer.pojo.otp.OtpResponseBody;
import org.styli.services.customer.pojo.webhook.ShukranWebhookRequest;
import org.styli.services.customer.pojo.webhook.ShukranWebhookResponse;
import org.styli.services.customer.pojo.otp.OtpValidationType;
import org.styli.services.customer.pojo.otp.SendOtpRegistrationRequest;
import org.styli.services.customer.pojo.otp.SendOtpRequest;
import org.styli.services.customer.pojo.otp.SendOtpResponse;
import org.styli.services.customer.pojo.otp.ValidateOtpRegistrationRequest;
import org.styli.services.customer.pojo.otp.ValidateOtpRequest;
import org.styli.services.customer.pojo.otp.ValidateOtpResponse;
import org.styli.services.customer.pojo.registration.request.*;
import org.styli.services.customer.pojo.registration.response.*;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4;
import org.styli.services.customer.pojo.whatsapp.WhatsappSignupRequest;
import org.styli.services.customer.pojo.whatsapp.WhatsappSignupResponse;
import org.styli.services.customer.pojo.nationalid.DocumentValidateRequest;
import org.styli.services.customer.pojo.nationalid.NationalIdValidationResponse;
import org.styli.services.customer.repository.Customer.CustomerLogsRepository;
import org.styli.services.customer.repository.Customer.GuestSessionsRepository;
import org.styli.services.customer.service.*;
import org.styli.services.customer.service.impl.AddressService;
import org.styli.services.customer.service.impl.AsyncService;
import org.styli.services.customer.service.impl.ConfigServiceImpl;
import org.styli.services.customer.service.impl.SaveCustomer;
import org.styli.services.customer.service.impl.ShukranWebhookServiceImpl;
import org.styli.services.customer.service.NationalIdExtractionService;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.MailPatternConfigs;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.styli.services.customer.service.RsaTokenService;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;

@RestController
@RequestMapping("/rest/customer/")
@Api(value = "/rest/customer/", produces = "application/json")
public class CustomerController {

	@Autowired
	CustomerV4Service customerV4Service;

	@Autowired
	CustomerV5Service customerV5Service;

	@Autowired
	SalesOrderService salesOrderService;

	@Autowired
	OtpService otpService;

	@Autowired
	PasswordV2Service passwordV2Service;

	@Value("${customer.jwt.flag}")
	String jwtFlag;

	@Autowired
	AsyncService asyncService;

	@Autowired
	KafkaAsyncService kafkaAsyncService;

	@Autowired
	private SaveCustomer saveCustomer;

	@Autowired
	GuestSessionsRepository guestSessionsRepository;

	@Autowired
	CustomerLogsRepository customerLogsRepository;

	@Autowired
	AccountDeleteService accountDeleteService;

	@Autowired
	WhatsappService whatsappService;

	@Autowired
	ConfigService configService;

	@Autowired
	private AddressService addressService;

	@Autowired
	private MagicLinkService magicLinkService;

	@Autowired
	private RsaTokenService rsaTokenService;

	@Autowired
	ConfigServiceImpl configServiceImpl;

	@Autowired
	ShukranWebhookServiceImpl shukranWebhookService;

	@Autowired
	NationalIdExtractionService nationalIdExtractionService;

	private static final Logger LOGGER = LoggerFactory.getLogger(CustomerController.class);

	private static final String KEY_MAGIC_LINK = "magicLinkUrl";
	private static final String KEY_MAGIC_LINK_MAIL_SUBJECT = "magicLinkSubject";
	private static final String KEY_MAGIC_LINK_MAIL_CONTENT = "magicLinkContent";
	private final String magicLinkBaseUrl = ServiceConfigs.getUrl(KEY_MAGIC_LINK);
	Map<String, String> mailSubjectMap = MailPatternConfigs.getMailPatternMap(KEY_MAGIC_LINK_MAIL_SUBJECT);
	Map<String, String> mailContentMap = MailPatternConfigs.getMailPatternMap(KEY_MAGIC_LINK_MAIL_CONTENT);
	private static final String USER_IDENTIFIER_EMAIL = "email";
	private static final String USER_IDENTIFIER_MOBILE = "mobile";
	private static final String MESSAGE_EMAIL_ID_DOESNT_EXIST = "Email ID Doesn't Exist";
	private static final String MESSAGE_MOBILE_NUMBER_DOESNT_EXIST = "Mobile Number Doesn't Exist";

	@ApiOperation(value = "Customer V4 Registration", response = CustomerRegistrationResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Registered Successfully", response = CustomerRegistrationResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error"),
			@ApiResponse(code = 404, message = "Customer not found") })
	@ApiImplicitParams({ @ApiImplicitParam(name = "Authorization", value = "", paramType = "header"),
			@ApiImplicitParam(name = "Content-Type", value = "application/json", paramType = "header") })

	@PostMapping("v4/registration")
	public CustomerV4RegistrationResponse saveV3Customer(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerV4Registration customerRegistration) {
		// For Mobile
		List<Integer> storeIds = ServiceConfigs.getStoreIdsForOtpFeature();
		LOGGER.info("registration : Store IDs enabled for OTP feature: " + storeIds);
		// For Email
		List<Integer> emailStoreIds = getStoreIdsForEmailOTPFeatureCheck();
		LOGGER.info("registration : Store IDs enabled for Email OTP feature: " + emailStoreIds);

		Integer storeIdFromPayload = customerRegistration.getCustomerInfo().getCustomer().getStoreId();
		// For Mobile
		Boolean isStoreValid = storeIds.contains(storeIdFromPayload);

		String source = null != requestHeader ? requestHeader.get(Constants.HEADER_X_SOURCE) : null;

		String isSignUpOtpEnabled = ServiceConfigs.getSignUpOtpEnabled();

		customerRegistration.setIsSignUpOtpEnabled(isSignUpOtpEnabled);

		if (Constants.SOURCE_MSITE.equals(source)) {

			customerRegistration.setSource(Constants.SOURCE_MSITE);

		} else if (!Constants.SOURCE_MSITE.equals(source)) {
			customerRegistration.setSource(Constants.SOURCE_MOBILE);
			String clientVersion = requestHeader.getOrDefault("x-client-version", null);

			if (clientVersion != null) {
				customerRegistration.setClientVersion(clientVersion);
				LOGGER.info("auth/address : Client version set to: {}", clientVersion);
			} else {
				LOGGER.info("auth/address : x-client-version header is missing, skipping client version setup.");
			}
		}

		CustomerV4RegistrationResponse response = new CustomerV4RegistrationResponse();
		try {
			String blockRegistration = ServiceConfigs.isRegistrationBlocked();
			if ("true".equals(blockRegistration)) {
				ErrorType error = new ErrorType();
				error.setErrorCode("500");
				error.setErrorMessage("Registration is blocked for customer migration activity");
				response.setError(error);
				response.setStatus(false);
				response.setStatusCode("500");
				response.setStatusMsg(Constants.ERROR_MSG);
				return response;
			}
			response = customerV4Service.saveV4Customer(customerRegistration, requestHeader);

		} catch (CustomerException exception) {

			ErrorType error = new ErrorType();
			error.setErrorCode(exception.getErrorCode());
			error.setErrorMessage(exception.getErrorMessage());
			response.setError(error);
			response.setStatus(false);
			response.setStatusCode("201");
			response.setStatusMsg(Constants.ERROR_MSG);
		}

		/**
		 * Async REST Call to update Sales orders with new customer id
		 * Async call to publish customer object to kafka
		 */
		procesCustomerOrders(customerRegistration, response);

		/**
		 * Sync call to save guest session info in customer logs
		 */
		if (ObjectUtils.isNotEmpty(response) && ObjectUtils.isNotEmpty(response.getResponse())
				&& ObjectUtils.isNotEmpty(response.getResponse().getCustomer())) {
			saveCustomer.processGuestSessionData(requestHeader,
					response.getResponse().getCustomer(),
					guestSessionsRepository,
					customerLogsRepository,
					OtpValidationType.REGISTRATION.toString());
		}

		// Free Shipping flag implementation
		try {
			if (ObjectUtils.isNotEmpty(response) && null != response.getResponse()
					&& null != response.getResponse().getCustomer()) {

				FirstFreeShipping firstFreeShipping = customerV4Service.setFreeShipping(
						response.getResponse().getCustomer().getCreatedAt(),
						response.getResponse().getCustomer().getStoreId());

				response.getResponse().getCustomer().setFirstFreeShipping(firstFreeShipping);
			}
		} catch (Exception ex) {
			LOGGER.info("Error in setting free shipping for customer :: {}", ex.getMessage());
		}

		if (response == null || ObjectUtils.isEmpty(response)) {
			CustomerV4RegistrationResponse response1 = new CustomerV4RegistrationResponse();
			response1.setStatus(false);
			response1.setStatusCode("204");
			response1.setStatusMsg("Something Went Wrong");
			return response1;
		}
		if (isStoreValid != null && isSignUpOtpEnabled != null && "true".equals(isSignUpOtpEnabled) && isStoreValid) {
			response.setIsSignUpOtpEnabled("true");
		} else {
			response.setIsSignUpOtpEnabled("false");
		}
		if (checkEmailStoreValid(emailStoreIds, storeIdFromPayload) && "true".equalsIgnoreCase(isSignUpOtpEnabled)) {
			response.setIsEmailOTPEnabled("true");
		}
		if (response.getResponse() != null && response.getResponse().getCustomer() != null) {
			Customer customer = response.getResponse().getCustomer();
			if (customer.getEmail() != null && customer.getMobileNumber() != null && customer.getFirstName() != null
					&& rsaTokenService.isInfluencerPortalFeatureEnabled(storeIdFromPayload)) {
				EncryptedTokenResponse tokenResponse = rsaTokenService.attachEncryptedRsaToken(customer.getEmail(),
						customer.getMobileNumber(), customer.getFirstName(),
						customer.getLastName() != null ? customer.getLastName() : "", storeIdFromPayload);
				if (tokenResponse != null) {
					response.setEncryptedRsaToken(tokenResponse.getToken());
					response.setEncryptedRsaTokenExpiry(tokenResponse.getExpiry());
				}
			}
		}
		return response;
	}

	private void procesCustomerOrders(CustomerV4Registration customerRegistration,
			CustomerV4RegistrationResponse response) {
		if (ObjectUtils.isNotEmpty(response) && response.getResponse() != null) {

			org.styli.services.customer.pojo.registration.request.Customer customerReg = customerRegistration
					.getCustomerInfo().getCustomer();

			Customer customerNew = new Customer();
			if (Objects.nonNull(customerReg.getOldEmail()) && Objects.nonNull(customerReg.getEmail())
					&& !customerReg.getEmail().equalsIgnoreCase(customerReg.getOldEmail())) {
				customerNew.setWhatsApp(response.getResponse().getCustomer().isWhatsApp());
				customerNew.setMobileNumber(customerReg.getPhone());
				customerNew.setFirstName(customerReg.getFirstName());
				customerNew.setLastName(customerReg.getLastName());
				customerNew.setCustomerId(response.getResponse().getCustomer().getCustomerId());
				customerNew.setEmail(customerReg.getOldEmail());
				customerNew.setUpdatedEmail(customerReg.getEmail());
				customerNew.setUpdateEmail(true);
			} else {
				customerNew = response.getResponse().getCustomer();
			}
			asyncService.asyncSalesOrdersUpdateCustId(customerNew);
			kafkaAsyncService.publishCustomerEntityToKafka(response.getResponse().getCustomer());
		}
	}

	@ApiOperation(value = "Customer validate", response = CustomerExistResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Validate Successfully", response = CustomerExistResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error"),
			@ApiResponse(code = 404, message = "User not found") })
	@ApiImplicitParams({ @ApiImplicitParam(name = "Content-Type", value = "application/json", paramType = "header") })
	@PostMapping("validate")
	public CustomerExistResponse isExistsCustomer(
			@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerQueryReq customerExitsReq) {

		String isSignUpOtpEnabled = ServiceConfigs.getSignUpOtpEnabled();
		Boolean isEmailVerificationEnabled = ServiceConfigs
				.getIsEmailVerificationEnabled(customerExitsReq.getStoreId());
		Boolean isMagicLinkEnabled = ServiceConfigs.getIsMagicLinkEnabled(customerExitsReq.getStoreId());
		Boolean isEmailOtpEnabledV1 = ServiceConfigs.getIsEmailOtpEnabledV1(customerExitsReq.getStoreId());

		String source = null != requestHeader ? requestHeader.get(Constants.HEADER_X_SOURCE) : null;

		if (Constants.SOURCE_MSITE.equals(source)) {
			customerExitsReq.setSource(Constants.SOURCE_MSITE);
		} else if (!Constants.SOURCE_MSITE.equals(source)) {
			customerExitsReq.setSource(Constants.SOURCE_MOBILE);
			String clientVersion = requestHeader.getOrDefault("x-client-version", null);
			if (clientVersion != null) {
				customerExitsReq.setClientVersion(clientVersion);
				LOGGER.info("auth/address : Client version set to: {}", clientVersion);
			} else {
				LOGGER.info("auth/address :  x-client-version header is missing, skipping client version setup.");
			}
		}

		if (customerExitsReq == null) {
			return null;
		}

		// ForPhone
		List<Integer> storeIds = ServiceConfigs.getStoreIdsForOtpFeature();
		LOGGER.info("validate : Store IDs enabled for OTP feature: " + storeIds);
		// For Email
		List<Integer> emailStoreIds = getStoreIdsForEmailOTPFeatureCheck();
		LOGGER.info("validate : Store IDs enabled for Email OTP feature: " + emailStoreIds);

		// Get store IDs for new feature flags

		Boolean isMagicLinkEnabledUpdateUser = ServiceConfigs
				.getMagicLinkEnableUpdateUser(customerExitsReq.getStoreId());
		Boolean isEmailOtpEnableUpdateUser = ServiceConfigs.getEmailOtpEnableUpdateUser(customerExitsReq.getStoreId());
		LOGGER.info("validate : Store IDs for Magic Link Update User feature: " + isMagicLinkEnabledUpdateUser);
		LOGGER.info("validate : Store IDs for Email OTP Update User feature: " + isEmailOtpEnableUpdateUser);

		Integer storeIdFromPayload = customerExitsReq.getStoreId();

		// For phone
		boolean isStoreValid = storeIds.contains(storeIdFromPayload);

		CustomerExistResponse response = customerV4Service.validateUser(customerExitsReq, requestHeader);
		LOGGER.info("validate : response " + response);

		response.setIsEmailVerificationEnabled(isEmailVerificationEnabled);
		response.setIsMagicLinkEnabled(isMagicLinkEnabled);
		response.setIsEmailOtpEnabledV1(isEmailOtpEnabledV1);

		// Set new feature flags in response as boolean strings
		response.setIsMagicLinkUpdateUserFeature(isMagicLinkEnabledUpdateUser);
		response.setIsEmailOtpUpdateUserFeature(isEmailOtpEnableUpdateUser);

		if (isValidResponse(response, customerExitsReq, isSignUpOtpEnabled, isStoreValid)) {
			LOGGER.info("Inside if after isValidResponse");
			processOtpRequest(customerExitsReq, requestHeader, response);
		}
		// For Email
		if (checkEmailStoreValid(emailStoreIds, storeIdFromPayload) && "true".equalsIgnoreCase(isSignUpOtpEnabled)) {
			response.setIsEmailOTPEnabled("true");
		}

		// When isOtpRequired=false but email OTP flags are on and email is not in DB (EMAIL_LOGIN), still send OTP and set isOtpSent=true (only when x-header-token is guest@stylishop.com)
		if (Boolean.FALSE.equals(customerExitsReq.getIsOtpRequired())
				&& "true".equalsIgnoreCase(response.getIsEmailOTPEnabled())
				&& Boolean.TRUE.equals(isEmailOtpEnabledV1)
				&& customerExitsReq.getLoginType() != null
				&& Constants.EMAIL.equalsIgnoreCase(customerExitsReq.getLoginType().value)
				&& USER_IDENTIFIER_EMAIL.equals(otpService.isValidUserIdentifier(customerExitsReq.getUseridentifier()))
				&& response.getResponse() != null
				&& MESSAGE_EMAIL_ID_DOESNT_EXIST.equals(response.getResponse().getMesage())
				&& "guest@stylishop.com".equals(requestHeader != null ? requestHeader.get("x-header-token") : null)) {
			SendOtpRegistrationRequest sendOtpRequest = createSendOtpRequest(customerExitsReq);
			try {
				OtpResponseBody<SendOtpResponse> otpResponse = sendRegistrationOtp(requestHeader, sendOtpRequest);
				if (otpResponse != null) {
					LOGGER.info("validate : Email OTP sent for non-existent user (isOtpRequired=false) to {}", customerExitsReq.getUseridentifier());
					response.setIsOtpSent(true);
				}
			} catch (Exception ex) {
				LOGGER.info("validate : Error while sending email OTP for non-existent user {}: {}", customerExitsReq.getUseridentifier(), ex);
			}
		}
		
		// Modify response based on loginType: only return email for EMAIL; for GOOGLE/APPLE return email + masked phone; only mobileNumber for MOBILE login
		// Only apply filtering if the flag is enabled (true)
		if (customerExitsReq.getLoginType() != null && ServiceConfigs.enableLoginResponseFiltering() 
				&& response.getResponse() != null) {
			String loginType = customerExitsReq.getLoginType().value;
			if (Constants.EMAIL.equalsIgnoreCase(loginType)) {
				// Email login: return only email, don't return mobileNumber in response
				response.getResponse().setMobileNumber(null);
			} else if (Constants.GOOGLELOGIN.equalsIgnoreCase(loginType)
					|| Constants.APPLELOGIN.equalsIgnoreCase(loginType)) {
				// Google/Apple login: return email + masked phone number
				String mobile = response.getResponse().getMobileNumber();
				if (StringUtils.isNotBlank(mobile)) {
					response.getResponse().setMobileNumber(maskPhoneNumber(mobile));
				} else {
					response.getResponse().setMobileNumber(null);
				}
			} else if (Constants.MOBILE.equalsIgnoreCase(loginType)) {
				// Mobile login: return only mobileNumber, don't return email in response
				response.getResponse().setEmail(null);
			}
			// For other login types (WHATSAPPLOGIN), return both (default behavior)
		}
		
		return response;
	}

	/**
	 * Masks a phone number for display (e.g. 1234567890 -> 123****90).
	 * Shows first 3 and last 2 digits; middle replaced with asterisks.
	 */
	private String maskPhoneNumber(String phone) {
		if (phone == null || phone.isEmpty()) {
			return phone;
		}
		String trimmed = phone.trim();
		int len = trimmed.length();
		if (len <= 5) {
			return len <= 2 ? "****" : trimmed.substring(0, 1) + "****" + trimmed.substring(len - 1);
		}
		int start = 3;
		int end = len - 2;
		return StringUtils.overlay(trimmed, "****", start, end);
	}

	public boolean isValidResponse(CustomerExistResponse response, CustomerQueryReq customerExitsReq,
			String signUpOtpEnabled, boolean isStoreValid) {
		String minAppVersion = ServiceConfigs.getMinAppVersionReqdForOtpFeature();

		try {
			LOGGER.info("validate : Inside isValidResponse ");

			if (customerExitsReq == null || signUpOtpEnabled == null) {
				LOGGER.info("validate : CustomerExitsReq or SignUpOtpEnabled is null.");
				response.setIsOtpSent(false);
				response.setIsSignUpOtpEnabled("false");
				return false;
			}

			if (!saveCustomer.isVersionGreaterOrEqual(customerExitsReq.getClientVersion(), minAppVersion)
					&& !customerExitsReq.getSource().equalsIgnoreCase(Constants.SOURCE_MSITE)) {
				LOGGER.info("validate : Client version or source validation failed.");
				response.setIsOtpSent(false);
				response.setIsSignUpOtpEnabled("false");
				return false;
			}

			Boolean isOtpRequired = customerExitsReq.getIsOtpRequired();
			if (isOtpRequired != null) {
				if (Boolean.TRUE.equals(isOtpRequired) && "true".equalsIgnoreCase(signUpOtpEnabled) && isStoreValid) {
					LOGGER.info("isValidResponse: isOtpRequired : in if " + isOtpRequired);
					response.setIsSignUpOtpEnabled("true");
					return true;
				} else if ((Boolean.FALSE.equals(isOtpRequired) && "true".equalsIgnoreCase(signUpOtpEnabled)
						&& isStoreValid)) {
					LOGGER.info("isValidResponse: isOtpRequired : in else if " + isOtpRequired);
					response.setIsSignUpOtpEnabled("true");
					return false;
				} else {
					LOGGER.info("isValidResponse: isOtpRequired : in else ");
					response.setIsSignUpOtpEnabled("false");
					return false;
				}
			}
			return false;

		} catch (Exception e) {
			LOGGER.info("validate : Error occurred in isValidResponse" + e);
			response.setIsSignUpOtpEnabled("false");
			return false;
		}
	}

	private void processOtpRequest(CustomerQueryReq customerExitsReq, Map<String, String> requestHeader,
			CustomerExistResponse response) {
		// Check if response and response body are not null before accessing
		if (response == null || response.getResponse() == null) {
			if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
				LOGGER.info("[enableCustomerServiceErrorHandling] In processOtpRequest: response or response body is null. Skipping OTP request processing.");
			}
			return;
		}
		
		String message = response.getResponse().getMesage();
		LOGGER.info("In processOtpRequest: message: " + message);
		if (isUserNotFound(message)) {
			SendOtpRegistrationRequest sendOtpRequest = createSendOtpRequest(customerExitsReq);
			LOGGER.info("In processOtpRequest: sendOtpRequest: " + sendOtpRequest);
			try {
				OtpResponseBody<SendOtpResponse> otpResponse = sendRegistrationOtp(requestHeader, sendOtpRequest);
				LOGGER.info("In processOtpRequest: otpResponse: " + otpResponse);
				if (otpResponse != null) {
					LOGGER.info("validate : Registration OTP sent to {}" + customerExitsReq.getUseridentifier());
					response.setIsOtpSent(true);
				} else {
					response.setIsOtpSent(false);
				}

			} catch (Exception ex) {
				LOGGER.info("validate : Error while sending registration OTP for Customer {}: {}" + ex);
			}
		}
	}

	private boolean isUserNotFound(String message) {
		return MESSAGE_MOBILE_NUMBER_DOESNT_EXIST.equals(message) || MESSAGE_EMAIL_ID_DOESNT_EXIST.equals(message);
	}

	private SendOtpRegistrationRequest createSendOtpRequest(CustomerQueryReq customerExitsReq) {
		LOGGER.info("validate : Inside SendOtpRegistrationRequest ");
		SendOtpRegistrationRequest sendOtpRequest = new SendOtpRegistrationRequest();
		sendOtpRequest.setUserIdentifier(customerExitsReq.getUseridentifier());
		sendOtpRequest.setStoreId(customerExitsReq.getStoreId());

		return sendOtpRequest;
	}

	@ApiOperation(value = "Customer V4 Login", response = CustomerLoginResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "LoggedIn Successfully", response = CustomerLoginResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error"),
			@ApiResponse(code = 404, message = "Product not found") })
	@ApiImplicitParams({ @ApiImplicitParam(name = "Authorization", value = "", paramType = "header"),
			@ApiImplicitParam(name = "Content-Type", value = "application/json", paramType = "header") })
	@PostMapping("v4/login")
	public CustomerLoginV4Response customerV31Login(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerLoginV4Request customerLogin,
			@RequestHeader(value = "deviceId", required = false) String deviceId) throws CustomerException {

		CustomerLoginV4Response customerRespone = null;
		Integer storeIdFromPayload = customerLogin.getStoreId();
		LOGGER.info("headers:" + requestHeader);

		customerRespone = customerV4Service.getCustomerLoginV4Details(customerLogin, requestHeader);
		if (("GOOGLELOGIN".equalsIgnoreCase(customerLogin.getLoginType().value)
				|| "APPLELOGIN".equalsIgnoreCase(customerLogin.getLoginType().value)
				|| "WHATSAPPLOGIN".equalsIgnoreCase(customerLogin.getLoginType().value)
				|| "MOBILE".equalsIgnoreCase(customerLogin.getLoginType().value)
				|| "EMAIL".equalsIgnoreCase(customerLogin.getLoginType().value))
				&& null != customerRespone.getResponse()
				&& customerRespone.getResponse().isRegistrationResponse()) {
			if ("WHATSAPPLOGIN".equalsIgnoreCase(customerLogin.getLoginType().value)) {
				customerRespone.getResponse().getCustomer().setWhatsApp(true);
			}
			asyncService.asyncSalesOrdersUpdateCustId(customerRespone.getResponse().getCustomer());
			kafkaAsyncService.publishCustomerEntityToKafka(customerRespone.getResponse().getCustomer());
		}

		if (ObjectUtils.isNotEmpty(customerRespone.getResponse())
				&& ObjectUtils.isNotEmpty(customerRespone.getResponse().getCustomer())) {
			String type = OtpValidationType.LOGIN.toString();
			if (customerRespone.getResponse().isRegistrationResponse())
				type = OtpValidationType.REGISTRATION.toString();
			saveCustomer.processGuestSessionData(requestHeader,
					customerRespone.getResponse().getCustomer(),
					guestSessionsRepository,
					customerLogsRepository,
					type);
			// asyncService.asynsaveCustomer(customerRespone.getResponse().getCustomer().getCustomerId());
		}

		// Free Shipping flag implementation
		try {
			if (null != customerRespone.getResponse()
					&& null != customerRespone.getResponse().getCustomer()) {

				String createdDate = customerRespone.getResponse().getCustomer().getCreatedAt();
				if (!(customerLogin.getLoginType().value.equals("EMAIL"))) {
					createdDate = customerRespone.getResponse().getCustomer().getOtpCreatedAt();
				}

				FirstFreeShipping firstFreeShipping = customerV4Service.setFreeShipping(
						createdDate,
						customerRespone.getResponse().getCustomer().getStoreId());

				customerRespone.getResponse().getCustomer().setFirstFreeShipping(firstFreeShipping);
			}
		} catch (Exception ex) {
			LOGGER.info("Error while logging Customer {} ", ex.getMessage());
		}
		if (customerRespone.getResponse() != null && customerRespone.getResponse().getCustomer() != null) {
			Customer customer = customerRespone.getResponse().getCustomer();
			if (customer.getEmail() != null && customer.getMobileNumber() != null && customer.getFirstName() != null
					&& rsaTokenService.isInfluencerPortalFeatureEnabled(storeIdFromPayload)) {
				EncryptedTokenResponse tokenResponse = rsaTokenService.attachEncryptedRsaToken(customer.getEmail(),
						customer.getMobileNumber(), customer.getFirstName(),
						customer.getLastName() != null ? customer.getLastName() : "", storeIdFromPayload);
				if (tokenResponse != null) {
					customerRespone.setEncryptedRsaToken(tokenResponse.getToken());
					customerRespone.setEncryptedRsaTokenExpiry(tokenResponse.getExpiry());
				}
			}
			
			// Modify response based on loginType: only return email for EMAIL/GOOGLELOGIN/APPLELOGIN, only mobileNumber for MOBILE login
			// Only apply filtering if the flag is enabled (true)
			if (customerLogin.getLoginType() != null && ServiceConfigs.enableLoginResponseFiltering()) {
				String loginType = customerLogin.getLoginType().value;
				if (Constants.EMAIL.equalsIgnoreCase(loginType) 
						|| Constants.GOOGLELOGIN.equalsIgnoreCase(loginType)
						|| Constants.APPLELOGIN.equalsIgnoreCase(loginType)) {
					// Email/Google/Apple login: return only email, don't return mobileNumber in response
					customerRespone.getResponse().getCustomer().setMobileNumber(null);
				} else if (Constants.MOBILE.equalsIgnoreCase(loginType)) {
					// Mobile login: return only mobileNumber, don't return email in response
					customerRespone.getResponse().getCustomer().setEmail(null);
				}
				// For other login types (WHATSAPPLOGIN), return both (default behavior)
			}
		}
		return customerRespone;
	}

	@PostMapping("auth/profile/update")
	public CustomerUpdateProfileResponse updateProfile(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerUpdateProfileRequest updateProfileReq) {
		// For Phone
		List<Integer> storeIds = ServiceConfigs.getStoreIdsForOtpFeature();
		LOGGER.info("profile/update : Store IDs enabled for OTP feature: " + storeIds);
		// For Email
		List<Integer> emailStoreIds = getStoreIdsForEmailOTPFeatureCheck();
		LOGGER.info("profile/update : Store IDs enabled for Email OTP feature: " + emailStoreIds);

		Integer storeIdFromPayload = updateProfileReq.getStoreId();
		// For Mobile
		boolean isStoreValid = storeIds.contains(storeIdFromPayload);

		LOGGER.info("profile/update : Starting profile update process for customer ID: {}",
				updateProfileReq.getCustomerId());
		CustomerUpdateProfileResponse response = new CustomerUpdateProfileResponse();
		try {
			String source = requestHeader != null ? requestHeader.get(Constants.HEADER_X_SOURCE) : null;
			LOGGER.info("profile/update : Request source: {}", source);

			String isSignUpOtpEnabled = ServiceConfigs.getSignUpOtpEnabled();
			updateProfileReq.setIsSignUpOtpEnabled(isSignUpOtpEnabled);
			LOGGER.info("profile/update : SignUp OTP Enabled flag set to: {}", isSignUpOtpEnabled);

			if (Constants.SOURCE_MSITE.equals(source)) {
				updateProfileReq.setSource(Constants.SOURCE_MSITE);
				LOGGER.info("profile/update : Source set to MSITE.");
			} else if (!Constants.SOURCE_MSITE.equals(source)) {
				updateProfileReq.setSource(Constants.SOURCE_MOBILE);
				String clientVersion = requestHeader.getOrDefault("x-client-version", null);

				if (clientVersion != null) {
					updateProfileReq.setClientVersion(clientVersion);
					LOGGER.info("profile/update : Client version set to: {}", clientVersion);
				} else {
					LOGGER.info("profile/update : x-client-version header is missing, skipping client version setup.");
				}

				updateProfileReq.setClientVersion(clientVersion);
				LOGGER.info("profile/update : Client version set to: {}", clientVersion);
			}

			if ("1".equals(jwtFlag)) {

				customerV4Service.authenticateCheck(requestHeader, updateProfileReq.getCustomerId());

			}

			if (null != updateProfileReq)

			{
				response = customerV4Service.updateCustomer(updateProfileReq, requestHeader);
				response.setIsSignUpOtpEnabled(
						("true".equalsIgnoreCase(isSignUpOtpEnabled) && isStoreValid) ? "true" : "false");
				response.setIsEmailOTPEnabled(("true".equalsIgnoreCase(isSignUpOtpEnabled)
						&& checkEmailStoreValid(emailStoreIds, storeIdFromPayload)) ? "true" : "false");
			}

			return response;
		} catch (Exception e) {
			LOGGER.error("profile/update : Error occurred while updating profile for customer ID: {}. Error: {}" + e);
			response.setStatus(false);
			response.setStatusMsg("profile/update : Profile update failed due to an error. Please try again later.");
			return response;
		}
	}

	@GetMapping("auth/profile/view/{customerId}")
	public CustomerUpdateProfileResponse getProfile(@RequestHeader Map<String, String> requestHeader,
			@PathVariable Integer customerId) {

		CustomerUpdateProfileResponse response = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, customerId);
		}
		if (null != customerId) {
			CustomerRequestBody customerRequestBody = new CustomerRequestBody();
			customerRequestBody.setCustomerId(customerId);
			response = customerV4Service.getCustomerDetails(customerRequestBody, requestHeader);
		}

		return response;
	}

	@PostMapping("auth/v2/profile")
	public CustomerUpdateProfileResponse getPOstProfile(@RequestHeader Map<String, String> requestHeader,
			@RequestBody CustomerRequestBody request) {

		CustomerUpdateProfileResponse response = new CustomerUpdateProfileResponse();
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, request.getCustomerId());
		}

		customerV4Service.validateDeletedUser(request.getCustomerId());

		if (null != request.getCustomerId())
			response = customerV4Service.getCustomerDetails(request, requestHeader);

		String isSignUpOtpEnabled = ServiceConfigs.getSignUpOtpEnabled();
		// For Email - use v2/profile-specific store list only for this endpoint
		List<Integer> emailStoreIdsProfileV2 = getStoreIdsForEmailOTPFeatureCheckProfileV2();
		LOGGER.info("Customer profile (auth/v2/profile) : Store IDs enabled for Email OTP feature: " + emailStoreIdsProfileV2);
		if (checkEmailStoreValid(emailStoreIdsProfileV2, request.getStoreId()) && "true".equalsIgnoreCase(isSignUpOtpEnabled)) {
			response.setIsEmailOTPEnabled("true");
		}
		// For Phone
		List<Integer> storeIds = ServiceConfigs.getStoreIdsForOtpFeature();
		LOGGER.info("Customer profile : Store IDs enabled for OTP feature: " + storeIds);
		if (storeIds.contains(request.getStoreId()) && "true".equalsIgnoreCase(isSignUpOtpEnabled)) {
			response.setIsSignUpOtpEnabled("true");
		}

		return response;
	}

	@ApiOperation(value = "Record Nudge Seen", response = GenericApiResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Nudge seen timestamp recorded successfully", response = GenericApiResponse.class),
			@ApiResponse(code = 400, message = "Bad Request"),
			@ApiResponse(code = 404, message = "Customer not found"),
			@ApiResponse(code = 500, message = "Internal Server Error") })
	@ApiImplicitParams({ @ApiImplicitParam(name = "Token", value = "KEY ", paramType = "header"),
			@ApiImplicitParam(name = "Content-Type", value = "application/json", paramType = "header") })
	@PostMapping("auth/cancelAddressComplianceNudge")
	public GenericApiResponse<String> recordNudgeSeen(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerRequestBody request) {

		GenericApiResponse<String> response = null;
		
		// Validate request body first to return proper 400 error instead of 500
		if (null == request || null == request.getCustomerId()) {
			response = new GenericApiResponse<>();
			response.setStatus(false);
			response.setStatusCode("400");
			response.setStatusMsg("Customer ID is required");
			return response;
		}

		// Perform authentication check only after request validation
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, request.getCustomerId());
		}

		response = customerV4Service.recordNudgeSeen(request, requestHeader);
		return response;
	}

	@PostMapping("auth/reset/password")
	public CustomerRestPassResponse changePassword(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerPasswordRequest passwordReset) {

		CustomerRestPassResponse response = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, passwordReset.getCustomerId());
		}

		if (null != passwordReset)

			response = customerV4Service.changePassword(passwordReset);

		return response;
	}

	@ApiOperation(value = "Customer Reset Password", response = CustomerRestPassResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "LoggedIn Successfully", response = CustomerRestPassResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error"),
			@ApiResponse(code = 404, message = "Email not found") })
	@ApiImplicitParams({ @ApiImplicitParam(name = "Content-Type", value = "application/json", paramType = "header") })
	@PostMapping("forget/password")
	public ResponseEntity<CustomerRestPassResponse> resetCustomerPassword(
			@RequestHeader Map<String, String> requestHeader, @Valid @RequestBody CustomerQueryReq PassResetReq) {
		CustomerRestPassResponse customerRestPasswordResponse = null;
		LOGGER.info("headers:" + requestHeader);
		try {
			if (null != PassResetReq)
				customerRestPasswordResponse = passwordV2Service.forgotPassword(requestHeader, PassResetReq);
		} catch (CustomerException ex) {
			customerRestPasswordResponse = new CustomerRestPassResponse();
			ErrorType error = new ErrorType();
			error.setErrorCode(ex.getErrorCode());
			error.setErrorMessage(ex.getErrorMessage());
			customerRestPasswordResponse.setStatus(false);
			customerRestPasswordResponse.setStatusCode("204");
			customerRestPasswordResponse.setStatusMsg(Constants.ERROR_MSG);
			customerRestPasswordResponse.setError(error);
		}
		return new ResponseEntity<CustomerRestPassResponse>(customerRestPasswordResponse, HttpStatus.OK);
	}

	@PostMapping("/auth/config/whatsaap/opt")
	public WhatsAppOptResponse getWhatsAppOtp(@RequestHeader Map<String, String> httpServletRequest,
			@RequestBody WhatsAppOtpRequest request) {
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(httpServletRequest, request.getCustomerId());
		}
		// asyncService.asynsaveCustomer(request.getCustomerId());
		return customerV4Service.getWhatsAppOtp(httpServletRequest, request);
	}

	@PutMapping("auth/v4.1/wishlist")
	public CustomerWishlistResponse updateV4OneWishList(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerWishListRequest customerWishList) {

		CustomerWishlistResponse response = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, customerWishList.getCustomerId());
		}

		if (null != customerWishList)

			response = customerV4Service.saveUpdateV4OneWishList(customerWishList, requestHeader, false);

		return response;
	}

	@PostMapping("auth/v4.1/wishlist")
	public CustomerWishlistResponse saveV4oneWishList(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerWishListRequest customerWishList) {

		CustomerWishlistResponse response = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, customerWishList.getCustomerId());
		}

		if (null != customerWishList)

			response = customerV4Service.saveUpdateV4OneWishList(customerWishList, requestHeader, true);

		return response;
	}

	@DeleteMapping("auth/v4.1/wishlist")
	public CustomerWishlistResponse removefourOneWishList(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerWishListRequest customerWishList) {

		CustomerWishlistResponse response = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, customerWishList.getCustomerId());
		}

		if (null != customerWishList)

			response = customerV4Service.removeWishList(customerWishList);

		return response;
	}

	@GetMapping("auth/v4.1/wishlist/ids/customerId/{customerId}/store/{storeId}")
	public CustomerWishlistResponse getWishForOneListIds(@RequestHeader Map<String, String> requestHeader,
			@PathVariable Integer customerId, @PathVariable Integer storeId) {

		CustomerWishlistResponse response = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, customerId);
		}

		if (null != customerId && null != storeId)

			response = customerV4Service.getWishList(customerId, storeId, false);

		return response;
	}

	@PostMapping("auth/v5/wishlist/view")
	public CustomerWishlistResponse getWishlistForCustomer(@RequestHeader Map<String, String> requestHeader,
			@RequestBody @Valid CustomerWishlistV5Request request) {
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, request.getCustomerId());
		}
		return customerV5Service.getWishList(request);

	}

	/**
	 * Return customer's cards
	 *
	 * @param customerId Integer Not Null
	 * @return CustomerCardsResponseDTO
	 */
	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	// @CrossOrigin(origins = "*")
	@GetMapping("auth/card/{customerId}")
	public CustomerCardsResponseDTO getCustomerCards(@RequestHeader Map<String, String> requestHeader,
			@PathVariable @NotNull Integer customerId) {

		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, customerId);
		}
		return salesOrderService.getCustomerCards(customerId);
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	// @CrossOrigin(origins = "*")
	@PostMapping("auth/v2/card")
	public CustomerCardsResponseDTO getCustomerPostCards(@RequestHeader Map<String, String> requestHeader,
			@RequestBody CustomerRequestBody requestBody) {

		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, requestBody.getCustomerId());
		}
		return salesOrderService.getCustomerCards(requestBody.getCustomerId());
	}

	/**
	 * Saves a customer's card
	 *
	 * @param request CreateCardRequest
	 * @return CustomerCardsResponseDTO
	 */
	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("auth/customer/card")
	// @CrossOrigin(origins = "*")
	public CustomerCardsResponseDTO createCustomerCard(@RequestHeader Map<String, String> requestHeader,
			@RequestBody @NotNull @Valid CreateCardRequest request) {

		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, request.getCustomerId());
		}

		return salesOrderService.createCard(request);
	}

	/**
	 * Deletes a customer's card
	 *
	 * @param request DeleteCardRequest
	 * @return CustomerCardsResponseDTO
	 */
	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@DeleteMapping("auth/customer/card")
	// @CrossOrigin(origins = "*")
	public CustomerCardsResponseDTO deleteCustomerCard(@RequestHeader Map<String, String> requestHeader,
			@RequestBody @NotNull @Valid DeleteCardRequest request) {

		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, request.getCustomerId());
		}

		return salesOrderService.deleteCard(request);
	}

	/**
	 * @param request
	 * @return
	 */
	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("log/print")
	// @CrossOrigin(origins = "*")
	public void printLogInfos(@RequestBody @NotNull @Valid PrintLogInfoRequest request) {
		salesOrderService.printLogInfos(request);
	}

	@ApiOperation(value = "Save Address", response = CustomerAddreesResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Saved Successfully", response = CustomerAddreesResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error"),
			@ApiResponse(code = 404, message = "Address not found") })
	@ApiImplicitParams({ @ApiImplicitParam(name = "Token", value = "KEY ", paramType = "header"),
			@ApiImplicitParam(name = "Content-Type", value = "application/json", paramType = "header") })
	// @CrossOrigin(origins = "*")
	@PostMapping("auth/address")
	public CustomerAddreesResponse saveAddress(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerAddrees customerAddRequest) {

		List<Integer> storeIds = ServiceConfigs.getStoreIdsForOtpFeature();
		System.out.println("validate : Store IDs enabled for OTP feature: " + storeIds);

		Integer storeIdFromPayload = customerAddRequest.getStoreId();

		boolean isStoreValid = storeIds.contains(storeIdFromPayload);

		String source = null != requestHeader ? requestHeader.get(Constants.HEADER_X_SOURCE) : null;

		if (Constants.SOURCE_MSITE.equals(source)) {

			customerAddRequest.setSource(Constants.SOURCE_MSITE);
		} else if (!Constants.SOURCE_MSITE.equals(source)) {
			customerAddRequest.setSource(Constants.SOURCE_MOBILE);
			String clientVersion = requestHeader.getOrDefault("x-client-version", null);
			if (clientVersion != null) {
				customerAddRequest.setClientVersion(clientVersion);
				LOGGER.info("auth/address : Client version set to: {}", clientVersion);
			} else {
				LOGGER.info("auth/address :  x-client-version header is missing, skipping client version setup.");
			}
		}

		String isSignUpOtpEnabled = ServiceConfigs.getSignUpOtpEnabled();

		customerAddRequest.setIsSignUpOtpEnabled(isSignUpOtpEnabled);

		CustomerAddreesResponse response = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, customerAddRequest.getCustomerId());
		}

		if (null != customerAddRequest) {

			response = customerV4Service.saveAddress(customerAddRequest, true, requestHeader);
			response.setIsSignUpOtpEnabled(
					("true".equalsIgnoreCase(isSignUpOtpEnabled) && isStoreValid) ? "true" : "false");

		}

		return response;
	}

	@ApiOperation(value = "Update Address", response = CustomerAddreesResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Updated Successfully", response = CustomerAddreesResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error"),
			@ApiResponse(code = 404, message = "Address not found") })
	@ApiImplicitParams({ @ApiImplicitParam(name = "Token", value = "KEY ", paramType = "header"),
			@ApiImplicitParam(name = "Content-Type", value = "application/json", paramType = "header") })
	// @CrossOrigin(origins = "*")
	@PutMapping("auth/address")
	public CustomerAddreesResponse updateAddress(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerAddrees customerAddRequest) {
		LOGGER.info("Customer address update request json :: {}", customerAddRequest);
		List<Integer> storeIds = ServiceConfigs.getStoreIdsForOtpFeature();
		System.out.println("auth/address : Store IDs enabled for OTP feature: " + storeIds);

		Integer storeIdFromPayload = customerAddRequest.getStoreId();

		boolean isStoreValid = storeIds.contains(storeIdFromPayload);

		String isSignUpOtpEnabled = ServiceConfigs.getSignUpOtpEnabled();

		String source = null != requestHeader ? requestHeader.get(Constants.HEADER_X_SOURCE) : null;

		if (Constants.SOURCE_MSITE.equals(source)) {

			customerAddRequest.setSource(Constants.SOURCE_MSITE);
		} else if (!Constants.SOURCE_MSITE.equals(source)) {
			customerAddRequest.setSource(Constants.SOURCE_MOBILE);
			String clientVersion = requestHeader.getOrDefault("x-client-version", null);
			if (clientVersion != null) {
				customerAddRequest.setClientVersion(clientVersion);
				LOGGER.info("auth/address : Client version set to: {}", clientVersion);
			} else {
				LOGGER.info("auth/address :  x-client-version header is missing, skipping client version setup.");
			}
		}

		customerAddRequest.setIsSignUpOtpEnabled(isSignUpOtpEnabled);

		CustomerAddreesResponse response = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, customerAddRequest.getCustomerId());
		}

		if (null != customerAddRequest) {

			response = customerV4Service.saveAddress(customerAddRequest, false, requestHeader);
			response.setIsSignUpOtpEnabled(
					("true".equalsIgnoreCase(isSignUpOtpEnabled) && isStoreValid) ? "true" : "false");

		}
		LOGGER.info("Customer address update response json :: {}", response);
		return response;
	}

	@ApiOperation(value = "Delete Address", response = CustomerAddreesResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Deleted Successfully", response = CustomerAddreesResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error"),
			@ApiResponse(code = 404, message = "Address not found") })
	@ApiImplicitParams({ @ApiImplicitParam(name = "Token", value = "KEY ", paramType = "header"),
			@ApiImplicitParam(name = "Content-Type", value = "application/json", paramType = "header") })
	// @CrossOrigin(origins = "*")
	@DeleteMapping("auth/address")
	public CustomerAddreesResponse deleteAddress(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerAddrees customerAddRequest) {

		CustomerAddreesResponse response = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, customerAddRequest.getCustomerId());
		}

		if (null != customerAddRequest && null != customerAddRequest.getAddressId()
				&& null != customerAddRequest.getCustomerId())

			response = customerV4Service.deleteAddress(customerAddRequest);

		return response;
	}

	@ApiOperation(value = "Fetch Address", response = CustomerAddreesResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Fetched Successfully", response = CustomerAddreesResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error"),
			@ApiResponse(code = 404, message = "Address not found") })
	@ApiImplicitParams({ @ApiImplicitParam(name = "Token", value = "KEY ", paramType = "header"),
			@ApiImplicitParam(name = "Content-Type", value = "application/json", paramType = "header") })

	@GetMapping("auth/address/view/{customerId}")
	public CustomerAddreesResponse getAddress(@RequestHeader Map<String, String> requestHeader,
			@Valid @PathVariable Integer customerId) {
		CustomerAddreesResponse response = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, customerId);
		}

		if (null != customerId)

			response = customerV4Service.getAddress(customerId, requestHeader);

		return response;
	}

	@ApiOperation(value = "Fetch Address", response = CustomerAddreesResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Fetched Successfully", response = CustomerAddreesResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error"),
			@ApiResponse(code = 404, message = "Address not found") })
	@ApiImplicitParams({ @ApiImplicitParam(name = "Token", value = "KEY ", paramType = "header"),
			@ApiImplicitParam(name = "Content-Type", value = "application/json", paramType = "header") })

	@PostMapping("auth/address/v2/view")
	public CustomerAddreesResponse getPostAddress(@RequestHeader Map<String, String> requestHeader,
			@RequestBody CustomerRequestBody request) {
		List<Integer> storeIds = ServiceConfigs.getStoreIdsForOtpFeature();
		System.out.println("auth/address/v2/view : Store IDs enabled for OTP feature: " + storeIds);

		Integer storeIdFromPayload = request.getStoreId();

		boolean isStoreValid = storeIds.contains(storeIdFromPayload);

		String isSignUpOtpEnabled = ServiceConfigs.getSignUpOtpEnabled();

		CustomerAddreesResponse response = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, request.getCustomerId());
		}

		if (null != request.getCustomerId())

		{
			response = customerV4Service.getAddress(request.getCustomerId(), requestHeader);
		}

		response.setIsSignUpOtpEnabled(
				("true".equalsIgnoreCase(isSignUpOtpEnabled) && isStoreValid) ? "true" : "false");
		return response;
	}

	/**
	 *
	 * @param addressId
	 * @param customerId
	 * @return CustomerAddreesResponse
	 */
	@GetMapping("auth/addressid/{addressId}/customerid/{customerId}")
	// @CrossOrigin(origins = "*")
	public CustomerAddreesResponse getAddressById(@RequestHeader Map<String, String> requestHeader,
			@PathVariable Integer addressId, @PathVariable Integer customerId) {
		CustomerAddreesResponse resp = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, customerId);
		}

		resp = customerV4Service.getAddressById(addressId, customerId);

		return resp;
	}

	@PostMapping("validity/check")
	public CustomerCheckvalidityResponse GetCustomer(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerValidityCheckRequest request) {

		return customerV4Service.customerValidityCheck(request, requestHeader);

	}

	@PostMapping("auth/send/otp")
	public OtpResponseBody<SendOtpResponse> sendOtp(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody SendOtpRequest request) {

		// If loginRationalisation is explicitly true, redirect to new handler
		if (Boolean.TRUE.equals(request.getLoginRationalisation())) {
			LOGGER.info("auth/send/otp : Handling login rationalisation .");
			return otpService.sendOtpRationalisation(requestHeader, request, null, false);
		}

		String source = null != requestHeader ? requestHeader.get(Constants.HEADER_X_SOURCE) : null;
		String isSignUpOtpEnabled = ServiceConfigs.getSignUpOtpEnabled();
		// For Phone
		List<Integer> storeIds = ServiceConfigs.getStoreIdsForOtpFeature();
		LOGGER.info("auth/send/otp : Store IDs enabled for OTP feature: " + storeIds);
		// For Email
		List<Integer> emailStoreIds = getStoreIdsForEmailOTPFeatureCheck();
		LOGGER.info("auth/send/otp : Store IDs enabled for Email OTP feature: " + emailStoreIds);

		boolean emailOtpEnabledV1 = ServiceConfigs.getIsEmailOtpEnabledV1(request.getStoreId());

		Integer storeIdFromPayload = request.getStoreId();
		// For phone
		boolean isStoreValid = storeIds.contains(storeIdFromPayload);

		if (storeIdFromPayload != null) {
			Object userIdentifierType = otpService.isValidUserIdentifier(request.getUserIdentifier());
			// For mobile
			if (USER_IDENTIFIER_MOBILE.equals(userIdentifierType) && isStoreValid
					&& "true".equalsIgnoreCase(isSignUpOtpEnabled)) {
				LOGGER.info("auth/send/otp : Triggering email otp for " + request.getUserIdentifier());
				return otpService.sendOtpMSiteMobile(requestHeader, request);
			}
			// For Email
			if (USER_IDENTIFIER_EMAIL.equals(userIdentifierType)
					&& (checkEmailStoreValid(emailStoreIds, storeIdFromPayload) || emailOtpEnabledV1)
					&& "true".equalsIgnoreCase(isSignUpOtpEnabled)) {
				LOGGER.info("auth/send/otp : Triggering email otp for " + request.getUserIdentifier());
				return otpService.sendOtpMSiteMobile(requestHeader, request);
			}
		}

		if (null != source
				&& ((Constants.SOURCE_MSITE.equals(source) && "true".equals(ServiceConfigs.getOtpMaskEnabledForMsite())
						&& StringUtils.isNotEmpty(request.getEmail()))
						|| ((Constants.SOURCE_MOBILE_ANDROID.equals(source)
								|| Constants.SOURCE_MOBILE_IOS.equals(source))
								&& "true".equals(ServiceConfigs.getOtpMaskEnabledForMobile())
								&& StringUtils.isNotEmpty(request.getEmail())))) {
			return otpService.sendOtpMSiteMobile(requestHeader, request);
		} else if (StringUtils.isEmpty(source) && StringUtils.isNotEmpty(request.getEmail())) {
			return otpService.sendOtpMSiteMobile(requestHeader, request);
		} else {
			return otpService.sendOtp(requestHeader, request, null);
		}
	}

	@PostMapping("validate/otp")
	public OtpResponseBody<ValidateOtpResponse> validateOtp(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody ValidateOtpRequest request) {
		if (request.getType() == null)
			request.setType(OtpValidationType.NORMAL);
		Integer storeIdFromPayload = request.getStoreId();
		String source = null != requestHeader ? requestHeader.get(Constants.HEADER_X_SOURCE) : null;

		// If loginRationalisation is explicitly true, redirect to new handler
		if (Boolean.TRUE.equals(request.getLoginRationalisation())) {
			LOGGER.info("validate/otp : Handling login rationalisation for " + request.getMobileNo());
			return otpService.validateOtpRationalisation(requestHeader, request);
		}

		if (StringUtils.isNotEmpty(request.getUserIdentifier())) {
			LOGGER.info("validate/otp : Validating email otp for " + request.getUserIdentifier());
			return otpService.validateOtpMSiteMobile(requestHeader, request);
		}

		OtpResponseBody<ValidateOtpResponse> resp = new OtpResponseBody<>();

		if (null != source
				&& ((Constants.SOURCE_MSITE.equals(source) && "true".equals(ServiceConfigs.getOtpMaskEnabledForMsite())
						&& StringUtils.isNotEmpty(request.getEmail()))
						|| ((Constants.SOURCE_MOBILE_ANDROID.equals(source)
								|| Constants.SOURCE_MOBILE_IOS.equals(source))
								&& "true".equals(ServiceConfigs.getOtpMaskEnabledForMobile())
								&& StringUtils.isNotEmpty(request.getEmail())))) {
			resp = otpService.validateOtpMSiteMobile(requestHeader, request);
		} else if (StringUtils.isEmpty(source) && StringUtils.isNotEmpty(request.getEmail())) {
			resp = otpService.validateOtpMSiteMobile(requestHeader, request);
		} else {
			resp = otpService.validateOtp(requestHeader, request);
		}

		/**
		 * save guest data to customer log
		 * only when it is login or registration type
		 *
		 */
		if (resp.getStatus() && (request.getType().equals(OtpValidationType.LOGIN)
				|| request.getType().equals(OtpValidationType.REGISTRATION))) {
			saveCustomer.processGuestSessionData(requestHeader,
					resp.getResponse().getCustomer(),
					guestSessionsRepository,
					customerLogsRepository,
					request.getType().toString());
			// asyncService.asynsaveCustomer(resp.getResponse().getCustomer().getCustomerId());
		}

		// Free Shipping flag implementation
		try {
			if (null != resp.getResponse()
					&& null != resp.getResponse().getCustomer()
					&& null != resp.getResponse().getCustomer().getCreatedAt()) {

				FirstFreeShipping firstFreeShipping = customerV4Service.setFreeShipping(
						resp.getResponse().getCustomer().getOtpCreatedAt(),
						resp.getResponse().getCustomer().getStoreId());

				resp.getResponse().getCustomer().setFirstFreeShipping(firstFreeShipping);
			}
		} catch (Exception ex) {
			LOGGER.info("Error while validating OTP {}", ex.getMessage());
		}
		// mapping of orders on basis of emailId
		try {
			if (null != resp.getResponse() && null != resp.getResponse().getCustomer()) {
				asyncService.asyncSalesOrdersUpdateCustId(resp.getResponse().getCustomer());
				kafkaAsyncService.publishCustomerEntityToKafka(resp.getResponse().getCustomer());
				LOGGER.info("Mapping done and synced to kafka");
			}
		} catch (Exception e) {
			LOGGER.info("Error while validating OTP {}", e.getMessage());
		}
		if (resp.getResponse() != null && resp.getResponse().getCustomer() != null) {
			Customer customer = resp.getResponse().getCustomer();
			if (customer.getEmail() != null && customer.getMobileNumber() != null && customer.getFirstName() != null
					&& rsaTokenService.isInfluencerPortalFeatureEnabled(storeIdFromPayload)) {
				EncryptedTokenResponse tokenResponse = rsaTokenService.attachEncryptedRsaToken(customer.getEmail(),
						customer.getMobileNumber(), customer.getFirstName(),
						customer.getLastName() != null ? customer.getLastName() : "", storeIdFromPayload);
				if (tokenResponse != null) {
					resp.setEncryptedRsaToken(tokenResponse.getToken());
					resp.setEncryptedRsaTokenExpiry(tokenResponse.getExpiry());
				}
			}
		}
		return resp;
	}

	@PostMapping("reset/token/password")
	public CustomerRestPassResponse resetTokenPassword(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody TokenPasswordRequest request) {
		return passwordV2Service.resetTokenPassword(requestHeader, request);
	}

	@PostMapping("recaptcha/verify")
	public RecaptchaVerifyResponse verifyRecaptcha(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody RecaptchaVerifyRequest request,
			@RequestHeader(value = "x-original-forwarded-for", required = false) String customerIp) {
		return customerV4Service.verifyRecaptcha(requestHeader, request, customerIp);
	}

	@PostMapping("reset/auth/refresh/token")
	public CustomerLoginV4Response loginregreshToken(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerLoginV4Request request) {
		return passwordV2Service.refreshToken(requestHeader, request);
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("auth/delete/send/otp")
	public AccountDeleteResponse sendOTPForAccountDeletion(
			@RequestBody @Valid AccountDeletionOTPRequest request,
			@RequestHeader("Token") String tokenHeader,
			@RequestHeader("x-header-token") String xHeaderToken,
			@RequestHeader Map<String, String> requestHeader) {

		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, request.getCustomerId());
		}
		return accountDeleteService.sendOTP(request, tokenHeader, xHeaderToken);

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("auth/delete/request")
	public AccountDeleteResponse deleteOrWithdrawCustomerAccount(@RequestBody @Valid AccountDeletionRequest request,
			@RequestHeader Map<String, String> requestHeader) {

		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, request.getCustomerId());
		}
		return accountDeleteService.deleteOrWithdrawCustomerAccount(request);
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("auth/delete/eligible")
	public AccountDeletionEligibleResponse checkAccountDeletionEligiblity(
			@RequestBody @Valid AccountDeletionEligibleRequest request, @RequestHeader("Token") String tokenHeader,
			@RequestHeader("x-header-token") String xHeaderToken,
			@RequestHeader Map<String, String> requestHeader) throws JsonProcessingException {

		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, request.getCustomerId());
		}
		return accountDeleteService.checkAccountDeletionEligiblity(request, tokenHeader, xHeaderToken);

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@GetMapping("delete/scheduler")
	public AccountDeleteResponse processDeleteRequests(
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (configService.checkAuthorizationInternal(authorizationToken)) {
			return accountDeleteService.processDeleteRequests();
		} else {
			AccountDeleteResponse response = new AccountDeleteResponse();
			response.setStatus(false);
			response.setStatusCode("401");
			response.setStatusMsg(Constants.MISSING_TOKEN_ERR_MSG);
			return response;
		}

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@GetMapping("delete/cleanup")
	public AccountDeleteResponse processDeleteRequestsCleanup(
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (configService.checkAuthorizationInternal(authorizationToken)) {
			return accountDeleteService.processDeleteRequestsCleanup();
		} else {
			AccountDeleteResponse response = new AccountDeleteResponse();
			response.setStatus(false);
			response.setStatusCode("401");
			response.setStatusMsg(Constants.MISSING_TOKEN_ERR_MSG);
			return response;
		}

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("delete/status/update")
	public AccountDeleteResponse processStatusUpdate(
			@RequestBody @Valid AccountDeleteTaskUpdateRequest request,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (configService.checkAuthorizationInternal(authorizationToken)
				|| configService.checkAuthorizationExternal(authorizationToken)) {
			return accountDeleteService.processStatusUpdates(request);
		} else {
			AccountDeleteResponse response = new AccountDeleteResponse();
			response.setStatus(false);
			response.setStatusCode("401");
			response.setStatusMsg(Constants.MISSING_TOKEN_ERR_MSG);
			return response;
		}

	}

	@PostMapping("auth/inventory/status")
	public ProductStatusResponse getProductQty(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody ProductStatusRequest productStatusReq) {
		return customerV5Service.getProductQty(requestHeader, productStatusReq);
	}

	@PostMapping("product/info")
	public ProductDetailsResponseV4 getProductDesInfo(@RequestBody @Valid GetProductV4Request request,
			@RequestHeader(value = "x-header-token", required = false) String xHeaderToken) {
		return customerV5Service.getProductInfo(request, xHeaderToken);
	}

	@PostMapping("whatsapp/signup")
	public GenericApiResponse<WhatsappSignupResponse> whatsappSignupLink(
			@RequestBody @Valid WhatsappSignupRequest requestBody, @RequestHeader Map<String, Object> requestHeader,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {
		if (configService.checkAuthorizationExternal(authorizationToken)) {
			return whatsappService.createWhatsappSignupLink(requestBody, requestHeader);
		}
		GenericApiResponse<WhatsappSignupResponse> response = new GenericApiResponse<>();
		response.setStatus(false);
		response.setStatusCode("401");
		response.setStatusMsg(Constants.MISSING_TOKEN_ERR_MSG);
		return response;
	}

	@PostMapping("auth/address/non-serviceable")
	public GenericApiResponse<String> addressNonServiceable(@RequestBody @Valid NonServiceableAddressDTO body,
			@RequestHeader Map<String, String> requestHeader) {
		if (Objects.nonNull(body.getCustomerId())) {
			Integer customerId = Integer.valueOf(body.getCustomerId());
			customerV4Service.authenticateCheck(requestHeader, customerId);
			return addressService.saveNonServiceableAddress(body);
		} else {
			return addressService.saveNonServiceableAddress(body);
		}
	}

	@PostMapping("auth/location/map")
	public GetLocationGoogleMapsResponse getLocationGoogleMaps(
			@RequestBody @Valid GetLocationGoogleMapsRequest request) {
		return customerV5Service.getLocationGoogleMaps(request);
	}

	@GetMapping("auth/places/autocomplete/{placeText}/store/{storeId}")
	public PlacesAutocompleteGoogleMapsResponse getGooglePlacesForAutocompleteText(
			@PathVariable String placeText,
			@PathVariable Integer storeId) {
		return customerV5Service.getGooglePlacesForAutocompleteText(placeText, storeId);
	}

	@PostMapping("registration/otp/send")
	public OtpResponseBody<SendOtpResponse> sendRegistrationOtp(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody SendOtpRegistrationRequest request) {
		LOGGER.info("Registration OTP : Inside Registration OTP function.");

		// If loginRationalisation is explicitly true and it's a resend call
		if (Boolean.TRUE.equals(request.getLoginRationalisation())
				&& Boolean.TRUE.equals(request.getResendCall())
				&& !USER_IDENTIFIER_EMAIL.equals(otpService.isValidUserIdentifier(request.getUserIdentifier()))) {

			LOGGER.info("Registration OTP :Handling login rationalisation.");
			SendOtpRequest sendOtpRequest = otpService.buildSendOtpRequestFromRegistration(request);
			// Skip email OTP for registration flow
			return otpService.sendOtpRationalisation(requestHeader, sendOtpRequest, null, true);
		}

		// For Phone
		List<Integer> storeIds = ServiceConfigs.getStoreIdsForOtpFeature();
		LOGGER.info("validate : Store IDs enabled for OTP feature: " + storeIds);
		// For Email
		List<Integer> emailStoreIds = getStoreIdsForEmailOTPFeatureCheck();
		LOGGER.info("validate : Store IDs enabled for Email OTP feature: " + emailStoreIds);

		boolean emailOtpEnabledV1 = ServiceConfigs.getIsEmailOtpEnabledV1(request.getStoreId());

		// Get the new update user feature flag
		Boolean isEmailOtpEnableUpdateUser = ServiceConfigs.getEmailOtpEnableUpdateUser(request.getStoreId());
		LOGGER.info("registration/otp/send : Email OTP Enable Update User feature: " + isEmailOtpEnableUpdateUser);

		Integer storeIdFromPayload = request.getStoreId();
		// For Phone
		boolean isStoreValid = storeIds.contains(storeIdFromPayload);
		OtpResponseBody<SendOtpResponse> responseBody = new OtpResponseBody<>();

		if (storeIdFromPayload != null) {

			Object userIdentifierType = otpService.isValidUserIdentifier(request.getUserIdentifier());
			LOGGER.info(
					"Registration OTP : Printing userIdentifierType received in input ." + request.getUserIdentifier());
			LOGGER.info(
					"Registration OTP : Printing type of userIdentifierType received in input ." + userIdentifierType);

			if (userIdentifierType.equals(false)) {
				LOGGER.info("Registration OTP : Invalid user identifier format.");
				responseBody.setStatus(false);
				responseBody.setStatusCode("400");
				responseBody.setStatusMsg("Invalid user identifier format.");
				return responseBody;
			}

			// For mobile
			if (USER_IDENTIFIER_MOBILE.equals(userIdentifierType) && isStoreValid) {
				LOGGER.info("Registration OTP : User identifier is a mobile number.");
				SendOtpRequest sendOtpRequest = new SendOtpRequest();
				sendOtpRequest.setStoreId(request.getStoreId());
				sendOtpRequest.setMobileNo(request.getUserIdentifier());
				sendOtpRequest.setDebugMode(request.getDebugMode());
				sendOtpRequest.setScreen(request.getScreen());
				sendOtpRequest.setCustomerId(request.getCustomerId());

				if (request.getCustomerId() != null) {
					return otpService.sendOtp(requestHeader, sendOtpRequest, "null");
				} else {
					return otpService.sendOtp(requestHeader, sendOtpRequest, "registration");
				}
			}

			// For Email - Enhanced logic to include isEmailOtpEnableUpdateUser
			boolean existingEmailConditions = checkEmailStoreValid(emailStoreIds, storeIdFromPayload)
					|| emailOtpEnabledV1;
			boolean shouldSendEmailOtp = existingEmailConditions || Boolean.TRUE.equals(isEmailOtpEnableUpdateUser);

			if (USER_IDENTIFIER_EMAIL.equals(userIdentifierType) && shouldSendEmailOtp) {
				LOGGER.info("Registration OTP : User identifier is an email address. Existing conditions: "
						+ existingEmailConditions + ", Update User flag: " + isEmailOtpEnableUpdateUser);
				if (request.getCustomerId() != null) {
					return otpService.sendOtpViaEmail(requestHeader, request, "null");
				} else {
					return otpService.sendOtpViaEmail(requestHeader, request, "registration");
				}

			}
		}
		responseBody.setStatus(false);
		responseBody.setStatusMsg("Invalid request type.");
		return responseBody;
	}

	@PostMapping("registration/otp/validate")
	public OtpResponseBody<ValidateOtpResponse> validateRegistrationOtp(
			@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody ValidateOtpRegistrationRequest request) {

		LOGGER.info("Validate Registration OTP: Inside Validate Registration OTP function.");

		Object userIdentifierType = otpService.isValidUserIdentifier(request.getUserIdentifier());
		OtpResponseBody<ValidateOtpResponse> responseBody = new OtpResponseBody<>();

		if (Boolean.FALSE.equals(userIdentifierType)) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("400");
			responseBody.setStatusMsg("Invalid user identifier format.");
			return responseBody;
		}
		// ForPhone
		List<Integer> storeIds = ServiceConfigs.getStoreIdsForOtpFeature();
		LOGGER.info("registration/otp/validate : Store IDs enabled for OTP feature: " + storeIds);
		// For Email
		List<Integer> emailStoreIds = getStoreIdsForEmailOTPFeatureCheck();
		LOGGER.info("registration/otp/validate : Store IDs enabled for Email OTP feature: " + emailStoreIds);

		boolean emailOtpEnabledV1 = ServiceConfigs.getIsEmailOtpEnabledV1(request.getStoreId());

		// Get the new update user feature flag
		Boolean isEmailOtpEnableUpdateUser = ServiceConfigs.getEmailOtpEnableUpdateUser(request.getStoreId());
		LOGGER.info("registration/otp/validate : Email OTP Enable Update User feature: " + isEmailOtpEnableUpdateUser);

		// For phone
		boolean isStoreValid = storeIds.contains(request.getStoreId());

		if (USER_IDENTIFIER_MOBILE.equals(userIdentifierType) && isStoreValid) {
			LOGGER.info("registration/otp/validate  OTP: User identifier is a {}", userIdentifierType);
			return otpService.validateRegistrationOtp(requestHeader, request, userIdentifierType);
		}

		// For Email - Enhanced logic to include isEmailOtpEnableUpdateUser
		boolean existingEmailConditions = checkEmailStoreValid(emailStoreIds, request.getStoreId())
				|| emailOtpEnabledV1;
		boolean shouldValidateEmailOtp = existingEmailConditions || Boolean.TRUE.equals(isEmailOtpEnableUpdateUser);

		if (USER_IDENTIFIER_EMAIL.equals(userIdentifierType) && shouldValidateEmailOtp) {
			LOGGER.info(
					"registration/otp/validate OTP: User identifier is a {}. Existing conditions: "
							+ existingEmailConditions + ", Update User flag: " + isEmailOtpEnableUpdateUser,
					userIdentifierType);
			return otpService.validateRegistrationOtp(requestHeader, request, userIdentifierType);
		}

		LOGGER.info("Validate Registration OTP: Invalid request type.");
		responseBody.setStatus(false);
		responseBody.setStatusCode("400");
		responseBody.setStatusMsg("Invalid request type.");
		return responseBody;
	}

	@GetMapping("/verification-status")
	public ResponseEntity<CustomerVerificationStatusResponse> getCustomerVerificationStatus(
			@RequestParam String customerId) {

		LOGGER.info("Customer Verification Status: Fetching verification status for customerId: {}", customerId);

		CustomerVerificationStatusResponse response = customerV4Service.getCustomerById(customerId);

		if (response != null) {
			LOGGER.info("Customer Verification Status: retrieved successfully for customerId: {}", customerId);
			return ResponseEntity.ok(response);
		} else {
			LOGGER.error("Customer Verification Status: Customer not found for customerId: {}", customerId);
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
		}
	}

	@PostMapping("auth/shukran/enroll")
	public EnrollmentResponse enrollShukranAccount(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody ShukranEnrollmentRequest enrollmentRequest) {
		EnrollmentResponse response = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, enrollmentRequest.getCustomerId());
		}
		if (null != enrollmentRequest)
			response = customerV4Service.enrollShukranAccount(enrollmentRequest, requestHeader);

		return response;
	}

	@PostMapping("auth/shukran/link")
	public EnrollmentResponse linkShukranAccount(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody LinkShukranRequest linkShukranRequest) {
		EnrollmentResponse response = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, linkShukranRequest.getCustomerId());
		}
		if (null != linkShukranRequest)
			response = customerV4Service.linkShukranAccount(linkShukranRequest, requestHeader);

		return response;
	}

	@PostMapping("auth/shukran/tier-upgrade")
	public BuildUpgradeShukranTierActivityResponse shukranUpgradeTierActivity(
			@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody UpgradeShukranTierActivityRequest upgradeshukrantieractivityrequest) {

		BuildUpgradeShukranTierActivityResponse responseActivity = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, upgradeshukrantieractivityrequest.getCustomerId());
		}
		if (null != upgradeshukrantieractivityrequest)
			responseActivity = customerV4Service.shukranUpgradeTierActivity(requestHeader,
					upgradeshukrantieractivityrequest);

		return responseActivity;
	}

    @PostMapping("webhook/shukran/phone-unlink")
    public ShukranWebhookResponse handleShukranPhoneUnlinkWebhook(
            @Valid @RequestBody ShukranWebhookRequest webhookRequest,
			@RequestHeader(value = "authorization-token") String authorizationToken) {

        LOGGER.info("Received Shukran phone unlink webhook for mobile number: " +webhookRequest.getMobileNumber() + " and action: " +webhookRequest.getAction());
        return shukranWebhookService.handleShukranWebhook(webhookRequest, authorizationToken, "shukran/phone-unlink",
            customerV4Service::handleShukranPhoneUnlinkWebhook);
    }

    @PostMapping("webhook/shukran/phone-update")
    public ShukranWebhookResponse handleShukranPhoneUpdateWebhook(
            @Valid @RequestBody ShukranWebhookRequest webhookRequest,
			@RequestHeader(value = "authorization-token") String authorizationToken) {

		LOGGER.info("Received Shukran phone update webhook for mobile number: " +webhookRequest.getMobileNumber() + " and card: " +webhookRequest.getLoyaltyCardNumber() + " and action: " +webhookRequest.getAction());
        return shukranWebhookService.handleShukranWebhook(webhookRequest, authorizationToken, "shukran/phone-update",
            customerV4Service::handleShukranPhoneUpdateWebhook);
    }


	@PostMapping("refresh/access/token")
	public ResponseEntity<?> refreshAccessToken(@RequestHeader Map<String, String> requestHeader,
			@RequestBody @Valid CustomerId requestBody) {
		AccessTokenRequest accessTokenRequest = new AccessTokenRequest();
		accessTokenRequest.setRefreshToken(requestHeader.get("refresh-token"));
		accessTokenRequest.setCustomerId(requestBody.getCustomerId());
		accessTokenRequest.setDeviceId(requestHeader.get("device-id"));
		accessTokenRequest.setToken(requestHeader.get("token"));
		accessTokenRequest.setSource(requestHeader.get("X-Source"));
		return customerV5Service.refreshAccessToken(accessTokenRequest, requestHeader);
	}

	@PostMapping("v4/logout")
	public ResponseEntity<LogoutResponse> logout(@RequestBody @Valid CustomerId requestBody,
			@RequestHeader Map<String, String> requestHeader) {
		String deviceId = requestHeader.get("device-id");
		Integer customerId = requestBody.getCustomerId();
		LogoutResponse logoutResponse = customerV5Service.logout(deviceId, customerId);
		return ResponseEntity.status(logoutResponse.isStatus() ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
				.body(logoutResponse);
	}

	@PostMapping("auth/shukran/delete/{customerId}")
	public DeleteShukranResponse deleteShukranAccount(@RequestHeader Map<String, String> requestHeader,
			@PathVariable Integer customerId) {
		DeleteShukranResponse response = null;
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, customerId);
		}
		if (null != customerId)
			response = customerV4Service.deleteShukranAccount(customerId);

		return response;
	}

	private List<Integer> getStoreIdsForEmailOTPFeatureCheck() {
		return ServiceConfigs.getStoreIdsForEmailOtpFeature();
	}

	/** Used only by POST auth/v2/profile for isEmailOTPEnabled. Consul key: storeIdsForEmailOtpFeatureProfileV2 */
	private List<Integer> getStoreIdsForEmailOTPFeatureCheckProfileV2() {
		return ServiceConfigs.getStoreIdsForEmailOtpFeatureProfileV2();
	}

	private boolean checkEmailStoreValid(List<Integer> emailStoreIds, Integer storeIdFromPayload) {
		return emailStoreIds.stream().anyMatch(id -> id.equals(storeIdFromPayload));
	}


	@PostMapping("/magiclink")
	public ResponseEntity<MagicLinkResponse> sendMagicLink(@RequestBody MagicLinkRequest request) {
		MagicLinkResponse response = magicLinkService.createAndSendMagicLink(request, magicLinkBaseUrl,
				mailSubjectMap.get(request.getLangCode()), mailContentMap.get(request.getLangCode()));
		return ResponseEntity.ok(response);
	}

	@PostMapping("/magiclink/validate")
	public ResponseEntity<MagicLinkResponse> validateMagicLink(
			@RequestBody MagiclinkValidationRequest magiclinkValidationRequest,
			@RequestHeader Map<String, String> requestHeader) {
		MagicLinkResponse response = magicLinkService.validateMagicLink(magiclinkValidationRequest, requestHeader);
		return ResponseEntity.ok(response);
	}

	@PostMapping("card/deactivate/expiry")
	public CustomerCardsResponseDTO inactiveExpiryCards(
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken,
			@RequestParam(name = "customerId", required = false) Integer customerId,
			@RequestParam(name = "expiryInHours", required = false) Integer expiryInHours) {
		if (configService.checkAuthorizationInternal(authorizationToken)) {
			return salesOrderService.deactivateAllExpiryCards(customerId, expiryInHours);
		} else {
			CustomerCardsResponseDTO response = new CustomerCardsResponseDTO();
			response.setStatus(false);
			response.setStatusCode("401");
			response.setStatusMsg(Constants.MISSING_TOKEN_ERR_MSG);
			return response;
		}
	}

	@PostMapping(value = "auth/document/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
	public NationalIdValidationResponse validateNationalIdOrPassport(@RequestHeader Map<String, String> requestHeader,
																 @RequestBody DocumentValidateRequest request) {

		if (null != request.getCustomerId() && "1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, Integer.valueOf(request.getCustomerId()));
		}

		NationalIdValidationResponse validationError = validateDocumentRequest(request);
		if (validationError != null) {
			return validationError;
		}

		try {
			byte[] fileBytes = java.util.Base64.getDecoder().decode(request.getFileContent().trim());
			if (fileBytes == null || fileBytes.length == 0) {
				return createDocumentValidationError("INVALID_BASE_64",
						"File content decoded to empty bytes. Ensure it is valid Base64.");
			}
			return nationalIdExtractionService.extractAndValidateDocument(
					request.getCustomerId(),
					fileBytes,
					request.getStoreId().trim(),
					request.getDocumentIdType().trim(),
					request.getFileType());
		} catch (IllegalArgumentException e) {
			LOGGER.error("Invalid Base64 in fileContent", e);
			return createDocumentValidationError("INVALID_FILE_CONTENT",
					"Invalid fileContent OR not valid Base64. " + StringUtils.defaultString(e.getMessage()));
		} catch (Exception e) {
			LOGGER.error("Error processing document validation request", e);
			return createDocumentValidationError("FILE_PROCESSING_FAILED",
					"Failed to process file: " + StringUtils.defaultString(e.getMessage(), "Unknown error"));
		}
	}

	private NationalIdValidationResponse validateDocumentRequest(DocumentValidateRequest request) {
		if (request.getFileContent() == null || request.getFileContent().isBlank()) {
			return createDocumentValidationError("INVALID_FILE_CONTENT",
					"File content is required. Send file bytes as Base64-encoded string in JSON body.");
		}
		if (request.getStoreId() == null || request.getStoreId().isBlank()) {
			return createDocumentValidationError("STORE_ID_MISSING", "storeId is required.");
		}
		if (request.getDocumentIdType() == null || request.getDocumentIdType().isBlank()) {
			return createDocumentValidationError("DOCUMENT_TYPE_MISSING",
					"documentIdType is required (e.g. 'Passport' or 'Oman National ID').");
		}
		return null;
	}

	private NationalIdValidationResponse createDocumentValidationError(String errorCode, String message) {
		NationalIdValidationResponse response = new NationalIdValidationResponse();
		response.setData(null);
		response.setStatus(false);
		response.setErrorCode(errorCode);
		response.setMessage(message);
		return response;
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "authorization-token", value = "Internal bearer token", paramType = "header", required = true) })
	@GetMapping(value = { "document/national-id" }, produces = MediaType.APPLICATION_PDF_VALUE)
	@ApiOperation(value = "Get stored National ID PDF for an address", notes = "Returns the National ID/Passport PDF from GCP for the given address. Requires addressId and customerId. Requires internal authorization via authorization-token header.")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "PDF returned successfully"),
			@ApiResponse(code = 401, message = "authorization-token missing or wrong"),
			@ApiResponse(code = 404, message = "Address not found or no PDF stored")
	})
	public ResponseEntity<byte[]> getStoredNationalIdPdf(
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken,
			@RequestParam Integer addressId,
			@RequestParam Integer customerId) {
		if (!configService.checkAuthorizationInternal(authorizationToken)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		byte[] pdfBytes = nationalIdExtractionService.getStoredNationalIdPdfBytes(addressId, customerId);
		if (pdfBytes == null || pdfBytes.length == 0) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.body(pdfBytes);
	}

	@PutMapping("auth/acknowledge-mobile-update-message")
	@ApiOperation(value = "Update mobile number update message acknowledgment", notes = "Updates the mobileNumberUpdateMessageAcknowledged flag for a customer")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Successfully updated acknowledgment status"),
			@ApiResponse(code = 404, message = "Customer not found"),
			@ApiResponse(code = 500, message = "Internal server error")
	})
	public ResponseEntity<MobileNumberUpdateAcknowledgmentResponse> updateMobileNumberAcknowledgment(
			@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody MobileNumberUpdateAcknowledgmentRequest request) {

		LOGGER.info("Received request to update mobile number acknowledgment for customerId: {}, acknowledged: {}",
				request.getCustomerId(), request.getAcknowledged());

		// Authenticate the request if JWT flag is enabled
		if ("1".equals(jwtFlag)) {
			customerV4Service.authenticateCheck(requestHeader, request.getCustomerId());
		}

		MobileNumberUpdateAcknowledgmentResponse response = customerV4Service
				.updateMobileNumberUpdateMessageAcknowledged(request, requestHeader);

		HttpStatus status = response.getStatus() ? HttpStatus.OK
				: "404".equals(response.getStatusCode()) ? HttpStatus.NOT_FOUND : HttpStatus.INTERNAL_SERVER_ERROR;

		return ResponseEntity.status(status).body(response);
	}

}