package org.styli.services.customer.helper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


/**
 * Created on 06-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Component
@Scope("singleton")
public class SmsHelper {

	private static final Log LOGGER = LogFactory.getLog(SmsHelper.class);
	private static final SecureRandom random = new SecureRandom();
	private final ObjectMapper mapper = new ObjectMapper();
	private static final String AUTH_TOKEN = "89c239047aa51ca833711cb9afe3185e";
	private static final String FROM_NUMBER = "whatsapp:+15557313973";
	private static final String MESSAGING_SERVICE_SID = "MG8fefe06c1e5a1c1e7b63f6f34a3837c0";
	private static final String CONTENT_SID = "HXef6d8ecd31485da192779c5cb09732d1";
	private static final String ACCOUNT_SID = "";


	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Value("${kaleyra.url}")
	private String kaleyraUrl;

	@Value("${env}")
	private String env;

	@Value("${freshchat_token}")
	private String freshchatToken;


	private String getApiUrl() {
		return "https://api.twilio.com/2010-04-01/Accounts/" + ACCOUNT_SID + "/Messages.json";
	}

	@PostConstruct
	public void init() {
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	public boolean sendSMS(String mobileNo, String message, int unicode, Boolean resendCall) {
		return sendSMSCommon(mobileNo, message, unicode, resendCall, false);
	}

	public boolean sendSMSIN(String mobileNo, String message, int unicode, Boolean resendCall) {
		return sendSMSCommon(mobileNo, message, unicode, resendCall, true);
	}

	private boolean sendSMSCommon(String mobileNo, String message, int unicode, Boolean resendCall, boolean isIndiaFormat) {
		boolean success = false;

		if (StringUtils.isEmpty(mobileNo) || StringUtils.isEmpty(message) || unicode > 1 || unicode < 0)
			return false;

		try {
			// Identify country by prefix
			String countryCode = getCountryCode(mobileNo, isIndiaFormat);
			if (countryCode == null) return false;

			// Get provider configuration
			ProviderConfig providerConfig = getProviderConfig(countryCode);

			// ================= Kaleyra SMS =================
			if (providerConfig.useKaleyra) {
				success = sendKaleyraMessage(mobileNo, message, unicode, isIndiaFormat);
			}
			// ================= RouteMobile SMS =================
			else if (providerConfig.useRouteMobile) {
				success = sendRouteMobileMessage(mobileNo, message, unicode);
			}
			// ================= WhatsApp =================
			if (Boolean.TRUE.equals(resendCall) && providerConfig.canSendWhatsApp) {
				sendWhatsAppMessage(mobileNo, message);
			}

		} catch (Exception e) {
			LOGGER.error("sendSMS error", e);
		}

		return success;
	}

	private String getCountryCode(String mobileNo, boolean isIndiaFormat) {
		if (mobileNo.startsWith("971")) return "AE";
		if (mobileNo.startsWith("966")) return "SA";
		if (mobileNo.startsWith("973")) return "BH";
		if (mobileNo.startsWith("974")) return "QA";
		if (mobileNo.startsWith("965")) return "KW";
		if (mobileNo.startsWith("968")) return "OM";
		if (isIndiaFormat && mobileNo.startsWith("91")) return "IN";
		return null;
	}

	/**
	 * Public method to check if WhatsApp is enabled for a given phone number
	 * @param mobileNo phone number
	 * @return true if WhatsApp can be sent for this phone number
	 */
	public boolean canSendWhatsAppForPhone(String mobileNo) {
		if (StringUtils.isEmpty(mobileNo)) {
			return false;
		}
		
		// Clean the phone number (remove non-digits)
		String cleanPhone = mobileNo.replaceAll("[^0-9]", "");
		
		// Determine country code (try both India format and regular format)
		String countryCode = getCountryCode(cleanPhone, false);
		if (countryCode == null) {
			countryCode = getCountryCode(cleanPhone, true);
		}
		
		if (countryCode == null) {
			return false;
		}
		
		// Get provider config and check WhatsApp flag
		ProviderConfig providerConfig = getProviderConfig(countryCode);
		return providerConfig.canSendWhatsApp;
	}

	private ProviderConfig getProviderConfig(String countryCode) {
		ProviderConfig config = new ProviderConfig();

		switch (countryCode) {
			case "AE":
				config.useKaleyra = ServiceConfigs.useKaleyraForAE();
				config.useRouteMobile = ServiceConfigs.useRouteMobileForAE();
				config.canSendWhatsApp = ServiceConfigs.getWhatsAppFlagForAE();
				break;
			case "SA":
				config.useKaleyra = ServiceConfigs.useKaleyraForSA();
				config.useRouteMobile = ServiceConfigs.useRouteMobileForSA();
				config.canSendWhatsApp = ServiceConfigs.getWhatsAppFlagForSA();
				break;
			case "BH":
				config.useKaleyra = ServiceConfigs.useKaleyraForBH();
				config.useRouteMobile = ServiceConfigs.useRouteMobileForBH();
				config.canSendWhatsApp = ServiceConfigs.getWhatsAppFlagForBH();
				break;
			case "KW":
				config.useKaleyra = ServiceConfigs.useKaleyraForKW();
				config.useRouteMobile = ServiceConfigs.useRouteMobileForKW();
				config.canSendWhatsApp = ServiceConfigs.getWhatsAppFlagForKW();
				break;
			case "QA":
				config.useKaleyra = ServiceConfigs.useKaleyraForQA();
				config.useRouteMobile = ServiceConfigs.useRouteMobileForQA();
				config.canSendWhatsApp = ServiceConfigs.getWhatsAppFlagForQA();
				break;
			case "OM":
				config.useKaleyra = ServiceConfigs.useKaleyraForOM();
				config.useRouteMobile = ServiceConfigs.useRouteMobileForOM();
				config.canSendWhatsApp = ServiceConfigs.getWhatsAppFlagForOM();
				break;
			case "IN":
				config.useKaleyra = ServiceConfigs.useKaleyraForIN();
				config.useRouteMobile = ServiceConfigs.useRouteMobileForIN();
				config.canSendWhatsApp = ServiceConfigs.getWhatsAppFlagForIN();
				break;
			default:
				LOGGER.warn("Unsupported country code: " + countryCode + ". Using default provider configuration.");
				break;
		}

		return config;
	}

	private static class ProviderConfig {
		boolean useKaleyra = false;
		boolean useRouteMobile = false;
		boolean canSendWhatsApp = false;
	}

	private boolean sendKaleyraMessage(String mobileNo, String message, int unicode, boolean isIndiaFormat) {
		try {
			Map<String, Object> parameters = buildKaleyraParameters(mobileNo, message, unicode, isIndiaFormat);
			OkHttpClient client = new OkHttpClient().newBuilder().build();

			if (isIndiaFormat) {
				return sendIndiaFormatRequest(client, parameters);
			} else {
				return sendStandardFormatRequest(client, parameters);
			}
		} catch (Exception e) {
			LOGGER.error("sendKaleyraMessage error", e);
		}
		return false;
	}

	private Map<String, Object> buildKaleyraParameters(String mobileNo, String message, int unicode, boolean isIndiaFormat) {
		Map<String, Object> parameters = new HashMap<>();

		if (isIndiaFormat) {
			// India format parameters
			parameters.put("sender", ServiceConfigs.getKaleyraSenderId());
			parameters.put("source", "API");
			parameters.put("type", "OTP");
			parameters.put("template_id", ServiceConfigs.getKaleyraOtpTemplateId());
			parameters.put("body", message);
		} else {
			// Standard format parameters
			try {
				parameters.put("payload", URLEncoder.encode(message, StandardCharsets.UTF_8.toString()));
			} catch (UnsupportedEncodingException e) {
				LOGGER.error("Error encoding message for Kaleyra", e);
				parameters.put("payload", message); // Fallback to unencoded message
			}
			parameters.put("unicode", unicode);
		}

		// Set phone number based on environment
		String phoneNumber = getPhoneNumberForEnvironment(mobileNo);
		if (isIndiaFormat) {
			parameters.put("to", phoneNumber);
		} else {
			parameters.put("telephone", phoneNumber);
		}

		return parameters;
	}

	private String getPhoneNumberForEnvironment(String mobileNo) {
		if ("live".equals(env)) {
			return mobileNo;
		} else {
			return StringUtils.isBlank(ServiceConfigs.getTestEnvPhoneNo())
					? mobileNo
					: getPlainPhoneNo(ServiceConfigs.getTestEnvPhoneNo());
		}
	}

	private boolean sendIndiaFormatRequest(OkHttpClient client, Map<String, Object> parameters) throws Exception {
		MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
		String formBody = Joiner.on("&").withKeyValueSeparator("=").join(parameters);
		LOGGER.info("Kaleyra Message: " + formBody);
		RequestBody body = RequestBody.create(mediaType, formBody);
		Request request = new Request.Builder()
				.url(kaleyraUrl)
				.method("POST", body)
				.addHeader("api-key", ServiceConfigs.getKaleyraKey())
				.addHeader("Content-Type", "application/x-www-form-urlencoded")
				.build();
		Response response = client.newCall(request).execute();
		if (response.code() == HttpStatus.ACCEPTED.value() && response.body() != null) {
			String responseBody = response.body().string();
			LOGGER.info("kaleyraResponse: " + responseBody);
			return true;
		}
		return false;
	}

	private boolean sendStandardFormatRequest(OkHttpClient client, Map<String, Object> parameters) throws Exception {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(kaleyraUrl);
		LOGGER.info("Kaleyra request URL: " + builder.buildAndExpand(parameters).toUri());

		RequestBody body = RequestBody.create(
				okhttp3.MediaType.parse("application/json; charset=utf-8"), "{}");
		Request httpRequest = new Request.Builder()
				.url(builder.buildAndExpand(parameters).toUriString())
				.method("POST", body)
				.build();
		Response response = client.newCall(httpRequest).execute();

		if (response.code() == HttpStatus.OK.value() && response.body() != null) {
			String responseBody = response.body().string();
			LOGGER.info("kaleyraResponse: " + responseBody);
			LinkedHashMap map = mapper.readValue(responseBody, LinkedHashMap.class);
			if (MapUtils.isNotEmpty(map) && "OK".equals(map.get("status"))) return true;
		}
		return false;
	}

	public static String getPlainPhoneNo(String phoneNo) {
		String result = phoneNo;
		if(StringUtils.isNotEmpty(phoneNo)) {
			result = phoneNo.trim()
					.replace(" ", "")
					.replace("-", "")
					.replace("+", "");
		}
		return result;
	}

	public static String generateOtp(int length) {
		return generateOtp(length, null);
	}

	public static String generateOtp(int length, List<String> ignoreList) {
		String result = "";
		if (CollectionUtils.isNotEmpty(ignoreList)) {
			while (StringUtils.isEmpty(result) || ignoreList.contains(result)) {
				result = generateOtpForLength(length);
			}
		} else {
			result = generateOtpForLength(length);
		}
		return result;
	}

	private static String generateOtpForLength(int length) {
		String result = "";
		try {
			for (int i = 0; i < length; i++) {
				int a = 0;
				if (i == 0) {
					a = random.nextInt(9) + 1;
				} else {
					a = random.nextInt(10);
				}
				result = result + String.valueOf(a);
			}
		} catch (Exception e) {
			result = "";
		}
		return result;
	}

	public boolean sendWhatsAppMessage(String mobileNo, String message) {

		if (StringUtils.isEmpty(freshchatToken)) {
			LOGGER.error("sendWhatsAppMessage: Freshchat token is missing. Aborting request.");
			return false;
		}
		String template = ServiceConfigs.getWhatsappTemplateName();
		if (StringUtils.isEmpty(template)) {
			LOGGER.error("sendWhatsAppMessage: template is missing. Aborting request.");
			return false;
		}
		LOGGER.info("sendWhatsAppMessage: Initiating WhatsApp message sending process.");

		if (StringUtils.isEmpty(mobileNo) || StringUtils.isEmpty(message)) {
			LOGGER.warn("sendWhatsAppMessage: Mobile number or message is empty. Aborting.");
			return false;
		}

		try {
			LOGGER.info("sendWhatsAppMessage: Extracting OTP from message.");
			Pattern otpPattern = Pattern.compile("\\b\\d{4,6}\\b");
			Matcher matcher = otpPattern.matcher(message);
			String otp = matcher.find() ? matcher.group() : "N/A";
			LOGGER.info("sendWhatsAppMessage: Extracted OTP - {}" +otp);
			LOGGER.info("sendWhatsAppMessage: template used - {}" +template);

			LOGGER.info("sendWhatsAppMessage: Preparing request payload.");
			String formattedMobileNo = mobileNo.startsWith("+") ? mobileNo : "+" + mobileNo;
			String requestBody = String.format("{ \"from\": { \"phone_number\": \"%s\" }, \"to\": [ { \"phone_number\": \"%s\" } ], \"data\": { \"message_template\": { \"storage\": \"conversation\", \"namespace\": \"%s\", \"template_name\": \"%s\", \"language\": { \"policy\": \"deterministic\", \"code\": \"en\" }, \"rich_template_data\": { \"body\": { \"params\": [ { \"data\": \"%s\" } ] }, \"button\": { \"subType\": \"url\", \"params\": [ { \"data\": \"%s\" } ] } } } } }",
					"+966115208333", formattedMobileNo, "5aad76a7_0ce5_40fa_a977_1266c9aaa6e5", template, otp, otp);

			LOGGER.info("sendWhatsAppMessage: Initializing HTTP client.");
			OkHttpClient client = new OkHttpClient();

			LOGGER.info("sendWhatsAppMessage: Building HTTP request.");

			RequestBody requestBodyObj = RequestBody.create(MediaType.get("application/json"), requestBody);

			Request request = new Request.Builder()
					.url("https://api.freshchat.com/v2/outbound-messages/whatsapp")
					.post(requestBodyObj)
					.addHeader("Authorization", "Bearer " + freshchatToken)
					.addHeader("Content-Type", "application/json")
					.build();


			LOGGER.info("sendWhatsAppMessage: Sending request to Freshchat API.");
			Response response = client.newCall(request).execute();

			LOGGER.info("sendWhatsAppMessage: Received response with status code {}" +response.code());

			if (response.isSuccessful() && response.body() != null) {
				String responseBody = response.body().string();
				LOGGER.info("sendWhatsAppMessage: Freshchat API Response for phone number " + mobileNo + " - " + responseBody);
				return true;
			} else {
				LOGGER.info("sendWhatsAppMessage: Failed to send WhatsApp message to " + mobileNo + ". Response code: " + response.code());
				if (response.body() != null) {
					LOGGER.info("sendWhatsAppMessage: Response Body for " + mobileNo + " - " + response.body().string());
				}
			}
		} catch (Exception e) {
			LOGGER.info("sendWhatsAppMessage: Error sending WhatsApp message to " + mobileNo + ". Exception: " + e.getMessage());
		}
		LOGGER.warn("sendWhatsAppMessage: Message sending failed.");
		return false;
	}

	public boolean sendRouteMobileMessage(String mobileNo, String message,int unicode) {

		String source = ServiceConfigs.getSourceForRouteMobileSMS();
		if (StringUtils.isEmpty(mobileNo) || StringUtils.isEmpty(message) || StringUtils.isEmpty(source) ) {
			LOGGER.info("sendRouteMobileMessage: Mobile number or message or source is empty. Aborting.");
			return false;
		}

		try {
			LOGGER.info("sendRouteMobileMessage: Preparing request URL.");
			String encodedMessage;
			if (unicode == 1) {
				encodedMessage = message;
			} else {
				encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.toString());
			}
			String apiUrl = String.format(
					"http://34.147.145.111:8081/bulksms/personalizedbulksms?username=StyliOTP&password=StyLmg1$&source=%s&destination=%s&message=%s",
					source, mobileNo, encodedMessage);

			LOGGER.info("sendRouteMobileMessage: Final API URL - {}" +apiUrl);

			OkHttpClient client = new OkHttpClient();
			Request request = new Request.Builder()
					.url(apiUrl)
					.get()
					.build();

			LOGGER.info("sendRouteMobileMessage: Sending request to RML Connect API.");
			Response response = client.newCall(request).execute();

			LOGGER.info("sendRouteMobileMessage: Received response with status code {}" +response.code());

			if (response.isSuccessful() && response.body() != null) {
				String responseBody = response.body().string();
				LOGGER.info("sendRouteMobileMessage: RML Connect API Response for phone number {} - {}" +mobileNo + "response : " +responseBody);
				return true;
			} else {
				LOGGER.info("sendRouteMobileMessage: Failed to send SMS to {}. Response code: {}" +mobileNo + "response : " +response.code());
				if (response.body() != null) {
					LOGGER.info("sendRouteMobileMessage: Response Body for {} - {}" +mobileNo+ "response : " + response.body().string());
				}
			}
		} catch (Exception e) {
			LOGGER.info("sendRouteMobileMessage: Error sending SMS to {}. Exception: {}" +mobileNo + "error : " +e.getMessage());
		}

		LOGGER.info("sendRouteMobileMessage: Message sending failed.");
		return false;
	}


}
