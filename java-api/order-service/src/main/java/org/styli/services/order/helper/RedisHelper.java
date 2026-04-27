package org.styli.services.order.helper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.styli.services.order.model.redis.TtlMode;
import org.styli.services.order.utility.Constants;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;

/**
 * Created on 19-May-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Component
@Scope("singleton")
public class RedisHelper {

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
                 getEnvCacheName(Constants.OTP_CACHE_NAME),
                 redissonClient.getMapCache(getEnvCacheName(Constants.OTP_CACHE_NAME)));
     }

    @PreDestroy
    public void destroy() {
        try {
            caches.clear();
            if (redissonClient != null) redissonClient.shutdown();
        } catch (Exception e) {
        } finally {
            redissonClient = null;
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
                LOGGER.info("redis.Put rejected for envCacheName \""+envCacheName+"\" not contains in cache map");
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
