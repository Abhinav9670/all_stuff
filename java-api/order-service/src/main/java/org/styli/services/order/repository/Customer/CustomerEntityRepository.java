package org.styli.services.order.repository.Customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.order.model.Customer.CustomerEntity;

@Repository
public interface CustomerEntityRepository extends JpaRepository<CustomerEntity, Integer> {


//	@Query("SELECT CASE WHEN COUNT(u) > 0 THEN 'true' ELSE 'false' END FROM CustomerEntity u WHERE u.entityId = ?1")
//	boolean exitsById(Integer customerId);

	//CustomerEntity findByEmail(String email);

	//CustomerEntity findByEntityId(Integer customerId);

    CustomerEntity findByPhoneNumber(String phoneNumber);
}
