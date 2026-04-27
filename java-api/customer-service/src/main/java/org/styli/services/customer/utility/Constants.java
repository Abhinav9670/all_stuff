package org.styli.services.customer.utility;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.styli.services.customer.pojo.AddressValidatetionMessage;
import org.styli.services.customer.pojo.GetOrderConsulValues;
import org.styli.services.customer.pojo.LoginCredentials;
import org.styli.services.customer.pojo.consul.QuoteFreeShippingConsul;
import org.styli.services.customer.utility.pojo.config.BaseConfig;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponse;
import org.styli.services.customer.utility.pojo.config.Stores;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.internal.LinkedTreeMap;

import lombok.Getter;
import lombok.Setter;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Getter
@Setter
public class Constants {

	private static final Log LOGGER = LogFactory.getLog(Constants.class);

	public static final String WHATSAPP_SIGNUP_CACHE_NAME = "whatsapp-signup-bucket";
	public static final String DELETE_CUSTOMER_OTP_CACHE_NAME = "delete-customer-otp-bucket";
    public static final String USER_AGENT_FOR_REST_CALLS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36";
    public static final String LISTING_VM_ENV_LIVE = "prod";
    public static final String HEADDER_X_HEADER_TOKEN = "Token";
    public static final String Token = "Token";
	public static final String Token_small = "token";
    public static final String HEADDER_X_TOKEN_PREFIX = "KEY ";
    public static final String HEADDER_X_TOKEN = "X-Header-Token";
	public static final String HEADDER_X_TOKEN_SMALL = "x-header-token";
    public static final String HEADDER_X_TOKEN_MISSING_MESSAGE = "invalid signature";
    public static final String HEADDER_CUSTOM_X_TOKEN_MISSING_MESSAGE = "invalid signature";
    public static final String HEADDER_X_HEADER_TOKEN_MISSING_MESSAGE = "X-Header-Token is missing!";
    public static final String HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE = "JWT token is not matching for given user!";
    public static final String HEADDER_INVALID_JWT_TOKEN_MESSAGE = "invalid signature";
    public static final String HEADER_DELETED_USER_MESSAGE = "User is deleted!";
    public static final String HEADDER_INVALID_JWT_AUTH_TOKEN_MESSAGE = "JWT token is not matching for given user!";
    public static final String HEADDER_INVALID_JWT_TOKEN_EXPIRED_MESSAGE = "JWT token has expired";
	public static final String HEADER_DEVICE_ID = "device-id";
	public static final String HEADER_X_SOURCE = "x-source";
	public static final String HEADER_X_SOURCE_LARGE = "X-Source";
	public static final String HEADER_X_CLIENT_VERSION = "x-client-version";
	public static final String HEADER_X_CLIENT_VERSION_CAPITAL = "X-Client-Version";
    public static final String AUTH_URL_KEY = "java/customer-service/authorization";
    public static boolean IS_JWT_TOKEN_ENABLE = false;
    public static boolean IS_JWT_REFRESH_TOKEN_ENABLE = false;
	public static Double CUSTOMER_TOKEN_DELAY_IN_MILLISECONDS= 500.0;
	public static Double JWT_TOKEN_EXPIRE_TIME_IN_MINUTES= 30.0;
	public static String JWT_TOKEN_APP_VERSION = "4.1.00";
	public static Double REFRESH_TOKEN_EXPIRE_TIME= 90.0;
    public static QuoteFreeShippingConsul freeShipping = null;
    public static AddressValidatetionMessage addressValidatetionMessage;
    private static HashMap<String, String> catalogConsulValues = new HashMap<>();
    public static final String SOURCE_MSITE = "msite";
    public static final String SOURCE_MOBILE_ANDROID = "Android";
    public static final String SOURCE_MOBILE_IOS = "iOS";
    public static final String CONSUL_CATALOG_KEYS = "catalogConsulKeys";
    public static final String EXCEPTION = "Exception";

	public static final List<String> GUEST_EMAIL_IDS = Collections
			.unmodifiableList(Arrays.asList("guest", "guest@stylishop.com"));
	public static final String SOURCE_MOBILE = "source_mobile";
    
    public static final String CONSUL_APPCONFIG_KEYS = "appConfig";
	 public static final String CONSUL_BASE_CONFIG_KEYS = "java/utility-service/base";

    private static Map<String, String> addressMapper = new HashMap<>();

