package org.styli.services.order.utility;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.pojo.DisabledServices;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.consul.oms.base.OmsBaseConfigs;
import org.styli.services.order.utility.consulValues.ConsulValues;
import org.styli.services.order.utility.consulValues.FeatureBasedFlag;
import org.styli.services.order.utility.consulValues.PromoRedemptionValues;
import org.styli.services.order.utility.consulValues.PromoValues;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.grpc.netty.shaded.io.netty.util.internal.StringUtil;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

public class Constants {

	public static final String RESULT_MODE_SHIPPING = "shipping";
	public static final String RESULT_MODE_CUSTOMER = "customer";
	
	public static final String EARN_SERVICE_OFF = "Earn Service off!";
	public static DisabledServices disabledServices = null;

	public static final String paymentMethodDeviceId= "";

	public static final ObjectMapper mapper = new ObjectMapper();

	private static final Log LOGGER = LogFactory.getLog(Constants.class);

	public static final Integer TAX_PERCENTAGE = 5;

	public static final Integer MAX_QTY_IN_BAG = 10;

	public static final Integer ADMIN_STORE_ID = 0;
	public static final Integer CUSTOMER_TAX_CLASS_ID = 3;

	public static final String SHIPPING_DESCRIPTION = "Navik Shipping - Navik Shipping";
	public static final String SHIPPING_METHOD_TITLE = "Navik Shipping";
	public static final String SHIPPING_METHOD = "navik_navik";
	public static final String SHIPPING_METHOD_CODE = "clickpost";

	public static final String WMS_USER_HEADER_NAME = "authusername";
	public static final String WMS_USER_HEADER_PASSWORD = "authpassword";
	public static final String HEADER_USER_AGENT = "user-agent";
	public static final String HEADER_DEVICE_ID = "device-id";

	public static final String USER_AGENT_FOR_REST_CALLS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36";

	public static final Integer QUOTE_BAG_VIEW_FLAG_TRUE = 1;
	public static final Integer QUOTE_BAG_VIEW_FLAG_FALSE = 0;

	public static final String QUOTE_ADDRESS_TYPE_SHIPPING = "shipping";
	public static final String QUOTE_ADDRESS_TYPE_BILLING = "billing";

	public static final String CARD_TOKEN_INFO_IN_BAG_TYPE_TOKEN = "token";
	public static final String CARD_TOKEN_INFO_IN_BAG_TYPE_HASH = "hash";

	public static final String QUOTE_SERVICE_ERROR_EMPTY_OBJECT = "Empty/wrong Request Object!";

	public static final ImmutableList<String> PRODUCT_ATTRIBUTE_RESTRICTION = ImmutableList.of("fabric_1", "qty",
			"size_qty", "enabled_at");

	public static final List<Integer> PROMO_VALIDATION_RESPONSE_CODE = Collections
			.unmodifiableList(Arrays.asList(207, 208, 209, 210));
	// value in code replaced by setBaseCurrencyCode in India B2C
	public static String QUOUTE_BASE_CURRENCY_CODE = "SAR";

	public static final String COUPON_EXTERNAL_LIST_URL_HEADER = "AIzaSyCADXDR33XJC3gCS3iaFLWytXo2m4aB_OM";

	public static final String HEADDER_X_HEADER_TOKEN = "Token";
	public static final String HEADDER_X_TOKEN_PREFIX = "KEY ";
	public static final String HEADDER_X_TOKEN = "X-Header-Token";
	public static final String HEADDER_X_TOKEN_MISSING_MESSAGE = "invalid signature";
	public static final String HEADDER_X_HEADER_TOKEN_MISSING_MESSAGE = "invalid signature";
	public static final String HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE = "JWT token is not matching for given user!";
	public static final String HEADDER_INVALID_JWT_AUTH_TOKEN_MESSAGE = "JWT token is not matching for given user!";
	public static final String HEADDER_INVALID_JWT_TOKEN_MESSAGE = "invalid signature";
	public static final String HEADER_X_CLIENT_VERSION="x-client-version";
	public static final String HEADER_X_SOURCE="x-source";
	
	public static final String HEADDER_INVALID_JWT_TOKEN_EXPIRED_MESSAGE = "JWT token has expired";

	public static final List<String> GUEST_EMAIL_IDS = Collections
			.unmodifiableList(Arrays.asList("guest", "guest@stylishop.com"));

