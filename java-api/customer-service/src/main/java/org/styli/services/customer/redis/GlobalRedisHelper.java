package org.styli.services.customer.redis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.redisson.config.BaseConfig;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;

/**
 * Global redis helper
 */
@Component
@Scope("singleton")
public class GlobalRedisHelper implements ServiceConfigs.ServiceConfigsListener {

  private static final Log LOGGER = LogFactory.getLog(GlobalRedisHelper.class);
  private static final ObjectMapper mapper = Constants.JSON_MAPPER;

  private RedissonClient globalRedissonClient;

  @Value("${global.redis.host}")
  private String globalRedisHost;

  @Value("${global.redis.port}")
  private String globalRedisPort;

  @Value("${global.redis.auth}")
  private String globalRedisAuth;

  @Value("${env}")
  private String env;

  @PostConstruct
  public void init() {
    if (StringUtils.isEmpty(globalRedisHost) || StringUtils.isEmpty(globalRedisPort)) {
      LOGGER.error("Redis host or port configuration is missing. Please check application properties.");
      return;
    }

    LOGGER.info("Initializing Redisson client with host: "+ globalRedisHost+" , port: " +globalRedisPort);

    try {
      Config globalRedisConfig = new Config();
      BaseConfig globalBaseConfig =
              globalRedisConfig.useSingleServer()
                      .setAddress("redis://" + globalRedisHost + ":" + globalRedisPort);
      if (StringUtils.isNotEmpty(globalRedisAuth)) {
        globalBaseConfig.setPassword(globalRedisAuth);
      }

      ObjectMapper globalRedisMapper = new ObjectMapper();
      globalRedisMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      globalRedisConfig.setCodec(new TypedJsonJacksonCodec(String.class, globalRedisMapper));

      globalRedissonClient = Redisson.create(globalRedisConfig);
      LOGGER.info("Redisson client initialized successfully.");
    } catch (Exception e) {
      LOGGER.error("Error initializing Redisson client: "+ e.getMessage(), e);
    }
  }

  @PreDestroy
  public void destroy() {
    LOGGER.info("Shutting down Redisson client");
    try {
      if (globalRedissonClient != null) globalRedissonClient.shutdown();
    } catch (Exception e) {
      LOGGER.error("Error shutting down Redisson client", e);
    }
  }

  @Override
  public void onConfigsUpdated(Map<String, Object> newConfigs) {
    if (MapUtils.isNotEmpty(newConfigs)
            && (newConfigs.get("ttl") instanceof Map || newConfigs.get("ttl") instanceof String)) {
      TtlMode.updateValues(newConfigs.get("ttl"));
    }
  }

  /**
   * Store value in Redis with cache name and key
   */
  public boolean put(String cacheName, String key, Object value) {
    return put(cacheName, key, value, null);
  }

  /**
   * Store value with optional TTL
   */
  public boolean put(String cacheName, String key, Object value, TtlMode ttlMode) {
    boolean success = false;
    String envCacheName = getCacheName(cacheName);

    if (validateParameters(envCacheName, key, value)) {
      try {
        RMapCache<String, Object> mapCache = globalRedissonClient.getMapCache(envCacheName);
        if (ttlMode == null) {
          mapCache.put(key, value);
        } else {
          mapCache.put(key, value, ttlMode.getValue(), ttlMode.getTimeUnit());
        }
        success = true;
        logObject("redis.Put." + envCacheName + "[" + key + "]: ", value);
      } catch (Exception e) {
        LOGGER.error("Error putting value in Redis", e);
      }
    }
    return success;
  }

  /**
   * Retrieve value from Redis by cache name and key
   */
  public Object getGloabalRedis(String globalRedisKey) {
    return getGloabalRedis(globalRedisKey, null);
  }

  public Object getGloabalRedis(String globalRedisKey, Class<?> resultClass) {
    Object result = null;

    if (validateGlobalRedisKey(globalRedisKey)) {
      try {
        RBucket<String> bucket = globalRedissonClient.getBucket(globalRedisKey, StringCodec.INSTANCE);
        result = bucket.get();
        if (resultClass != null) {
          result = mapper.readValue(mapper.writeValueAsString(result), resultClass);
        } else if (result != null && !(result instanceof String)) {
          result = mapper.writeValueAsString(result);
        }
        logObject("redis.Get." +globalRedisKey +": ", result);
      } catch (Exception e) {
        LOGGER.error("Error retrieving value from Redis", e);
      }
    }
    return result;
  }

  /**
   * Remove value from Redis by cache name and key
   */
  public Object remove(String cacheName, String key) {
    Object result = null;
    String envCacheName = getCacheName(cacheName);

    if (validateCacheNameAndKey(envCacheName, key)) {
      try {
        result = globalRedissonClient.getMapCache(envCacheName).remove(key);
        logObject("redis.Remove." + envCacheName + "[" + key + "]: ", result);
      } catch (Exception e) {
        LOGGER.error("Error removing value from Redis", e);
      }
    }
    return result;
  }

  private String getCacheName(String cacheName) {
    return StringUtils.isNotEmpty(cacheName) ? cacheName : null;
  }

  private boolean validateParameters(String cacheName, String key, Object value) {
    if (StringUtils.isEmpty(cacheName)) {
      LOGGER.info("Operation rejected for empty cacheName");
      return false;
    }
    if (StringUtils.isEmpty(key)) {
      LOGGER.info("Operation rejected for empty key");
      return false;
    }
    if (value == null) {
      LOGGER.info("Operation rejected for null value");
      return false;
    }
    return true;
  }

  private boolean validateCacheNameAndKey(String cacheName, String key) {
    if (StringUtils.isEmpty(cacheName)) {
      LOGGER.info("Operation rejected for empty cacheName");
      return false;
    }
    if (StringUtils.isEmpty(key)) {
      LOGGER.info("Operation rejected for empty key");
      return false;
    }

    return true;
  }

  private boolean validateGlobalRedisKey(String globalRedisKey) {
    if (StringUtils.isEmpty(globalRedisKey)) {
      LOGGER.info("Operation rejected for empty globalRedisKey");
      return false;
    }

    return true;
  }

  private void logObject(String prefix, Object object) {
    String objectString = "null";
    if (object != null) {
      try {
        objectString = mapper.writeValueAsString(object);
      } catch (Exception e) {
        objectString = object.toString();
      }
    }
    LOGGER.info(prefix + objectString);
  }
}
