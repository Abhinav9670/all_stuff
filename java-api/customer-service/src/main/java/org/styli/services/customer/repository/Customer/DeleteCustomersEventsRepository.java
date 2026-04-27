package org.styli.services.customer.repository.Customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.customer.model.DeleteCustomersEventsEntity;

import java.util.List;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 15/06/2022 - 12:25 PM
 */

@Repository
public interface DeleteCustomersEventsRepository extends JpaRepository<DeleteCustomersEventsEntity, Integer> {

    List<DeleteCustomersEventsEntity> findByCustomerId(Integer customerId);

}
