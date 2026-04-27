package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.order.model.sales.SellerBackOrder;

import java.util.Optional;

@Repository
public interface SellerBackOrderRepository extends JpaRepository<SellerBackOrder, Integer> {
    Optional<SellerBackOrder> findTopBySellerIdAndStatusOrderByEntityIdDesc(Integer sellerId, String status);
}