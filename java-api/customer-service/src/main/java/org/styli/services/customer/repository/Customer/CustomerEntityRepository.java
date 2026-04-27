package org.styli.services.customer.repository.Customer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;

public interface CustomerEntityRepository extends MongoRepository<CustomerEntity, Integer> {

	CustomerEntity findByEmail(String email);

	CustomerEntity findByEntityId(Integer customerId);

	CustomerEntity findByEntityIdAndEmail(Integer customerId, String email);

	CustomerEntity findByPhoneNumber(String phoneNumber);

	CustomerEntity findByCardNumber(String cardNumber);

	@Query("{'$or':[ {'firstName':{$regex: ?0}}, {'lastName':{$regex: ?0}} ] }")
	Page<CustomerEntity> findBy(String firstName, Pageable page);

	Page<CustomerEntity> findByPhoneNumber(String phoneNumber, Pageable page);

	List<CustomerEntity> findAllByEntityIdIn(List<Integer> entityIds);
	
	List<CustomerEntity> findByIdGreaterThan(Integer customerId, Pageable page);
	
	 @Query("{ 'entityId': ?0, 'loginHistories.deviceId': ?1 }")
	 Optional<CustomerEntity> findByLoginHistoriesDeviceId(Integer entityId, String deviceId);

	CustomerEntity findByLoginHistoriesRefreshToken(String refreshToken);

	List<CustomerEntity> findAllByEmail(String email);
	
}
