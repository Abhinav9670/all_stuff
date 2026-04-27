package org.styli.services.order.repository.SalesOrder;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;

import org.styli.services.order.model.sales.SplitSellerOrder;

@Repository
public interface SplitSellerOrderRepository extends JpaRepository<SplitSellerOrder, Integer> {

    @EntityGraph(attributePaths = {"splitOrder"})
    @Query("SELECT sso FROM SplitSellerOrder sso JOIN FETCH sso.splitOrder " +
           "WHERE sso.splitOrder.status IN ('processing', 'pending_payment') " +
           "AND sso.splitOrder.wmsStatus IS NULL " +
           "AND sso.createdAt BETWEEN :startTime AND :endTime")
    List<SplitSellerOrder> ordersforwmspush(@Param("startTime") java.sql.Timestamp startTime, @Param("endTime") java.sql.Timestamp endTime);

    SplitSellerOrder findByEntityId(Integer entityId);
    
    // Native query similar to SplitSalesOrderRepository.ordersforwmspush
    @Query(value = "select so.* from split_seller_order so " +  
            "where so.status in ('processing') and (so.wms_status is null OR so.wms_status = 0)" +
            " AND (so.warehouse_id is not null AND so.warehouse_id in (:includeWarehouseIds)) "+
            "and so.created_at BETWEEN (NOW() - INTERVAL :minutesAgo MINUTE) AND (NOW() - INTERVAL :pushMinutes MINUTE)", nativeQuery = true)
    List<SplitSellerOrder> ordersforwmspushNative(@Param("minutesAgo") Integer minutesAgo, @Param("pushMinutes") Integer pushMinutes,@Param("includeWarehouseIds") List<String> includeWarehouseIds);
    
    // Method to get split_order_id by entity_id
    @Query(value = "SELECT split_order_id FROM split_seller_order WHERE entity_id = :entityId", nativeQuery = true)
    Integer getSplitOrderIdByEntityId(@Param("entityId") Integer entityId);

    // Method to get sales_order_id by entity_id
    @Query(value = "SELECT main_order_id FROM split_seller_order WHERE entity_id = :entityId", nativeQuery = true)
    Integer getSalesOrderIdByEntityId(@Param("entityId") Integer entityId);

    @Modifying
    @Query(value = "update split_seller_order set ext_order_id = :status WHERE entity_id = :entityId", nativeQuery = true)
    void updateHoldOrderPushStatus(@Param("status") Integer status, @Param("entityId") Integer entityId);

    @Modifying
    @Query(value = "update split_seller_order set ext_order_id = :status WHERE entity_id IN :entityIds", nativeQuery = true)
    void updateHoldOrderPushStatusOfSellerOrders(@Param("status") Integer status, @Param("entityIds") List<Integer> entityIds);

    @Modifying
    @Query(value = "update split_seller_order set wms_status = :wmsStatus WHERE entity_id = :entityId", nativeQuery = true)
    void updateWMSStatus(@Param("wmsStatus") Integer wmsStatus, @Param("entityId") Integer entityId);

    @Modifying
    @Query(value = "update split_seller_order set wms_status = :wmsStatus WHERE entity_id IN :entityIds", nativeQuery = true)
    void updateWMSStatusOfSellerOrders(@Param("wmsStatus") Integer wmsStatus, @Param("entityIds") List<Integer> entityIds);
    
    SplitSellerOrder findByIncrementId(String incrementId);
    
    List<SplitSellerOrder> findByIncrementIdIn(List<String> incrementIds);
    
    @Query(value = "SELECT seller_order_id FROM split_seller_order_item WHERE sales_order_item_id IN(:itemIds)", nativeQuery = true)
    List<Integer> findSellerOrderIdBySalesOrderItemId(@Param("itemIds") List<Integer> itemIds);

   @Query(value = "select * from split_seller_order so where so.wms_status in (2) and so.status in (:statusList) and so.updated_at BETWEEN (NOW() - INTERVAL :minutesAgo MINUTE) AND (NOW()) AND (so.warehouse_id is not null AND so.warehouse_id not in (:excludeWarehouseIds))", nativeQuery = true)
   List<SplitSellerOrder> cancelledOrderforwmspush(@Param("statusList") List<String> statusList, @Param("minutesAgo") Integer minutesAgo, @Param("excludeWarehouseIds") List<String> excludeWarehouseIds);

    // Check if any seller orders exist for a given increment ID
    @Query(
            value = "SELECT * FROM split_seller_order so WHERE so.increment_id = :incrementId",
            nativeQuery = true
    )
    List<SplitSellerOrder> findBySellerOrdersByIncrementId(@Param("incrementId") String incrementId);

    @Modifying
    @Query(value = "update split_seller_order set timelines = :timelines WHERE entity_id = :entityId", nativeQuery = true)
    int updateTimeLines(@Param("timelines") String timelines, @Param("entityId") Integer entityId);

    @Query(value = "SELECT * FROM split_seller_order WHERE split_order_id = :splitOrderId", nativeQuery = true)
    List<SplitSellerOrder> findBySplitOrderId(@Param("splitOrderId") Integer splitOrderId);

    @Query(value = "SELECT * FROM split_seller_order WHERE awb_failed = 1 and updated_at BETWEEN (NOW() - INTERVAL :minutesAgo MINUTE) AND (NOW())", nativeQuery = true)
    List<SplitSellerOrder> findByAwbFailed(@Param("minutesAgo") Integer minutesAgo);

    @Query(value = "SELECT DISTINCT sso.* FROM split_seller_order sso " +
            "INNER JOIN split_seller_order_item ssoi ON sso.entity_id = ssoi.seller_order_id " +
            "WHERE sso.seller_id != :sellerId AND ssoi.sku = :sku LIMIT 1", nativeQuery = true)
    SplitSellerOrder findByNoStyliWarehouseAndSku(@Param("sellerId") String sellerId, @Param("sku") String sku);

    /**
     * Batch query to find non-Styli warehouse orders by multiple SKUs.
     * Returns orders where sellerId != given sellerId and SKU is in the provided list.
     */
    @Query(value = "SELECT DISTINCT sso.* FROM split_seller_order sso " +
            "INNER JOIN split_seller_order_item ssoi ON sso.entity_id = ssoi.seller_order_id " +
            "WHERE sso.seller_id != :sellerId AND ssoi.sku IN (:skus)", nativeQuery = true)
    List<SplitSellerOrder> findByNoStyliWarehouseAndSkuIn(@Param("sellerId") String sellerId, @Param("skus") List<String> skus);
}