package org.styli.services.customer.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.styli.services.customer.gateway.getters.TokenBucketObjectGetter;
import org.styli.services.customer.pojo.limiter.TokenBucketObject;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.gateway.getters.MapValueGetter;
import org.styli.services.customer.gateway.getters.RequestIpGetter;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Created on 30-Jun-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
public abstract class CoreFilterWorker
    implements FilterWorker,
        RequestIpGetter,
        MapValueGetter,
        TokenBucketObjectGetter,
        ServiceConfigs.ServiceConfigsListener {

  private static final Log LOGGER = LogFactory.getLog(CoreFilterWorker.class);

  protected static final String KEY_UPPER_HITS = "upperHits";
  protected static final String KEY_UPPER_REFRESH_PERIOD = "upperRefreshPeriodsMilli";
  protected static final String KEY_HITS = "hits";
  protected static final String KEY_REFRESH_PERIOD = "refreshPeriodsMilli";
  protected static final String KEY_ENABLED = "enabled";
  protected static final String KEY_URL_PATTERNS = "urlPatterns";

  protected static final ObjectMapper mapper = Constants.JSON_MAPPER;

  @Autowired protected RedisHelper redisHelper;
  protected Integer upperHits = 200;
  protected Long upperRefreshPeriodMilli = 60000L;
  protected Integer hits = 3;
  protected Long refreshPeriodMilli = 60000L;
  protected Boolean isEnabled = true;

  @Getter protected Long updatedAt = 0L;
  @Getter protected Integer count = 0;

  protected CoreFilterWorker() {
    updatedAt = Instant.now().toEpochMilli();
    count = 0;
  }

  public abstract String getCacheName();

  protected boolean isWithinUpperLimit() {
    boolean result = true;
    try {
      long now = Instant.now().toEpochMilli();
      if (updatedAt != null && now >= (updatedAt + upperRefreshPeriodMilli)) {
        updatedAt = now;
        count = 1;
      } else {
        result = (count == null || upperHits == null || count < upperHits);
        if (result) {
          count = ((count == null) ? 1 : (count + 1));
        }
      }
    } catch (Exception e) {
      LOGGER.error(e);
    }
    return result;
  }

  @Override
  public boolean isFilterEnabled() {
    if (isEnabled != null) return isEnabled;
    return true;
  }

  @Override
  public void onConfigsUpdated(Map<String, Object> newConfigs) {
    if (MapUtils.isNotEmpty(newConfigs) && newConfigs.get("limiter") instanceof Map<?, ?>) {
      Map<?, ?> limiterMap = (Map<?, ?>) newConfigs.get("limiter");
      Map<?, ?> cacheMap = getMapFromMap(limiterMap, getCacheName());

      Boolean newDefaultEnabled = getBooleanFromMap(limiterMap, KEY_ENABLED);
      Integer newUpperHits = getIntFromMap(limiterMap, KEY_UPPER_HITS, upperHits);
      Long newUpperRefreshPeriod =
          getLongFromMap(limiterMap, KEY_UPPER_REFRESH_PERIOD, upperRefreshPeriodMilli);

      Integer newHits = getIntFromMap(cacheMap, KEY_HITS, hits);
      Long newRefreshPeriod = getLongFromMap(cacheMap, KEY_REFRESH_PERIOD, refreshPeriodMilli);
      Boolean newEnabled = getBooleanFromMap(cacheMap, KEY_ENABLED);
      newUpperHits = getIntFromMap(cacheMap, KEY_UPPER_HITS, newUpperHits);
      newUpperRefreshPeriod =
          getLongFromMap(cacheMap, KEY_UPPER_REFRESH_PERIOD, newUpperRefreshPeriod);

      upperHits = newUpperHits;
      upperRefreshPeriodMilli = newUpperRefreshPeriod;
      hits = newHits;
      refreshPeriodMilli = newRefreshPeriod;
      isEnabled =
          (newDefaultEnabled != null && newEnabled != null && (newDefaultEnabled && newEnabled));
    }
  }

  protected boolean validateTokenBucketObject(TokenBucketObject tokenBucketObject, long now) {
    return this.validateTokenBucketObject(
        tokenBucketObject,
        now,
        ((refreshPeriodMilli != null) ? refreshPeriodMilli : 0),
        ((hits != null) ? hits : 0));
  }

  protected TokenBucketObject getTokenBucketObjectFromCache(String token) {
    return this.getTokenBucketObjectFromCache(redisHelper, getCacheName(), token);
  }

  protected String getRequestToken(HttpServletRequest request) {
    String result = "";
    if (request != null) {
      result = getAllRequestIpJoined(request);
    }
    return result;
  }

  protected String getRequestToken(ServerHttpRequest request) {
    String result = "";
    if (request != null) {
      result = getAllRequestIpJoined(request);
    }
    return result;
  }

  private Map<?, ?> getMapFromMap(Map<?, ?> parent, Object key) {
    Map<?, ?> child = null;
    if (MapUtils.isNotEmpty(parent) && parent.get(key) instanceof Map<?, ?>) {
      child = (Map<?, ?>) parent.get(key);
    }
    return child;
  }
}
