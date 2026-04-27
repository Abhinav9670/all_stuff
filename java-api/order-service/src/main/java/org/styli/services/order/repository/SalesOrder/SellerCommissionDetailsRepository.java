package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.order.model.SalesOrder.SellerCommissionDetails;

import java.util.Optional;

@Repository
public interface SellerCommissionDetailsRepository extends JpaRepository<SellerCommissionDetails, Integer> {
    Optional<SellerCommissionDetails> findTopBySellerIdAndL4CategoryOrderByIdDesc(Integer sellerId, String l4Category);
    Optional<SellerCommissionDetails> findTopBySellerIdOrderByIdDesc(Integer sellerId);
}



