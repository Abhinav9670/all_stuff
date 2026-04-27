package org.styli.services.customer.limiter;

import org.apache.commons.lang.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.styli.services.customer.gateway.CoreFilterWorker;
import org.styli.services.customer.pojo.limiter.BlockedIpObject;
import org.styli.services.customer.pojo.limiter.TokenBucketObject;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.response.CustomerLoginV4Response;
import org.styli.services.customer.redis.TtlMode;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Created on 29-Jun-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Component
@Scope("singleton")
public class LoginLimiterWorker extends CoreFilterWorker {

  public static final String CACHE_NAME = "login-v4";

  private static final UrlPattern pattern = new UrlPattern("[POST]/rest/customer/v4/login");

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
  public void beforeBodyWrite(
      Object responseBody,
      ServerHttpRequest serverHttpRequest,
      ServerHttpResponse serverHttpResponse) {
    if (responseBody instanceof CustomerLoginV4Response && isFilterEnabled()) {
      CustomerLoginV4Response v4Response = (CustomerLoginV4Response) responseBody;
      if ("202".equals(v4Response.getStatusCode())) {
        String token = getRequestToken(serverHttpRequest);
        BlockedIpObject blockedIpObject =
            BlockedIpObject.of(token, CACHE_NAME + ".inValidUserId", Instant.now().toEpochMilli());

        redisHelper.put(
            IpLimiterWorker.CACHE_NAME,
            blockedIpObject.getToken(),
            blockedIpObject,
            TtlMode.IP_BLOCKER);
      }
    }
  }

  @Override
  public boolean doFilter(HttpServletRequest request) {
    boolean result = true;
    if (request != null && pattern.match(request)) {
      result = isWithinUpperLimit();
      if (result) result = hasValidSession(getCustomerV4Request(request));
    }
    return result;
  }

  private boolean hasValidSession(CustomerLoginV4Request requestBody) {
    boolean result = true;
    if (requestBody != null && StringUtils.isNotEmpty(requestBody.getUseridentifier())) {
      final String token = requestBody.getUseridentifier();
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

  private CustomerLoginV4Request getCustomerV4Request(HttpServletRequest request) {
    String body = "";
    CustomerLoginV4Request v4Request = null;
    try {
      body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));

    } catch (Exception e) {
      body = "";
    }
    if (StringUtils.isNotEmpty(body)) {
      try {
        v4Request = mapper.readValue(body, CustomerLoginV4Request.class);
      } catch (Exception e) {
        v4Request = null;
      }
    }
    return v4Request;
  }
}
