package org.styli.services.customer.limiter;

import org.apache.commons.lang.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.styli.services.customer.gateway.CoreFilterWorker;
import org.styli.services.customer.pojo.limiter.TokenBucketObject;
import org.styli.services.customer.pojo.registration.request.CustomerV4Registration;
import org.styli.services.customer.redis.TtlMode;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Created on 12-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Component
@Scope("singleton")
public class RegistrationLimiterWorker extends CoreFilterWorker {

  public static final String CACHE_NAME = "registration-v4";

  private static final UrlPattern pattern = new UrlPattern("[POST]/rest/customer/v4/registration");

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
  public String getCacheName() {
    return CACHE_NAME;
  }

  @Override
  public boolean doFilter(HttpServletRequest request) {
    boolean result = true;
    if (request != null && pattern.match(request)) {
      result = isWithinUpperLimit();
      if (result) result = hasValidSession(getCustomerV4Request(request), request);
    }
    return result;
  }

  private boolean hasValidSession(CustomerV4Registration requestBody, HttpServletRequest request) {
    boolean result = true;
    if (requestBody != null && request != null) {
      final String token = getRequestToken(request);
      long now = Instant.now().toEpochMilli();
      TokenBucketObject tokenBucketObject = getTokenBucketObjectFromCache(token);
      if (tokenBucketObject == null) {
        tokenBucketObject = TokenBucketObject.of(token, now, 1);
      } else {
        result = validateTokenBucketObject(tokenBucketObject, now);
      }
      if (result) {
        redisHelper.put(CACHE_NAME, tokenBucketObject.getToken(), tokenBucketObject, TtlMode.LIMITER);
      }
    }
    return result;
  }

  private CustomerV4Registration getCustomerV4Request(HttpServletRequest request) {
    String body = "";
    CustomerV4Registration v4Request = null;
    try {
      body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));

    } catch (Exception e) {
      body = "";
    }
    if (StringUtils.isNotEmpty(body)) {
      try {
        v4Request = mapper.readValue(body, CustomerV4Registration.class);
      } catch (Exception e) {
        v4Request = null;
      }
    }
    return v4Request;
  }
}
