package org.styli.services.customer.repository.Customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.customer.pojo.DeleteCustomerEntity;

import java.util.Date;
import java.util.List;

@Repository
public interface DeleteCustomerEntityRepository extends JpaRepository<DeleteCustomerEntity, Integer> {

	DeleteCustomerEntity findByCustomerId(Integer customerId);

	List<DeleteCustomerEntity> findAllByTtlTimeLessThanAndMarkedForDeleteAndCronProcessedNot(
			Date ttlTime,
			Integer markedForDelete,
			Integer cronProcessed);

	List<DeleteCustomerEntity> findAllByCronProcessedAndCompletedAt(Integer cronProcessed, Date completedAt);
}
