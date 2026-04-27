package org.styli.services.customer.service.impl.Address;

import org.springframework.dao.DataAccessException;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.address.response.CustomerAddreesResponse;
import org.styli.services.customer.pojo.address.response.CustomerAddressBody;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;

public class DeleteAddress {

    public CustomerAddreesResponse delete(CustomerAddrees customerAddRequest,
            CustomerEntityRepository customerEntityRepository,
            CustomerAddressEntityRepository customerAddressEntityRepository) {
        CustomerAddreesResponse response = new CustomerAddreesResponse();
        CustomerEntity customer = null;

        try {

            if (customerAddressEntityRepository.existsById(customerAddRequest.getAddressId())
                    && customerEntityRepository.existsById(customerAddRequest.getCustomerId())) {

                customerAddressEntityRepository.deleteByEntityIdAndCustomerId(customerAddRequest.getAddressId(),
                        customerAddRequest.getCustomerId());

                customer = customerEntityRepository.findByEntityId(customerAddRequest.getCustomerId());

            } else {

                response.setStatus(false);
                response.setStatusCode("201");
                response.setStatusMsg("Invalid Address/Customer ID");

                return response;
            }
            if (null != customer.getDefaultShipping()
                    && customer.getDefaultShipping().equals(customerAddRequest.getAddressId())) {

                customer.setDefaultShipping(null);
                customer.setDefaultBilling(null);
                customerEntityRepository.save(customer);
            }

            CustomerAddressBody responseBody = new CustomerAddressBody();

            responseBody.setMessage("Address ID " + customerAddRequest.getAddressId() + " Deleted Successfully");
            response.setResponse(responseBody);
            response.setStatus(true);
            response.setStatusCode("200");
            response.setStatusMsg("SUCCESS");

        } catch (DataAccessException exception) {

            ErrorType error = new ErrorType();

            error.setErrorCode("400");
            error.setErrorMessage(exception.getMessage());

            response.setStatus(false);
            response.setStatusCode("204");
            response.setStatusMsg("ERROR");
            response.setError(error);
        }

        return response;
    }

}