    public static final ObjectMapper JSON_MAPPER = defaultObjectMapper();

	public static final String CONSUL_ORDER_CREDENTIALS_KEY = "java/order-service/credentials";
	public static final String CONSUL_CUSTOMER_CREDENTIALS_KEY = "java/customer-service/logincredentials";
	public static final String CONSUL_QUOTE_SERVICE_KEY = "quote-service/base_config";
	public static final String EARN_SERVICE_OFF = "Earn Service off!";
	public static final String EMAIL_ALREADY_EXISTS= "Email Address Is Already Exists";
	public static final String deviceId = "device-id";
	
	public static final String DeviceId = "Device-Id";
	public static final String  EAS_SUCCESS_MSG= "Success!";

	public static final String EPSILON_CACHE_NAME = "epsilon-bucket";
	public static final String EPSILON_TOKEN_KEY = "epsilon-token";
	public static final String SHUKRAN_PROGRAM_CODE = "SHUKRAN";
	public static final String SHUKRAN_SOURCE_APPLICATION = "SHUKRANDIGITAL";
	public static final String TOKEN = "Token";
	public static final String  TIER_NOT_UPGRADED= "Tier not upgraded";
	public static final String  MAINTAIN= "maintain";
	public static final String  UNLOCK= "unlock";
	public static final String  UPGRADE= "UPGRADE";
	public static final String  CLASSIC= "CLASSIC";
	public static final String  PLATINUM= "PLATINUM";
    private static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    public static String getAddressMapper(String countryCode) {
    	LOGGER.info("address mapper string:"+addressMapper.get(countryCode));
        return addressMapper.get(countryCode);

    }

    public static void setAddressMapper(String countryCode, String value) {
    	LOGGER.info("Set AddressMapper:"+addressMapper.get(value));
        addressMapper.put(countryCode, value);
    }
    
    public static GetOrderConsulValues orderCredentials = null;
    
    public static LoginCredentials loginCredentials = null;
    
    public static void setOrderCredentials(String stringObject) {

		try {
			orderCredentials = JSON_MAPPER.readValue(stringObject, GetOrderConsulValues.class);
			LOGGER.info("Customer-service : order credentials consul:"+JSON_MAPPER.writeValueAsString(orderCredentials));
		} catch (JsonProcessingException e) {

			LOGGER.error("exception during consul order credentials parse:"+e.getMessage());
		}
	}
    
    public static void setLoginCredentials(String stringObject) {

		try {
			loginCredentials = JSON_MAPPER.readValue(stringObject, LoginCredentials.class);
			LOGGER.info("Customer-service : order credentials consul:"+JSON_MAPPER.writeValueAsString(loginCredentials));
		} catch (JsonProcessingException e) {

			LOGGER.error("exception during consul login credentials parse:"+e.getMessage());
		}
	}
    
	public static final String CHARACTERFILETR = "[^\\p{L}\\p{M}\\p{N}\\p{P}\\p{Z}\\p{Cf}\\p{Cs}\\p{C}\\s]";

	public static final String AUTH_BEARER_HEADER = "authorization-token";
	
	public static final String MOBILE_NUMBER_VALIDATION_REGEX=  "^(\\+\\d{1,4}?)(\\s)(\\d{1,10})";

	public static final String WHATSAPP_EMAIL_REGEX = "^w_(\\d)+\\@stylishop\\.com$";
	
	public static final String BEAUTYLRGTXT_ATTR = "beautyLargeTextAttributes";
	public static final String LRGTXT_ATTR = "allLargeTextAttributes";
	public static final String GOOGLE_MAP_COUNTRY_CODE = "google_maps_country_codes";
	
	public static final String LANGUAGE_EN = "&language=en";
	public static final String LANGUAGE_AR = "&language=ar";
	public static final String X_HEADER_TOKEN = "x-header-token";
	public static final String USER_AGENT = "user-agent";
	
	public static final String WHATSAPPLOGIN = "WHATSAPPLOGIN";
	public static final String APPLELOGIN = "APPLELOGIN";
	public static final String GOOGLELOGIN = "GOOGLELOGIN";
	public static final String MOBILE = "MOBILE";
	public static final String EMAIL = "EMAIL";

	

