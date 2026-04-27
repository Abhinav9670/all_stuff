package org.styli.services.customer.redis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

/**
 * Created on 30-Jun-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Getter
public enum TtlMode {

  OTP_REDIS(24, TimeUnit.HOURS),
  OTP_VALID(10, TimeUnit.MINUTES),
  RESET_PASSWORD(15, TimeUnit.MINUTES),
  WHATSAPP_SIGNUP(15, TimeUnit.MINUTES),
  LIMITER(300, TimeUnit.SECONDS),
  IP_BLOCKER(15, TimeUnit.MINUTES),
  CAPCHA( 5, TimeUnit.MINUTES),
  CITY_SEARCH(0, TimeUnit.MINUTES),
  SHUKRAN_REDIS(1, TimeUnit.HOURS);

  private static final ObjectMapper mapper = new ObjectMapper();

  private long value;
  private TimeUnit timeUnit;

  TtlMode( long value, TimeUnit timeUnit) {
    this.value = value;
    this.timeUnit = timeUnit;
  }

  public static void updateValues(Object inputJson) {
    Map map = null;
    if(inputJson instanceof String) {
      try{
        map = mapper.readValue(((String) inputJson), LinkedHashMap.class);
      } catch (Exception e) {
        map = null;
      }
    } else if(inputJson instanceof Map) {
      map = (Map)inputJson;
    }

    if(map != null) {
      List<Map.Entry> entries = new ArrayList<>(map.entrySet());

      for (Map.Entry entry : entries) {
        if(entry != null && entry.getKey() instanceof String) {
          final String key = ((String)entry.getKey()).trim().toUpperCase();
          updateEachValue(decode(key), entry);
        }
      }
    }
  }

  private static void updateEachValue(TtlMode ttlMode, Map.Entry entry) {
    if(ttlMode != null && entry.getValue() instanceof Map) {
      Map elementMap = (Map) entry.getValue();
      Object valueObject = elementMap.get("value");
      Number valueNumber = null;
      if(valueObject instanceof Number) {
        valueNumber = (Number) valueObject;
      }
      Object unitObject = elementMap.get("unit");
      TimeUnit unitEnum = null;
      if(unitObject instanceof String) {
        try {
          unitEnum = TimeUnit.valueOf(unitObject.toString().trim().toUpperCase());
        } catch (Exception e) {
          unitEnum = null;
        }
      }

      if(valueNumber!= null && unitEnum!=null) {
        ttlMode.value = valueNumber.longValue();
        ttlMode.timeUnit = unitEnum;
      }
    }
  }

  @JsonCreator
  public static TtlMode decode(final Object code) {
    if(code instanceof String) {
      String codeString = ((String) code).trim();
      return Stream.of(TtlMode.values())
              .filter(targetEnum -> targetEnum.name().equals(codeString))
              .findFirst()
              .orElse(null);
    }
    return null;
  }

  @JsonValue
  @Override
  public String toString() {
    if (StringUtils.isNotEmpty(name())) {
      return name();
    }
    return super.toString();
  }
}