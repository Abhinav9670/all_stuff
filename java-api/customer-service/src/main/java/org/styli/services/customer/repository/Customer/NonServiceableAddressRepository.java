package org.styli.services.customer.repository.Customer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.customer.model.NonServiceableAddress;

@Repository
public interface NonServiceableAddressRepository extends MongoRepository<NonServiceableAddress, String> {


}