	/**
	 *Error code and Status code constants 
	 */
	public static final String ERROR_MSG = "ERROR";
	public static final String STATUS_500 = "500";
	public static final String MISSING_TOKEN_ERR_MSG = "authorization-token missing or wrong!";
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
	public static final String PROFILE_ID = "ProfileId";
	public static final String CARD_NUMBER = "CardNumber";
	public static final String JSON_EXTERNAL_DATA = "JsonExternalData";
	public static final String STORE_NOT_FOUND_MESSAGE = "Store not found!";
	public static final String FAILED_TO_SAVE_OTP = "Failed to save otp!";
	public static final String INVALID_REQUEST_AR = "طلب غير صالح!";
	public static final String INTERNAL_SERVER_ERROR_EN = "Something went wrong";
	public static final String INTERNAL_SERVER_ERROR_AR = "حدث خطأ ما. يرجى المحاولة لاحقاً.";
	public static final String OTP_EXPIRED_EN = "OTP Expired . Please try again";
	public static final String OTP_EXPIRED_AR = "انتهت صلاحية الرمز. يرجى المحاولة مرة أخرى.";
	public static final String INCORRECT_OTP_EXCEEDED_EN = "Incorrect OTP requested attempts exceeded. Please request for new OTP.";
	public static final String INCORRECT_OTP_EXCEEDED_AR = "تم تجاوز محاولات إدخال الرمز غير الصحيح. يرجى طلب رمز جديد.";
	public static final String INCORRECT_OTP_EN = "Incorrect OTP requested. Please try again";
	public static final String INCORRECT_OTP_AR = "الرمز المدخل غير صحيح. يرجى المحاولة مرة أخرى.";
	public static final String PHONE_ALREADY_VERIFIED_EN = "Phone number is already verified!";
	public static final String PHONE_ALREADY_VERIFIED_AR = "تم التحقق من رقم الهاتف بالفعل!";
	public static final String ERROR_EN = "Error occurred while processing phone verification for mobile number";
	public static final String ERROR_AR = "حدث خطأ أثناء معالجة التحقق من الهاتف لرقم الجوال";
	public static final String REGISTERED_MOBILE_EN = "Mobile Number is Already Registered !!";
	public static final String REGISTERED_MOBILE_AR = "رقم الجوال مسجل بالفعل !!";
	public static final String FAILED_TO_GET_TEMPLATE_EN = "Failed to get message template!";
	public static final String FAILED_TO_GET_TEMPLATE_AR = "فشل في الحصول على قالب الرسالة!";
	public static final String FAILED_TO_SEND_OTP_OVER_SMS_EN = "Failed to send otp over sms!";
	public static final String FAILED_TO_SEND_OTP_OVER_SMS_AR = "فشل في إرسال رمز التحقق عبر الرسائل القصيرة!";
	public static final String PHONE_NUMBER = "PhoneNumber";

	public static final String LOGIN = "login";
	public static final String VALIDATE = "validate";

	 public static String getInventoryBaseUrl() {
	        if (catalogConsulValues != null && catalogConsulValues.containsKey(CONSUL_CATALOG_KEYS)) {
	            try {
	                ObjectMapper mapper = new ObjectMapper();
	                ConsulValues values = mapper.readValue(catalogConsulValues.get(CONSUL_CATALOG_KEYS),
	                        ConsulValues.class);
	                return values.getInventoryBaseUrl();
	            } catch (Exception e) {
	                return "";
	            }
	        }
	        return "";
	    }
	 
	 public static void setCatalogConsulValues(String value) {
	        catalogConsulValues.put(CONSUL_CATALOG_KEYS, value);
	    }
   
