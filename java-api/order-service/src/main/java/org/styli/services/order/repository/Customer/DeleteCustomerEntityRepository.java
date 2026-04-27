package org.styli.services.order.repository.Customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.order.model.Customer.DeleteCustomerEntity;

@Repository
public interface DeleteCustomerEntityRepository extends JpaRepository<DeleteCustomerEntity, Integer> {

	DeleteCustomerEntity findByCustomerId(Integer customerId);
}
