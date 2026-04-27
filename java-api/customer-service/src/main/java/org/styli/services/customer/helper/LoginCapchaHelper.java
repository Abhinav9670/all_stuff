package org.styli.services.customer.helper;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.styli.services.customer.gateway.getters.MapValueGetter;
import org.styli.services.customer.pojo.limiter.TokenBucketObject;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.redis.TtlMode;
import org.styli.services.customer.utility.CommonUtility;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.Map;

/**
 * Created on 15-Nov-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Component
@Scope("singleton")
public class LoginCapchaHelper implements MapValueGetter, ServiceConfigs.ServiceConfigsListener {

  private static final Log LOGGER = LogFactory.getLog(LoginCapchaHelper.class);

  public static final String CACHE_NAME = "login-v4-capcha";

  private boolean enabled = false;
  private String tokenMode = "";
  private Integer hits = 3;
  private Long refreshPeriodMilli = 60000L;

  @Autowired RedisHelper redisHelper;

  @PostConstruct
  public void init() {
    ServiceConfigs.addConfigListener(this);
    onConfigsUpdated(ServiceConfigs.getConsulServiceMap());
  }

  @PreDestroy
  public void destroy() {
    ServiceConfigs.removeConfigListener(this);
  }

  @Override
  public void onConfigsUpdated(Map<String, Object> newConfigs) {
    if (MapUtils.isNotEmpty(newConfigs) && newConfigs.get("capcha") instanceof Map<?, ?>) {
      final Map<?, ?> capchaMap = (Map<?, ?>) newConfigs.get("capcha");
      final Map<?, ?> currentMap = CommonUtility.getMapFromMap(capchaMap, CACHE_NAME);
      final Boolean newEnabled = getBooleanFromMap(currentMap, "enabled");
      final Integer newHits = getIntFromMap(currentMap, "hits", hits);
      final Long newRefreshPeriodMilli =
          getLongFromMap(currentMap, "refreshPeriodsMilli", refreshPeriodMilli);
      enabled = (newEnabled != null && newEnabled);
      tokenMode = getStringFromMap(currentMap, "tokenMode", tokenMode);
      hits = newHits;
      refreshPeriodMilli = newRefreshPeriodMilli;
    }
  }

  /**
   * Fetches TokenBucketObject from redis cache from login capcha bucket.
   *
   * @param loginV4Request
   * @return
   */
  public TokenBucketObject getCapchaBucketObject(
      CustomerLoginV4Request loginV4Request, Map<String, String> requestHeader) {
    TokenBucketObject tokenBucketObject = null;
    final String token =
        (("ip".equalsIgnoreCase(tokenMode)
                && requestHeader != null
                && StringUtils.isNotEmpty(requestHeader.get("x-original-forwarded-for")))
            ? requestHeader.get("x-original-forwarded-for")
            : loginV4Request.getUseridentifier());
    try {
      tokenBucketObject =
          (TokenBucketObject) redisHelper.get(CACHE_NAME, token, TokenBucketObject.class);
      if (tokenBucketObject != null) {
        tokenBucketObject.setToken(token);
      }
    } catch (Exception e) {
      LOGGER.error("getCapchaBucketObject error: " + e.toString());
      tokenBucketObject = null;
    }
    if (tokenBucketObject == null) {
      tokenBucketObject = TokenBucketObject.of(token, 0L, 1);
    }
    return tokenBucketObject;
  }

  /**
   * compares and resolves if re-capcha is needed for this user.
   *
   * @param tokenBucketObject
   * @return
   */
	public boolean needsReCapcha(TokenBucketObject tokenBucketObject) {
		boolean result = false;
		try {
			if (enabled && tokenBucketObject != null && refreshPeriodMilli > 0) {
				boolean needsPut = false;
				final long now = Instant.now().toEpochMilli();

				if (tokenBucketObject.getUpdatedAt() != null
						&& now >= (tokenBucketObject.getUpdatedAt() + refreshPeriodMilli)) {
					needsPut = true;
					tokenBucketObject.setUpdatedAt(now);
					tokenBucketObject.setCount(1);
				} else {
					boolean allowed = (tokenBucketObject.getCount() == null || tokenBucketObject.getCount() < hits);
					result = !allowed;
					if (allowed) {
						needsPut = true;
						tokenBucketObject.setCount(
								(tokenBucketObject.getCount() == null) ? 1 : (tokenBucketObject.getCount() + 1));
					}
				}

				/** Do Put operation to redis if needed. */
				if (needsPut) {
					redisHelper.put(CACHE_NAME, tokenBucketObject.getToken(), tokenBucketObject, TtlMode.CAPCHA);
				}
			}
		} catch (Exception e) {
			LOGGER.error("needsReCapcha error: " + e.getMessage());
		}
		return result;
	}
}