	 public static void setJwtToken(Map <String, Object> hashMap) {
         if (MapUtils.isNotEmpty(hashMap)) {
			LOGGER.info("token expire time " + hashMap.get("jwt_token_expire_time_in_minutes"));
         	IS_JWT_TOKEN_ENABLE =(Boolean) hashMap.get("java_create_order_jwt_token_enable");
         	IS_JWT_REFRESH_TOKEN_ENABLE = hashMap.get("refresh_jwt_token_enable") != null ?(Boolean) hashMap.get("refresh_jwt_token_enable"): false;
			 JWT_TOKEN_EXPIRE_TIME_IN_MINUTES= hashMap.get("jwt_token_expire_time_in_minutes")!= null ? (Double) hashMap.get("jwt_token_expire_time_in_minutes") : 30.0;
			 JWT_TOKEN_APP_VERSION= (String) hashMap.get("refresh_jwt_token_app_version");
			 REFRESH_TOKEN_EXPIRE_TIME=hashMap.get("refresh_token_expire_time_in_days")!= null ? (Double) hashMap.get("refresh_token_expire_time_in_days") : 90.0;
			 CUSTOMER_TOKEN_DELAY_IN_MILLISECONDS= hashMap.get("customer_token_delay_in_milliseconds")!= null ? (Double) hashMap.get("customer_token_delay_in_milliseconds") : 500.0;
         	LinkedTreeMap<String,String> addresMEssage =(LinkedTreeMap) hashMap.get("addressValidationMessage");
         	
         	AddressValidatetionMessage validationMessage = new AddressValidatetionMessage();
         	for(Map.Entry<String, String> entrySet : addresMEssage.entrySet()) {
         		
         		if( "area_validation_en".equalsIgnoreCase(entrySet.getKey())){
         			
         			validationMessage.setAreaValidateMsgEn(entrySet.getValue());
         		}else if("area_validation_ar".equalsIgnoreCase(entrySet.getKey())) {
         			
         			validationMessage.setAreaValidateMsgAr(entrySet.getValue());
         		}else if("city-validation_en".equalsIgnoreCase(entrySet.getKey())) {
         			
         			validationMessage.setCityValidateMsgEn(entrySet.getValue());
         			
         		}else if("city-validation_ar".equalsIgnoreCase(entrySet.getKey())) {
         			
         			validationMessage.setCityValidateMsgAr(entrySet.getValue());
         		}else if("region-validation_en".equalsIgnoreCase(entrySet.getKey())) {
         			
         			validationMessage.setRegionValidateMsgEn(entrySet.getValue());
         			
         		}else if("region-validation_ar".equalsIgnoreCase(entrySet.getKey())) {
         			
         			validationMessage.setRegionValidateMsgAr(entrySet.getValue());
         		}
         	}
         	
         	addressValidatetionMessage = validationMessage;
         	LOGGER.info("Set address message validation:"+validationMessage);
         }
 } 
	 
	public static void setFreeShipping(String obj) {
		try {
			freeShipping = JSON_MAPPER.readValue(obj, QuoteFreeShippingConsul.class);
			LOGGER.info("Customer-service : quote consul:" + JSON_MAPPER.writeValueAsString(freeShipping));
		} catch (JsonProcessingException e) {

			LOGGER.error("exception during consul free shiping parse:" + e.getMessage());
		}
	}
	 
	public static AddressValidatetionMessage getAddressvalidation() {
		
		return addressValidatetionMessage;
	}
	
		public static final List<Integer> arabicStores = Collections.unmodifiableList(Arrays.asList(3, 11, 13, 17, 21, 25));
	 
	 
	 	public static final Integer ADMIN_STORE_ID = 0;
	 
		public DateFormat dtFormarmater = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		

		private static List<Stores> storesList = new ArrayList<>();
		
		public static StoreConfigResponse StoreConfigResponse;
		public static BaseConfig baseConfig;


		public static Map<String, String> getAddressMapper() {
			return addressMapper;
		}

		public static void setStoresList(List<Stores> stores) {
			storesList = stores;
		}

		public static List<Stores> getStoresList() {
			return storesList;
		}
		
		public static void setConsulConfigResponse(StoreConfigResponse setStoreConfigResponse) {
			
			StoreConfigResponse = setStoreConfigResponse;
		}
		
		public static StoreConfigResponse getConsulConfigResponse() {
			
			return StoreConfigResponse;
		}


		public static void setConsulBaseConfigResponse(BaseConfig baseConfigResponse) {
			baseConfig = baseConfigResponse;
		}
		
		public static String checkStoreIds(Integer storeId) {
			switch (storeId) {
			case 1:
				return "sa";
			case 3:
				return "sa";
			case 7:
				return "ae";
			case 11:
				return "ae";
			case 12:
				return "kw";
			case 13:
				return "kw";
			case 15:
				return "qa";
			case 17:
				return "qa";
		case 19:
			return "bh";
		case 21:
			return "bh";
		case 23:
			return "om";
		case 25:
			return "om";
		default:
			return "sa";
		}
	}

