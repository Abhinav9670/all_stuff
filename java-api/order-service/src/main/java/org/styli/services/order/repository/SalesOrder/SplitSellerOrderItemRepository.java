package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.styli.services.order.model.sales.SplitSellerOrderItem;

public interface SplitSellerOrderItemRepository extends JpaRepository<SplitSellerOrderItem, Integer> {
    
}
