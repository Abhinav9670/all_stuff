package org.styli.services.customer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.customer.pojo.registration.response.CustomerEntityMysql;

@Repository
public interface CustomerEntityMysqlRepository extends JpaRepository<CustomerEntityMysql, Integer> {



	CustomerEntityMysql findByEmailIgnoreCase(String email);

	CustomerEntityMysql findByEntityId(Integer customerId);

	CustomerEntityMysql findByEntityIdAndEmail(Integer customerId,String email);

	CustomerEntityMysql findByPhoneNumber(String phoneNumber);
	

}