		public static boolean validateRefershTokenEnable(Map<String, String> requestHeader) {
			boolean isRefreshToken= false;
			if(IS_JWT_REFRESH_TOKEN_ENABLE) {
				if (StringUtils.isNotEmpty(requestHeader.get(Constants.HEADER_X_SOURCE)) && StringUtils.isNotBlank(requestHeader.get(Constants.HEADER_X_SOURCE)) && requestHeader.get(Constants.HEADER_X_SOURCE).equals("msite")) {
					isRefreshToken = true;
				} else if (StringUtils.isNotBlank(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION)) && StringUtils.isNotEmpty(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION))) {
					int version= compareVersions(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION), JWT_TOKEN_APP_VERSION);

					if (version>=0) {
						isRefreshToken = true;
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
					int version= compareVersions(xClientVersion, JWT_TOKEN_APP_VERSION);
					if (version>=0) {
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
			if(org.apache.commons.lang.StringUtils.isNotEmpty(requestHeader.get(Constants.HEADER_X_SOURCE)) && org.apache.commons.lang.StringUtils.isNotBlank(requestHeader.get(Constants.HEADER_X_SOURCE)) && requestHeader.get(Constants.HEADER_X_SOURCE).equals("msite")){
				isRefreshToken = true;
			}else{
				if(org.apache.commons.lang.StringUtils.isNotBlank(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION)) && org.apache.commons.lang.StringUtils.isNotEmpty(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION))) {
					int version= compareVersions(requestHeader.get(Constants.HEADER_X_CLIENT_VERSION), JWT_TOKEN_APP_VERSION);
					if (version>=0) {
						isRefreshToken = true;
					}
				}
			}
		}
		return isRefreshToken;
		}


	public static int compareVersions(String version1, String version2) {
		String[] version1Parts = version1.split("\\.");
		String[] version2Parts = version2.split("\\.");

		int value= 0;
		int length = Math.max(version1Parts.length, version2Parts.length);

		StringBuilder finalValue1= new StringBuilder();
		StringBuilder finalValue2=new StringBuilder();;
		for (int i = 0; i < length; i++) {
			if(StringUtils.isBlank(version1Parts[i]) || StringUtils.isEmpty(version1Parts[i])){
				version1Parts[i] = "0000";
			}
			if(StringUtils.isBlank(version2Parts[i]) || StringUtils.isEmpty(version2Parts[i])){
				version2Parts[i] = "0000";
			}
			String value1= version1Parts[i];
			String value2= version2Parts[i];
			if(version1Parts[i].length() > version2Parts[i].length()){
				value2= StringUtils.repeat("0", (version1Parts[i].length() - version2Parts[i].length())) + version2Parts[i];
			}else if(version1Parts[i].length() < version2Parts[i].length()){
				value1= StringUtils.repeat("0", (version2Parts[i].length() - version1Parts[i].length())) + version1Parts[i];
			}
			finalValue1.append(value1);
			finalValue2.append(value2);
		}
		long value1= Long.parseLong(String.valueOf(finalValue1));
		long value2= Long.parseLong(String.valueOf(finalValue2));
		if(value1> value2){
			value = 1;
		}else if(value1< value2){
			value = -1;
		}
		return value;
	}


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



	public static int jwtTokenExpireTimeInMinutes() {
		return (int) (double) JWT_TOKEN_EXPIRE_TIME_IN_MINUTES;
	}
	public static int refreshTokenExpireTimeInDays() {
		return (int) (double) REFRESH_TOKEN_EXPIRE_TIME;
	}

	/**
	 * Gets store IDs for a given country code
	 * @param countryCode The country code (e.g., "966" for Saudi Arabia)
	 * @return List of store IDs for that country
	 */
	public static List<Integer> getStoreIdsByCountryCode(String countryCode) {
		List<Integer> storeIds = new ArrayList<>();
		
		switch (countryCode) {
		case "966": // Saudi Arabia
			storeIds.add(1);
			storeIds.add(3);
			break;
		case "971": // UAE
			storeIds.add(7);
			storeIds.add(11);
			break;
		case "965": // Kuwait
			storeIds.add(12);
			storeIds.add(13);
			break;
		case "974": // Qatar
			storeIds.add(15);
			storeIds.add(17);
			break;
		case "973": // Bahrain
			storeIds.add(19);
			storeIds.add(21);
			break;
		case "968": // Oman
			storeIds.add(23);
			storeIds.add(25);
			break;
		default:
			// Return null for unknown country codes
			return null;
		}
		
		return storeIds;
	}
}
