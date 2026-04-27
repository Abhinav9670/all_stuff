package org.styli.services.customer.gateway;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created on 29-Jun-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
public interface FilterWorker {

  /**
   * It should return true if you want to further serve the request.
   *
   * @param request
   * @return
   */
  public boolean doFilter(HttpServletRequest request);

  default void beforeBodyWrite(
      Object responseBody,
      ServerHttpRequest serverHttpRequest,
      ServerHttpResponse serverHttpResponse) {}

  default boolean isFilterEnabled() {
    return true;
  }

  default IOException getException() {
    return null;
  }
}
