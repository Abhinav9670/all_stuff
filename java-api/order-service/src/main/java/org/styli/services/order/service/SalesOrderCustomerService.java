package org.styli.services.order.service;

import org.springframework.stereotype.Service;
import org.styli.services.order.pojo.AccountDeletionEligibleRequest;
import org.styli.services.order.pojo.AccountDeletionEligibleResponse;

@Service
public interface SalesOrderCustomerService {
	
	void updateSalesOrdersCustomerId(Integer hoursAgo);
	
	AccountDeletionEligibleResponse checkAccountDeletionEligiblity(AccountDeletionEligibleRequest request);

}
