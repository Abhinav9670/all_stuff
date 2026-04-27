package org.styli.services.customer.repository.Address;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntityVarchar;

@Repository
public interface CustomerAddressEntityVarcharRepository extends JpaRepository<CustomerAddressEntityVarchar, Integer> {

	CustomerAddressEntityVarchar findByEntityIdAndAttributeId(Integer entityId, Integer attributeId);

}
