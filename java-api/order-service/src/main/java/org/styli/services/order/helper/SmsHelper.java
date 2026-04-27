package org.styli.services.order.helper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import okhttp3.*;
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
import org.styli.services.order.utility.consulValues.ServiceConfigs;

import javax.annotation.PostConstruct;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Biswabhusan Pradhan <biswabhusan.pradhan@landmarkgroup.com>
 */
@Component
@Scope("singleton")
public class SmsHelper {

  private static final Log LOGGER = LogFactory.getLog(SmsHelper.class);
  private static final SecureRandom random = new SecureRandom();
  private final ObjectMapper mapper = new ObjectMapper();
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
  
  
  private String getApiUrl() {
      return "https://api.twilio.com/2010-04-01/Accounts/" + ACCOUNT_SID + "/Messages.json";
  }

  @PostConstruct
  public void init() {
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public boolean sendSMS(String mobileNo, String message, int unicode) {
    boolean success = false;
    if (StringUtils.isEmpty(mobileNo) || StringUtils.isEmpty(message) || unicode > 1 || unicode < 0)
      return false;
    try {

      Map<String, Object> parameters = new HashMap<>();
      parameters.put("payload", URLEncoder.encode(message, StandardCharsets.UTF_8.toString()));
      parameters.put("unicode", unicode);
      if ("live".equals(env)) parameters.put("telephone", mobileNo);
      else parameters.put("telephone", (StringUtils.isBlank(ServiceConfigs.getTestEnvPhoneNo()))
              ? mobileNo
              : getPlainPhoneNo(ServiceConfigs.getTestEnvPhoneNo()) );

      UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(kaleyraUrl);
      LOGGER.info(builder.buildAndExpand(parameters).toUri());

      OkHttpClient client = new OkHttpClient().newBuilder().build();
      MediaType jsonMediaType = MediaType.parse("application/json; charset=utf-8");
      RequestBody body = RequestBody.create(jsonMediaType, "{}");
      Request httpRequest =
          new Request.Builder()
              .url(builder.buildAndExpand(parameters).toUriString())
              .method("POST", body)
              .build();
      Response response = client.newCall(httpRequest).execute();
      if (response.code() == HttpStatus.OK.value() && response.body() != null) {
        String responseBody = response.body().string();
        LOGGER.info("kaleyraResponse: " + responseBody);
        LinkedHashMap map = mapper.readValue(responseBody, LinkedHashMap.class);
        if (MapUtils.isNotEmpty(map) && "OK".equals(map.get("status"))) success = true;
      }
//		if (ServiceConfigs.getWhatsAppFlag()) {
//			sendWhatsAppMessage(mobileNo, message);
//		}
    } catch (Exception e) {
      LOGGER.error(e);
    }
    return success;
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





}
