package org.styli.services.customer.limiter;

import org.springframework.context.annotation.Scope;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.styli.services.customer.gateway.CoreFilterWorker;
import org.styli.services.customer.pojo.otp.OtpBucketObject;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;

/**
 * Created on 12-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Component
@Scope("singleton")
public class SendOtpLimiterWorker extends CoreFilterWorker {
  public static final String CACHE_NAME = "send-otp";

  private static final UrlPattern pattern = new UrlPattern("[POST]/rest/customer/auth/send/otp");

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
    super.beforeBodyWrite(responseBody, serverHttpRequest, serverHttpResponse);
  }

  @Override
  public boolean doFilter(HttpServletRequest request) {
    boolean result = true;
    if (request != null && pattern.match(request)) {
      result = isWithinUpperLimit();
    }
    return result;
  }

  public boolean validateOtpObject(OtpBucketObject otpBucketObject, long now) {
    boolean result = true;
    if (otpBucketObject != null
        && now > 0L
        && refreshPeriodMilli != null
        && refreshPeriodMilli > 0L
        && otpBucketObject.getCreateCount() != null) {
      if (otpBucketObject.getOriginAt() != null
          && now >= (otpBucketObject.getOriginAt() + refreshPeriodMilli)) {
        otpBucketObject.setOriginAt(now);
        otpBucketObject.setCreateCount(1);
      } else {
        result =
            (otpBucketObject.getCreateCount() == null || otpBucketObject.getCreateCount() < hits);
        if (result) {
          otpBucketObject.setCreateCount(
              (otpBucketObject.getCreateCount() == null)
                  ? 1
                  : (otpBucketObject.getCreateCount() + 1));
        }
      }
    }
    return result;
  }
}
