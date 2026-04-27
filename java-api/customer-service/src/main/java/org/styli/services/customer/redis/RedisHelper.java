package org.styli.services.customer.redis;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.redisson.Redisson;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.redisson.config.BaseConfig;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.styli.services.customer.helper.LoginCapchaHelper;
import org.styli.services.customer.limiter.IpLimiterWorker;
import org.styli.services.customer.limiter.LoginLimiterWorker;
import org.styli.services.customer.limiter.RegistrationLimiterWorker;
import org.styli.services.customer.service.CustomerV4Service;
import org.styli.services.customer.service.CustomerV5Service;
import org.styli.services.customer.service.OtpService;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Created on 29-Jun-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Component
@Scope("singleton")
public class RedisHelper implements ServiceConfigs.ServiceConfigsListener {

  private static final Log LOGGER = LogFactory.getLog(RedisHelper.class);
  private static final ObjectMapper mapper = Constants.JSON_MAPPER;

  private final HashMap<String, RMapCache<String, Object>> caches = new HashMap<>();

  private RedissonClient redissonClient;

  @Value("${redis.host}")
  private String redisHost;

  @Value("${redis.port}")
  private String redisPort;

  @Value("${redis.auth}")
  private String redisAuth;

  @Value("${env}")
  private String env;

   @PostConstruct
   public void init() {
 	  LOGGER.info("redisHost:"+redisHost);
 	  LOGGER.info("redisPort:"+redisPort);
 	  LOGGER.info("redisAuth:"+redisAuth);
     ServiceConfigs.addConfigListener(this);
     try {
       onConfigsUpdated(ServiceConfigs.getConsulServiceMap());
     } catch (Exception e) {
     }
     Config config = new Config();
     BaseConfig baseConfig =
         config.useSingleServer().setAddress("redis://" + redisHost + ":" + redisPort);
     if (StringUtils.isNotEmpty(redisAuth)) {
       baseConfig.setPassword(redisAuth);
     }
     ObjectMapper newMapper = new ObjectMapper();
     newMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
     config.setCodec(new TypedJsonJacksonCodec(String.class, newMapper));
     redissonClient = Redisson.create(config);

     /** initializing all the cache objects */
     caches.clear();
     caches.put(
         getEnvCacheName(OtpService.CACHE_NAME),
         redissonClient.getMapCache(getEnvCacheName(OtpService.CACHE_NAME)));
     caches.put(
         getEnvCacheName(IpLimiterWorker.CACHE_NAME),
         redissonClient.getMapCache(getEnvCacheName(IpLimiterWorker.CACHE_NAME)));
     caches.put(
         getEnvCacheName(LoginLimiterWorker.CACHE_NAME),
         redissonClient.getMapCache(getEnvCacheName(LoginLimiterWorker.CACHE_NAME)));
     caches.put(
         getEnvCacheName(RegistrationLimiterWorker.CACHE_NAME),
         redissonClient.getMapCache(getEnvCacheName(RegistrationLimiterWorker.CACHE_NAME)));
     caches.put(
             getEnvCacheName(LoginCapchaHelper.CACHE_NAME),
             redissonClient.getMapCache(getEnvCacheName(LoginCapchaHelper.CACHE_NAME)));
     caches.put(
             getEnvCacheName(Constants.DELETE_CUSTOMER_OTP_CACHE_NAME),
             redissonClient.getMapCache(getEnvCacheName(Constants.DELETE_CUSTOMER_OTP_CACHE_NAME)));
     caches.put(
             getEnvCacheName(Constants.WHATSAPP_SIGNUP_CACHE_NAME),
             redissonClient.getMapCache(getEnvCacheName(Constants.WHATSAPP_SIGNUP_CACHE_NAME)));
     caches.put(
             getEnvCacheName(CustomerV5Service.CITY_CACHE_NAME),
             redissonClient.getMapCache(getEnvCacheName(CustomerV5Service.CITY_CACHE_NAME)));
     caches.put(
             getEnvCacheName(CustomerV4Service.CACHE_NAME),
             redissonClient.getMapCache(getEnvCacheName(CustomerV4Service.CACHE_NAME)));
     caches.put(
             getEnvCacheName(CustomerV4Service.PROVINCE_LIST_CACHE),
             redissonClient.getMapCache(getEnvCacheName(CustomerV4Service.PROVINCE_LIST_CACHE)));
   }

  @PreDestroy
  public void destroy() {
    ServiceConfigs.removeConfigListener(this);
    try {
      caches.clear();
      if (redissonClient != null) redissonClient.shutdown();
    } catch (Exception e) {
    } finally {
      redissonClient = null;
    }
  }

  @Override
  public void onConfigsUpdated(Map<String, Object> newConfigs) {
    if (MapUtils.isNotEmpty(newConfigs)
        && (newConfigs.get("ttl") instanceof Map || newConfigs.get("ttl") instanceof String)) {
      TtlMode.updateValues(newConfigs.get("ttl"));
    }
  }

  public boolean put(String cacheName, String key, Object value) {
    return put(cacheName, key, value, null);
  }

