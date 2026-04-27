package org.styli.services.order.utility.consulValues;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Biswabhusan Pradhan <biswabhusan.pradhan@landmarkgroup.com>
 */
public class MailPatternConfigs {

  private static final Log LOGGER = LogFactory.getLog(MailPatternConfigs.class);

  public static String CONSUL_MAIL_PATTERN_KEY = "java/customer-service/mailPatternKey";

  private static final ArrayList<MailPatternListener> listeners = new ArrayList<>();
  private static Map<String, Object> consulMailPatternMap = new LinkedHashMap<>();

  public static Map<String, Object> getConsulMailPatternMap() {
    return consulMailPatternMap;
  }

  public static void setConsulMailPatternMap(Map<String, Object> consulMailPatternMap) {
    MailPatternConfigs.consulMailPatternMap = consulMailPatternMap;
    for (MailPatternListener listener : listeners) {
      if (listener != null) {
        try {
          listener.onPatternsUpdated(consulMailPatternMap);
        } catch (Exception e) {
          LOGGER.error(
              "ServiceConfigsListener error when trying to update "
                  + consulMailPatternMap.getClass().getSimpleName()
                  + ":\n"
                  + e.toString());
        }
      }
    }
  }

  public static void addMailPatternListener(MailPatternListener listener) {
    if (listener != null && !listeners.contains(listener)) {
      listeners.add(listener);
    }
  }

  public static void removeMailPatternListener(MailPatternListener listener) {
    if (listener != null && listeners.contains(listener)) {
      listeners.remove(listener);
    }
  }

  public static Map<String, String> getMailPatternMap(String key) {
    Map<String, String> result = new LinkedHashMap();
    if (StringUtils.isNotEmpty(key)) {
      Object value = MailPatternConfigs.getConsulMailPatternMap().get(key);
      if (value instanceof Map<?, ?> && MapUtils.isNotEmpty(((Map<?, ?>) value))) {
        for (Map.Entry entry : ((Map<?, ?>) value).entrySet()) {
          if (entry.getKey() != null && entry.getValue() != null)
            result.put(entry.getKey().toString(), entry.getValue().toString());
        }
      }
    }
    return result;
  }


  public interface MailPatternListener {

    void onPatternsUpdated(Map<String, Object> newConfigs);
  }

}
