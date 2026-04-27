package org.styli.services.order.repository.Rma;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.styli.services.order.model.rma.AmastyRmaRequest;

import java.math.BigDecimal;
import java.util.List;

public interface AmastyRmaRequestRepository
        extends JpaRepository<AmastyRmaRequest, Integer>, JpaSpecificationExecutor<AmastyRmaRequest> {


    AmastyRmaRequest findFirstByCustomerIdAndOrderIdOrderByCreatedAtDesc(Integer customerId, Integer orderId);

    List<AmastyRmaRequest> findByCustomerIdAndStoreIdIn(Integer customerId, List<Integer> storeIds, Pageable page);

    AmastyRmaRequest findByRequestId(Integer requestId);
    
    AmastyRmaRequest findByRequestIdAndCustomerId(Integer requestId, Integer customerId);

    AmastyRmaRequest findByRmaIncId(String requestId);

    AmastyRmaRequest findByRmaIncIdAndOrderId(String remIncId,Integer orderId);

    @Modifying
    @Transactional
	@Query("DELETE AmastyRmaRequest am WHERE am.requestId = ?1")
	public void deleteByItemId(Integer requestId);

    @Query(value = "select * from amasty_rma_request so where so.status = 4 and so.created_at BETWEEN (NOW() - INTERVAL :minutesAgo MINUTE) AND (NOW())", nativeQuery = true)
    List<AmastyRmaRequest> requestForReturn( @Param("minutesAgo") Integer minutesAgo);

    @Query(value = "select * from amasty_rma_request as arr left join amasty_rma_tracking as art on art.request_id = arr.request_id where art.tracking_id is null and arr.status not in (12,13) and arr.created_at >= :startDate and arr.created_at <= :endDate LIMIT :limit", nativeQuery = true)
    List<AmastyRmaRequest> createReturnAwb( @Param("startDate") String startDate,  @Param("endDate") String endDate
    		,@Param("limit") Integer limit);
    
    @Query(value = "select * from amasty_rma_request as arr left join amasty_rma_tracking as art on art.request_id = arr.request_id where art.tracking_id is null and arr.status not in (12,13) and arr.return_type=1", nativeQuery = true)
    List<AmastyRmaRequest> createReturnDropOffAwb();

    @Query(
            value = "select distinct order_id from amasty_rma_request " +
                    "where customer_id = :customerId " +
                    "and store_id in (:storeIds) " +
                    "order by created_at DESC " +
                    "limit :limit offset :offset",
            nativeQuery = true
    )
    List<Integer> getOrderIdsForReturnList(
            @Param("customerId") int customerId,
            @Param("storeIds") List<Integer> storeIds,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query(
            value = "select count(distinct order_id) from amasty_rma_request " +
                    "where customer_id = :customerId " +
                    "and store_id in (:storeIds)",
            nativeQuery = true
    )
    Integer getOrderCountForReturnList(
            @Param("customerId") int customerId,
            @Param("storeIds") List<Integer> storeIds
    );

	AmastyRmaRequest findByOrderId(Integer entityId);

    List<AmastyRmaRequest> findByOrderIdIn(List<Integer> orderIds);

    long countByCustomerIdAndStoreIdIn(Integer customerId, List<Integer> storeIds);
    
    List<AmastyRmaRequest> findByCustomerIdAndStatusNotIn(Integer customerId, List<Integer> status);
    
    List<AmastyRmaRequest> findByCustomerId(Integer customerId);

    List<AmastyRmaRequest> findByOrderIdAndStatusNot(Integer orderId, Integer status);
    
	@Query(value = "select * from amasty_rma_request "
			+ "WHERE order_id = (select entity_id from sales_order where increment_id = :input ) "
			+ "OR request_id = (select request_id from amasty_rma_tracking where tracking_number = :input ) "
			+ "OR rma_inc_id = :input ", nativeQuery = true)
	List<AmastyRmaRequest> findByOrderOrRmaOrAwb(@Param("input") String input);

    @Query(value = "SELECT CASE WHEN EXISTS ( SELECT 1 FROM amasty_rma_request WHERE order_id = ?1 AND status NOT IN (12, 13) AND created_at >= DATE_SUB(NOW(), INTERVAL ?2 HOUR) AND return_fee > 0 AND return_type = 0) THEN 0 ELSE ( SELECT count(*) FROM amasty_rma_request WHERE order_id = ?1 AND status NOT IN (12, 13) AND return_fee <= 0 AND (return_type <> 0 OR created_at < DATE_SUB(NOW(), INTERVAL ?2 HOUR))) END", nativeQuery = true)
    Integer getRMACount(int orderId, int hours);

    @Query(value = "SELECT request_id FROM amasty_rma_request WHERE order_id = ?1 AND status NOT IN (12, 13) AND created_at >= DATE_SUB(NOW(), INTERVAL ?2 HOUR) ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    String getLastRequestId(int orderId, int hours);

    @Query(value = "SELECT request_id FROM amasty_rma_request WHERE split_order_id = ?1 AND status NOT IN (12, 13) AND created_at >= DATE_SUB(NOW(), INTERVAL ?2 HOUR) ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    String getLastRequestIdOfSplitOrder(int splitOrderId, int hours);

    @Query(value = "SELECT CASE WHEN EXISTS ( SELECT 1 FROM amasty_rma_request WHERE split_order_id = ?1 AND status NOT IN (12, 13) AND created_at >= DATE_SUB(NOW(), INTERVAL ?2 HOUR) AND return_fee > 0 AND return_type = 0) THEN 0 ELSE ( SELECT count(*) FROM amasty_rma_request WHERE split_order_id = ?1 AND status NOT IN (12, 13) AND return_fee <= 0 AND (return_type <> 0 OR created_at < DATE_SUB(NOW(), INTERVAL ?2 HOUR))) END", nativeQuery = true)
    Integer getRMACountOfSplitOrder(int splitOrderId, int hours);
    
    @Query(value = "SELECT rma.rma_inc_id " +
            "FROM amasty_rma_request rma " +
            "LEFT JOIN sales_creditmemo scm " +
            "ON rma.order_id = scm.order_id " +
            "WHERE scm.order_id IS NULL " +
            "AND rma.status IN (15, 19) " +
            "AND rma.created_at BETWEEN CURDATE() - INTERVAL :daysFromEnd DAY " +
            "AND CURDATE() - INTERVAL :daysFromStart DAY", 
    nativeQuery = true)
    List<String> findReturnOrdersNotRefundedQuery(@Param("daysFromStart") int daysFromStart, @Param("daysFromEnd") int daysFromEnd);

    @Query(value = "SELECT request_id FROM amasty_rma_request WHERE order_id = ?1", nativeQuery = true)
    List<Integer> getAllRequestIdsByOrderIds(int orderId);

    @Query(value = "SELECT request_id FROM amasty_rma_request WHERE order_id = ?1 AND request_id != ?2", nativeQuery = true)
    List<Integer> getAllRequestIdsByOrderIdsAndAmastyIds(int orderId, int requestId);

    @Query(value = "SELECT SUM(qty) AS total_qty FROM amasty_rma_request_item WHERE request_id IN (:ids)", nativeQuery = true)
    BigDecimal getAllReturnedQuantity(@Param("ids") List<Integer> ids);

    @Query(value = "SELECT SUM(shukran_points_refunded) AS total_qty FROM amasty_rma_request WHERE request_id IN (:ids)", nativeQuery = true)
    BigDecimal getAllShukranPoints(@Param("ids") List<Integer> ids);

    @Query(value = "SELECT CASE WHEN EXISTS ( SELECT 1 FROM amasty_rma_request WHERE split_order_id = ?1 AND status NOT IN (12, 13) AND created_at >= DATE_SUB(NOW(), INTERVAL ?2 HOUR) AND return_fee > 0 AND return_type = 0) THEN 0 ELSE ( SELECT count(*) FROM amasty_rma_request WHERE split_order_id = ?1 AND status NOT IN (12, 13) AND return_fee <= 0 AND (return_type <> 0 OR created_at < DATE_SUB(NOW(), INTERVAL ?2 HOUR))) END", nativeQuery = true)
    Integer getSplitOrderRMACount(int splitOrderId, int hours);

    @Query(value = "SELECT request_id FROM amasty_rma_request WHERE split_order_id = ?1 AND status NOT IN (12, 13) AND created_at >= DATE_SUB(NOW(), INTERVAL ?2 HOUR) ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    String getLastSplitOrderRequestId(int splitOrderId, int hours);






}