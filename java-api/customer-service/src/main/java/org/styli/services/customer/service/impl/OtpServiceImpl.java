package org.styli.services.customer.service.impl;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.styli.services.customer.exception.ForbiddenRuntimeException;
import org.styli.services.customer.helper.AccountHelper;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.helper.SmsHelper;
import org.styli.services.customer.limiter.SendOtpLimiterWorker;
import org.styli.services.customer.pojo.EncryptedTokenResponse;
import org.styli.services.customer.pojo.FirstFreeShipping;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntity;
import org.styli.services.customer.pojo.otp.*;
import org.styli.services.customer.pojo.registration.request.CustomerInfoRequest;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.request.CustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.registration.request.CustomerV4Registration;
import org.styli.services.customer.pojo.registration.request.LoginType;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerLoginV4Response;
import org.styli.services.customer.pojo.registration.response.CustomerV4RegistrationResponse;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.redis.TtlMode;
import org.styli.services.customer.repository.StaticComponents;
import org.styli.services.customer.service.*;
import org.styli.services.customer.service.impl.UpdateUser;
import org.styli.services.customer.utility.CommonUtility;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import com.sendgrid.Response;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;

import java.util.*;
import java.util.Date;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Created on 06-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Component
@Scope("singleton")
public class OtpServiceImpl implements OtpService {

	private static final Log LOGGER = LogFactory.getLog(OtpServiceImpl.class);
	private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

	@Autowired
	StaticComponents staticComponents;

	@Autowired
	CustomerV4Service customerV4Service;

	@Autowired
	RedisHelper redisHelper;

	@Autowired
	SmsHelper smsHelper;

	@Autowired
	SendOtpLimiterWorker sendOtpWorker;

	@Autowired
	AccountHelper accountHelper;

	@Autowired
	PasswordHelper passwordHelper;

	@Autowired
	AsyncService asyncService;

	@Value("${secret.react.java.api}")
	private String secretReactJavaApi;

	@Value("${env}")
	private String env;

	@Value("${region}")
	private String region;

	@Autowired
	Client client;

	@Autowired
	EmailService emailService;

	@Autowired
	private CustomerAddressEntityRepository customerAddressEntityRepository;

	@Autowired
	private RsaTokenService rsaTokenService;

	@Autowired
	UpdateUser updateUser;


	private static final int VERIFIED = 1;

	CustomerEntity customer;
	List<CustomerAddressEntity> customerAddressList = new ArrayList<>();

	@Override
	public OtpResponseBody<SendOtpResponse> sendOtp(Map<String, String> requestHeader, SendOtpRequest request,
			String flowType) {
		OtpResponseBody<SendOtpResponse> responseBody = new OtpResponseBody<>();
		Integer storeId = request.getStoreId();
		Boolean phoneVerifiedAtAddress = false;
		// Initial Condition Check
		LOGGER.info("sendOtp : Checking initial conditions before proceeding with the API logic");

		if ("address".equalsIgnoreCase(request.getScreen())) {
			LOGGER.info("sendOtp : Address screen detected for mobile number: " + request.getMobileNo());

			try {
				customer = client.findByEntityId(request.getCustomerId());

				if (customer != null) {
					LOGGER.info("sendOtp : Customer found with mobile number: " + request.getMobileNo());

					List<CustomerAddressEntity> customerAddressList = customerAddressEntityRepository
							.findAllByCustomerId(request.getCustomerId());

					if (customerAddressList != null && !customerAddressList.isEmpty()) {
						LOGGER.info("sendOtp : Found " + customerAddressList.size()
								+ " address(es) for customer with mobile number: " + request.getMobileNo());

						// Check if the mobile number is verified at any address
						phoneVerifiedAtAddress = customerAddressList.stream()
								.filter(Objects::nonNull)
								.anyMatch(address -> request.getMobileNo().equals(address.getTelephone()) &&
										Integer.valueOf(VERIFIED).equals(address.getIsMobileVerified()));

						if (phoneVerifiedAtAddress) {
							LOGGER.info(
									"sendOtp : Mobile number " + request.getMobileNo() + " is verified at an address.");
						} else {
							LOGGER.info("sendOtp : Mobile number " + request.getMobileNo()
									+ " is not verified at any address.");
						}
					}

					// Check if the mobile number is verified at the account level
					boolean phoneVerifiedAtAccount = false;
					if (request.getMobileNo().equals(customer.getPhoneNumber())) {
						phoneVerifiedAtAccount = Boolean.TRUE.equals(customer.getIsMobileVerified());
						LOGGER.info("sendOtp : Mobile number " + request.getMobileNo()
								+ (phoneVerifiedAtAccount ? " is verified" : " is not verified")
								+ " at the account level.");
					}

					boolean phoneVerified = phoneVerifiedAtAccount || phoneVerifiedAtAddress;
					if (phoneVerified) {
						LOGGER.info("sendOtp : Phone number is already verified for customer with mobile number: "
								+ request.getMobileNo());
						responseBody.setStatus(false);
						responseBody.setStatusCode("211");
						responseBody.setStatusMsg(Arrays.asList(1, 7, 15, 12, 23).contains(storeId)
								? Constants.PHONE_ALREADY_VERIFIED_EN
								: Constants.PHONE_ALREADY_VERIFIED_AR);
						return responseBody;
					}
				} else {
					LOGGER.info("sendOtp : Customer not found or is null for mobile number: " + request.getMobileNo());
				}
			} catch (Exception e) {
				LOGGER.error("sendOtp : Error occurred while processing phone verification for mobile number: "
						+ request.getMobileNo(), e);
				responseBody.setStatus(false);
				responseBody.setStatusCode("500");
				responseBody.setStatusMsg(Arrays.asList(1, 7, 15, 12, 23).contains(storeId)
						? Constants.ERROR_EN
						: Constants.ERROR_AR);
				return responseBody;
			}
		}

		if (StringUtils.isEmpty(request.getMobileNo()) && StringUtils.isEmpty(request.getUserIdentifier())) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("400");
			if (Arrays.asList(1, 7, 15, 12 , 23).contains(storeId)) {
				responseBody.setStatusMsg(Constants.INVALID_REQ_BODY);
			} else {
				responseBody.setStatusMsg(Constants.INVALID_REQUEST_AR);
			}
			return responseBody;
		}

		List<Stores> stores = staticComponents.getStoresArray();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
				.findAny().orElse(null);

		if ((flowType != null && flowType.equalsIgnoreCase("registration"))) {

			LOGGER.info("Registration flow detected for " + request.getMobileNo());
			customer = client.findByPhoneNumber(request.getMobileNo());

			if (customer != null) {
				responseBody.setStatus(false);
				responseBody.setStatusCode("206");

				if (Arrays.asList(1, 7, 15, 12 ,23).contains(storeId)) {
					responseBody.setStatusMsg(Constants.REGISTERED_MOBILE_EN);
				} else {
					responseBody.setStatusMsg(Constants.REGISTERED_MOBILE_AR);
				}

				return responseBody;
			}
		}
		
