package org.styli.services.customer.gateway.getters;

import org.styli.services.customer.pojo.limiter.TokenBucketObject;
import org.styli.services.customer.redis.RedisHelper;

/**
 * Created on 12-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
public interface TokenBucketObjectGetter {

  default boolean validateTokenBucketObject(
      TokenBucketObject tokenBucketObject, long now, long refreshPeriodMilli, int hits) {
    boolean result = true;
    if (tokenBucketObject != null && refreshPeriodMilli>0) {
      if (tokenBucketObject.getUpdatedAt() != null
              && now >= (tokenBucketObject.getUpdatedAt() + refreshPeriodMilli)) {
        tokenBucketObject.setUpdatedAt(now);
        tokenBucketObject.setCount(1);
      } else {
        result = (tokenBucketObject.getCount() == null || tokenBucketObject.getCount() < hits);
        if (result) {
          tokenBucketObject.setCount(
                  (tokenBucketObject.getCount() == null) ? 1 : (tokenBucketObject.getCount() + 1));
        }
      }
    }
    return result;
  }

  default TokenBucketObject getTokenBucketObjectFromCache(
      RedisHelper redisHelper, String cacheName, String token) {
    TokenBucketObject tokenBucketObject = null;
    try {
      tokenBucketObject =
          (TokenBucketObject) redisHelper.get(cacheName, token, TokenBucketObject.class);
      if (tokenBucketObject != null) {
        tokenBucketObject.setToken(token);
      }
    } catch (Exception e) {
      tokenBucketObject = null;
    }
    return tokenBucketObject;
  }
}
