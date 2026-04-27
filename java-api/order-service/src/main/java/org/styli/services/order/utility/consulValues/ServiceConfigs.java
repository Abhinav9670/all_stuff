package org.styli.services.order.utility.consulValues;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.styli.services.order.utility.Constants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * @author Biswabhusan Pradhan <biswabhusan.pradhan@landmarkgroup.com>
 */
public class ServiceConfigs {
  private static final Log LOGGER = LogFactory.getLog(ServiceConfigs.class);

  public static final ObjectMapper mapper = new ObjectMapper();

  public static final String CONSUL_CUSTOMER_SERVICE_KEY =
      "java/customer-service/customerConsulKeys";

  private static final ArrayList<ServiceConfigsListener> listeners = new ArrayList<>();
  public static Map<String, Object> consulServiceMap = new LinkedHashMap<>();

  public static Map<String, Object> getConsulServiceMap() {
    return consulServiceMap;
  }

  public static void setConsulServiceMap(Map<String, Object> consulServiceMap) {
    ServiceConfigs.consulServiceMap = consulServiceMap;
    for (ServiceConfigsListener listener : listeners) {
      if (listener != null) {
        try {
          listener.onConfigsUpdated(consulServiceMap);
        } catch (Exception e) {
          LOGGER.error(
              "ServiceConfigsListener error when trying to update "
                  + consulServiceMap.getClass().getSimpleName()
                  + ":\n"
                  + e.toString());
        }
      }
    }
  }


  public static FromEmail getFromEmail() {
    FromEmail result;
    try {
      Object fromEmailObject = ServiceConfigs.consulServiceMap.get("fromEmail");
      String jsonString;
      if (fromEmailObject instanceof String) {
        jsonString = (String) fromEmailObject;
      } else {
        jsonString = Constants.JSON_MAPPER.writeValueAsString(fromEmailObject);
      }
      result = Constants.JSON_MAPPER.readValue(jsonString, FromEmail.class);
    } catch (Exception e) {
      result = FromEmail.of("", new LinkedHashMap<>());
    }
    return result;
  }

  public static String getTestEnvPhoneNo() {
    String result = "";
    try {
      Object object = ServiceConfigs.consulServiceMap.get("testEnvPhoneNo");
      if (object != null) result = object.toString();
    } catch (Exception e) {
      LOGGER.error(e);
    }
    return result;
  }

  public static String getUrl(String key) {
    String result = "";
    if (StringUtils.isEmpty(key)) return result;
    try {
      Object value = getConsulServiceMap().get("urls");
      if (value instanceof Map<?, ?>) {
        Map<?, ?> urls = (Map<?, ?>) value;
        if (urls.get(key) != null) result = urls.get(key).toString();
      }
    } catch (Exception e) {
      result = "";
    }
    return result;
  }

  public interface ServiceConfigsListener {

    void onConfigsUpdated(Map<String, Object> newConfigs);
  }

}
