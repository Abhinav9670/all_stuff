package org.styli.services.customer.repository.Customer;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntity;

@Repository
public interface CustomerAddressEntityRepository extends JpaRepository<CustomerAddressEntity, Integer> {

	@Modifying
	@Transactional
	@Query("DELETE CustomerAddressEntity q WHERE q.entityId = ?1 AND q.parentId=?2")
	void deleteByEntityIdAndCustomerId(Integer entityId, Integer customerId);

	@Query("select cae from CustomerAddressEntity cae where cae.parentId = :#{#customerId}")
	List<CustomerAddressEntity> findAllByCustomerId(@Param("customerId") Integer customerId);

	CustomerAddressEntity findByEntityId(Integer addressId);

	CustomerAddressEntity findByEntityIdAndParentId(Integer entityId, Integer parentId);

	@Transactional
	void deleteByParentId(Integer customerId);
}