  public boolean put(String cacheName, String key, Object value, TtlMode ttlMode) {
    boolean success = false;
    final String envCacheName = getEnvCacheName(cacheName);
    if (StringUtils.isNotEmpty(cacheName)
        && StringUtils.isNotEmpty(envCacheName)
        && caches.containsKey(envCacheName)
        && StringUtils.isNotEmpty(key)
        && value != null) {
      try {
        if (ttlMode == null) caches.get(envCacheName).put(key, value);
        else
          caches
              .get(envCacheName)
              .put(key, value, ttlMode.getValue(), ttlMode.getTimeUnit());
        success = true;

        logObject("redis.Put." + envCacheName + "[" + key + "]: ", value);
      } catch (Exception e) {
        LOGGER.error(e);
      }
    } else {
      if(StringUtils.isEmpty(envCacheName)) {
        LOGGER.info("redis.Put rejected for empty envCacheName: "+ envCacheName);
      } else if(StringUtils.isEmpty(cacheName)) {
        LOGGER.info("redis.Put rejected for empty cacheName: "+ cacheName);
      } else if(!caches.containsKey(envCacheName)) {
        LOGGER.info("redis.Put rejected for envCacheName \""+envCacheName+"\" not contains in cache map ");
      } else if(StringUtils.isEmpty(key)) {
        LOGGER.info("redis.Put rejected for empty key: "+ key);
      } else {
        LOGGER.info("redis.Put rejected for null value");
      }
    }
    return success;
  }

  public Object get(String cacheName, String key) {
    return get(cacheName, key, null);
  }

  public Object get(String cacheName, String key, Class resultClass) {
    Object result = null;
    final String envCacheName = getEnvCacheName(cacheName);
    if (StringUtils.isNotEmpty(cacheName)
        && StringUtils.isNotEmpty(envCacheName)
        && caches.containsKey(envCacheName)
        && StringUtils.isNotEmpty(key)) {
      try {
        result = caches.get(envCacheName).get(key);
        if (resultClass != null) {
          result = mapper.readValue(mapper.writeValueAsString(result), resultClass);
        } else if (result != null && !(result instanceof String)) {
          result = mapper.writeValueAsString(result);
        }

        logObject("redis.Get." + envCacheName + "[" + key + "]: ", result);
      } catch (Exception e) {
        LOGGER.error(e);
        result = null;
      }
    } else {
      if(StringUtils.isEmpty(envCacheName)) {
        LOGGER.info("redis.Get rejected for empty envCacheName: "+ envCacheName);
      } else if(StringUtils.isEmpty(cacheName)) {
        LOGGER.info("redis.Get rejected for empty cacheName: "+ cacheName);
      } else if(!caches.containsKey(envCacheName)) {
        LOGGER.info("redis.Get rejected for envCacheName \""+envCacheName+"\" not contains in cache map");
      } else if(StringUtils.isEmpty(key)) {
        LOGGER.info("redis.Get rejected for empty key: "+ key);
      } else {
        LOGGER.info("redis.Get rejected for null value");
      }
    }

    return result;
  }

  public Object remove(String cacheName, String key) {
    Object result = null;
    final String envCacheName = getEnvCacheName(cacheName);
    if (StringUtils.isNotEmpty(cacheName)
        && StringUtils.isNotEmpty(envCacheName)
        && caches.containsKey(envCacheName)
        && StringUtils.isNotEmpty(key)) {
      try {
        result = caches.get(envCacheName).remove(key);
        logObject("redis.Remove." + envCacheName + "[" + key + "]: ", result);
      } catch (Exception e) {
        LOGGER.error(e);
      }
    } else {
      if(StringUtils.isEmpty(envCacheName)) {
        LOGGER.info("redis.Remove rejected for empty envCacheName: "+ envCacheName);
      } else if(StringUtils.isEmpty(cacheName)) {
        LOGGER.info("redis.Remove rejected for empty cacheName: "+ cacheName);
      } else if(!caches.containsKey(envCacheName)) {
        LOGGER.info("redis.Remove rejected for envCacheName \""+envCacheName+"\" not contains in cache map");
      } else if(StringUtils.isEmpty(key)) {
        LOGGER.info("redis.Remove rejected for empty key: "+ key);
      } else {
        LOGGER.info("redis.Remove rejected for null value");
      }
    }
    return result;
  }

  private String getEnvCacheName(String cacheName) {
    String result = cacheName;
    if (StringUtils.isNotEmpty(cacheName) && !cacheName.startsWith(env + "_")) {
      result = env + "_" + cacheName;
    }
    return result;
  }

  private void logObject(String prefix, Object object) {
    String objectString;
    if (object != null) {
      try {
        objectString = mapper.writeValueAsString(object);
      } catch (Exception e) {
        objectString = object.toString();
      }
    } else {
      objectString = "null";
    }
    if (StringUtils.isNotEmpty(prefix)) {
      LOGGER.info(prefix + objectString);
    } else {
      LOGGER.info(objectString);
    }
  }
}