		if (store == null || store.getWebsiteCode() == null) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("201");
			responseBody.setStatusMsg(Constants.STORE_NOT_FOUND_MESSAGE);
			return responseBody;
		} else if (!CommonUtility.isPossibleNumber(request.getMobileNo(), store)) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("202");
			responseBody.setStatusMsg(Constants.INVALID_PHONE);
			return responseBody;
		}
		String langCode = CommonUtility.getLanguageCode(store);
		int unicode = ("ar".equalsIgnoreCase(langCode)) ? 1 : 0;
		String plainPhoneNo = SmsHelper.getPlainPhoneNo(request.getMobileNo());
		OtpBucketObject bucketObject = getBucketObject(plainPhoneNo);
		long now = Instant.now().toEpochMilli();
		String otp = accountHelper.generateSafeOtp(bucketObject, now);
		if (bucketObject == null) {
			bucketObject = new OtpBucketObject();
			bucketObject.setOriginAt(now);
			bucketObject.setCreateCount(0);
		}
		bucketObject.setMobileNo(plainPhoneNo);
		bucketObject.setOtp(otp);
		bucketObject.setCreatedAt(now);
		bucketObject.setExpiresAt(now + 600000);

		if (sendOtpWorker.isFilterEnabled() && !sendOtpWorker.validateOtpObject(bucketObject, now)) {
			throw new ForbiddenRuntimeException("Sending otp is forbidden for this request!");
		}
		if (bucketObject.getCreateCount() == null || bucketObject.getCreateCount() < 1)
			bucketObject.setCreateCount(1);
		LOGGER.info("Starting otp put to redis!");
		boolean success = redisHelper.put(CACHE_NAME, bucketObject.getMobileNo(), bucketObject, TtlMode.OTP_REDIS);
		LOGGER.info("Otp put to redis ended with success: " + success + "!");
		if (!success) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("203");
			responseBody.setStatusMsg(Constants.FAILED_TO_SAVE_OTP);
			return responseBody;
		}

		String message = ServiceConfigs.getOtpMessage(request.getMessageCodeName(), langCode);
		if (StringUtils.isEmpty(message)) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("204");
			if (Arrays.asList(1, 7, 15, 12 , 23).contains(storeId)) {
				responseBody.setStatusMsg(Constants.FAILED_TO_GET_TEMPLATE_EN);
			} else {
				responseBody.setStatusMsg(Constants.FAILED_TO_GET_TEMPLATE_AR);
			}
			return responseBody;
		}

		message = message.replace("{{otp}}", bucketObject.getOtp());
		boolean resendOtp = Boolean.TRUE.equals(request.getResendCall());
		boolean otpSuccess = region.equalsIgnoreCase("IN")
				? smsHelper.sendSMSIN(bucketObject.getMobileNo(), message, unicode, resendOtp)
				: smsHelper.sendSMS(bucketObject.getMobileNo(), message, unicode, resendOtp);

		if (!otpSuccess) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("205");
			if (Arrays.asList(1, 7, 15, 12 , 23).contains(storeId)) {
				responseBody.setStatusMsg(Constants.FAILED_TO_SEND_OTP_OVER_SMS_EN);
			} else {
				responseBody.setStatusMsg(Constants.FAILED_TO_SEND_OTP_OVER_SMS_AR);
			}
			return responseBody;
		}

		// Determine channel: SMS is always sent, WhatsApp may be sent if resendCall = true
		boolean smsSent = true;
		boolean whatsappSent = false;
		if (resendOtp && smsHelper.canSendWhatsAppForPhone(bucketObject.getMobileNo())) {
			whatsappSent = true;
		}
		String channel = buildChannelString(smsSent, false, whatsappSent);

		SendOtpResponse otpResponse = SendOtpResponse.of(true, null);
		otpResponse.setChannel(channel);

		LOGGER.info("DebugMode is : " + request.getDebugMode());
		LOGGER.info("Environment is : " + env);

		if (request.getDebugMode() != null && request.getDebugMode() && StringUtils.isNotEmpty(env)
				&& !"live".equalsIgnoreCase(env) && !"prod".equalsIgnoreCase(env)) {
			otpResponse.setOtpData(bucketObject);
		}
		responseBody.setStatus(true);
		responseBody.setStatusCode("200");
		responseBody.setStatusMsg(Constants.SUCCESS_MSG);
		responseBody.setResponse(otpResponse);
		return responseBody;
	}

	@Override
	public OtpResponseBody<SendOtpResponse> sendOtpMSiteMobile(Map<String, String> requestHeader,
															   SendOtpRequest request) {
		Boolean resendCall = request.getResendCall();
		OtpResponseBody<SendOtpResponse> responseBody = new OtpResponseBody<>();
		Integer storeId = request.getStoreId();

		if (StringUtils.isEmpty(request.getMobileNo()) && StringUtils.isEmpty(request.getUserIdentifier())) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("400");
			if (Arrays.asList(1, 7, 15, 12 , 23).contains(storeId)) {
				responseBody.setStatusMsg(Constants.INVALID_REQ_BODY);
			} else {
				responseBody.setStatusMsg(Constants.INVALID_REQUEST_AR);
			}
			return responseBody;
		}
		SendOtpRegistrationRequest request1 = new SendOtpRegistrationRequest();

		if (StringUtils.isNotEmpty(request.getUserIdentifier())) {
			LOGGER.info("sendOtpMSiteMobile : Triggering email otp for " + request.getUserIdentifier());
			request1.setUserIdentifier(request.getUserIdentifier());
			request1.setStoreId(request.getStoreId());
			request1.setDebugMode(request.getDebugMode());
			return sendOtpViaEmail(requestHeader, request1, null);
		}

		String phoneNumber = null;

		List<Stores> stores = staticComponents.getStoresArray();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
				.findAny().orElse(null);

		if (store == null || store.getWebsiteCode() == null) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("201");
			responseBody.setStatusMsg(Constants.STORE_NOT_FOUND_MESSAGE);
			return responseBody;
		} else if (request.getEmail() != null && isValidEmailId(request.getEmail())) {
			phoneNumber = customerV4Service.getPhoneNumberByEmailId(request.getEmail());
			if (!CommonUtility.isPossibleNumber(phoneNumber, store)) {
				responseBody.setStatus(false);
				responseBody.setStatusCode("202");
				responseBody.setStatusMsg(Constants.INVALID_PHONE);
				return responseBody;
			}
		} else {

			responseBody.setStatus(false);
			responseBody.setStatusCode("202");
			responseBody.setStatusMsg(Constants.INVALID_PHONE);
			return responseBody;
		}

		String langCode = CommonUtility.getLanguageCode(store);
		int unicode = ("ar".equalsIgnoreCase(langCode)) ? 1 : 0;

		String plainPhoneNo = SmsHelper.getPlainPhoneNo(phoneNumber);
		LOGGER.info("Phone Number : " + phoneNumber);
		OtpBucketObject bucketObject = getBucketObject(plainPhoneNo);
		long now = Instant.now().toEpochMilli();
		if (bucketObject == null) {
			bucketObject = new OtpBucketObject();
			bucketObject.setOriginAt(now);
			bucketObject.setCreateCount(0);
		}
		String otp = accountHelper.generateSafeOtp(bucketObject, now);
		bucketObject.setMobileNo(plainPhoneNo);
		bucketObject.setOtp(otp);
		bucketObject.setCreatedAt(now);
		bucketObject.setExpiresAt(now + 600000);

		if (sendOtpWorker.isFilterEnabled() && !sendOtpWorker.validateOtpObject(bucketObject, now)) {
			throw new ForbiddenRuntimeException("Sending otp is forbidden for this request!");
		}
		if (bucketObject.getCreateCount() == null || bucketObject.getCreateCount() < 1)
			bucketObject.setCreateCount(1);
		LOGGER.info("Starting otp put to redis!");
		boolean success = redisHelper.put(CACHE_NAME, bucketObject.getMobileNo(), bucketObject, TtlMode.OTP_REDIS);
		LOGGER.info("Otp put to redis ended with success: " + success + "!");
		if (!success) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("203");
			responseBody.setStatusMsg(Constants.FAILED_TO_SAVE_OTP);
			return responseBody;
		}

		String message = ServiceConfigs.getOtpMessage(request.getMessageCodeName(), langCode);
		if (StringUtils.isEmpty(message)) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("204");
			if (Arrays.asList(1, 7, 15, 12, 23).contains(storeId)) {
				responseBody.setStatusMsg(Constants.FAILED_TO_GET_TEMPLATE_EN);
			} else {
				responseBody.setStatusMsg(Constants.FAILED_TO_GET_TEMPLATE_AR);
			}
			return responseBody;
		}

		message = message.replace("{{otp}}", bucketObject.getOtp());

		boolean otpSuccess = region.equalsIgnoreCase("IN") ? smsHelper.sendSMSIN(bucketObject.getMobileNo(), message , unicode , resendCall)
				: smsHelper.sendSMS(bucketObject.getMobileNo(), message, unicode ,resendCall);

		if (!otpSuccess) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("205");
			if (Arrays.asList(1, 7, 15, 12, 23).contains(storeId)) {
				responseBody.setStatusMsg(Constants.FAILED_TO_SEND_OTP_OVER_SMS_EN);
			} else {
				responseBody.setStatusMsg(Constants.FAILED_TO_SEND_OTP_OVER_SMS_AR);
			}
			return responseBody;
		}

		SendOtpResponse otpResponse = SendOtpResponse.of(true, null);

		LOGGER.info("sendOtpMSiteMobile : DebugMode is : " + request.getDebugMode());
		LOGGER.info("sendOtpMSiteMobile : Environment is : " + env);

		if (request.getDebugMode() != null && request.getDebugMode() && StringUtils.isNotEmpty(env)
				&& !"live".equalsIgnoreCase(env) && !"prod".equalsIgnoreCase(env)) {
			otpResponse.setOtpData(bucketObject);
		}
		responseBody.setStatus(true);
		responseBody.setStatusCode("200");
		responseBody.setStatusMsg(Constants.SUCCESS_MSG);
		responseBody.setResponse(otpResponse);
		return responseBody;
	}

	private boolean isValidEmailId(String emailId) {

		if (!CommonUtility.isValidEmailAddress(emailId)) {
			return false;
		}
		return true;
	}

	@Override
	public OtpResponseBody<ValidateOtpResponse> validateOtp(Map<String, String> requestHeader,
			ValidateOtpRequest request) {
		OtpResponseBody<ValidateOtpResponse> responseBody = new OtpResponseBody<>();
		Integer storeId = request.getStoreId();
		if (StringUtils.isEmpty(request.getMobileNo()) && StringUtils.isEmpty(request.getUserIdentifier())) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("400");
			if (Arrays.asList(1, 7, 15, 12, 23).contains(storeId)) {
				responseBody.setStatusMsg(Constants.INVALID_REQ_BODY);
			} else {
				responseBody.setStatusMsg(Constants.INVALID_REQUEST_AR);
			}
			return responseBody;
		}
		if (!CommonUtility.isPossibleNumber(request.getMobileNo(), null) || StringUtils.isEmpty(request.getOtp())) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("400");
			if (Arrays.asList(1, 7, 15, 12, 23).contains(storeId)) {
				responseBody.setStatusMsg(Constants.INVALID_REQ_BODY);
			} else {
				responseBody.setStatusMsg(Constants.INVALID_REQUEST_AR);
			}
			return responseBody;
		}
		String plainPhoneNo = SmsHelper.getPlainPhoneNo(request.getMobileNo());
		OtpBucketObject otpBucketObject = getBucketObject(plainPhoneNo);

		if (otpBucketObject == null || otpBucketObject.getExpiresAt() == null
				|| StringUtils.isEmpty(otpBucketObject.getOtp())) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("201");
			if (Arrays.asList(1, 7, 15, 12 ,23).contains(storeId)) {
				responseBody.setStatusMsg(Constants.INTERNAL_SERVER_ERROR_EN);
			} else {
				responseBody.setStatusMsg(Constants.INTERNAL_SERVER_ERROR_AR);
			}
			return responseBody;
		}
		otpBucketObject.setMobileNo(plainPhoneNo);
		long now = Instant.now().toEpochMilli();
		if (now > otpBucketObject.getExpiresAt()) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("202");
			if (Arrays.asList(1, 7, 15, 12, 23).contains(storeId)) {
				responseBody.setStatusMsg(Constants.OTP_EXPIRED_EN);
			} else {
				responseBody.setStatusMsg(Constants.OTP_EXPIRED_AR);
			}
			return responseBody;
		}

		if (otpBucketObject.getIncorrectAttempts() == null) {
			otpBucketObject.setIncorrectAttempts(0);
		}

		if (!request.getOtp().equals(otpBucketObject.getOtp())) {
			otpBucketObject.setIncorrectAttempts(otpBucketObject.getIncorrectAttempts() + 1);
			if (otpBucketObject.getIncorrectAttempts() > 5) {
				responseBody.setStatus(false);
				responseBody.setStatusCode("206");
				if (Arrays.asList(1, 7, 15, 12 ,23).contains(storeId)) {
					responseBody.setStatusMsg(Constants.INCORRECT_OTP_EXCEEDED_EN);
				} else {
					responseBody.setStatusMsg(Constants.INCORRECT_OTP_EXCEEDED_AR);
				}
				return responseBody;
			} else {
				responseBody.setStatus(false);
				responseBody.setStatusCode("203");
				if (Arrays.asList(1, 7, 15, 12, 23).contains(storeId)) {
					responseBody.setStatusMsg(Constants.INCORRECT_OTP_EN);
				} else {
					responseBody.setStatusMsg(Constants.INCORRECT_OTP_AR);
				}
				redisHelper.put(CACHE_NAME, otpBucketObject.getMobileNo(), otpBucketObject, TtlMode.OTP_REDIS);
				return responseBody;
			}
		}

		otpBucketObject.setIncorrectAttempts(0);
		boolean postValidationTaskStatus = false;
		Customer customer = null;
		OtpValidationType type = request.getType();
		switch (type) {
			case LOGIN:

				CustomerLoginV4Response loginResponse = new CustomerLoginV4Response();
				if (StringUtils.isNotEmpty(request.getUserIdentifier())) {
					LOGGER.info("validate/otp : doLoginTaskEmail: " + request.getUserIdentifier());
					loginResponse = doLoginTaskEmail(request.getUserIdentifier(), null, requestHeader);
				}
				if (request.getCustomerInfo() != null && request.getCustomerInfo().getCustomer() != null
						&& request.getCustomerInfo().getCustomer().getIsUserConsentProvided() != null) {
					loginResponse = doLoginTask(request.getMobileNo(),
							request.getCustomerInfo().getCustomer().getIsUserConsentProvided(), requestHeader);
				} else {
					loginResponse = doLoginTask(request.getMobileNo(), null, requestHeader);
				}
				if (loginResponse == null) {
					responseBody.setStatus(false);
					responseBody.setStatusCode("500");
					responseBody.setStatusMsg(Constants.INTERNAL_SERVER_ERROR);
					return responseBody;
				}

				if (!"200".equals(loginResponse.getStatusCode()) || loginResponse.getResponse() == null) {
					responseBody.setStatus(loginResponse.isStatus());
					responseBody.setStatusCode(loginResponse.getStatusCode());
					responseBody.setStatusMsg(loginResponse.getStatusMsg());
					responseBody.setError(loginResponse.getError());
					return responseBody;
				} else {
					customer = loginResponse.getResponse().getCustomer();
					postValidationTaskStatus = true;
				}
				break;
			case REGISTRATION:
				String blockRegistration = ServiceConfigs.isRegistrationBlocked();
				if (null != blockRegistration && "true".equals(blockRegistration)) {
					ErrorType error = new ErrorType();
					error.setErrorCode("500");
					error.setErrorMessage("Registration is blocked for customer migration activity");
					responseBody.setError(error);
					responseBody.setStatus(false);
					responseBody.setStatusCode("500");
					responseBody.setStatusMsg("ERROR");
					return responseBody;
				}
				CustomerV4RegistrationResponse registerResponse = doRegistrationTask(request.getMobileNo(),
						request.getCustomerInfo(), requestHeader);
				if (registerResponse == null) {
					responseBody.setStatus(false);
					responseBody.setStatusCode("500");
					responseBody.setStatusMsg(Constants.INTERNAL_SERVER_ERROR);
					return responseBody;
				}
				if (!"200".equals(registerResponse.getStatusCode()) || registerResponse.getResponse() == null) {
					responseBody.setStatus(registerResponse.isStatus());
					responseBody.setStatusCode(registerResponse.getStatusCode());
					responseBody.setStatusMsg(registerResponse.getStatusMsg());
					responseBody.setError(registerResponse.getError());
					return responseBody;
				} else {
					customer = registerResponse.getResponse().getCustomer();
					postValidationTaskStatus = true;
				}
				break;

			default:
				postValidationTaskStatus = true;
				if (Objects.nonNull(request.getCustomerId())) {
					CustomerEntity customerEntity = client.findByEntityId(request.getCustomerId());
					if (Objects.nonNull(customerEntity)) {
						customerEntity.setIsPhoneNumberVerified(true);
						client.saveAndFlushCustomerEntity(customerEntity);
					}
				}
				break;
		}
		redisHelper.remove(CACHE_NAME, otpBucketObject.getMobileNo());
		saveVerificationStatusInRedis(request.getMobileNo(), "true");
		
		// For guest users only: Save separate verification status key for order placement check
		// This is in addition to the regular verification status, not a replacement
		if (request.getCustomerId() == null) {
			LOGGER.info("validateOtp: Guest user detected (customerId is null). Saving guest phone verification status.");
			saveGuestPhoneVerificationStatus(plainPhoneNo);
		}
		
		responseBody.setStatus(true);
		responseBody.setStatusCode("200");
		responseBody.setStatusMsg(Constants.SUCCESS_MSG);
		ValidateOtpResponse validateOtpResponse = ValidateOtpResponse.of(postValidationTaskStatus, customer, null);
		if (customer != null) {
			validateOtpResponse.getCustomer().setIsInfluencer(customer.getIsInfluencer());
		}
		responseBody.setResponse(validateOtpResponse);

		try {
			if (null != responseBody.getResponse() && null != responseBody.getResponse().getCustomer()) {

				FirstFreeShipping firstFreeShipping = customerV4Service.setFreeShipping(
						responseBody.getResponse().getCustomer().getCreatedAt(),
						responseBody.getResponse().getCustomer().getStoreId());

				responseBody.getResponse().getCustomer().setFirstFreeShipping(firstFreeShipping);
			}
		} catch (Exception ex) {
			LOGGER.info("Error in setting free shipping for customer :: " + ex.getMessage());
		}

		return responseBody;
	}

	@Override
	public OtpResponseBody<ValidateOtpResponse> validateOtpMSiteMobile(Map<String, String> requestHeader,
			ValidateOtpRequest request) {
		OtpResponseBody<ValidateOtpResponse> responseBody = new OtpResponseBody<>();
		Integer storeId = request.getStoreId();
		if (StringUtils.isEmpty(request.getMobileNo()) && StringUtils.isEmpty(request.getUserIdentifier())) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("400");
			if (Arrays.asList(1, 7, 15, 12).contains(storeId)) {
				responseBody.setStatusMsg(Constants.INVALID_REQ_BODY);
			} else {
				responseBody.setStatusMsg(Constants.INVALID_REQUEST_AR);
			}
			return responseBody;
		}

		String identifier = request.getUserIdentifier();
		String phoneNumber = null;

		String lookupKey = StringUtils.isNotEmpty(identifier) ? identifier : null;
		if (lookupKey == null && isValidEmailId(request.getEmail())) {
			phoneNumber = customerV4Service.getPhoneNumberByEmailId(request.getEmail());
			if (!CommonUtility.isPossibleNumber(phoneNumber, null)) {
				responseBody.setStatus(false);
				responseBody.setStatusCode("400");
				if (Arrays.asList(1, 7, 15, 12).contains(storeId)) {
					responseBody.setStatusMsg(Constants.INVALID_REQ_BODY);
				} else {
					responseBody.setStatusMsg(Constants.INVALID_REQUEST_AR);
				}
				return responseBody;
			}
			lookupKey = SmsHelper.getPlainPhoneNo(phoneNumber);
			String plainPhoneNo = SmsHelper.getPlainPhoneNo(phoneNumber);
			LOGGER.info("Phone Number : " + phoneNumber);
		} else if (lookupKey == null) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("400");
			if (Arrays.asList(1, 7, 15, 12).contains(storeId)) {
				responseBody.setStatusMsg(Constants.INVALID_REQ_BODY);
			} else {
				responseBody.setStatusMsg(Constants.INVALID_REQUEST_AR);
			}
			return responseBody;
		}

		LOGGER.info("validate/otp : Lookup Key for OTP Validation: " + lookupKey);
		OtpBucketObject otpBucketObject = getBucketObject(lookupKey);

		if (otpBucketObject == null || otpBucketObject.getExpiresAt() == null
				|| StringUtils.isEmpty(otpBucketObject.getOtp())) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("201");
			if (Arrays.asList(1, 7, 15, 12).contains(storeId)) {
				responseBody.setStatusMsg(Constants.INTERNAL_SERVER_ERROR_EN);
			} else {
				responseBody.setStatusMsg(Constants.INTERNAL_SERVER_ERROR_AR);
			}
			return responseBody;
		}
		otpBucketObject.setMobileNo(lookupKey);
		long now = Instant.now().toEpochMilli();
		if (now > otpBucketObject.getExpiresAt()) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("202");
			if (Arrays.asList(1, 7, 15, 12).contains(storeId)) {
				responseBody.setStatusMsg(Constants.OTP_EXPIRED_EN);
			} else {
				responseBody.setStatusMsg(Constants.OTP_EXPIRED_AR);
			}
			return responseBody;
		}

		if (otpBucketObject.getIncorrectAttempts() == null) {
			otpBucketObject.setIncorrectAttempts(0);
		}

		if (!request.getOtp().equals(otpBucketObject.getOtp())) {
			otpBucketObject.setIncorrectAttempts(otpBucketObject.getIncorrectAttempts() + 1);
			if (otpBucketObject.getIncorrectAttempts() > 5) {
				responseBody.setStatus(false);
				responseBody.setStatusCode("206");
				if (Arrays.asList(1, 7, 15, 12,23).contains(storeId)) {
					responseBody.setStatusMsg(Constants.INCORRECT_OTP_EXCEEDED_EN);
				} else {
					responseBody.setStatusMsg(Constants.INCORRECT_OTP_EXCEEDED_AR);
				}
				return responseBody;
			} else {
				responseBody.setStatus(false);
				responseBody.setStatusCode("203");
				if (Arrays.asList(1, 7, 15, 12,23).contains(storeId)) {
					responseBody.setStatusMsg(Constants.INCORRECT_OTP_EN);
				} else {
					responseBody.setStatusMsg(Constants.INCORRECT_OTP_AR);
				}
				redisHelper.put(CACHE_NAME, otpBucketObject.getMobileNo(), otpBucketObject, TtlMode.OTP_REDIS);
				return responseBody;
			}
		}

		// Reset the incorrect attempts counter on successful validation
		otpBucketObject.setIncorrectAttempts(0);
		boolean postValidationTaskStatus = false;
		Customer customer = null;
		OtpValidationType type = request.getType();
		switch (type) {
			case LOGIN:

				CustomerLoginV4Response loginResponse = new CustomerLoginV4Response();
				if (StringUtils.isNotEmpty(request.getUserIdentifier())) {
					LOGGER.info("validate/otp : doLoginTaskEmail: " + request.getUserIdentifier());
					loginResponse = doLoginTaskEmail(request.getUserIdentifier(), null, requestHeader);
				} else if (request.getCustomerInfo() != null && request.getCustomerInfo().getCustomer() != null
						&& request.getCustomerInfo().getCustomer().getIsUserConsentProvided() != null) {
					loginResponse = doLoginTask(phoneNumber,
							request.getCustomerInfo().getCustomer().getIsUserConsentProvided(), requestHeader);
				} else {
					loginResponse = doLoginTask(phoneNumber, null, requestHeader);
				}
				if (loginResponse == null) {
					responseBody.setStatus(false);
					responseBody.setStatusCode("500");
					responseBody.setStatusMsg(Constants.INTERNAL_SERVER_ERROR);
					return responseBody;
				}
				if (!"200".equals(loginResponse.getStatusCode()) || loginResponse.getResponse() == null) {
					responseBody.setStatus(loginResponse.isStatus());
					responseBody.setStatusCode(loginResponse.getStatusCode());
					responseBody.setStatusMsg(loginResponse.getStatusMsg());
					responseBody.setError(loginResponse.getError());
					return responseBody;
				} else {
					customer = loginResponse.getResponse().getCustomer();
					postValidationTaskStatus = true;
				}
				break;
			case REGISTRATION:
				String blockRegistration = ServiceConfigs.isRegistrationBlocked();
				if (null != blockRegistration && "true".equals(blockRegistration)) {
					ErrorType error = new ErrorType();
					error.setErrorCode("500");
					error.setErrorMessage("Registration is blocked for customer migration activity");
					responseBody.setError(error);
					responseBody.setStatus(false);
					responseBody.setStatusCode("500");
					responseBody.setStatusMsg("ERROR");
					return responseBody;
				}
				CustomerV4RegistrationResponse registerResponse = doRegistrationTask(phoneNumber,
						request.getCustomerInfo(), requestHeader);
				if (registerResponse == null) {
					responseBody.setStatus(false);
					responseBody.setStatusCode("500");
					responseBody.setStatusMsg(Constants.INTERNAL_SERVER_ERROR);
					return responseBody;
				}
				if (!"200".equals(registerResponse.getStatusCode()) || registerResponse.getResponse() == null) {
					responseBody.setStatus(registerResponse.isStatus());
					responseBody.setStatusCode(registerResponse.getStatusCode());
					responseBody.setStatusMsg(registerResponse.getStatusMsg());
					responseBody.setError(registerResponse.getError());
					return responseBody;
				} else {
					customer = registerResponse.getResponse().getCustomer();
					postValidationTaskStatus = true;
				}
				break;
			default:
				postValidationTaskStatus = true;
				break;
		}
		redisHelper.remove(CACHE_NAME, otpBucketObject.getMobileNo());
		saveVerificationStatusInRedis(phoneNumber, "true");
		responseBody.setStatus(true);
		responseBody.setStatusCode("200");
		responseBody.setStatusMsg(Constants.SUCCESS_MSG);
		responseBody.setResponse(ValidateOtpResponse.of(postValidationTaskStatus, customer, null));

		if (responseBody.getResponse() != null && responseBody.getResponse().getCustomer() != null) {
			Customer customer1 = responseBody.getResponse().getCustomer();
			if (rsaTokenService.isInfluencerPortalFeatureEnabled(storeId) && customer1.getEmail() != null
					&& customer1.getMobileNumber() != null && customer1.getFirstName() != null) {
				EncryptedTokenResponse tokenResponse = rsaTokenService.attachEncryptedRsaToken(customer1.getEmail(),
						customer1.getMobileNumber(), customer1.getFirstName(),
						customer1.getLastName() != null ? customer1.getLastName() : "", storeId);
				if (tokenResponse != null) {
					responseBody.setEncryptedRsaToken(tokenResponse.getToken());
					responseBody.setEncryptedRsaTokenExpiry(tokenResponse.getExpiry());
				}
			}
		}
		return responseBody;
	}

	private CustomerLoginV4Response doLoginTask(String phoneNo, Boolean isUserConsentProvided,
			Map<String, String> requestHeader) {
		CustomerLoginV4Response response = null;
		try {
			CustomerLoginV4Request v4Request = new CustomerLoginV4Request();
			v4Request.setUseridentifier(phoneNo);
			v4Request.setLoginType(LoginType.MOBILE_LOGIN);
			v4Request.setPassword(secretReactJavaApi);
			if (isUserConsentProvided != null) {
				v4Request.setIsUserConsentProvided(isUserConsentProvided);
			}
			response = customerV4Service.getCustomerLoginV4Details(v4Request, requestHeader);
		} catch (Exception e) {
			LOGGER.error(e);
		}
		return response;
	}

	private CustomerLoginV4Response doLoginTaskEmail(String email, Boolean isUserConsentProvided,
			Map<String, String> requestHeader) {
		CustomerLoginV4Response response = null;
		try {
			LOGGER.info("validate/otp : doLoginTaskEmail: " + email);
			CustomerLoginV4Request v4Request = new CustomerLoginV4Request();
			v4Request.setIsOtpVerified(true);
			v4Request.setUseridentifier(email);
			v4Request.setLoginType(LoginType.EMAIL_LOGIN);
			v4Request.setPassword(secretReactJavaApi);
			if (isUserConsentProvided != null) {
				v4Request.setIsUserConsentProvided(isUserConsentProvided);
			}
			response = customerV4Service.getCustomerLoginV4Details(v4Request, requestHeader);
		} catch (Exception e) {
			LOGGER.error(e);
		}
		return response;
	}

	private CustomerV4RegistrationResponse doRegistrationTask(String phoneNo, CustomerInfoRequest customerInfo,
			Map<String, String> requestHeader) {
		CustomerV4RegistrationResponse response = null;
		try {
			CustomerV4Registration v4Request = new CustomerV4Registration();
			if (customerInfo != null && customerInfo.getCustomer() != null) {
				customerInfo.getCustomer().setPhone(phoneNo);
			}
			v4Request.setCustomerInfo(customerInfo);
			response = customerV4Service.saveV4Customer(v4Request, requestHeader);

			/**
			 * Async REST Call to update Sales orders with new customer id
			 */
			if (response.getResponse() != null) {
				asyncService.asyncSalesOrdersUpdateCustId(response.getResponse().getCustomer());
			}
		} catch (Exception e) {
			LOGGER.error(e);
		}
		return response;
	}

	private OtpBucketObject getBucketObject(String phoneNo) {
		OtpBucketObject result = null;
		if (StringUtils.isEmpty(phoneNo))
			return result;
		result = setMobileNumber(phoneNo);
		return result;
	}

	private OtpBucketObject setMobileNumber(String phoneNo) {
		OtpBucketObject result;
		try {
			result = (OtpBucketObject) redisHelper.get(CACHE_NAME, phoneNo, OtpBucketObject.class);
			if (result != null) {
				result.setMobileNo(phoneNo);
			}
		} catch (Exception e) {
			result = null;
		}
		return result;
	}

	private long getExpiryPeriodMilli() {
		return TtlMode.OTP_VALID.getTimeUnit().toMillis(TtlMode.OTP_VALID.getValue());
	}

	public OtpResponseBody<SendOtpResponse> sendOtpViaEmail(Map<String, String> requestHeader,
			SendOtpRegistrationRequest request, String flowType) {
		OtpResponseBody<SendOtpResponse> responseBody = new OtpResponseBody<>();
		Integer storeId = request.getStoreId();
		List<Stores> stores = staticComponents.getStoresArray();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
				.findAny().orElse(null);

		CustomerEntity customer;

		if (flowType != null && flowType.equalsIgnoreCase("registration")) {
			LOGGER.info("Registration flow detected for " + request.getUserIdentifier());
			customer = client.findByEmail(request.getUserIdentifier());
			if (null != customer) {
				responseBody.setStatus(false);
				responseBody.setStatusCode("206");
				responseBody.setStatusMsg("Email is Already Registered !!");
				return responseBody;
			}
		}

		if (store == null || store.getWebsiteCode() == null) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("201");
			responseBody.setStatusMsg(Constants.STORE_NOT_FOUND_MESSAGE);
			return responseBody;
		}

		String plainEmail = request.getUserIdentifier();
		OtpBucketObject bucketObject = getBucketObject(plainEmail);
		long now = Instant.now().toEpochMilli();
		String otp = accountHelper.generateSafeOtp(bucketObject, now);

		if (bucketObject == null) {
			bucketObject = new OtpBucketObject();
			bucketObject.setOriginAt(now);
			bucketObject.setCreateCount(0);
		}
		bucketObject.setEmail(plainEmail);
		bucketObject.setOtp(otp);
		bucketObject.setCreatedAt(now);
		bucketObject.setExpiresAt(now + 600000);

		if (!redisHelper.put(CACHE_NAME, bucketObject.getEmail(), bucketObject, TtlMode.OTP_REDIS)) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("203");
			responseBody.setStatusMsg(Constants.FAILED_TO_SAVE_OTP);
			return responseBody;
		}
		LOGGER.info("Registration OTP : OTP is " + otp);
		// Send OTP via email
		String subject = "Styli Registration OTP";
		String message = "Dear User,\n\n" + "Your One-Time Password (OTP) for Styli is : " + otp + "\n\n"
				+ "Note that this OTP is valid for 10 minutes only.\n\n"
				+ "If you did not request this OTP, please ignore this message.\n\n" + "Best regards,\n"
				+ "The Styli Team";

		Response response1 = emailService.sendText(plainEmail, subject, message);
		LOGGER.info("Registration OTP : emailService response received " + response1.getBody());
		boolean emailSuccess = (response1 != null && response1.getStatusCode() == 202);
		if (!emailSuccess) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("205");
			if (Arrays.asList(1, 7, 15, 12).contains(storeId)) {
				responseBody.setStatusMsg(Constants.FAILED_TO_SEND_OTP_OVER_SMS_EN);
			} else {
				responseBody.setStatusMsg(Constants.FAILED_TO_SEND_OTP_OVER_SMS_AR);
			}
			return responseBody;
		}

		// Set channel: Email only
		String channel = buildChannelString(false, true, false);

		SendOtpResponse otpResponse = SendOtpResponse.of(true, null);
		otpResponse.setChannel(channel);
		LOGGER.info("sendOtpViaEmail : DebugMode is : " + request.getDebugMode());
		LOGGER.info("sendOtpViaEmail : Environment is : " + env);
		if (request.getDebugMode() != null && request.getDebugMode() && StringUtils.isNotEmpty(env)
				&& !"live".equalsIgnoreCase(env) && !"prod".equalsIgnoreCase(env)) {
			otpResponse.setOtpData(bucketObject);
		}
		responseBody.setStatus(true);
		responseBody.setStatusCode("200");
		responseBody.setStatusMsg(Constants.SUCCESS_MSG);
		responseBody.setResponse(otpResponse);

		return responseBody;
	}

	public OtpResponseBody<ValidateOtpResponse> validateRegistrationOtp(
			Map<String, String> requestHeader,
			ValidateOtpRegistrationRequest request,
			Object userIdentifierType) {
		OtpResponseBody<ValidateOtpResponse> responseBody = new OtpResponseBody<>();
		Integer storeId = request.getStoreId();
		String originalEmail = null; // Store original email for token generation logic

		if (StringUtils.isEmpty(request.getUserIdentifier()) || StringUtils.isEmpty(request.getOtp())) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("400");
			if (Arrays.asList(1, 7, 15, 12).contains(storeId)) {
				responseBody.setStatusMsg(Constants.INVALID_REQ_BODY);
			} else {
				responseBody.setStatusMsg(Constants.INVALID_REQUEST_AR);
			}
			return responseBody;
		}

		String plainIdentifier;
		if (isValidUserIdentifier(request.getUserIdentifier()).equals("mobile")) {
			plainIdentifier = SmsHelper.getPlainPhoneNo(request.getUserIdentifier());
		} else {
			plainIdentifier = request.getUserIdentifier();
		}

		OtpBucketObject otpBucketObject = getBucketObject(plainIdentifier);

		if (otpBucketObject == null || otpBucketObject.getExpiresAt() == null ||
				StringUtils.isEmpty(otpBucketObject.getOtp())) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("201");
			if (Arrays.asList(1, 7, 15, 12).contains(storeId)) {
				responseBody.setStatusMsg(Constants.INTERNAL_SERVER_ERROR_EN);
			} else {
				responseBody.setStatusMsg(Constants.INTERNAL_SERVER_ERROR_AR);
			}
			return responseBody;
		}

		long now = Instant.now().toEpochMilli();
		if (now > otpBucketObject.getExpiresAt()) {
			responseBody.setStatus(false);
			responseBody.setStatusCode("202");
			if (Arrays.asList(1, 7, 15, 12).contains(storeId)) {
				responseBody.setStatusMsg(Constants.OTP_EXPIRED_EN);
			} else {
				responseBody.setStatusMsg(Constants.OTP_EXPIRED_AR);
			}
			return responseBody;
		}

		if (otpBucketObject.getIncorrectAttempts() == null) {
			otpBucketObject.setIncorrectAttempts(0);
		}

		if (!request.getOtp().equals(otpBucketObject.getOtp())) {
			otpBucketObject.setIncorrectAttempts(otpBucketObject.getIncorrectAttempts() + 1);
			if (otpBucketObject.getIncorrectAttempts() > 5) {
				responseBody.setStatus(false);
				responseBody.setStatusCode("206");
				if (Arrays.asList(1, 7, 15, 12,23).contains(storeId)) {
					responseBody.setStatusMsg(Constants.INCORRECT_OTP_EXCEEDED_EN);
				} else {
					responseBody.setStatusMsg(Constants.INCORRECT_OTP_EXCEEDED_AR);
				}
				return responseBody;
			} else {
				responseBody.setStatus(false);
				responseBody.setStatusCode("203");
				if (Arrays.asList(1, 7, 15, 12,23).contains(storeId)) {
					responseBody.setStatusMsg(Constants.INCORRECT_OTP_EN);
				} else {
					responseBody.setStatusMsg(Constants.INCORRECT_OTP_AR);
				}
				redisHelper.put(CACHE_NAME, otpBucketObject.getMobileNo(), otpBucketObject, TtlMode.OTP_REDIS);
				return responseBody;
			}
		}

		otpBucketObject.setIncorrectAttempts(0);

		if (request.getOtp().equals(otpBucketObject.getOtp())) {
			CustomerEntity customer = null;

			if (request.getCustomerId() != null) {
				customer = client.findByEntityId(request.getCustomerId());
				if (customer != null) {
					if ("mobile".equals(userIdentifierType)) {
						if (StringUtils.isNotEmpty(customer.getPhoneNumber())
								&& StringUtils.isNotBlank(customer.getPhoneNumber())
								&& customer.getPhoneNumber().equals(request.getUserIdentifier())) {
							customer.setIsMobileVerified(true);
							LOGGER.info("Checking for same phone number in address to make it verified in DB ");
							validateAndMarkMobileVerifiedInAddressDB(request.getCustomerId(),
									request.getUserIdentifier());
						} else if (StringUtils.isNotBlank(request.getScreen())
								&& StringUtils.isNotEmpty(request.getScreen())
								&& ("address".equalsIgnoreCase(request.getScreen())
										|| "profile".equalsIgnoreCase(request.getScreen()))) {
							LOGGER.info("Validate mobile number : " + request.getUserIdentifier());
							redisHelper.remove(CACHE_NAME, plainIdentifier);
							saveVerificationStatusInRedis(request.getUserIdentifier(), "true");
							
							// Check if autoSavePhone is true and save phone number to DB
							if (Boolean.TRUE.equals(request.getAutoSavePhone())) {
								LOGGER.info("autoSavePhone is true, calling handlePhoneNumberUpdate for phone number: " + request.getUserIdentifier());
								
								// Create CustomerUpdateProfileRequest object for handlePhoneNumberUpdate
								CustomerUpdateProfileRequest customerUpdateRequest = new CustomerUpdateProfileRequest();
								customerUpdateRequest.setCustomerId(request.getCustomerId());
								customerUpdateRequest.setMobileNumber(request.getUserIdentifier());
								
								// Call handlePhoneNumberUpdate to handle phone number transfer logic
								try {
									updateUser.handlePhoneNumberUpdate(customerUpdateRequest, customer, client);
									LOGGER.info("Successfully called handlePhoneNumberUpdate for customer: " + request.getCustomerId());
								} catch (Exception e) {
									LOGGER.info("Error calling handlePhoneNumberUpdate for customer: " + request.getCustomerId() + ". Error: " + e.getMessage());
									// Re-throw to ensure the transaction is rolled back and the caller is aware of the failure.
									throw new RuntimeException("Failed to update phone number for customer " + request.getCustomerId());
								}
							}
						} else {
							customer.setPhoneNumber(request.getUserIdentifier());
							customer.setIsMobileVerified(true);
							LOGGER.info("Checking for same phone number in address to make it verified in DB ");
							validateAndMarkMobileVerifiedInAddressDB(request.getCustomerId(),
									request.getUserIdentifier());
						}
					} else if ("email".equals(userIdentifierType)) {
						// Store original email before updating for token generation logic
						originalEmail = customer.getEmail();
						
						if (StringUtils.isNotBlank(request.getScreen())
								&& StringUtils.isNotEmpty(request.getScreen())
								&& ("address".equalsIgnoreCase(request.getScreen())
										|| "profile".equalsIgnoreCase(request.getScreen()))) {
							LOGGER.info("Validate Email : " + customer.getEmail());
							redisHelper.remove(CACHE_NAME, plainIdentifier);
							saveVerificationStatusInRedis(request.getUserIdentifier(), "true");

							customer.setEmail(request.getUserIdentifier());
							customer.setIsEmailVerified(true);
						}

						else {
							customer.setEmail(request.getUserIdentifier());
							customer.setIsEmailVerified(true);
						}
					}
					client.saveAndFlushCustomerEntity(customer);
				}
			} else {
				redisHelper.remove(CACHE_NAME, plainIdentifier);
				saveVerificationStatusInRedis(request.getUserIdentifier(), "true");
			}

			// Generate new JWT token if email was updated and screen is profileSave or profile
			String newJwtToken = null;

			boolean isEmailUpdate = "email".equals(userIdentifierType) && customer != null;
			boolean isProfileScreen = StringUtils.isNotBlank(request.getScreen()) && ("profileSave".equalsIgnoreCase(request.getScreen()) || "profile".equalsIgnoreCase(request.getScreen()));
			boolean emailHasChanged = !StringUtils.equalsIgnoreCase(request.getUserIdentifier(), originalEmail);

			if (isEmailUpdate && isProfileScreen && emailHasChanged) {
			try {

					// Generate new JWT token with updated email
					newJwtToken = passwordHelper.generateToken(
						request.getUserIdentifier(), // new email
						String.valueOf(new Date().getTime()), // code
						customer.getEntityId(), // customer ID
						false // refresh token flag
					);
					LOGGER.info("Generated new JWT token for updated email: " + request.getUserIdentifier() + " with screen: " + request.getScreen());
				} catch (Exception e) {
					LOGGER.error("Error generating new JWT token for updated email: " + e.getMessage());
				}
			}
            // For guest users only: Save separate verification status key for order placement check
            // This is in addition to the regular verification status, not a replacement
            if ("mobile".equals(userIdentifierType) && request.getCustomerId() == null) {
                LOGGER.info("validateRegistrationOtp: Guest user detected (customerId is null). Saving guest phone verification status.");
                saveGuestPhoneVerificationStatus(plainIdentifier);
            }
			responseBody.setStatus(true);
			responseBody.setStatusCode("200");
			responseBody.setStatusMsg(Constants.SUCCESS_MSG);
			ValidateOtpResponse validateOtpResponse = ValidateOtpResponse.of(true, null, null);
			if (newJwtToken != null) {
				validateOtpResponse.setNewJwtToken(newJwtToken);
			}
			responseBody.setResponse(validateOtpResponse);
			return responseBody;
		}

		return responseBody;
	}

	public Object isValidUserIdentifier(String userIdentifier) {
		if (userIdentifier == null || userIdentifier.isEmpty()) {
			return false;
		}

		if (userIdentifier.startsWith("+")) {
			return "mobile";
		}

		if (EMAIL_PATTERN.matcher(userIdentifier).matches()) {
			return "email";
		}

		return false;
	}

	public void saveVerificationStatusInRedis(String userIdentifier, String isVerified) {
		// Original method - keeps existing behavior for registered users
		String key = userIdentifier;

		LOGGER.info("saveVerificationStatusInRedis: Saving verification status for identifier: {} with status: {}" + key
				+ "isVerified :" + isVerified);

		try {
			boolean success = redisHelper.put(CACHE_NAME, key, isVerified, TtlMode.OTP_REDIS);

			if (success) {
				LOGGER.info("saveVerificationStatusInRedis: Verification status saved successfully for identifier: {}"
						+ key);
			} else {
				LOGGER.info(
						"saveVerificationStatusInRedis: Failed to save verification status for identifier: {}" + key);
			}
		} catch (Exception e) {
			LOGGER.error("saveVerificationStatusInRedis: Error saving verification status for identifier: {}. Error: {}"
					+ e);
		}
	}

	/**
	 * Save phone verification status specifically for guest users.
	 * Uses a separate key pattern (phone_verified:{phone}) that persists for order placement check.
	 * This is ONLY called for guest users (when customerId is null).
	 */
	private void saveGuestPhoneVerificationStatus(String phoneNumber) {
		if (StringUtils.isEmpty(phoneNumber)) {
			return;
		}
		String key = "phone_verified:" + phoneNumber;
		LOGGER.info("saveGuestPhoneVerificationStatus: Saving guest phone verification status for: " + key);
		try {
			boolean success = redisHelper.put(CACHE_NAME, key, "true", TtlMode.OTP_REDIS);
			if (success) {
				LOGGER.info("saveGuestPhoneVerificationStatus: Guest phone verification status saved successfully for: " + key);
			} else {
				LOGGER.warn("saveGuestPhoneVerificationStatus: Failed to save guest phone verification status for: " + key);
			}
		} catch (Exception e) {
			LOGGER.error("saveGuestPhoneVerificationStatus: Error saving guest phone verification status for " + key + ": " + e);
		}
	}

	@Override
	public Boolean getVerificationStatusFromRedis(String userIdentifier) {
		// Original method - keeps existing behavior for registered users
		String key = userIdentifier;

		try {
			Object result = redisHelper.get(CACHE_NAME, key, Boolean.class);

			if (result != null && result instanceof Boolean) {
				Boolean verificationStatus = (Boolean) result;
				return verificationStatus;
			} else {
				return false;
			}
		} catch (Exception e) {
			return false;
		}
	}

	public void validateAndMarkMobileVerifiedInAddressDB(Integer customerId, String mobileNumber) {

		String normalizedMobileNumber = mobileNumber.replaceAll("\\s+", "");
		LOGGER.info("validateAndMarkMobileVerifiedInAddressDB : Normalized mobileNumber: " + normalizedMobileNumber);

		List<CustomerAddressEntity> customerAddressList = customerAddressEntityRepository
				.findAllByCustomerId(customerId);

		if (customerAddressList != null && !customerAddressList.isEmpty()) {
			LOGGER.info("validateAndMarkMobileVerifiedInAddressDB: Found " + customerAddressList.size()
					+ " address(es) for customer with mobile number: " + mobileNumber);

			boolean phoneMatchedAndUpdated = false;

			for (CustomerAddressEntity address : customerAddressList) {
				if (address != null) {
					String normalizedTelephone = address.getTelephone().replaceAll("\\s+", "");
					LOGGER.info("validateAndMarkMobileVerifiedInAddressDB : Normalized telephone from address: "
							+ normalizedTelephone);

					if (normalizedMobileNumber.equals(normalizedTelephone)) {
						LOGGER.info("validateAndMarkMobileVerifiedInAddressDB : Mobile number " + mobileNumber
								+ " matched with phone number in address with address ID: " + address.getParentId());

						address.setIsMobileVerified(1);
						customerAddressEntityRepository.saveAndFlush(address);
						phoneMatchedAndUpdated = true;
					}
				}
			}

			if (phoneMatchedAndUpdated) {
				LOGGER.info("validateAndMarkMobileVerifiedInAddressDB : Mobile number " + mobileNumber
						+ " has been verified and updated successfully for customer " + customerId);
			} else {
				LOGGER.info(
						"validateAndMarkMobileVerifiedInAddressDB : No address with matching mobile number found for customer ID: "
								+ customerId);
			}
		} else {
			LOGGER.info("validateAndMarkMobileVerifiedInAddressDB : addresses found for customer ID: " + customerId);
		}
	}

	@Override
	public OtpResponseBody<SendOtpResponse> sendOtpRationalisation(Map<String, String> requestHeader, SendOtpRequest request, String flowType, Boolean skipEmailOtp) {

		LOGGER.info("sendOtpRationalisation : Received request");

		// Extract and validate request parameters
		RequestParams params = extractRequestParams(request);
		if (!params.isValid()) {
			return createInvalidRequestResponse();
		}
		// Handle email-only login case
		if (params.isEmailOnlyLogin()) {
			return handleEmailOnlyLogin(requestHeader, params);
		}
		// Handle phone OTP case
		return handlePhoneOtpCase(requestHeader, params, skipEmailOtp);
	}

	private RequestParams extractRequestParams(SendOtpRequest request) {
		RequestParams params = new RequestParams();
		params.storeId = request.getStoreId();
		params.mobileNo = request.getMobileNo();
		params.userIdentifier = request.getUserIdentifier();
		params.debugMode = request.getDebugMode();
		params.resendCall = request.getResendCall();

		// Auto-populate mobileNo if missing and userIdentifier is numeric (not email)
		if (StringUtils.isEmpty(params.mobileNo) && StringUtils.isNotEmpty(params.userIdentifier) && !params.userIdentifier.contains("@")) {
			params.mobileNo = params.userIdentifier;
			request.setMobileNo(params.mobileNo);
			LOGGER.info("sendOtpRationalisation : Derived mobileNo from userIdentifier " + params.mobileNo);
		}

		return params;
	}

	private OtpResponseBody<SendOtpResponse> handleEmailOnlyLogin(Map<String, String> requestHeader, RequestParams params) {
		LOGGER.info("sendOtpRationalisation : Mobile number empty, loginRationalisation=true, checking DB for phone");

		boolean smsSent = false;
		boolean whatsappSent = false;
		
		// Try to find phone number in DB
		String phoneFromDb = findPhoneNumberByEmail(params.userIdentifier);
		
		// Send OTP to phone if found
		if (StringUtils.isNotEmpty(phoneFromDb)) {
			LOGGER.info("sendOtpRationalisation : Sending OTP to phone " + phoneFromDb);
			sendPhoneOtp(requestHeader, phoneFromDb, params);
			smsSent = true;
			// Check if WhatsApp was sent (when resendCall is true and WhatsApp is enabled)
			if (Boolean.TRUE.equals(params.resendCall) && isWhatsAppEnabledForStore(params.storeId, phoneFromDb)) {
				whatsappSent = true;
			}
		}

		// Always send email OTP
		LOGGER.info("sendOtpRationalisation : Sending email OTP to " + params.userIdentifier);
		OtpResponseBody<SendOtpResponse> emailResponse = sendOtpMSiteMobile(requestHeader, buildEmailOtpRequest(params.userIdentifier, params.storeId, params.debugMode));
		
		// Create response with channel information
		OtpResponseBody<SendOtpResponse> response = new OtpResponseBody<>();
		if (emailResponse.getStatus()) {
			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg(Constants.SUCCESS_MSG);
			
			// Build channel string (email is always sent, plus SMS/WhatsApp if applicable)
			String channel = buildChannelString(smsSent, true, whatsappSent);
			SendOtpResponse sendOtpResponse = SendOtpResponse.of(true, null);
			sendOtpResponse.setChannel(channel);
			response.setResponse(sendOtpResponse);
		} else {
			// If email OTP failed, return the error response
			return emailResponse;
		}
		
		return response;
	}

	private OtpResponseBody<SendOtpResponse> handlePhoneOtpCase(Map<String, String> requestHeader, RequestParams params, Boolean skipEmailOtp) {
		// Send phone OTP
		LOGGER.info("sendOtpRationalisation : Sending OTP to mobile " + params.mobileNo);
		OtpResponseBody<SendOtpResponse> responseBody = sendOtp(requestHeader, buildPhoneOtpRequest(params), null);
		if (!responseBody.getStatus()) {
			return responseBody;
		}

		boolean smsSent = true; // SMS is always sent when sendOtp succeeds
		boolean whatsappSent = false;
		boolean emailSent = false;
		
		// Check if WhatsApp was sent (when resendCall is true and WhatsApp is enabled)
		if (Boolean.TRUE.equals(params.resendCall) && isWhatsAppEnabledForStore(params.storeId, params.mobileNo)) {
			whatsappSent = true;
		}

		// Send optional email OTP
		if (!Boolean.TRUE.equals(skipEmailOtp)) {
			String emailToSend = findEmailToSend(params);
			if (StringUtils.isNotEmpty(emailToSend)) {
				try {
					LOGGER.info("sendOtpRationalisation : Sending email OTP " + emailToSend);
					sendOtpMSiteMobile(requestHeader, buildEmailOtpRequest(emailToSend, params.storeId, params.debugMode));
					emailSent = true;
				} catch (Exception e) {
					LOGGER.warn("sendOtpRationalisation : Failed to send Email OTP " + emailToSend);
				}
			}
		} else {
			LOGGER.info("sendOtpRationalisation : Skipping email OTP as requested.");
		}

		// Build channel string and set in response
		String channel = buildChannelString(smsSent, emailSent, whatsappSent);
		if (responseBody.getResponse() != null) {
			responseBody.getResponse().setChannel(channel);
		}

		// Return success response
		return createSuccessResponse(responseBody);
	}

	private String findPhoneNumberByEmail(String email) {
		try {
			CustomerEntity customerByEmail = client.findByEmail(email);
			if (customerByEmail != null && StringUtils.isNotEmpty(customerByEmail.getPhoneNumber())) {
				return customerByEmail.getPhoneNumber();
			}
		} catch (Exception e) {
			LOGGER.info("sendOtpRationalisation : Error fetching customer by email " + e);
		}
		return null;
	}

	private void sendPhoneOtp(Map<String, String> requestHeader, String phoneNumber, RequestParams params) {
		SendOtpRequest phoneRequest = new SendOtpRequest();
		phoneRequest.setMobileNo(phoneNumber);
		phoneRequest.setStoreId(params.storeId);
		phoneRequest.setDebugMode(params.debugMode);
		phoneRequest.setResendCall(params.resendCall);
		sendOtp(requestHeader, phoneRequest, null);
	}

	private SendOtpRequest buildPhoneOtpRequest(RequestParams params) {
		SendOtpRequest request = new SendOtpRequest();
		request.setMobileNo(params.mobileNo);
		request.setStoreId(params.storeId);
		request.setDebugMode(params.debugMode);
		request.setResendCall(params.resendCall);
		return request;
	}

	private void sendOptionalEmailOtp(Map<String, String> requestHeader, RequestParams params) {
		String emailToSend = findEmailToSend(params);
		
		if (StringUtils.isNotEmpty(emailToSend)) {
			try {
				LOGGER.info("sendOtpRationalisation : Sending email OTP " + emailToSend);
				sendOtpMSiteMobile(requestHeader, buildEmailOtpRequest(emailToSend, params.storeId, params.debugMode));
			} catch (Exception e) {
				LOGGER.warn("sendOtpRationalisation : Failed to send Email OTP " + emailToSend);
			}
		}
	}

	/**
	 * Builds channel string based on which channels OTP was sent through
	 * @param smsSent true if SMS was sent
	 * @param emailSent true if Email was sent
	 * @param whatsappSent true if WhatsApp was sent
	 * @return channel string like "sms", "sms/email", "sms/email/whatsapp"
	 */
	private String buildChannelString(boolean smsSent, boolean emailSent, boolean whatsappSent) {
		List<String> channels = new ArrayList<>();
		
		if (smsSent) {
			channels.add("sms");
		}
		if (emailSent) {
			channels.add("email");
		}
		if (whatsappSent) {
			channels.add("whatsapp");
		}
		
		if (channels.isEmpty()) {
			return null; // No channels used
		}
		
		return String.join("/", channels);
	}

	/**
	 * Checks if WhatsApp is enabled for the given phone number
	 * Uses existing SmsHelper method to determine WhatsApp capability
	 * @param storeId store ID (not used, kept for consistency)
	 * @param mobileNo phone number (to determine country)
	 * @return true if WhatsApp is enabled
	 */
	private boolean isWhatsAppEnabledForStore(Integer storeId, String mobileNo) {
		try {
			return smsHelper.canSendWhatsAppForPhone(mobileNo);
		} catch (Exception e) {
			LOGGER.warn("sendOtpRationalisation : Error checking WhatsApp flag: " + e.getMessage());
			return false;
		}
	}

	private String findEmailToSend(RequestParams params) {
		// Prefer email from request if present
		if (StringUtils.isNotEmpty(params.userIdentifier) && params.userIdentifier.contains("@")) {
			LOGGER.info("sendOtpRationalisation : Using email from request " + params.userIdentifier);
			return params.userIdentifier;
		}

		// Otherwise look up in DB using phone
		try {
			CustomerEntity foundCustomer = client.findByPhoneNumber(params.mobileNo);
			if (foundCustomer != null && StringUtils.isNotEmpty(foundCustomer.getEmail())) {
				return foundCustomer.getEmail();
			}
		} catch (Exception e) {
			LOGGER.error("sendOtpRationalisation : Error finding customer by phone" + params.mobileNo);
		}

		return null;
	}

	private OtpResponseBody<SendOtpResponse> createInvalidRequestResponse() {
		LOGGER.warn("sendOtpRationalisation : Mobile number and valid email both missing.");
		OtpResponseBody<SendOtpResponse> responseBody = new OtpResponseBody<>();
		responseBody.setStatus(false);
		responseBody.setStatusCode("400");
		responseBody.setStatusMsg(Constants.INVALID_REQ_BODY);
		return responseBody;
	}

	private OtpResponseBody<SendOtpResponse> createSuccessResponse(OtpResponseBody<SendOtpResponse> responseBody) {
		responseBody.setStatus(true);
		responseBody.setStatusCode("200");
		responseBody.setStatusMsg(Constants.SUCCESS_MSG);
		if (responseBody.getResponse() == null) {
			responseBody.setResponse(SendOtpResponse.of(true, null));
		}
		return responseBody;
	}

	private static class RequestParams {
		Integer storeId;
		String mobileNo;
		String userIdentifier;
		Boolean debugMode;
		Boolean resendCall;

		boolean isValid() {
			return StringUtils.isNotEmpty(mobileNo) || 
				   (StringUtils.isNotEmpty(userIdentifier) && userIdentifier.contains("@"));
		}

		boolean isEmailOnlyLogin() {
			return StringUtils.isEmpty(mobileNo) && 
				   StringUtils.isNotEmpty(userIdentifier) && 
				   userIdentifier.contains("@");
		}
	}

	// Helper to send email OTP only
	private OtpResponseBody<SendOtpResponse> sendEmailOtpOnly(Map<String, String> requestHeader, String email, Integer storeId, Boolean debugMode) {
		OtpResponseBody<SendOtpResponse> emailOtpResponse = sendOtpMSiteMobile(requestHeader, buildEmailOtpRequest(email, storeId, debugMode));
		if (!emailOtpResponse.getStatus()) {
			LOGGER.warn("sendOtpRationalisation : Failed to send Email OTP: " + emailOtpResponse.getStatusMsg());
			return emailOtpResponse;
		}
		OtpResponseBody<SendOtpResponse> response = new OtpResponseBody<>();
		response.setStatus(true);
		response.setStatusCode("200");
		response.setStatusMsg(Constants.SUCCESS_MSG);
		SendOtpResponse sendOtpResponse = SendOtpResponse.of(true, null);
		sendOtpResponse.setChannel("email");
		response.setResponse(sendOtpResponse);
		return response;
	}

	// Helper to build email OTP request
	private SendOtpRequest buildEmailOtpRequest(String email, Integer storeId, Boolean debugMode) {
		SendOtpRequest emailOtpRequest = new SendOtpRequest();
		emailOtpRequest.setUserIdentifier(email);
		emailOtpRequest.setStoreId(storeId);
		emailOtpRequest.setDebugMode(debugMode);
		return emailOtpRequest;
	}

	@Override
	public OtpResponseBody<ValidateOtpResponse> validateOtpRationalisation(Map<String, String> requestHeader, ValidateOtpRequest request) {
		LOGGER.info("validateOtpRationalisation: Received OTP validation request");

		// Extract request parameters
		ValidateOtpParams params = extractValidateOtpParams(request);
		
		// Validate request parameters
		if (!params.isValid()) {
			return createInvalidValidateOtpResponse(params.isArabic);
		}

		// Step 1: Try mobile OTP validation
		OtpResponseBody<ValidateOtpResponse> mobileResponse = validateMobileOtp(requestHeader, params);
		if (mobileResponse != null) {
			return mobileResponse;
		}

		// Step 2: Try email OTP validation
		String emailToUse = findEmailForValidation(params);
		if (StringUtils.isNotEmpty(emailToUse)) {
			return validateEmailOtp(requestHeader, emailToUse, params);
		}

		// Step 3: If neither worked
		return createOtpValidationFailureResponse(params.isArabic);
	}

	private ValidateOtpParams extractValidateOtpParams(ValidateOtpRequest request) {
		ValidateOtpParams params = new ValidateOtpParams();
		params.storeId = request.getStoreId();
		params.mobileNo = request.getMobileNo();
		params.otp = request.getOtp();
		params.userIdentifier = request.getUserIdentifier();
		params.type = request.getType();
		params.customerInfo = request.getCustomerInfo();
		params.loginRationalisation = request.getLoginRationalisation();
		params.isArabic = !Arrays.asList(1, 7, 15, 12).contains(params.storeId);
		return params;
	}

	private OtpResponseBody<ValidateOtpResponse> validateMobileOtp(Map<String, String> requestHeader, ValidateOtpParams params) {
		if (StringUtils.isEmpty(params.mobileNo)) {
			// If loginRationalisation is true and mobileNo is empty, try to find phone number by email
			if (Boolean.TRUE.equals(params.loginRationalisation) && StringUtils.isNotEmpty(params.userIdentifier)) {
				LOGGER.info("validateMobileOtp: loginRationalisation=true, mobileNo empty, searching for phone number using userIdentifier: " + params.userIdentifier);
				String phoneNumber = findPhoneNumberByEmail(params.userIdentifier);
				if (StringUtils.isNotEmpty(phoneNumber)) {
					LOGGER.info("validateMobileOtp: Phone number found in DB: " + phoneNumber);
					params.mobileNo = phoneNumber;
				} else {
					LOGGER.info("validateMobileOtp: Phone number not found in DB for userIdentifier: " + params.userIdentifier);
					return null;
				}
			} else {
				return null;
			}
		}

		ValidateOtpRequest phoneOtpRequest = buildMobileOtpRequest(params);
		OtpResponseBody<ValidateOtpResponse> phoneOtpResponse = validateOtp(requestHeader, phoneOtpRequest);
		
		if ("200".equals(phoneOtpResponse.getStatusCode())) {
			LOGGER.info("validateOtpRationalisation: OTP validated using mobile number");
			return phoneOtpResponse;
		}
		
		return null;
	}

	private ValidateOtpRequest buildMobileOtpRequest(ValidateOtpParams params) {
		ValidateOtpRequest phoneOtpRequest = new ValidateOtpRequest();
		phoneOtpRequest.setMobileNo(params.mobileNo);
		phoneOtpRequest.setOtp(params.otp);
		phoneOtpRequest.setStoreId(params.storeId);
		phoneOtpRequest.setType(params.type);
		return phoneOtpRequest;
	}

	private String findEmailForValidation(ValidateOtpParams params) {
		// Prefer email from request if present
		if (StringUtils.isNotEmpty(params.userIdentifier) && params.userIdentifier.contains("@")) {
			LOGGER.info("validateOtpRationalisation: Email provided in request, skipping DB lookup.");
			return params.userIdentifier;
		}

		// Otherwise look up in DB using mobile
		if (StringUtils.isNotEmpty(params.mobileNo)) {
			return findEmailByPhoneNumber(params.mobileNo);
		}

		return null;
	}

	private String findEmailByPhoneNumber(String mobileNo) {
		try {
			CustomerEntity customerByPhone = client.findByPhoneNumber(mobileNo);
			if (customerByPhone != null && StringUtils.isNotEmpty(customerByPhone.getEmail())) {
				return customerByPhone.getEmail();
			}
		} catch (Exception e) {
			LOGGER.error("validateOtpRationalisation: Error while finding customer by phone number: " + mobileNo, e);
		}
		return null;
	}

	private OtpResponseBody<ValidateOtpResponse> validateEmailOtp(Map<String, String> requestHeader, String emailToUse, ValidateOtpParams params) {
		LOGGER.info("validateOtpRationalisation: Trying OTP validation via email " + emailToUse);

		ValidateOtpRequest emailOtpRequest = buildEmailOtpRequest(emailToUse, params);
		OtpResponseBody<ValidateOtpResponse> emailOtpResponse = validateOtpMSiteMobile(requestHeader, emailOtpRequest);
		
		if ("200".equals(emailOtpResponse.getStatusCode())) {
			LOGGER.info("validateOtpRationalisation: OTP validated using email");
		} else {
			LOGGER.warn("validateOtpRationalisation: OTP validation failed for both mobile and email");
		}
		
		return emailOtpResponse;
	}

	private ValidateOtpRequest buildEmailOtpRequest(String emailToUse, ValidateOtpParams params) {
		ValidateOtpRequest emailOtpRequest = new ValidateOtpRequest();
		emailOtpRequest.setUserIdentifier(emailToUse);
		emailOtpRequest.setEmail(emailToUse);
		emailOtpRequest.setOtp(params.otp);
		emailOtpRequest.setStoreId(params.storeId);
		emailOtpRequest.setType(params.type);
		emailOtpRequest.setCustomerInfo(params.customerInfo);
		return emailOtpRequest;
	}

	private OtpResponseBody<ValidateOtpResponse> createInvalidValidateOtpResponse(boolean isArabic) {
		LOGGER.warn("validateOtpRationalisation: Invalid request. Mobile/Email or OTP is empty.");
		OtpResponseBody<ValidateOtpResponse> responseBody = new OtpResponseBody<>();
		setFailure(responseBody, "400", isArabic ? Constants.INVALID_REQUEST_AR : Constants.INVALID_REQ_BODY);
		return responseBody;
	}

	private OtpResponseBody<ValidateOtpResponse> createOtpValidationFailureResponse(boolean isArabic) {
		LOGGER.info("validateOtpRationalisation: No valid email found for OTP validation");
		OtpResponseBody<ValidateOtpResponse> responseBody = new OtpResponseBody<>();
		setFailure(responseBody, "203", isArabic ? Constants.INCORRECT_OTP_AR : Constants.INCORRECT_OTP_EN);
		return responseBody;
	}

	private static class ValidateOtpParams {
		Integer storeId;
		String mobileNo;
		String otp;
		String userIdentifier;
		OtpValidationType type;
		CustomerInfoRequest customerInfo;
		Boolean loginRationalisation;
		boolean isArabic;

		boolean isValid() {
			return StringUtils.isNotEmpty(otp) && 
				   (StringUtils.isNotEmpty(mobileNo) || StringUtils.isNotEmpty(userIdentifier));
		}
	}
	
	private void setFailure(OtpResponseBody<?> responseBody, String code, String msg) {
		responseBody.setStatus(false);
		responseBody.setStatusCode(code);
		responseBody.setStatusMsg(msg);
	}

	@Override
	public SendOtpRequest buildSendOtpRequestFromRegistration(SendOtpRegistrationRequest request) {
		SendOtpRequest sendOtpRequest = new SendOtpRequest();
		sendOtpRequest.setStoreId(request.getStoreId());
		sendOtpRequest.setMessageCode(request.getMessageCode());
		sendOtpRequest.setDebugMode(request.getDebugMode());
		sendOtpRequest.setScreen(request.getScreen());
		sendOtpRequest.setUserIdentifier(request.getUserIdentifier());
		sendOtpRequest.setCustomerId(request.getCustomerId());
		sendOtpRequest.setLoginRationalisation(request.getLoginRationalisation());
		sendOtpRequest.setResendCall(request.getResendCall());
		return sendOtpRequest;
	}
}
