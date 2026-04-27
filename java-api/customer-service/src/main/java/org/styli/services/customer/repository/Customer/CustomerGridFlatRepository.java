package org.styli.services.customer.repository.Customer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.styli.services.customer.model.CustomerGridFlat;

public interface CustomerGridFlatRepository
		extends MongoRepository<CustomerGridFlat, Integer> {

	CustomerGridFlat findByEntityId(Integer customerId);
	
}