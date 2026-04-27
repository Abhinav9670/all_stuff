package org.styli.services.order.repository.SalesOrder;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.sales.SplitSalesOrderItem;

public interface SplitSalesOrderItemRepository
        extends JpaRepository<SplitSalesOrderItem, Integer>, JpaSpecificationExecutor<SplitSalesOrderItem> {

    SplitSalesOrderItem findByItemId(Integer itemId);
    
    @Query(value = "SELECT * FROM split_sales_order_item AS ssoi "
                + "WHERE ssoi.split_order_id = :splitOrderId AND ssoi.product_type = 'configurable'", nativeQuery = true)
    List<SplitSalesOrderItem> findSalesOrderItemConfigurableByOrderId(@Param("splitOrderId") Integer splitOrderId);
    
    @Query("SELECT ssoi FROM SplitSalesOrderItem ssoi WHERE ssoi.splitSalesOrder.entityId = :splitOrderId")
    List<SplitSalesOrderItem> findBySplitSalesOrderEntityId(@Param("splitOrderId") Integer splitOrderId);

    /**
     * Find split_sales_order_item by order_id, product_type = 'configurable' and sku.
     * Used to resolve which split (shipment) a return item belongs to for clawback OriginalTransactionNumber.
     * select split_order_id from split_sales_order_item where order_id = :orderId and product_type = 'configurable' and sku = :sku
     */
    SplitSalesOrderItem findFirstBySalesOrder_EntityIdAndProductTypeAndSku(Integer orderId, String productType, String sku);
}