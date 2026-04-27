package org.styli.services.customer.gateway.getters;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.styli.services.customer.utility.CommonUtility;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created on 05-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
public interface RequestIpGetter {

  public static final Log IP_GETTER_LOGGER = LogFactory.getLog(RequestIpGetter.class);

  default List<String> getClientIpHeaderNames() {
    return Arrays.asList(
            "X-Forwarded-For",
            "X-Original-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR");
  }
  default List<String> getExtraHeaderNames() {
    return Arrays.asList("cf-ipcountry", "accept-language", "user-agent", "cookie");
  }

  default Set<String> checkEmptyIp(ServerHttpRequest serverHttpRequest, Set<String> ips) {
    if(ips!=null && CollectionUtils.isEmpty(ips)) {
      try {
        String remoteAddress = serverHttpRequest.getRemoteAddress().getAddress().getHostAddress();
        if (StringUtils.isNotEmpty(remoteAddress)) ips.add(remoteAddress);
      } catch (Exception e) {
      }
    }
    return ips;
  }

  default List<String> getAllRequestIp(ServerHttpRequest serverHttpRequest) {
    Set<String> result = new LinkedHashSet<>();
    Set<String> extraResult = new LinkedHashSet<>();
    if (serverHttpRequest != null) {

      HttpHeaders headers = serverHttpRequest.getHeaders();
      final List<String> clientIpHeaderNames = getClientIpHeaderNames();
      String loggerMsg = CommonUtility.getLoggerMsg(clientIpHeaderNames);
      IP_GETTER_LOGGER.info("redis.ipGetter.clientIpHeaderNames: "+loggerMsg);
      for (final String headerName : clientIpHeaderNames) {
        List<String> values = headers.get(headerName);
        if (CollectionUtils.isNotEmpty(values))
          result.addAll(
              values.stream().filter(StringUtils::isNotEmpty).collect(Collectors.toList()));
      }
      result = checkEmptyIp(serverHttpRequest, result);
      final List<String> extraHeaderNames = getExtraHeaderNames();
      loggerMsg = CommonUtility.getLoggerMsg(extraHeaderNames);
      IP_GETTER_LOGGER.info("redis.ipGetter.extraHeaderNames: "+loggerMsg);
      for (final String headerName : extraHeaderNames) {
        List<String> values = headers.get(headerName);
        if (CollectionUtils.isNotEmpty(values))
          extraResult.addAll(
              values.stream().filter(StringUtils::isNotEmpty).collect(Collectors.toList()));
      }
    }
    ArrayList<String> finalResult =
            result.stream().sorted().collect(Collectors.toCollection(ArrayList::new));
    finalResult.addAll(extraResult);
    return finalResult;
  }

  default String getAllRequestIpJoined(ServerHttpRequest serverHttpRequest) {
    List<String> ips = getAllRequestIp(serverHttpRequest);
    return String.join("|", ips);
  }

  default Set<String> checkEmptyIp(HttpServletRequest serverRequest, Set<String> ips) {
    if(ips!=null && CollectionUtils.isEmpty(ips)) {
      try {
        String remoteAddress = serverRequest.getRemoteAddr();
        if (StringUtils.isNotEmpty(remoteAddress)) ips.add(remoteAddress);
      } catch (Exception e) {
      }
    }
    return ips;
  }

  default List<String> getAllRequestIp(HttpServletRequest serverRequest) {
    Set<String> result = new LinkedHashSet<>();
    Set<String> extraResult = new LinkedHashSet<>();
    if (serverRequest != null) {
      final List<String> clientIpHeaderNames = getClientIpHeaderNames();
      String loggerMsg = CommonUtility.getLoggerMsg(clientIpHeaderNames);
      IP_GETTER_LOGGER.info("redis.ipGetter.clientIpHeaderNames: "+loggerMsg);
      for (final String headerName : clientIpHeaderNames) {
        List<String> values = Collections.list(serverRequest.getHeaders(headerName));
        if (CollectionUtils.isNotEmpty(values))
          result.addAll(
              values.stream().filter(StringUtils::isNotEmpty).collect(Collectors.toList()));
      }
      result = checkEmptyIp(serverRequest, result);
      final List<String> extraHeaderNames = getExtraHeaderNames();
      loggerMsg = CommonUtility.getLoggerMsg(extraHeaderNames);
      IP_GETTER_LOGGER.info("redis.ipGetter.extraHeaderNames: "+loggerMsg);
      for (final String headerName : extraHeaderNames) {
        List<String> values = Collections.list(serverRequest.getHeaders(headerName));
        if (CollectionUtils.isNotEmpty(values))
          extraResult.addAll(
              values.stream().filter(StringUtils::isNotEmpty).collect(Collectors.toList()));
      }
    }
    ArrayList<String> finalResult =
            result.stream().sorted().collect(Collectors.toCollection(ArrayList::new));
    finalResult.addAll(extraResult);
    return finalResult;
  }

  default String getAllRequestIpJoined(HttpServletRequest serverRequest) {
    List<String> ips = getAllRequestIp(serverRequest);
    return String.join("|", ips);
  }

}
