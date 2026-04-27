package org.styli.services.customer.service.impl;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.pojo.registration.response.CustomerRestPassResponse;
import org.styli.services.customer.pojo.registration.response.PasswordResetResponse;
import org.styli.services.customer.service.Client;

public class ResetPassword {

    private static final String SUCCESS = "SUCCESS";

	public CustomerRestPassResponse reset(String email, Integer storeId, Map<String, String> requestHeader,
            Client client, String magentoBaseUrl) throws CustomerException {
        CustomerRestPassResponse response = new CustomerRestPassResponse();

        String responseString = client.resetPassword(email, storeId, magentoBaseUrl);

        if (StringUtils.isNotBlank(responseString) && responseString.trim().equals("true")) {

            PasswordResetResponse passwordRes = new PasswordResetResponse();
            passwordRes.setValue(true);
            response.setStatus(true);
            response.setStatusCode("200");
            response.setStatusMsg(SUCCESS);

            response.setResponse(passwordRes);

        }
        if (StringUtils.isNotBlank(responseString) && responseString.trim().equals("false")) {

            PasswordResetResponse passwordRes = new PasswordResetResponse();
            passwordRes.setValue(false);
            response.setStatus(true);
            response.setStatusCode("201");
            response.setStatusMsg(SUCCESS);

            response.setResponse(passwordRes);

        }

        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg(SUCCESS);

        return response;

    }

}
