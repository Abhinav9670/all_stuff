package org.styli.services.order.repository.SalesOrder;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.sales.SalesOrderItem;

public interface SalesOrderItemRepository
        extends JpaRepository<SalesOrderItem, Integer>, JpaSpecificationExecutor<SalesOrderItem> {

    SalesOrderItem findByItemId(Integer itemId);
    
    @Query("SELECT soi FROM SalesOrderItem soi WHERE soi.salesOrder.entityId = :orderId AND soi.productType = 'configurable'")
    List<SalesOrderItem> findSalesOrderItemConfigurableByOrderId(@Param("orderId") Integer orderId);
}