	public static final String CONSUL_APPCONFIG_KEYS = "appConfig";

	public static final String CONSUL_OMS_BASE_KEYS = "oms/base";

	public static final String CONSUL_ORDER_KEYS = "orderConsulKeys";

	public static final String CONSUL_CUSTOMER_KEYS = "customerConsulKeys";

	public static final String PROMO_URL_KEY = "promoBaseUrl";
	public static final String AUTH_URL_KEY = "java/customer-service/authorization";

	public static final String COD_CANCEL_MESSAGE = "cod_cancel_message";

	public static final String PREPAID_CANCEL_MESSAGE = "prepaid_cancel_message";

	public static final String COD_RETURN_MESSAGE = "cod_return_message";

	public static final String PREPAID_RETURN_MESSAGE = "prepaid_return_message";

	public static final String SMS_CHECK_NUMBER = "sms-check-number";

	public static final Integer STRECREDIT_MAX_AMOUNT = 1000;

	public static GetOrderConsulValues orderCredentials = null;

	private static final Map<String, String> orderConsulValues = new HashMap<>();

	private static String promoBaseUrl = "";

	public static boolean IS_JWT_TOKEN_ENABLE = false;

	public static boolean IS_TABBY_REPLICA_FAILED_TRIGGER = false;

	public static final String PROMO_CONSUL_VALUES = "promo/promo";

	public static final String PROMO_REDEMPTION_URL = "promoRedemptionUrl";

	private static HashMap<String, String> promoConsulValues = new HashMap<>();

	public static final String INTERNAL_USERS_EMAIL = "stylishop.com";

	public static final String CUSTOMER_REFERRAL_ATTRIBUTE_CODE = "referral_user";

	public static final String OTP_CACHE_NAME = "otp-bucket";
	
	
	public static final String STORE_NOT_FOUND_MSG = "Store not found!";
	public static final String ERROR_FOUND_MSG = "ERROR!";

	public static final String USER_CONSTANT = "user-agent";
	public static final String AUTHORIZATION_HEADER_KEY = "Authorization";
	public static final String BEARER_HEADER_KEY = "Bearer ";

	public static final String CUSTOMER_GROUP = "GENERAL";

	public static final String NAVIC_CARRIOR_CODE = "custom";

	public static final String X_HEADER_TOKEN = "admin@stylishop.com";

	public static final String CONSUL_ORDER_CREDENTIALS_KEY = "java/order-service/credentials";

	public static final String CONSUL_ALPHA_TOKEN_KEY = "java/order-service/alpha/AlphaAuthToken_";
	public static final String CONSUL_BETA_TOKEN_KEY = "java/order-service/beta/BetaAuthToken_";
	public static final String AUTH_BEARER_HEADER = "authorization-token";

	/**
	 *Error code and Status code constants 
	 */
	public static final String EXCEPTION = "Exception";
	public static final String CHARACTERFILETR = "[^\\p{L}\\p{M}\\p{N}\\p{P}\\p{Z}\\p{Cf}\\p{Cs}\\p{C}\\s]";
	public static final String AUTHORIZATION_HEADER = "Authorization";
	public static final String ORDER_ENTITY  = "order";
	public static final String QOUTE_NOT_FOUND_MSG = "Error: Quote was not found for given parameters!";
	public static final String CREDIT_FAILED_REQ = "One or more credit requests has failed to execute successfully!";
	public static final String QOUTE_ENABLED_MSG = "Quote enabled successfully!";
	public static final String INVALID_CURRENCY_MSG = "Invalid currency!";
	public static final String MISSING_OR_WRONG_TOKEN = "authorization-token missing or wrong!";
	public static final String ERROR_MSG = "ERROR";
	public static final String STATUS_500 = "500";
	public static final String MISSING_TOKEN_ERR_MSG = "authorization-token missing or wrong!";
	public static final String UNAUTHENTICATED_REQUEST_MSG = "You're not authenticated to make this request.";
	public static final String SUCCESS_MSG = "SUCCESS";
	public static final String REGISTRATION_BLOCKED = "Registration is blocked for customer migration activity";
	public static final String SUCCESSFUL_LOGIN = "Logged in successfully!";
	public static final String INVALID_TOKEN  = "Invalid Token!!";
	public static final String INVALID_PHONE  = "Invalid Phone no!";
	public static final String INVALID_REQ_BODY = "Invalid request body!";
	public static final String INTERNAL_SERVER_ERROR = "Internal server Error";
	public static final String RESPONSE_KEY = "response";
	public static final String STATUS_KEY = "status";
	public static final String STATUS_CODE_KEY = "statusCode";
	public static final String STATUS_MSG_KEY = "statusMsg";
	public static final String SHIPPING_TITLE = "shippingTitle";
	public static final String SHIPPING_URL = "shippingUrl";
	public static final String ORDER_ID_KEY = "order_id";
	public static final String UNKNOWN_ERROR = "Something went wrong!";
	public static final String GLOBAL_SHIPMENT = "global";
	public static final String LOCAL_SHIPMENT = "local";
	public static final String EXPRESS_SHIPMENT = "express";

