package org.styli.services.customer.limiter;

import lombok.Getter;
import org.apache.commons.lang.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Created on 12-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Getter
public class UrlPattern {

  private static final String MAIN_REGEX = "^\\[[a-zA-Z]+\\].*";

  final String method;
  final String pattern;

  public UrlPattern(String completePattern) {
    this(decodeMethod(completePattern), decodePattern(completePattern));
  }

  public UrlPattern(String method, String pattern) {
    this.method = method;
    this.pattern = pattern;
  }

  public boolean match(HttpServletRequest request) {
    if (request == null) return false;
    boolean result;
    final String requestUrl = request.getRequestURL().toString();
    final String requestMethod = request.getMethod();
    boolean pathMatches =
        (StringUtils.isEmpty(pattern)
            || (StringUtils.isNotEmpty(requestUrl) && requestUrl.contains(pattern)));
    boolean methodMatches =
        (StringUtils.isEmpty(method)
            || (StringUtils.isNotEmpty(requestMethod) && requestMethod.equalsIgnoreCase(method)));
    result = (pathMatches && methodMatches);
    return result;
  }

  private static String decodeMethod(String completePattern) {
    if (StringUtils.isEmpty(completePattern)) return "";
    String result = "";
    if (completePattern.trim().matches(MAIN_REGEX)) {
      String newPattern = completePattern.trim().substring(1);
      ArrayList<String> chunks = new ArrayList<>(Arrays.asList(newPattern.trim().split("\\]")));
      result = chunks.get(0);
    }
    return result;
  }

  private static String decodePattern(String completePattern) {
    if (StringUtils.isEmpty(completePattern)) return "";
    String result = "";
    if (completePattern.trim().matches(MAIN_REGEX)) {
      String newPattern = completePattern.trim().substring(1);
      ArrayList<String> chunks = new ArrayList<>(Arrays.asList(newPattern.trim().split("\\]")));
      chunks.remove(0);
      result = chunks.stream().collect(Collectors.joining("]"));
    } else {
      result = completePattern.trim();
    }
    return result;
  }
}
