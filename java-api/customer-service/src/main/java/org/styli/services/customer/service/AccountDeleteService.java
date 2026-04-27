package org.styli.services.customer.service;

import org.springframework.stereotype.Service;
import org.styli.services.customer.pojo.account.*;
import org.styli.services.customer.pojo.registration.response.Customer;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 10/06/2022 - 12:25 PM
 */

@Service
public interface AccountDeleteService {
    AccountDeleteResponse sendOTP(AccountDeletionOTPRequest request, String tokenHeader, String xHeaderToken);
    
    AccountDeleteResponse deleteOrWithdrawCustomerAccount(AccountDeletionRequest request);
    
    AccountDeletionEligibleResponse checkAccountDeletionEligiblity(AccountDeletionEligibleRequest request, String tokenHeader, String xHeaderToken) throws JsonProcessingException;

    AccountDeleteResponse processDeleteRequests();

    AccountDeleteResponse processStatusUpdates(AccountDeleteTaskUpdateRequest request);
    
    void revokeAppleAuth(Customer customer);

    AccountDeleteResponse processDeleteRequestsCleanup();
}
