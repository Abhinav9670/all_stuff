package org.styli.services.order.repository.SalesOrder;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.styli.services.order.model.sales.SplitSellerShipment;

@Repository
public interface SplitSellerShipmentRepository extends JpaRepository<SplitSellerShipment, Integer> {

    /**
     * Find shipments by order ID
     */
    List<SplitSellerShipment> findByOrderId(Integer orderId);

    /**
     * Find shipments by split order ID
     */
    List<SplitSellerShipment> findBySplitOrderId(Integer splitOrderId);

    /**
     * Find shipments by seller order ID
     */
    List<SplitSellerShipment> findBySellerOrderId(Integer sellerOrderId);

    /**
     * Find shipments by increment ID
     */
    SplitSellerShipment findByIncrementId(String incrementId);

    /**
     * Find shipments by customer ID
     */
    List<SplitSellerShipment> findByCustomerId(Integer customerId);

    /**
     * Find shipments by shipment status
     */
    List<SplitSellerShipment> findByShipmentStatus(Integer shipmentStatus);

    /**
     * Find shipments by store ID and status
     */
    @Query("SELECT s FROM SplitSellerShipment s WHERE s.storeId = :storeId AND s.shipmentStatus = :status")
    List<SplitSellerShipment> findByStoreIdAndStatus(@Param("storeId") Integer storeId, @Param("status") Integer status);

    /**
     * Find shipments by order ID and status
     */
    @Query("SELECT s FROM SplitSellerShipment s WHERE s.orderId = :orderId AND s.shipmentStatus = :status")
    List<SplitSellerShipment> findByOrderIdAndStatus(@Param("orderId") Integer orderId, @Param("status") Integer status);
}
