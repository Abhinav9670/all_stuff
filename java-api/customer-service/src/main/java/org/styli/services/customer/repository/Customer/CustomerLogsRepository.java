package org.styli.services.customer.repository.Customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.customer.model.CustomerLogs;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 05/05/2022 - 2:57 PM
 */

@Repository
public interface CustomerLogsRepository extends JpaRepository<CustomerLogs, Integer> {

}
