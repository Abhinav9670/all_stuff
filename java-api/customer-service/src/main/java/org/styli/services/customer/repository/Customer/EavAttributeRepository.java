package org.styli.services.customer.repository.Customer;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.styli.services.customer.model.EavAttribute;

@Repository
public interface EavAttributeRepository extends JpaRepository<EavAttribute, Integer> {

    
    @Query(value = "SELECT eav FROM EavAttribute eav where eav.entityTypeId in (1,2,3,4)")
    List<EavAttribute> getEavAttributes();

    EavAttribute findByAttributeId(Integer superAttributeId);

}

