package org.styli.services.customer.utility;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.styli.services.customer.helper.SmsHelper;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.otp.OtpBucketObject;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import java.util.Map;
import java.util.UUID;

public class CommonUtility {

	private static final Log LOGGER = LogFactory.getLog(CommonUtility.class);
	private static final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

	public static boolean isValidEmailAddress(String email) {

		LOGGER.info("email id for validation:" + email);
		
		String ePattern = "^([a-zA-Z0-9_\\-\\.]+)@(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)+[a-zA-Z0-9]{2}(?:[a-zA-Z0-9-]*[a-zA-Z0-9])?$";
		java.util.regex.Pattern p = java.util.regex.Pattern.compile(ePattern);
		java.util.regex.Matcher m = p.matcher(email);

		LOGGER.info("Is email valid::" + m.matches());
		return m.matches();
	}

	public static UUID getUuid() {
		return UUID.randomUUID();
	}

	/**
	 * -------------- Valid Email IDs --------------- my@gmail.com my@my.co.ek
	 * my-100@gmail.com my.100@my.com my@1.com my-100@gmail.com my..@2000.com
	 * my.@gmail.com my@gmail.com.in --------------- InValid Email IDs
	 * --------------- my+100@gmail.com my@.com.my my123@.com my@my@gmail.com
	 * تيهي@ابعهب.ىنل
	 * 
	 **/

	public static String getLanguageCode(Stores store) {
		return getLanguageCode(store, "");
	}

	public static String getLanguageCode(Stores store, String fallback) {
		String result=fallback;
		try{
			if(store!=null && StringUtils.isNotEmpty(store.getStoreLanguage())) {
				result = store.getStoreLanguage().split("_")[0];
			}
		} catch (Exception e) {
			result=fallback;
		}
		return result;
	}
	
	public static String getLoggerMsg(Object object) {
		String loggerMsg = null;
		if(object == null)
			loggerMsg = "null";
		try{
			loggerMsg = Constants.JSON_MAPPER.writeValueAsString(object);
		} catch (Exception e) {
			if(object != null)
				loggerMsg =  object.toString();
		}
		return loggerMsg;
	}


	public static Map<?, ?> getMapFromMap(Map<?, ?> parent, Object key) {
		Map<?, ?> child = null;
		if (MapUtils.isNotEmpty(parent) && parent.get(key) instanceof Map<?, ?>) {
			child = (Map<?, ?>) parent.get(key);
		}
		return child;
	}

	public static boolean isPossibleNumber(String phoneNo, Stores store) {
		if (StringUtils.isEmpty(phoneNo)) return false;
		String phoneNoToCheck = (!phoneNo.trim().startsWith("+")) ? "+" + phoneNo.trim() : phoneNo;
		Phonenumber.PhoneNumber phone;
		if (store == null || StringUtils.isEmpty(store.getWebsiteCode())) {
			try {
				phone =
						phoneNumberUtil.parse(
								phoneNoToCheck, Phonenumber.PhoneNumber.CountryCodeSource.UNSPECIFIED.name());
			} catch (Exception e) {
				return false;
			}
		} else {
			try {
				phone = phoneNumberUtil.parse(phoneNoToCheck, store.getWebsiteCode().toUpperCase());
			} catch (Exception e) {
				return false;
			}
		}
		return phoneNumberUtil.isPossibleNumber(phone);
	}

}
