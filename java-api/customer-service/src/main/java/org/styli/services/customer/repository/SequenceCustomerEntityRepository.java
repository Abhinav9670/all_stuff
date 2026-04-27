package org.styli.services.customer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.customer.model.SequenceCustomerEntity;
import org.styli.services.customer.model.Store;

public interface SequenceCustomerEntityRepository extends JpaRepository<SequenceCustomerEntity, Integer>, JpaSpecificationExecutor<Store> {

   
}