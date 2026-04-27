package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.pojo.projection.ReferralOrderProjection;

import javax.persistence.LockModeType;
import java.util.Date;
import java.util.List;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Integer>, JpaSpecificationExecutor<SalesOrder> {

	
	SalesOrder findByEntityId(Integer entityId);

		@Lock(LockModeType.PESSIMISTIC_WRITE)
		@Query("select so from SalesOrder so where so.entityId = :entityId")
		SalesOrder lockByEntityId(@Param("entityId") Integer entityId);

        SalesOrder findByState(String state);
        
        SalesOrder findByIncrementId(String incrementId);
        
        SalesOrder findByEditIncrement(String incrementId);
        
        SalesOrder findByEditIncrementOrIncrementId(String editIncrementId, String incrementId);
        
        SalesOrder findByIncrementIdAndStatus(String incrementId, String status);

        List<SalesOrder> findByCustomerId(Integer customerId);

        List<SalesOrder> findByEntityIdIn(List<Integer> entityIds);

        @Query(value = "select count(*) from sales_order sa where  sa.store_id in ?2 and sa.customer_id=?1 \n"
                        + " and sa.entity_id not in (select sa.entity_id from sales_order sa inner join  sales_order sa2 on sa.customer_id = sa2.customer_id where sa.status != sa2.status \n"
                        + " and sa.status = 'payment_failed' and sa2.status != 'payment_failed' ) ", nativeQuery = true)
        Integer findByCustomerIdAndStoreIdIn(Integer customerId, List<Integer> storeIds);

        @Query(value = "select * from sales_order sa where  sa.store_id in ?2 and sa.customer_id=?1"
                        + " and sa.status != 'corrupted'", nativeQuery = true)
        List<SalesOrder> findByCustomerIdAndStoreIdIn(Integer customerId, List<Integer> storeIds, Pageable page);

	@Query(value = "SELECT * FROM sales_order sa WHERE sa.store_id IN (:storeIds) "
			+ "AND sa.customer_id = :customerId AND sa.status != 'corrupted'",
			countQuery = "SELECT COUNT(*) FROM sales_order sa WHERE sa.store_id IN (:storeIds) "
					+ "AND sa.customer_id = :customerId AND sa.status != 'corrupted'",
			nativeQuery = true)
	Page<SalesOrder> findByCustomerIdAndStoreIdInWithPagination(@Param("customerId") Integer customerId,
																@Param("storeIds") List<Integer> storeIds,
																Pageable pageable);

	@Query(value= "SELECT * FROM sales_order WHERE customer_email = :customerEmail " +
				"AND status IS NOT NULL " +
				"AND status NOT IN (:canceledStatus, :failedStatus, :canceledState)", nativeQuery = true)
		List<SalesOrder> findFirstCreateOrder(String customerEmail,
											  String canceledStatus,
											  String failedStatus,
											  String canceledState);

		@Query(value= "SELECT COUNT(*) FROM sales_order WHERE customer_email = :customerEmail " +
				"AND status IS NOT NULL " +
				"AND status NOT IN (:canceledStatus, :failedStatus, :canceledState)", nativeQuery = true)
		int countCreateOrders(String customerEmail,
							  String canceledStatus,
							  String failedStatus,
							  String canceledState);
     

        List<SalesOrder> findByCustomerIdAndStatusAndStoreIdIn(Integer customerId, String status,
                        List<Integer> storeIds, Pageable page);

        List<SalesOrder> findByCustomerEmail(String email);
        
        List<SalesOrder> findByCustomerEmailAndCustomerId(String email , Integer customerId);

        List<SalesOrder> findByQuoteIdIn(List<Integer> quoteIds);
        
		@Query(value = "select * from sales_order sa , sub_sales_order q where sa.store_id in ?2 and sa.customer_id=?1 and q.external_quote_status=1 and sa.status = ?3 "
				+ " and sa.created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) group by sa.entity_id order by sa.created_at desc", nativeQuery = true)
		List<SalesOrder> findFailedOrderList(Integer customerId, List<Integer> storeIds, String orderStatus,
				Pageable page);
        
        @Query(value = "select count(*) from sales_order sa where  sa.store_id in ?2 and sa.customer_email = ?1 \n" + 
        		" and sa.entity_id not in (select sa.entity_id from sales_order sa inner join  sales_order sa2 on sa.quote_id = sa2.quote_id where sa.status != sa2.status \n" + 
        		" and sa.status = 'payment_failed' and sa2.status not in ('payment_failed','corrupted','fraud','payfort_fort_failed','payment_canceled') )", nativeQuery = true)
        Integer findByCustomeEmailAndStoreIdIn(String customerEmail, List<Integer> storeIds);

        @Query(value = "SELECT so.customer_id AS customerId, so.entity_id AS entityId, so.store_id AS storeId, "
        		+ "so.created_at AS createdAt, so.delivered_at AS deliveredAt, so.grand_total AS grandTotal, "
        		+ "so.amstorecredit_amount AS amstorecreditAmount, so.customer_email AS customerEmail "
        		+ "FROM sales_order so WHERE so.status = 'delivered' AND so.customer_id IS NOT NULL "
        		+ "AND so.delivered_at BETWEEN (NOW() - INTERVAL :hoursAgo HOUR) AND NOW()", nativeQuery = true)
        List<ReferralOrderProjection> getReferralDeliveredOrders(@Param("hoursAgo") Integer hoursAgo);
        
        @Query(value = "select * from sales_order so where so.wms_status in(2) and so.status in ?1 and so.updated_at BETWEEN (NOW() - INTERVAL ?2 MINUTE) AND (NOW())", nativeQuery = true)
        List<SalesOrder> cancelledOrderforwmspush(@Param("statusList") List<String> statusList, @Param("minutesAgo") Integer minutesAgo);

	@Query(value = "SELECT\n" +
			"  so.entity_id,\n" +
			"  so.increment_id ,\n" +
			"  so.store_id ,\n" +
			"  sb.warehouse_id,\n" +
			"  soi.item_id, \n" +
			"  soi.sku ,\n" +
			"  soi.qty_ordered ,\n" +
			"  soi.product_type "+
			"FROM (\n" +
			"  SELECT entity_id, increment_id , store_id \n" +
			"  FROM sales_order\n" +
			"  WHERE wms_status = 2\n" +
			"    AND status IN ?1 \n" +
			"    and updated_at BETWEEN (NOW() - INTERVAL ?2 MINUTE) AND (NOW())\n" +
			") so\n" +
			"JOIN sales_order_item soi\n" +
			"  ON soi.order_id = so.entity_id\n" +
			"JOIN sub_sales_order sb\n" +
			"  ON sb.order_id = so.entity_id and soi.product_type !='configurable' ", nativeQuery = true)
	List<Object[]> cancelledOrderforwmspushNew(@Param("statusList") List<String> statusList, @Param("minutesAgo") Integer minutesAgo);

        @Query(value = "select * from  sales_order so where so.status in ('processing', 'pending_payment') and so.wms_status is null and so.created_at BETWEEN (NOW() - INTERVAL :minutesAgo MINUTE) AND (NOW() - INTERVAL :pushMinutes MINUTE)", nativeQuery = true)
        List<SalesOrder> ordersforwmspush( @Param("minutesAgo") Integer minutesAgo, Integer pushMinutes);

		@Query(
				value = "SELECT * " +
						"FROM sales_order so " +
						"WHERE so.status IN ('processing', 'pending_payment') " +
						"  AND (so.wms_status IS NULL OR so.wms_status=0) " +
						"  AND so.created_at BETWEEN (NOW() - INTERVAL :minutesAgo MINUTE) " +
						"                        AND (NOW() - INTERVAL :pushMinutes MINUTE) " +
						"  AND COALESCE(so.is_split_order, 0) = 0",
				nativeQuery = true
		)
		List<SalesOrder> ordersForWmsPushExcludingSplit(@Param("minutesAgo") Integer minutesAgo,
														@Param("pushMinutes") Integer pushMinutes);
		@Query(
				value = "SELECT * " +
					"FROM sales_order so " +
					"WHERE NOT EXISTS ( " +
						"  SELECT 1 " +
						"  FROM split_seller_order sso" +
						"  WHERE sso.main_order_id = so.entity_id" +
						")"	+
					" AND so.status IN ('processing') " +
					"  AND so.created_at BETWEEN (NOW() - INTERVAL :toMinutes MINUTE) AND NOW() " +
					"  AND COALESCE(so.is_split_order, 0) = 0",
				nativeQuery = true
		)
		List<SalesOrder> sellerWmsPushExcludingSplit(@Param("toMinutes") Integer toMinutes);

        @Query(value = "select * from sales_order so where so.status in ('pending_payment','payment_hold') and so.wms_status is null  and so.created_at BETWEEN (NOW()- INTERVAL  :lasthrsAgo HOUR) AND (NOW() - INTERVAL :minutesAgo MINUTE)", nativeQuery = true)
		List<SalesOrder> findPaymentFailedOrders(@Param("minutesAgo") Integer minutesAgo , @Param("lasthrsAgo") Integer lasthrsAgo);

        @Query(value = "select * from sales_order so where so.customer_id is null and so.created_at BETWEEN (NOW() - INTERVAL :hoursAgo HOUR) AND (NOW())", nativeQuery = true)
        List<SalesOrder> getAllOrdersForCustIdUpdate( @Param("hoursAgo") Integer hoursAgo);
        
        SalesOrder findByEntityIdAndCustomerId(Integer entityId,Integer customerId);
        
        @Modifying
        @Query(value = "update sub_sales_order c set c.extra_2 = :ratingStatus WHERE c.order_id = :orderId", nativeQuery = true)
        int updateRatingStatus(@Param("ratingStatus") String ratingStatus, @Param("orderId") Integer orderId);

		@Query(value = "SELECT * FROM sales_order so, sub_sales_order sso, sales_order_payment sop where "
				+ "so.entity_id = sso.order_id and so.entity_id = sop.parent_id "
				+ "and sop.method IN :methods and sop.parent_id = sso.order_id "
				+ "and so.status = :status and sso.payment_id IS NOT NULL "
				+ "and so.created_at BETWEEN DATE_SUB(NOW(), INTERVAL :durationhour HOUR) AND "
				+ "DATE_SUB(NOW(), INTERVAL :duration MINUTE)", nativeQuery = true)
		List<SalesOrder> findTabbyOrders(@Param("methods") List<String> paymentmethods,
				@Param("duration") String duration, @Param("durationhour") String hour,
				@Param("status") String paymentstatus);

		@Query(value = """
		SELECT sa.*
		FROM sales_order sa
		JOIN sales_order_payment sop ON sop.parent_id = sa.entity_id
		WHERE sa.status IN ('rto', 'rto_initiated')
		AND sa.created_at >= :fromDate 
		AND sop.method IN (
			'tamara_installments_6', 'tamara_installments_3', 'tabby_paylater', 
			'tabby_installments', 'payfort_fort_cc', 'md_payfort_cc_vault', 
			'md_payfort', 'apple_pay', 'free', 'shukran_payment'
		)
		ORDER BY sa.created_at DESC
	""",
				countQuery = """
		SELECT COUNT(*) FROM sales_order sa
		JOIN sales_order_payment sop ON sop.parent_id = sa.entity_id
		WHERE sa.status IN ('rto', 'rto_initiated')
		AND sa.created_at >= :fromDate
		AND sop.method IN (
			'tamara_installments_6', 'tamara_installments_3', 'tabby_paylater',
			'tabby_installments', 'payfort_fort_cc', 'md_payfort_cc_vault',
			'md_payfort', 'apple_pay', 'free', 'shukran_payment'
		)
	""",
				nativeQuery = true)
		Page<SalesOrder> getRtoOrders(Pageable page, @Param("fromDate") Date rtoFromDate);


		@Query(value = """
		SELECT sa.entity_id, sa.increment_id, sa.customer_email, sa.created_at, sop.method
		FROM sales_order sa
		JOIN sales_order_payment sop ON sop.parent_id = sa.entity_id
		WHERE NOT EXISTS (SELECT 1 FROM rto_auto_refund raf WHERE raf.order_id = sa.entity_id)
		AND sa.created_at > :fromDate
		AND sa.status IN ('rto', 'rto_initiated')
		AND sop.method IN (
			'tamara_installments_6', 'tamara_installments_3', 'tabby_paylater', 
			'tabby_installments', 'payfort_fort_cc', 'md_payfort_cc_vault', 
			'md_payfort', 'apple_pay', 'free', 'shukran_payment'
		)
		ORDER BY sa.created_at DESC
	""",
				countQuery = """
		SELECT COUNT(*)
		FROM sales_order sa
		JOIN sales_order_payment sop ON sop.parent_id = sa.entity_id
		WHERE NOT EXISTS (SELECT 1 FROM rto_auto_refund raf WHERE raf.order_id = sa.entity_id)
		AND sa.created_at > :fromDate
		AND sa.status IN ('rto', 'rto_initiated')
		AND sop.method IN (
			'tamara_installments_6', 'tamara_installments_3', 'tabby_paylater',
			'tabby_installments', 'payfort_fort_cc', 'md_payfort_cc_vault',
			'md_payfort', 'apple_pay', 'free', 'shukran_payment'
		)
	""",
				nativeQuery = true)
		Page<SalesOrder> getRtoOrdersByStatus(Pageable page, @Param("fromDate") Date rtoFromDate);

		@Query(value = """
		SELECT so.entity_id, so.increment_id, so.customer_email, so.created_at, sop.method
		FROM sales_order so
		JOIN sales_order_payment sop ON sop.parent_id = so.entity_id
		WHERE so.status IN ('rto', 'rto_initiated')
		AND so.created_at >= :fromDate
		AND so.increment_id IN (:incrementids)
		AND sop.method IN (
			'tamara_installments_6', 'tamara_installments_3', 'tabby_paylater',
			'tabby_installments', 'payfort_fort_cc', 'md_payfort_cc_vault',
			'md_payfort', 'apple_pay', 'free', 'shukran_payment'
		)
		ORDER BY so.created_at DESC
	""",
				countQuery = """
		SELECT COUNT(*)
		FROM sales_order so
		JOIN sales_order_payment sop ON sop.parent_id = so.entity_id
		WHERE so.status IN ('rto', 'rto_initiated')
		AND so.created_at >= :fromDate
		AND so.increment_id IN (:incrementids)
		AND sop.method IN (
			'tamara_installments_6', 'tamara_installments_3', 'tabby_paylater',
			'tabby_installments', 'payfort_fort_cc', 'md_payfort_cc_vault',
			'md_payfort', 'apple_pay', 'free', 'shukran_payment'
		)
	""",
				nativeQuery = true)
		Page<SalesOrder> findByIncrementIdInAndCreatedAfter(@Param("incrementids") List<String> incrementIds, @Param("fromDate") Date rtoFromDate, Pageable page);

	List<SalesOrder> findByIncrementIdIn(List<String> incrementIds);
		
		@Modifying
		@Query(value = "update sales_order set wms_status = :wmsStatus WHERE entity_id = :entityId", nativeQuery = true)
		int updateWMSStatus(@Param("wmsStatus") Integer wmsStatus, @Param("entityId") Integer entityId);
		
		@Modifying
		@Query(value = "update sales_order set ext_order_id = :status WHERE entity_id = :entityId", nativeQuery = true)
		int updateHoldOrderPushStatus(@Param("status") Integer status, @Param("entityId") Integer entityId);

		@Query(value = "select * from sales_order so where so.status IS NOT NULL " +
				"and so.status IN ?1 and " +
				"so.updated_at BETWEEN (NOW() - INTERVAL ?2 MINUTE) AND (NOW())", nativeQuery = true)
		List<SalesOrder> findCanceledAndFailedOrders(@Param("statusList") List<String> statusList, @Param("minutesAgo") Integer minutesAgo);
		
		
		@Query(value = "SELECT * from sales_order sa "
				+ "LEFT JOIN  rto_auto_refund raf ON  sa.entity_id = raf.order_id "
				+ "LEFT JOIN sub_sales_order sso ON sa.entity_id = sso.order_id "
				+ "JOIN sales_order_payment sop ON sop.parent_id = sa.entity_id "
				+ "WHERE raf.order_id IS NULL "
				+ "AND sa.status = 'rto' "
				+ "AND sop.method = 'cashondelivery' "
				+ "AND sa.updated_at BETWEEN (NOW() - INTERVAL :hoursAgo HOUR) AND (NOW())"
				+ "AND sso.eas_coins > 0", nativeQuery = true)
		List<SalesOrder> findRTOOrdersWithCoinsInXhrs(@Param("hoursAgo") Integer hoursAgo);
		
		
		List<SalesOrder> findByCustomerEmailAndStoreIdIn(String email, List<Integer> storeId);
		
		@Query(value = "select count(*) from sales_order sa where sa.customer_id = ?1 and sa.status not in ?2", nativeQuery = true)
		Integer countPendingOrdersByCustomerId(Integer customerId, List<String> terminalStatus);
		
		SalesOrder findByEntityIdAndCustomerEmail(Integer entityId, String customerEmail);
		
		@Query(value = "SELECT * FROM sales_order so, sub_sales_order sso, sales_order_payment sop where "
				+ "so.entity_id = sso.order_id and so.entity_id = sop.parent_id "
				+ "and sop.method = :method and sop.parent_id = sso.order_id "
				+ "and so.status = :status "
				+ "and so.created_at BETWEEN DATE_SUB(NOW(), INTERVAL :durationhour HOUR) AND "
				+ "DATE_SUB(NOW(), INTERVAL :duration MINUTE)", nativeQuery = true)
		List<SalesOrder> findCashfreeOrders(@Param("method") String paymentmethod,
				@Param("duration") String duration, @Param("durationhour") String hour,
				@Param("status") String paymentstatus);

		@Query(value = "SELECT increment_id FROM sales_order "
				+"WHERE customer_id = ?1 AND created_at > ?2 "
				+"AND status NOT IN ('delivered', 'closed', 'payment_failed', 'refunded', 'rto') "
				+"ORDER BY created_at DESC;",
				nativeQuery = true)
		List<String> findCurrentOrdersByCustomer(Integer customerId, long thresholdTimestampInMillis);
		
		@Query(value = "select * from sales_order so WHERE so.store_id in ?2 and so.customer_id = ?1 and so.status = 'pending_payment' and retry_payment = 1 and so.created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) order by so.created_at desc limit 1", nativeQuery = true)
SalesOrder findPendingOrderList(Integer customerId, List<Integer> storeId);
		
		@Query(value = "SELECT * from sales_order so LEFT JOIN sub_sales_order sso ON so.entity_id = sso.order_id WHERE NOW() > sso.order_expired_at AND so.status = 'pending_payment'", nativeQuery = true)
		List<SalesOrder> findPendingPaymentOrderWithinMinutes();
		
		@Query(value = "select * from  sales_order so where so.status in ('processing') and (so.wms_status = 7 or ext_order_id = 1) and COALESCE(so.is_split_order, 0) = 0 and so.created_at BETWEEN (NOW() - INTERVAL :minutesAgo MINUTE) AND (NOW())", nativeQuery = true)
	       List<SalesOrder> ordersHoldwmspush( @Param("minutesAgo") Integer minutesAgo);
		
		@Query(value = "SELECT * from sales_order so LEFT JOIN sub_sales_order sso ON so.entity_id = sso.order_id WHERE sso.retry_payment=1 and so.customer_id = ?1  AND so.status = ?2", nativeQuery = true)
		List<SalesOrder> findByCustomerIdAndStatus(Integer customerId, String status);
		
		@Query(value = "SELECT * from sales_order so LEFT JOIN sub_sales_order sso ON so.entity_id = sso.order_id WHERE sso.order_expired_at is null AND so.status = 'pending_payment' and so.created_at < (NOW()  - INTERVAL :minutesAgo MINUTE) AND so.created_at > (NOW() - INTERVAL 10080 MINUTE)", nativeQuery = true)
		List<SalesOrder> findPendingPaymentOrdersTomakeFailed(Integer minutesAgo);

		 @Query(value = "select * from sales_order so where (so.wms_status is null or so.wms_status = 1) and so.status in ?1 and so.updated_at BETWEEN (NOW() - INTERVAL ?2 MINUTE) AND (NOW())", nativeQuery = true)
	        List<SalesOrder> updateOrdercancelforwmspush(@Param("statusList") List<String> statusList, @Param("minutesAgo") Integer minutesAgo);
		 
			
			@Query(value = "select * from sales_order so , sales_order_address soa WHERE soa.parent_id = so.entity_id and  soa.telephone = ?1 and soa.address_type='shipping' "
					+ "and so.customer_id is null",
					nativeQuery = true)
			 List<SalesOrder> findGuestOrdersByTelephone(String telephone);

		@Query(value = "select * from sales_order so where so.payfort_authorized = 1 "
				+ "and so.authorization_capture != 1 and status NOT IN ('closed', 'payment_failed') "
				+ "and TIMESTAMPDIFF(HOUR, created_at, NOW()) > 72", nativeQuery = true)
		List<SalesOrder> findAuthorizationCaptureDropOffOrderList();
		
		
		@Query(value = "select * from sales_order AS SO left join sub_sales_order AS SSO on SO.entity_id = SSO.order_id "
				+ "WHERE external_coupon_redemption_status = 3 and DATE(SO.created_at) = DATE(:targetDate)", nativeQuery = true)
		List<SalesOrder> findOrderRedemptionMissed(Date targetDate);
		
		@Query(value = "SELECT * FROM sales_order so " +
                "WHERE so.status = 'processing' " +
                "AND (so.ext_order_id IS NULL OR so.ext_order_id = 0) " +
                "AND EXISTS (" +
                "    SELECT 1 FROM sub_sales_order sso " +
                "    WHERE sso.order_id = so.entity_id " +
                "    AND sso.retry_payment_count > 0 " +
                ") " +
                "AND so.created_at < (NOW() - INTERVAL :hours HOUR)",
        nativeQuery = true)
        List<SalesOrder> orderHoldFalseInWms(@Param("hours") int hours);


		@Modifying
		@Query(value = "UPDATE sales_order SET ext_order_id = 1 WHERE entity_id = :entityId", nativeQuery = true)
		int updateHoldOrderFalseInWms(@Param("entityId") Integer entityId);
		
		@Query(value = "SELECT so.increment_id " +
	               "FROM sales_order so " +
	               "LEFT JOIN sales_creditmemo scm ON so.entity_id = scm.order_id " +
	               "JOIN sales_order_payment sop ON so.entity_id = sop.parent_id " +
	               "WHERE scm.order_id IS NULL " +
	               "AND so.status = 'rto' " +
	               "AND sop.method != 'cashondelivery' " +
	               "AND so.updated_at BETWEEN CURDATE() - INTERVAL :daysFromEnd DAY " +
	               "AND CURDATE() - INTERVAL :daysFromStart DAY", 
	       nativeQuery = true)
	    List<String> findRtoOrdersNotRefundedQuery(@Param("daysFromStart") int daysFromStart, @Param("daysFromEnd") int daysFromEnd);

		
		@Query(value = "SELECT COUNT(1) FROM sales_order_payment sop " +
	               "JOIN sales_order so ON so.entity_id = sop.parent_id " +
	               "JOIN sales_order_item soi ON soi.order_id = so.entity_id " +
	               "WHERE JSON_EXTRACT(sop.additional_information, '$.payment_option') = 'MADA' " +
	               "AND so.entity_id = :orderId", nativeQuery = true)
	    int checkIfMadaTransaction(@Param("orderId") int orderId);
		
		@Query(value = "SELECT COUNT(1) " +
	               "FROM sales_order_status_history " +
	               "WHERE parent_id = :orderId " +
	               "AND comment LIKE '%Seller cancellation pushed%'", nativeQuery = true)
	    int checkIfSellerCancelExists(@Param("orderId") int orderId);

		@Query(value = "select sa.entity_id from sales_order sa where  sa.store_id in ?2 and sa.customer_id=?1 \n"
				+ " and sa.entity_id not in (select sa.entity_id from sales_order sa inner join  sales_order sa2 on sa.customer_id = sa2.customer_id where sa.status != sa2.status \n"
				+ " and sa.status = 'payment_failed' and sa2.status != 'payment_failed' ) ", nativeQuery = true)
		 List<Integer> findSalesOrderIds(Integer customerId, List<Integer> storeIds);

		@Query(value = "select sa.entity_id from sales_order sa where  sa.store_id in ?2 and sa.customer_email = ?1 \n" +
				" and sa.entity_id not in (select sa.entity_id from sales_order sa inner join  sales_order sa2 on sa.quote_id = sa2.quote_id where sa.status != sa2.status \n" +
				" and sa.status = 'payment_failed' and sa2.status not in ('payment_failed','corrupted','fraud','payfort_fort_failed','payment_canceled') )", nativeQuery = true)
		List<Integer> findSalesOrderIdsWithCustomerEmail(String customerEmail, List<Integer> storeIds);

}