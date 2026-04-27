package org.styli.services.order.repository.Rma;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.AmastyStoreCredit;

import java.util.List;

public interface AmastyStoreCreditRepository
        extends JpaRepository<AmastyStoreCredit, Integer>, JpaSpecificationExecutor<AmastyStoreCredit> {

    List<AmastyStoreCredit> findByCustomerId(Integer customerId);

    List<AmastyStoreCredit> findByCustomerIdIn(List<Integer> customerIds);
}