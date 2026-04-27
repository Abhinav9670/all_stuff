package org.styli.services.customer.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.styli.services.customer.pojo.epsilon.request.ShukranEnrollmentRequest;
import org.styli.services.customer.pojo.registration.request.CustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerProfileResponse;
import org.styli.services.customer.pojo.registration.response.CustomerResponse;

@Service
public interface ExternalServiceAdapter {
    static final String CACHE_NAME = "epsilon-bucket";

    ResponseEntity<String> getEpsilonProfile(String mobileNo,Integer storeId);

    ResponseEntity<String> createShukranAccount(ShukranEnrollmentRequest shukranEnrollmentRequest, Customer customer);

    ResponseEntity<String> updateEpsilonProfile(CustomerUpdateProfileRequest customerInfoRequest,String profileId);

    ResponseEntity<String> linkShukranAccount(String profileId);
}
