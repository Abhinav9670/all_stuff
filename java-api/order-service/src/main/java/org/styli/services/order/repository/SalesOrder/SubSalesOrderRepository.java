package org.styli.services.order.repository.SalesOrder;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.sales.SubSalesOrder;

public interface SubSalesOrderRepository extends JpaRepository<SubSalesOrder, Integer>, JpaSpecificationExecutor<SubSalesOrder> {
	
	
	@Query(value = "select order_id from sub_sales_order sso where sso.payment_id = ?", nativeQuery = true)
	Integer findOrderId(@Param("payment_id") String paymentId);

	List<SubSalesOrder> findByPaymentId(String paymentId);
	
	@Query(value = "SELECT sso.* FROM sales_order so, sub_sales_order sso where so.entity_id = sso.order_id "
			+ "and so.status = 'pending_payment' "
			+ "and sso.order_expired_at > NOW()"
			+ "and (sso.payment_pending_first_notification_at BETWEEN DATE_SUB(NOW(), INTERVAL :notificationInMins MINUTE) AND NOW() "
			+ "or sso.payment_pending_second_notification_at BETWEEN DATE_SUB(NOW(), INTERVAL :notificationInMins MINUTE) AND NOW())", nativeQuery = true)
	List<SubSalesOrder> findExpiredOrder(@Param("notificationInMins") Integer notificationInMins);
    
}
