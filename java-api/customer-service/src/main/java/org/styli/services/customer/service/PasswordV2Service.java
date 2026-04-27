package org.styli.services.customer.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.request.CustomerQueryReq;
import org.styli.services.customer.pojo.registration.request.TokenPasswordRequest;
import org.styli.services.customer.pojo.registration.response.CustomerLoginV4Response;
import org.styli.services.customer.pojo.registration.response.CustomerRestPassResponse;

/**
 * Created on 06-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Service
public interface PasswordV2Service {

  CustomerRestPassResponse forgotPassword(
      Map<String, String> requestHeader, CustomerQueryReq request) throws CustomerException;

  CustomerRestPassResponse resetTokenPassword(
      Map<String, String> requestHeader, TokenPasswordRequest request);

  CustomerLoginV4Response refreshToken(Map<String, String> requestHeader,  CustomerLoginV4Request request);
}
