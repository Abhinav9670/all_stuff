package org.styli.services.customer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.styli.services.customer.model.VaultPaymentToken;

import java.util.List;

public interface VaultPaymentTokenRepository
        extends JpaRepository<VaultPaymentToken, Integer>, JpaSpecificationExecutor<VaultPaymentToken> {

    List<VaultPaymentToken> findByCustomerId(Integer Id);

    List<VaultPaymentToken> findByCustomerIdAndActiveAndVisible(Integer Id, Integer active, Integer visible);

    @Modifying
    @Transactional
    @Query("DELETE VaultPaymentToken q WHERE q.entityId = ?1 and q.customerId = ?2 ")
    void deleteByEntityId(Integer entityId, Integer customerId);
    
    List<VaultPaymentToken> findByEntityIdAndCustomerId(Integer entityId, Integer customerId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE vault_payment_token v  " +
            "SET v.is_active = 0, v.is_visible = 0 " +
            "WHERE v.expires_at IS NOT NULL " +
            "AND ( " +
            "    (:expiryInHours IS NULL AND v.expires_at < NOW()) OR " +
            "    (:expiryInHours IS NOT NULL AND v.expires_at BETWEEN DATE_SUB(NOW(), INTERVAL :expiryInHours HOUR) AND NOW()) " +
            ") " +
            "AND (:customerId IS NULL OR v.customer_id = :customerId) AND v.is_active = 1 AND v.is_visible = 1", nativeQuery = true)
    int deactivateAllExpiredTokens(@Param("customerId") Integer customerId,
                               @Param("expiryInHours") Integer expiryInHours);

}