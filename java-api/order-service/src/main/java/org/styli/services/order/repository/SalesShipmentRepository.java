package org.styli.services.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.styli.services.order.model.sales.SalesShipment;

public interface SalesShipmentRepository extends JpaRepository<SalesShipment, Integer>, JpaSpecificationExecutor<SalesShipment> {

	SalesShipment findByOrderId(Integer entityId);
	
	SalesShipment findByEntityId(Integer entityId);
	
	SalesShipment findByIncrementId(String incrementId);

	@Query(value = "select * from sales_shipment where split_order_id=:splitOrderId", nativeQuery = true)
	SalesShipment findBySplitOrderId(Integer splitOrderId);

	@Query(value = "select count(*) from sales_shipment where order_id = :orderId and split_order_id is null", nativeQuery = true)
	long countByOrderIdAndSplitNull(@Param("orderId") Integer orderId);

	@Query(value = "select count(*) from sales_shipment where order_id = :orderId and split_order_id = :splitOrderId", nativeQuery = true)
	long countByOrderIdAndSplit(@Param("orderId") Integer orderId, @Param("splitOrderId") Integer splitOrderId);

}