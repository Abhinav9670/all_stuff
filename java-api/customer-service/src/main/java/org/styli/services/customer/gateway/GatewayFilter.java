package org.styli.services.customer.gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.styli.services.customer.exception.ForbiddenException;
import org.styli.services.customer.limiter.IpLimiterWorker;
import org.styli.services.customer.limiter.LoginLimiterWorker;
import org.styli.services.customer.limiter.RegistrationLimiterWorker;
import org.styli.services.customer.limiter.SendOtpLimiterWorker;
import org.styli.services.customer.pojo.otp.OtpResponseBody;
import org.styli.services.customer.pojo.otp.SendOtpResponse;
import org.styli.services.customer.pojo.registration.response.CustomerLoginV4Response;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created on 29-Jun-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@ControllerAdvice
@Component
public class GatewayFilter implements Filter, ResponseBodyAdvice<Object> {

	@Autowired IpLimiterWorker ipLimiterWorker;
  @Autowired LoginLimiterWorker loginLimiterWorker;
  @Autowired RegistrationLimiterWorker registrationWorker;
  @Autowired SendOtpLimiterWorker sendOtpWorker;


  @Override
  public void doFilter(
      ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
      throws IOException, ServletException {
    boolean shouldDoFilter = true;
    if (servletRequest instanceof HttpServletRequest) {
      HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
      final HttpServletRequest requestToUse;

      String contentType = httpRequest.getContentType();
      if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
        // Do not wrap multipart requests: reading the body as text causes MalformedInputException for binary file uploads
        requestToUse = httpRequest;
      } else {
        requestToUse = new HttpRequestWrapper(httpRequest);
      }

      shouldDoFilter = checkWorker(ipLimiterWorker, requestToUse, servletResponse);
      if (!shouldDoFilter) return;
      shouldDoFilter = checkWorker(loginLimiterWorker, requestToUse, servletResponse);
      if (!shouldDoFilter) return;
      shouldDoFilter = checkWorker(registrationWorker, requestToUse, servletResponse);
      if (!shouldDoFilter) return;
      shouldDoFilter = checkWorker(sendOtpWorker, requestToUse, servletResponse);
      if (!shouldDoFilter) return;

      filterChain.doFilter(requestToUse, servletResponse);
    } else {
      filterChain.doFilter(servletRequest, servletResponse);
    }
  }

  @Override
  public boolean supports(
      MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter methodParameter,
      MediaType mediaType,
      Class<? extends HttpMessageConverter<?>> aClass,
      ServerHttpRequest serverHttpRequest,
      ServerHttpResponse serverHttpResponse) {
    if (body instanceof CustomerLoginV4Response) {
      loginLimiterWorker.beforeBodyWrite(body, serverHttpRequest, serverHttpResponse);
    } else if (body instanceof OtpResponseBody
        && ((OtpResponseBody) body).getResponse() instanceof SendOtpResponse) {
      sendOtpWorker.beforeBodyWrite(body, serverHttpRequest, serverHttpResponse);
    }
    return body;
  }

  private boolean checkWorker(
      FilterWorker worker, HttpServletRequest request, ServletResponse response)
      throws IOException {
    boolean status = true;
    if (worker != null && request != null && response != null && worker.isFilterEnabled()) {
      status = worker.doFilter(request);
      if (!status) {
        IOException exception = loginLimiterWorker.getException();
        if (exception == null) exception = new ForbiddenException();
        writeResponseException(response, exception);
      }
    }
    return status;
  }

  private void writeResponseException(ServletResponse servletResponse, IOException ex)
      throws IOException {
    if (servletResponse instanceof HttpServletResponse) {
      HttpServletResponse response = ((HttpServletResponse) servletResponse);
      HttpStatus status =
          ((ex instanceof ForbiddenException)
              ? ForbiddenException.HTTP_STATUS
              : HttpStatus.INTERNAL_SERVER_ERROR);
      response.sendError(status.value(), ex.getMessage());
    } else if (ex != null) {
      throw ex;
    }
  }
}
