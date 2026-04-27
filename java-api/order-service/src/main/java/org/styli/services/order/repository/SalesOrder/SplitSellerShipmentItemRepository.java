package org.styli.services.order.repository.SalesOrder;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.styli.services.order.model.sales.SplitSellerShipmentItem;

@Repository
public interface SplitSellerShipmentItemRepository extends JpaRepository<SplitSellerShipmentItem, Integer> {

    /**
     * Find shipment items by parent shipment ID
     */
    @Query("SELECT si FROM SplitSellerShipmentItem si WHERE si.splitSellerShipment.entityId = :parentId")
    List<SplitSellerShipmentItem> findByParentId(@Param("parentId") Integer parentId);

    /**
     * Find shipment items by product ID
     */
    List<SplitSellerShipmentItem> findByProductId(Integer productId);

    /**
     * Find shipment items by order item ID
     */
    List<SplitSellerShipmentItem> findByOrderItemId(Integer orderItemId);

    /**
     * Find shipment items by SKU
     */
    List<SplitSellerShipmentItem> findBySku(String sku);

    /**
     * Find shipment items by product name
     */
    List<SplitSellerShipmentItem> findByNameContainingIgnoreCase(String name);

    /**
     * Find shipment items by parent shipment and product ID
     */
    @Query("SELECT si FROM SplitSellerShipmentItem si WHERE si.splitSellerShipment.entityId = :parentId AND si.productId = :productId")
    List<SplitSellerShipmentItem> findByParentIdAndProductId(@Param("parentId") Integer parentId, @Param("productId") Integer productId);

    /**
     * Get total quantity for a specific shipment
     */
    @Query("SELECT SUM(si.quantity) FROM SplitSellerShipmentItem si WHERE si.splitSellerShipment.entityId = :parentId")
    Double getTotalQuantityByParentId(@Param("parentId") Integer parentId);

    /**
     * Get total value for a specific shipment
     */
    @Query("SELECT SUM(si.rowTotal) FROM SplitSellerShipmentItem si WHERE si.splitSellerShipment.entityId = :parentId")
    Double getTotalValueByParentId(@Param("parentId") Integer parentId);
}
