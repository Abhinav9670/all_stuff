package org.styli.services.customer.service.impl;

import java.util.Map;

import org.styli.services.customer.pojo.registration.request.WhatsAppOtpRequest;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.WhatsAppOptResBody;
import org.styli.services.customer.pojo.registration.response.WhatsAppOptResponse;
import org.styli.services.customer.service.Client;

public class WhatsAppOpt {

    Map<Integer, String> attributeMap;

    public WhatsAppOptResponse opt(Map<String, String> httpServletRequest, WhatsAppOtpRequest request, Client client) {

        WhatsAppOptResponse response = new WhatsAppOptResponse();
        CustomerEntity customerObject = client.findByEntityId(request.getCustomerId());

        if (null != customerObject) {

        	if (request.getRequestFlag()) {
        		customerObject.setWhatsappOptn(1);
            } else {
            	customerObject.setWhatsappOptn(0);
            }
        	client.saveAndFlushCustomerEntity(customerObject);

       } else {

            response.setStatus(false);
            response.setStatusCode("201");
            response.setStatusMsg("Invalid Customer ID!");

            return response;
        }

        WhatsAppOptResBody resBody = new WhatsAppOptResBody();

        resBody.setStatusFlag(request.getRequestFlag());
        resBody.setCustomerId(request.getCustomerId());
        response.setResponse(resBody);

        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg("SUCCESS!!");

        return response;
    }

}