	public static final String ZERO = "0";
	public static final String ONE = "1";
	public static final String TWO = "2";
	
	public static boolean IS_JWT_REFRESH_TOKEN_ENABLE = false;
	public static int JWT_TOKEN_EXPIRE_TIME_IN_MINUTES= 30;
	public static String JWT_TOKEN_APP_VERSION = "4.1.00";
	@Setter
    @Getter
    private static String shukranProgramCode= "";
    @Setter
	@Getter
	private static String shukranSourceApplication= "";

	@Setter
	@Getter
    private static String globalRedisKey="";
    @Setter
	@Getter
	private static int shukranEnrollmentConceptCode=0;
	@Setter
	@Getter
	private static String shukranEnrollmentCommonCode="";
	@Setter
	@Getter
	private static String shukranItemTypeCode="";
	@Setter
	@Getter
	private static String shukranTransactionRTPRURL="";
	@Setter
	@Getter
	private static String shukarnEnrollmentStoreCode="";
	
    public static final String deviceId = "device-id";
	
	public static final String DeviceId = "Device-Id";
	
	public static final String Token = "Token";

	public static final String SHIPPED = "shipped";
	public static final String DELIVERED = "delivered";
	public static final String CLOSED = "closed";
	public static final String REFUNDED = "refunded";
	

	private static HashMap<String, String> addressMapper = new HashMap<>();

	private static List<Stores> storesList = new ArrayList<>();

	private static OmsBaseConfigs omsBaseConfigs = new OmsBaseConfigs();

	public static OmsBaseConfigs getOmsBaseConfigs() {
		return omsBaseConfigs;
	}

	public static void setOmsBaseConfigs(OmsBaseConfigs omsBaseConfigs) {
		Constants.omsBaseConfigs = omsBaseConfigs;
	}

	public static List<Stores> getStoresList() {
		return storesList;
	}

	public static void setStoresList(List<Stores> stores) {
		storesList = stores;
	}
	
	public static final List<Integer> RETURN_TYPE_PROCESS_STATUS = Collections
			.unmodifiableList(Arrays.asList(7, 15, 12, 13));

	public static final ObjectMapper JSON_MAPPER = defaultObjectMapper();

