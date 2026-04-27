package org.styli.services.customer.limiter;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.styli.services.customer.gateway.CoreFilterWorker;
import org.styli.services.customer.pojo.limiter.BlockedIpObject;
import org.styli.services.customer.redis.TtlMode;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created on 04-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Component
@Scope("singleton")
public class IpLimiterWorker extends CoreFilterWorker {

  public static final String CACHE_NAME = "blocked-ip";
  private static final Log LOGGER = LogFactory.getLog(IpLimiterWorker.class);

  private final List<UrlPattern> urlPatterns = new ArrayList<>();

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
    super.onConfigsUpdated(newConfigs);
    if (MapUtils.isNotEmpty(ServiceConfigs.getLimiterConfigs())) {
      Map<?, ?> limiterMap = ServiceConfigs.getLimiterConfigs();
      if(!(limiterMap.get(getCacheName()) instanceof Map<?, ?>)
              || MapUtils.isEmpty((Map<?, ?>) limiterMap.get(getCacheName())))
        return;
      Map<?, ?> blockedIpMap = (Map<?, ?>) limiterMap.get(getCacheName());
      if(!(blockedIpMap.get(KEY_URL_PATTERNS) instanceof List))
        return;
      List<?> rawPatterns = (List<?>) blockedIpMap.get(KEY_URL_PATTERNS);
      List<UrlPattern> newPatterns = new ArrayList<>();
      for (final Object item : rawPatterns) {
        if(item instanceof String && StringUtils.isNotEmpty(((String)item).trim()))
          newPatterns.add(new UrlPattern(((String)item)));
      }

      if(CollectionUtils.isNotEmpty(newPatterns)) {
        urlPatterns.clear();
        urlPatterns.addAll(newPatterns);
      }
    }
  }

  @Override
  public String getCacheName() {
    return CACHE_NAME;
  }

  @Override
  public boolean doFilter(HttpServletRequest request) {
    boolean result = true;
    if(request != null) {
      boolean patternMatches = false;
      for (final UrlPattern pattern : urlPatterns) {
        if(pattern != null && pattern.match(request)) {
          patternMatches = true;
          break;
        }
      }
      if(patternMatches) {
        result = isWithinUpperLimit();
        if (result)  result = hasValidSession(request);
      }
    }
    return result;
  }

  private boolean hasValidSession(HttpServletRequest request) {
    boolean result = true;
    try{
      String token = getRequestToken(request);
      BlockedIpObject cacheSession = (BlockedIpObject) redisHelper.get(CACHE_NAME, token, BlockedIpObject.class);
      long now = 0L;
      if(cacheSession != null) {
        cacheSession.setToken(token);
        if(cacheSession.getCreatedAt()!=null) {
          now = Instant.now().toEpochMilli();
          long interval = TtlMode.IP_BLOCKER.getTimeUnit().toMillis(TtlMode.IP_BLOCKER.getValue());
          result = (now > (cacheSession.getCreatedAt()+interval));
        } else {
          result = false;
        }
      }
      LOGGER.info("redis.ip: "+token);
      LOGGER.info("redis.ip.now: "+now);
      LOGGER.info("redis.ip.cacheSession(NullCheck): "+(cacheSession != null));
      LOGGER.info("redis.ip.cacheSession.result: "+result);
    } catch(Exception e) {
      result = true;
      LOGGER.info("redis.ip.cacheSession.result(Exception): "+result);
    }
    return result;
  }
}
