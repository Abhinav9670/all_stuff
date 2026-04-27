package org.styli.services.customer.service.impl;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.styli.services.customer.pojo.registration.request.CustomerQueryReq;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerExistResponse;
import org.styli.services.customer.pojo.registration.response.CustomerIsExistsBody;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.utility.CommonUtility;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import lombok.extern.apachecommons.CommonsLog;

import java.util.Map;
import java.util.Objects;

@Component
public class ValidateUser {
    private static final Log LOGGER = LogFactory.getLog(ValidateUser.class);

    Map<Integer, String> attributeMap;


    public CustomerExistResponse validate(CustomerQueryReq customerExitsReq, Map<String, String> requestHeader, Client clinet) {

        CustomerExistResponse response = new CustomerExistResponse();
        CustomerIsExistsBody responseBody = new CustomerIsExistsBody();

        CustomerEntity customerEntity = null;

        try {

            if (StringUtils.isBlank(customerExitsReq.getUseridentifier())) {

                response.setStatus(false);
                response.setStatusCode("201");
                response.setStatusMsg("Bad Request!!");

                return response;
            }

            /**
             * Assuming loginType EMAIL, GOOGLELOGIN, APPLELOGIN all requires email address to validate user
             */
            if (null != customerExitsReq.getLoginType()
                    && (
                            Constants.EMAIL.equals(customerExitsReq.getLoginType().value)
                            || Constants.GOOGLELOGIN.equals(customerExitsReq.getLoginType().value)
                            || Constants.APPLELOGIN.equals(customerExitsReq.getLoginType().value))
            ) {

                LOGGER.info("LoginType Email:");

                if (!CommonUtility.isValidEmailAddress(customerExitsReq.getUseridentifier())) {

                    response.setStatus(false);
                    response.setStatusCode("300");
                    response.setStatusMsg("Invalid Email ID");

                    return response;
                }

                customerEntity = clinet.findByEmail(customerExitsReq.getUseridentifier());

                if (null == customerEntity) {

                    responseBody.setExists(false);
                    responseBody.setMesage("Email ID Doesn't Exist");

                    response.setResponse(responseBody);
                    response.setStatus(true);
                    response.setStatusCode("201");
                    response.setStatusMsg(Constants.SUCCESS_MSG);

                    return response;
                }

            } else if (null != customerExitsReq.getLoginType()
                    && Constants.MOBILE.equals(customerExitsReq.getLoginType().value)) {

                LOGGER.info("LoginType Mobile:");

                customerEntity = clinet.findByPhoneNumber(customerExitsReq.getUseridentifier());

                if (null == customerEntity) {

                    responseBody.setExists(false);
                    responseBody.setMesage("Mobile Number Doesn't Exist");
                    response.setResponse(responseBody);
                    response.setStatus(true);
                    response.setStatusCode("201");
                    response.setStatusMsg(Constants.SUCCESS_MSG);

                    return response;
                }
            }

            if (null != customerEntity) {

                responseBody.setExists(true);
                responseBody.setEmail(customerEntity.getEmail());
                responseBody.setMobileNumber(customerEntity.getPhoneNumber());
                responseBody.setSignedInNowUsing(customerEntity.getSignedInNowUsing());
                responseBody.setPasswordAvailable(ObjectUtils.isNotEmpty(customerEntity.getPasswordHash()));
                if(customerEntity.getIsUserConsentProvided() != null){
                    responseBody.setIsUserConsentProvided(customerEntity.getIsUserConsentProvided());
                }
				if (Objects.isNull(customerEntity.getIsPhoneNumberVerified())) {
					responseBody.setIsVerifyMobileNumber(true);
					response.setIsVerifyMobileNumber(true);
				} else {
					responseBody.setIsVerifyMobileNumber(customerEntity.getIsPhoneNumberVerified());
					response.setIsVerifyMobileNumber(customerEntity.getIsPhoneNumberVerified());
				}
                
                String source = null != requestHeader ? requestHeader.get(Constants.HEADER_X_SOURCE) : null;
        		
                if(null != source
        				&& ((Constants.SOURCE_MSITE.equals(source) && "true".equals(ServiceConfigs.getOtpMaskEnabledForMsite()))
        						|| ((Constants.SOURCE_MOBILE_ANDROID.equals(source) || Constants.SOURCE_MOBILE_IOS.equals(source))
        								&& "true".equals(ServiceConfigs.getOtpMaskEnabledForMobile())))) {
        			
        			if (null != customerExitsReq.getLoginType() 
                    		&& Constants.EMAIL.equals(customerExitsReq.getLoginType().value) 
                    		&& !StringUtils.isEmpty(customerEntity.getPhoneNumber())) {
                    	
                    	responseBody.setMobileNumber(StringUtils.overlay(customerEntity.getPhoneNumber(), "XXXX", 7, 12));
                    	
                    } else if (null != customerExitsReq.getLoginType()
                    		&& Constants.MOBILE.equals(customerExitsReq.getLoginType().value) ) {
                    	
                    	responseBody.setMobileNumber(customerEntity.getPhoneNumber());
                    	responseBody.setEmail("");
                    }
        		}
                responseBody.setIsMobileVerified(customerEntity.getIsMobileVerified() != null ? customerEntity.getIsMobileVerified() : false);
                responseBody.setIsEmailVerified(customerEntity.getIsEmailVerified() != null ? customerEntity.getIsEmailVerified() : false);

                response.setResponse(responseBody);
                response.setStatus(true);
                response.setStatusCode("200");
                response.setStatusMsg(Constants.SUCCESS_MSG);

            } else {

                responseBody.setExists(false);
                responseBody.setMesage("Something Went Wrong !!");

                response.setResponse(responseBody);
                response.setStatus(false);
                response.setStatusCode("202");
                response.setStatusMsg("ERROR !!");
            }

        } catch (DataAccessException ex) {

            response = new CustomerExistResponse();
            ErrorType error = new ErrorType();

            error.setErrorCode("400");
            error.setErrorMessage(ex.getMessage());

            response.setStatus(false);
            response.setStatusCode("204");
            response.setStatusMsg("ERROR");
            response.setError(error);

        }

        return response;

    }

}
