package org.styli.services.order.repository.SalesOrder;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.sales.ProxyOrder;

public interface ProxyOrderRepository extends JpaRepository<ProxyOrder, Long> {

	ProxyOrder findByPaymentId(String paymentId);
	
	List<ProxyOrder> findByCustomerId(Integer customerId);

	@Query(value = "SELECT * FROM proxy_order po where po.inventory_released is false "
			+ "and po.created_at <= (NOW() - INTERVAL :duration MINUTE)", nativeQuery = true)
	List<ProxyOrder> findTamaraOrders(@Param("duration") String ordersMinutesAgo);

	Optional<ProxyOrder> findByIdOrPaymentId(Long proxyOrderId, String tabbyPaymentId);

	@Query(value = "SELECT * FROM proxy_order po where po.status = 'pending_payment' and  po.inventory_released is false "
			+ "and po.created_at BETWEEN DATE_SUB(NOW(), INTERVAL :durationhour HOUR) "
			+ "AND DATE_SUB(NOW(), INTERVAL :duration MINUTE)", nativeQuery = true)
	List<ProxyOrder> findPendingPaymentOrders(@Param("duration") String ordersMinutesAgo,
			@Param("durationhour") String ordersHoursAgo);
	
	
	@Query(value = "SELECT EXISTS(SELECT * FROM proxy_order WHERE increment_id =:incrementId)", nativeQuery = true)
	boolean isProxyOrder(@Param("incrementId") String incrementId);
	
	List<ProxyOrder> findByIncrementId(String incrementId);
	
	@Query(value = "SELECT increment_id FROM proxy_order WHERE increment_id IN (:incrementIds)", nativeQuery = true)
	List<String> findAllByIncrementId(@Param("incrementIds") List<String> incrementIds);
}