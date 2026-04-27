package org.styli.services.customer.service;

import org.springframework.stereotype.Service;
import org.styli.services.customer.pojo.otp.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * Created on 06-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Service
public interface OtpService {

    static final String CACHE_NAME = "otp-bucket"; 

    OtpResponseBody<SendOtpResponse> sendOtp(Map<String, String> requestHeader, SendOtpRequest request,  String flowType);
    
    OtpResponseBody<SendOtpResponse> sendOtpMSiteMobile(Map<String, String> requestHeader, SendOtpRequest request);

    OtpResponseBody<ValidateOtpResponse> validateOtp(Map<String, String> requestHeader, ValidateOtpRequest request);
    
    public OtpResponseBody<ValidateOtpResponse> validateOtpMSiteMobile(
    	      Map<String, String> requestHeader, ValidateOtpRequest request);
    
    OtpResponseBody<SendOtpResponse> sendOtpViaEmail(Map<String, String> requestHeader, SendOtpRegistrationRequest request , String flowType);
    
    OtpResponseBody<ValidateOtpResponse> validateRegistrationOtp(Map<String, String> requestHeader, ValidateOtpRegistrationRequest request , Object userIdentifierType);
   
	Object isValidUserIdentifier(String userIdentifier);
	
	public void saveVerificationStatusInRedis(String verificationType, String value);
	
	public Boolean getVerificationStatusFromRedis(String userIdentifier);

    public OtpResponseBody<ValidateOtpResponse> validateOtpRationalisation(Map<String, String> requestHeader, ValidateOtpRequest request);

    OtpResponseBody<SendOtpResponse> sendOtpRationalisation(Map<String, String> requestHeader, SendOtpRequest request, String flowType, Boolean skipEmailOtp);

    SendOtpRequest buildSendOtpRequestFromRegistration(@Valid SendOtpRegistrationRequest request);
}
