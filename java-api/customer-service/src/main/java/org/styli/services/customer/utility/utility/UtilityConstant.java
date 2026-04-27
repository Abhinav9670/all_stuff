package org.styli.services.customer.utility.utility;

import java.util.Collections;
import java.util.LinkedHashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.styli.services.customer.utility.Constants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class UtilityConstant {

	public static final String SUBCATEGORIES_DELIMETER = " /// ";
	private static final ObjectMapper mapper = new ObjectMapper();
	private static final Log LOGGER = LogFactory.getLog(UtilityConstant.class);

	public static HttpHeaders basicHeaders() {
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
		return requestHeaders;
	}

	public static String convertAdrsMprCode(String inputCode) {
		String result = inputCode;
		try {
			if(StringUtils.isNotBlank(inputCode)) {
				result = inputCode.toUpperCase();
			}
		} catch (Exception e) {
			result = inputCode;
		}
		return  result;
	}

	public static String responseMap(Object status, Object statusCode, Object statusMsg, Object response) {
		try {
			LinkedHashMap<String, Object> map = new LinkedHashMap<>();
			if(ObjectUtils.isNotEmpty(status)) map.put("status", status);
			if(ObjectUtils.isNotEmpty(statusCode)) map.put("statusCode", statusCode);
			if(ObjectUtils.isNotEmpty(statusMsg)) map.put("statusMsg", statusMsg);
			if(ObjectUtils.isNotEmpty(response)) map.put("response", response);
			return mapper.writeValueAsString(map);
		} catch (JsonProcessingException jpe) {
			LOGGER.error("Error converting map to string for address mapper push" + jpe.getMessage());
			return null;
		}
	}

}
