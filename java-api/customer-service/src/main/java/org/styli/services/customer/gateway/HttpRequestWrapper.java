package org.styli.services.customer.gateway;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Created on 30-Jun-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
public class HttpRequestWrapper extends HttpServletRequestWrapper {

  private String _body;
  private HttpServletRequest _request;

  public HttpRequestWrapper(HttpServletRequest request) throws IOException {
    super(request);
    _request = request;
    _body = _request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    final ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(getBody().getBytes());
    ServletInputStream servletInputStream =
        new ServletInputStream() {

          @Override
          public int read() throws IOException {
            return byteArrayInputStream.read();
          }

          @Override
          public boolean isFinished() {
            return false;
          }

          @Override
          public boolean isReady() {
            return false;
          }

          @Override
          public void setReadListener(ReadListener listener) {
        	  //overriding setReadListener
          }
        };
    return servletInputStream;
  }

  @Override
  public BufferedReader getReader() throws IOException {
    return new BufferedReader(new InputStreamReader(this.getInputStream()));
  }

  public String getBody() {
    return this._body;
  }
}
