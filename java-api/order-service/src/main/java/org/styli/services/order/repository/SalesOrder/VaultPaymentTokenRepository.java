package org.styli.services.order.repository.SalesOrder;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.styli.services.order.model.sales.VaultPaymentToken;

public interface VaultPaymentTokenRepository
        extends JpaRepository<VaultPaymentToken, Integer>, JpaSpecificationExecutor<VaultPaymentToken> {

    List<VaultPaymentToken> findByCustomerId(Integer Id);

    List<VaultPaymentToken> findByCustomerIdAndActiveAndVisible(Integer Id, Integer active, Integer visible);

    @Modifying
    @Transactional
    @Query("DELETE VaultPaymentToken q WHERE q.entityId = ?1")
    void deleteByEntityId(Integer entityId);
    
    @Modifying
    @Query("DELETE VaultPaymentToken q WHERE q.customerId = ?1")
   void deleteByCustomerId(Integer customerId);

}