package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import javax.persistence.LockModeType;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrder;

import java.util.List;

@Repository
public interface SplitSalesOrderRepository extends JpaRepository<SplitSalesOrder, Integer> {
    SplitSalesOrder findByIncrementId(String incrementId);
    SplitSalesOrder findByIncrementIdAndStatus(String incrementId, String status);
    List<SplitSalesOrder> findBySalesOrder(SalesOrder salesOrder);
    @Query(value = "select * from  split_sales_order so where so.status in ('processing', 'pending_payment')  and (so.wms_status is null OR so.wms_status = 0) and so.created_at BETWEEN (NOW() - INTERVAL :minutesAgo MINUTE) AND (NOW() - INTERVAL :pushMinutes MINUTE)", nativeQuery = true)
    List<SplitSalesOrder> ordersforwmspush(@Param("minutesAgo") Integer minutesAgo, @Param("pushMinutes")Integer pushMinutes);
    SplitSalesOrder findByEntityId(Integer entityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sso from SplitSalesOrder sso where sso.entityId = :entityId")
    SplitSalesOrder lockByEntityId(@Param("entityId") Integer entityId);
    @Modifying
    @Query(value = "update split_sub_sales_order c set c.extra_2 = :ratingStatus WHERE c.split_order_id = :orderId", nativeQuery = true)
    int updateRatingStatus(@Param("ratingStatus") String ratingStatus, @Param("orderId") Integer orderId);
    @Modifying
    @Query(value = "update split_sales_order set ext_order_id = :status WHERE entity_id = :entityId", nativeQuery = true)
    int updateHoldOrderPushStatus(@Param("status") Integer status, @Param("entityId") Integer entityId);
    @Modifying
    @Query(value = "update split_sales_order set wms_status = :wmsStatus WHERE entity_id = :entityId", nativeQuery = true)
    int updateWMSStatus(@Param("wmsStatus") Integer wmsStatus, @Param("entityId") Integer entityId);
    @Query(value = "SELECT * FROM split_sales_order WHERE order_id = :orderId", nativeQuery = true)
    List<SplitSalesOrder> findByOrderId(Integer orderId);
    
    @Query(value = "SELECT sso FROM SplitSalesOrder sso " +
                   "LEFT JOIN FETCH sso.splitSubSalesOrder " +
                   "LEFT JOIN FETCH sso.splitSalesOrderPayments " +
                   "WHERE sso.salesOrder.entityId = :orderId")
    List<SplitSalesOrder> findByOrderIdWithRelationships(@Param("orderId") Integer orderId);
    @Query(value = "select sa.order_id from split_sales_order sa where  sa.store_id in ?2 and sa.customer_id=?1 \n"
            + " and sa.entity_id not in (select sa.entity_id from split_sales_order sa inner join  split_sales_order sa2 on sa.customer_id = sa2.customer_id where sa.status != sa2.status \n"
            + " and sa.status = 'payment_failed' and sa2.status != 'payment_failed' ) ", nativeQuery = true)
    List<Integer> findSalesOrderIdsOfSplitSalesOrders(Integer customerId, List<Integer> storeIds);
    @Query(value = "select * from split_sales_order sa where  sa.store_id in ?2 and sa.customer_id=?1"
            + " and sa.status != 'corrupted'", nativeQuery = true)
    List<SplitSalesOrder> findByCustomerIdAndStoreIdIn(Integer customerId, List<Integer> storeIds, Pageable page);
    @Query(value = "select sa.order_id from split_sales_order sa where  sa.store_id in ?2 and sa.customer_email = ?1 \n" +
            " and sa.entity_id not in (select sa.entity_id from split_sales_order sa inner join  split_sales_order sa2 on sa.quote_id = sa2.quote_id where sa.status != sa2.status \n" +
            " and sa.status = 'payment_failed' and sa2.status not in ('payment_failed','corrupted','fraud','payfort_fort_failed','payment_canceled') )", nativeQuery = true)
    List<Integer>  findSalesOrderIdsOfSplitSalesOrdersWithCustomerEmail(String customerEmail, List<Integer> storeIds);

    @Query(value = "select * from split_sales_order where entity_id = :entityId and customer_id = :customerId", nativeQuery = true)
    SplitSalesOrder findByEntityIdAndCustomerId(Integer entityId,Integer customerId);

    @Query(value = "SELECT COUNT(1) FROM split_sales_order_payment sop " +
	               "JOIN split_sales_order so ON so.entity_id = sop.parent_id " +
	               "JOIN split_sales_order_item soi ON soi.order_id = so.entity_id " +
	               "WHERE JSON_EXTRACT(sop.additional_information, '$.payment_option') = 'MADA' " +
	               "AND so.entity_id = :orderId", nativeQuery = true)
	    int checkIfMadaTransaction(@Param("orderId") int orderId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE split_sales_order SET status = :status, state = :state, updated_at = :updatedAt WHERE order_id = :orderId", nativeQuery = true)
    int updateStatusAndStateByOrderId(@Param("status") String status, @Param("state") String state, @Param("updatedAt") java.sql.Timestamp updatedAt, @Param("orderId") Integer orderId);

    @Query(value = "SELECT * FROM split_sales_order WHERE increment_id IN (:incrementIds)", nativeQuery = true)
    List<SplitSalesOrder> findByIncrementIdIn(List<String> incrementIds);

    @Query(value = "select * from split_sales_order so where so.wms_status in(2) and so.status in ?1 and so.updated_at BETWEEN (NOW() - INTERVAL ?2 MINUTE) AND (NOW())", nativeQuery = true)
    List<SplitSalesOrder> cancelledOrderforwmspush(@Param("statusList") List<String> statusList, @Param("minutesAgo") Integer minutesAgo);

    @Modifying
    @Query(value = "update split_sales_order set ext_order_id = :status WHERE entity_id = :entityId", nativeQuery = true)
    int updateHoldOrderPushStatusForSplitOrder(@Param("status") Integer status, @Param("entityId") Integer entityId);

    @Query(value = "select * from  split_sales_order so where so.status in ('processing') and (so.wms_status = 7 or ext_order_id = 1)and so.created_at BETWEEN (NOW() - INTERVAL :minutesAgo MINUTE) AND (NOW())", nativeQuery = true)
    List<SplitSalesOrder> ordersHoldwmspushForSplitOrder( @Param("minutesAgo") Integer minutesAgo);
		
}