	private static ObjectMapper defaultObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return mapper;
	}

	public static String getPlainPhoneNo(String phoneNo) {
		String result = phoneNo;
		if (StringUtils.isNotEmpty(phoneNo)) {
			result = phoneNo.trim().replace(" ", "").replace("-", "").replace("+", "");
		}
		return result;
	}

	public static String getPromoBaseUrl() {
		return promoBaseUrl;
	}

	public static void setPromoBaseUrl(String url) {
		if (!StringUtil.isNullOrEmpty(url)) {
			promoBaseUrl = url;
		}
	}

	public static void setJwtToken(Map<String, Object> hashMap) {
		if (MapUtils.isNotEmpty(hashMap)) {
			IS_JWT_TOKEN_ENABLE = (Boolean)hashMap.get("java_create_order_jwt_token_enable");
			IS_JWT_REFRESH_TOKEN_ENABLE = hashMap.get("refresh_jwt_token_enable") != null ?(Boolean) hashMap.get("refresh_jwt_token_enable"): false;
			 JWT_TOKEN_EXPIRE_TIME_IN_MINUTES= hashMap.get("jwt_token_expire_time_in_minutes")!= null ?(int) hashMap.get("jwt_token_expire_time_in_minutes"): 30;
			JWT_TOKEN_APP_VERSION = hashMap.get("refresh_jwt_token_app_version")!=null?(String) hashMap.get("refresh_jwt_token_app_version"):"4.1.00";
		}
	}

	public static String getPrepaidCancelMessage(String langCode) {
		try {
			if (orderConsulValues.containsKey(CONSUL_ORDER_KEYS)) {
				ObjectMapper mapper = new ObjectMapper();
				ConsulValues value = mapper.readValue(orderConsulValues.get(CONSUL_ORDER_KEYS), ConsulValues.class);
				if (langCode.equals("en"))
					return value.getPrepaidCancelMessage().getEn();
				else if (langCode.equals("ar")) {
					return value.getPrepaidCancelMessage().getAr();

				}
			}
		} catch (Exception e) {
			return "exception occoured during return prepaid sms fetch:" + e.getMessage();
		}
		return "";
	}

	public static String getCodCancelMessage(String langCode) {
		try {
			if (orderConsulValues.containsKey(CONSUL_ORDER_KEYS)) {
				ConsulValues value = mapper.readValue(orderConsulValues.get(CONSUL_ORDER_KEYS), ConsulValues.class);
				if (langCode.equals("en"))
					return value.getCodCancelMessage().getEn();
				else if (langCode.equals("ar")) {
					return value.getCodCancelMessage().getAr();

				}
			}
		} catch (Exception e) {
			return "exception occoured during cod cancel sms fetch:" + e.getMessage();
		}
		return "";
	}

	public static String getCodRerurnMessage(String langCode) {
		try {
			if (orderConsulValues.containsKey(CONSUL_ORDER_KEYS)) {
				ConsulValues value = mapper.readValue(orderConsulValues.get(CONSUL_ORDER_KEYS), ConsulValues.class);
				if (langCode.equals("en"))
					return value.getCodReturnMessage().getEn();
				else if (langCode.equals("ar")) {
					return value.getCodReturnMessage().getAr();

				}
			}
		} catch (Exception e) {
			return "exception occoured during return cod sms fetch:" + e.getMessage();
		}
		return "";
	}

	public static String getPrepaidRerurnMessage(String langCode) {
		try {
			if (orderConsulValues.containsKey(CONSUL_ORDER_KEYS)) {
				ConsulValues value = mapper.readValue(orderConsulValues.get(CONSUL_ORDER_KEYS), ConsulValues.class);
				if (langCode.equals("en"))
					return value.getPrepaidReturnMessage().getEn();
				else if (langCode.equals("ar")) {
					return value.getPrepaidReturnMessage().getAr();

				}
			}
		} catch (Exception e) {
			return "exception occoured during return prepaid sms fetch:" + e.getMessage();
		}
		return "";
	}

	public static String getSmsCheckNummber() {
		try {
			if (orderConsulValues.containsKey(CONSUL_ORDER_KEYS)) {
				ConsulValues value = mapper.readValue(orderConsulValues.get(CONSUL_ORDER_KEYS), ConsulValues.class);
				return value.getSmsCheckNumber().getMobile();

			}
		} catch (Exception e) {
			return "exception occoured during return mobile number fetch:" + e.getMessage();
		}
		return "";
	}

	public static String getOtpVerificationThresholdVersion() {
		try {
			if (orderConsulValues.containsKey(CONSUL_ORDER_KEYS)) {
				ConsulValues value = mapper.readValue(orderConsulValues.get(CONSUL_ORDER_KEYS), ConsulValues.class);
				return ((StringUtils.isBlank(value.getOtpVerificationThresholdVersion())) ? "3.5.4"
						: value.getOtpVerificationThresholdVersion());
			}
		} catch (Exception e) {
			return "";
		}
		return "";
	}

	public static boolean getOtpVerificationInCreateOrderFlag() {
		try {
			if (orderConsulValues.containsKey(CONSUL_ORDER_KEYS)) {
				ConsulValues value = mapper.readValue(orderConsulValues.get(CONSUL_ORDER_KEYS), ConsulValues.class);
				return value.getOtpVerificationInCreateOrder();
			}
		} catch (Exception e) {
			return false;
		}
		return false;
	}

	public static Map<String, String> getOrderConsulValues() {
		return orderConsulValues;
	}

	public static void setOrderConsulValues(String value) {
		orderConsulValues.put(CONSUL_ORDER_KEYS, value);
	}

	public static void setPromoConsulValues(String value) {
		promoConsulValues.put(PROMO_CONSUL_VALUES, value);
	}

	public static PromoRedemptionValues getPromoRedemptionUrl() {
		try {
			if (promoConsulValues != null && promoConsulValues.containsKey(PROMO_CONSUL_VALUES)) {
				ObjectMapper mapper = new ObjectMapper();
				PromoValues value = mapper.readValue(promoConsulValues.get(PROMO_CONSUL_VALUES), PromoValues.class);
				return value.getPromoRedemptionUrl();
			}
		} catch (Exception e) {
			LOGGER.error("exception occoured while mapping promo redemption url:" + e.getMessage());
		}
		return null;
	}

	public static FeatureBasedFlag getFeatureBasedFlag() {
		try {
			if (promoConsulValues != null && promoConsulValues.containsKey(PROMO_CONSUL_VALUES)) {
				ObjectMapper mapper = new ObjectMapper();
				PromoValues value = mapper.readValue(promoConsulValues.get(PROMO_CONSUL_VALUES), PromoValues.class);
				return value.getFeatureBasedFlag();
			}
		} catch (Exception e) {
			LOGGER.error("exception occoured while mapping promo FeatureBasedFlag Values:" + e.getMessage());
		}
		return null;
	}




	private static String alphaToken;
	private static String betaToken;

	public static void setOrderCredentials(String stringObject) {

		try {
			orderCredentials = mapper.readValue(stringObject, GetOrderConsulValues.class);
			LOGGER.info("order credentials:" + mapper.writeValueAsString(orderCredentials));
			if (null != orderCredentials && null != orderCredentials.getTabby()) {
				IS_TABBY_REPLICA_FAILED_TRIGGER = orderCredentials.getTabby().isTabbyReplicaFailedCall();
			}
		} catch (JsonProcessingException e) {

			LOGGER.error("exception during consul order credentials parse:" + e.getMessage());
		}
	}

	public static String getAddressMapper(String countryCode) {
		return addressMapper.get(countryCode);

	}

	public static void setAddressMapper(String countryCode, String value) {
		addressMapper.put(countryCode, value);
	}

	public static final Map<Integer, String> IBAN_COUNTRY_MAP = ImmutableMap.<Integer, String>builder().put(1, "SA")
			.put(3, "SA").put(7, "AE").put(11, "AE").put(12, "KW").put(13, "KW").put(15, "QA").put(17, "QA")
			.put(19, "BH").put(21, "BH").put(23, "OM").put(25, "OM").build();

	public static final List<Integer> ARABIC_STORES = Collections
			.unmodifiableList(Arrays.asList(3, 11, 12, 13, 17, 21, 25));
	
	// Freshdesk Integration Constants
	public static final long FRESHDESK_ORDER_INTERVAL_30_DAYS = 30L * 24 * 60 * 60 * 1000;
	public static final int FRESHDESK_DEFAULT_ORDER_LIST_COUNT = 3;
	public static final String FRESHDESK_SUFFIX_GLOBAL = "-G1";
	public static final String FRESHDESK_SUFFIX_LOCAL = "-L1";
	public static final String FRESHDESK_DATE_FORMAT_DD_MMM_YYYY = "dd MMM yyyy";
	public static final String FRESHDESK_LOG_JOIN_TRACKING_URL = "joinTrackingUrl : ";
	public static final String FRESHDESK_LOG_SPLIT_ORDER_ID = ", split order ID: ";
	public static final String FRESHDESK_LOG_URL = ", URL: ";
	public static final String FRESHDESK_LOG_TRACK_NUMBER = ", track number: ";
	public static final String FRESHDESK_LOG_INCREMENT_ID = ", increment ID: ";
	public static final String FRESHDESK_LOG_INCREMENT_ID_LOWER = ", incrementId: ";
	public static final String FRESHDESK_TRACKING_URL_WAYBILL_PARAM = "?waybill=";
	
	// Arabic store IDs for Freshdesk (stores that are already Arabic)
	public static final List<Integer> FRESHDESK_ARABIC_STORE_IDS = Collections
			.unmodifiableList(Arrays.asList(3, 11, 13, 17, 21, 25));
	
	// Arabic store ID mapping for Freshdesk: English -> Arabic
	public static final Map<Integer, Integer> FRESHDESK_ARABIC_STORE_ID_MAP = ImmutableMap.<Integer, Integer>builder()
			.put(1, 3)   // KSA English -> KSA Arabic
			.put(7, 11)  // UAE English -> UAE Arabic
			.put(12, 13) // KWT English -> KWT Arabic
			.put(15, 17) // QAT English -> QAT Arabic
			.put(19, 21) // BAH English -> BAH Arabic
			.put(23, 25) // OMAN English -> OMAN Arabic
			.build();

	public static final Long decodeAppVersion(String input) {
		Long result = null;
		try {
			if (StringUtils.isNotEmpty(input) && input.matches("^[0-9]+\\.[0-9]+\\.[0-9]+$")) {
				String[] chunks = input.split("\\.");
				String finalNum = "";
				for (int i = 0; i < chunks.length; i++) {
					String chunk = chunks[i];
					if (i == 0) {
						chunk = ((chunk.length() < 3) ? StringUtils.repeat("0", (3 - chunk.length())) : "") + chunk;
					} else {
						chunk = chunk + ((chunk.length() < 3) ? StringUtils.repeat("0", (3 - chunk.length())) : "");
					}
					finalNum = finalNum + chunk;
				}
				result = Long.parseLong(finalNum);
			}
		} catch (Exception e) {
			result = null;
		}
		return result;
	}

	public static void setAlphaToken(String token) {
		alphaToken = token;
	}

	public static String getAlphaToken() {
		return alphaToken;
	}

	public static void setBetaToken(String token) {
		betaToken = token;
	}

	public static String getBetaToken() {
		return betaToken;
	}

	public static final String MOBILE_NUMBER_VALIDATION_REGEX = "^(\\+\\d{1,4}?)(\\s)(\\d{1,10})";
	
	public static void setDisabledServices(DisabledServices disabledServicesFromConsul) {
		disabledServices = new DisabledServices(disabledServicesFromConsul.isRecommendationDisabled(),
				disabledServicesFromConsul.isEarnDisabled(), disabledServicesFromConsul.isReferralDisabled());
	}
	
	public static void setBaseCurrencyCode(String code) {
		QUOUTE_BASE_CURRENCY_CODE = code;
	}
	
	public static String getPaymentFailedThresholdVersion() {
		try {
			if (orderConsulValues.containsKey(CONSUL_ORDER_KEYS)) {
				ConsulValues value = mapper.readValue(orderConsulValues.get(CONSUL_ORDER_KEYS), ConsulValues.class);
				return ((StringUtils.isBlank(value.getPaymentFailedThresholdVersion())) ? "3.7.0"
						: value.getPaymentFailedThresholdVersion());
			}
		} catch (Exception e) {
			return "";
		}
		return "";
	}
	
	/**
	 * Get GCS Shipping Label feature flag from Consul (GetOrderConsulValues)
	 * Returns true if enabled, false if disabled
	 * Falls back to application.properties if not set in Consul
	 */
	public static Boolean getGcsShippingLabelEnabled() {
		try {
			if (orderCredentials != null) {
				return orderCredentials.getGcsShippingLabelEnabled();
			}
		} catch (Exception e) {
			LOGGER.error("Error reading GCS shipping label flag from Consul: " + e.getMessage());
			return null; // Return null to fallback to application.properties
		}
		return null; // Return null to fallback to application.properties
	}
	
	/**
	 * Get GCS Shipping Label bucket name from Consul (GetOrderConsulValues)
	 * Falls back to application.properties if not set in Consul
	 */
	public static String getGcsShippingLabelBucket() {
		try {
			if (orderCredentials != null) {
				return orderCredentials.getGcsShippingLabelBucket();
			}
		} catch (Exception e) {
			LOGGER.error("Error reading GCS bucket from Consul: " + e.getMessage());
			return null;
		}
		return null;
	}
	
	/**
	 * Get GCS Shipping Label folder prefix from Consul (GetOrderConsulValues)
	 * Falls back to application.properties if not set in Consul
	 */
	public static String getGcsShippingLabelFolderPrefix() {
		try {
			if (orderCredentials != null) {
				return orderCredentials.getGcsShippingLabelFolderPrefix();
			}
		} catch (Exception e) {
			LOGGER.error("Error reading GCS folder prefix from Consul: " + e.getMessage());
			return null;
		}
		return null;
	}
	
	/**
	 * Get GCS Signed URL expiry minutes from Consul (GetOrderConsulValues)
	 * Falls back to application.properties if not set in Consul
	 */
	public static Integer getGcsSignedUrlExpiryMinutes() {
		try {
			if (orderCredentials != null) {
				return orderCredentials.getGcsSignedUrlExpiryMinutes();
			}
		} catch (Exception e) {
			LOGGER.error("Error reading GCS expiry minutes from Consul: " + e.getMessage());
			return null;
		}
		return null;
	}
	
	public static String getConvertedAmount(String amount, Integer multiplier) {

		LOGGER.info("amount:" + amount);
		LOGGER.info("multiplier:" + multiplier);
		if (null != amount && null != multiplier) {

			Integer payfortValue = new BigDecimal(amount).multiply(new BigDecimal(multiplier)).intValue();

			return payfortValue.toString();
		} else {

			return null;
		}

	}
	
	public static final String CONSUL_FEATURECONGIF_KEYS = "feature_config";
	
	private static List<Integer> zatcaFlag;
	
	public static void setZatcaFlag(List<Integer> flag) {
		zatcaFlag = flag;
	}

	public static boolean getZatcaFlag(Integer StoreId) {
		return zatcaFlag.contains(StoreId);
	}
	
	public static boolean validateRefershTokenEnable(Map<String, String> requestHeader) {
		
		boolean isRefreshToken= false;

		if(IS_JWT_REFRESH_TOKEN_ENABLE){
			if(StringUtils.isNotEmpty(requestHeader.get(Constants.HEADER_X_SOURCE)) && StringUtils.isNotBlank(requestHeader.get(Constants.HEADER_X_SOURCE)) && requestHeader.get(Constants.HEADER_X_SOURCE).equals("msite")){
				isRefreshToken = true;
			}else{
				if(StringUtils.isNotBlank(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION)) && StringUtils.isNotEmpty(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION))) {
					Long finalXClientVersion = decodeAppVersion(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION));
					Long finalJwtTokenAppVersion = decodeAppVersion(JWT_TOKEN_APP_VERSION);
					if(finalXClientVersion >= finalJwtTokenAppVersion){
						isRefreshToken = true;
					}
				}
			}
		}
		return isRefreshToken;
	}
	
	public static boolean validateRefershTokenEnable(HttpServletRequest httpServletRequest, String xClientVersion, String xSource) {
		boolean isRefreshToken= false;

		if(IS_JWT_TOKEN_ENABLE && IS_JWT_REFRESH_TOKEN_ENABLE){
			if(StringUtils.isNotEmpty(xSource) && StringUtils.isNotBlank(xSource) && xSource.equals("msite")){
				isRefreshToken = true;
			}else{
				if(StringUtils.isNotBlank(xClientVersion) && StringUtils.isNotEmpty(xClientVersion)) {
					Long finalXClientVersion = decodeAppVersion(xClientVersion);
					Long finalJwtTokenAppVersion = decodeAppVersion(JWT_TOKEN_APP_VERSION);
					if(finalXClientVersion >= finalJwtTokenAppVersion){
						isRefreshToken = true;
					}
				}
			}
		}
		return isRefreshToken;
	}
	
	public static boolean validateRefershTokenEnable(Map<String, String> requestHeader, Boolean jwtRefreshTokenFlag) {
			

		boolean isRefreshToken= false;

		if(IS_JWT_TOKEN_ENABLE && jwtRefreshTokenFlag){
			if(StringUtils.isNotEmpty(requestHeader.get(Constants.HEADER_X_SOURCE)) && StringUtils.isNotBlank(requestHeader.get(Constants.HEADER_X_SOURCE)) && requestHeader.get(Constants.HEADER_X_SOURCE).equals("msite")){
				isRefreshToken = true;
			}else{
				if(StringUtils.isNotBlank(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION)) && StringUtils.isNotEmpty(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION))) {
					Long finalXClientVersion = decodeAppVersion(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION));
					Long finalJwtTokenAppVersion = decodeAppVersion(JWT_TOKEN_APP_VERSION);
					if(finalXClientVersion >= finalJwtTokenAppVersion){
						isRefreshToken = true;
					}
				}
			}
		}
		return isRefreshToken;
		}
	

public static int jwtTokenExpireTimeInMinutes() {
	return JWT_TOKEN_EXPIRE_TIME_IN_MINUTES;
}

}
