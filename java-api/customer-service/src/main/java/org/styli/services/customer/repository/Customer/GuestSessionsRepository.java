package org.styli.services.customer.repository.Customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.customer.model.GuestSessions;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 04/05/2022 - 12:20 PM
 */

@Repository
public interface GuestSessionsRepository extends JpaRepository<GuestSessions, Integer> {

    GuestSessions findByDeviceId(String deviceId);

}
