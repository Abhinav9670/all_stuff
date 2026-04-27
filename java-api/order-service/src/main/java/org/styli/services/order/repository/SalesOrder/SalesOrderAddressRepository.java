package org.styli.services.order.repository.SalesOrder;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.sales.SalesOrderAddress;

public interface SalesOrderAddressRepository
		extends JpaRepository<SalesOrderAddress, Integer>, JpaSpecificationExecutor<SalesOrderAddress> {

	@Query(value = "select dcr.region_id from directory_country_region  dcr where dcr.code=?1", nativeQuery = true)
	String findIdRegionIdByNativeQuery(String region);
	
	 void deleteByCustomerId(Integer customerId);

	@Query(value = "SELECT so.increment_id FROM sales_order_address soa , sales_order so "
			+"WHERE so.entity_id = soa.parent_id AND soa.address_type = 'shipping' AND "
			+"so.status NOT IN ('delivered', 'closed', 'payment_failed', 'refunded', 'rto') AND "
			+"soa.telephone = ?1 AND so.created_at > ?2 ORDER BY so.created_at DESC",
			nativeQuery = true)
	 List<String> findCurrentOrdersByTelephone(String telephone, long thresholdTimestampInMillis);
	
	List<SalesOrderAddress> findByCustomerId(Integer customerId);

	@Query(value = "SELECT arr.rma_inc_id FROM sales_order_address soa , sales_order so, amasty_rma_request arr "
			+ "WHERE so.entity_id = soa.parent_id AND arr.order_id = so.entity_id AND arr.rma_inc_id IS NOT NULL AND soa.address_type = 'shipping' AND "
			+ "soa.telephone = :phone AND so.created_at > DATE_SUB(NOW(), INTERVAL 30 DAY) ORDER BY so.created_at DESC", nativeQuery = true)
	List<String> findOrderReturnsByTelephone(@Param("phone") String telephone);
			
	@Query(value = "SELECT arr.rma_inc_id FROM sales_order_address soa , sales_order so, amasty_rma_request arr "
			+ "WHERE so.entity_id = soa.parent_id AND arr.order_id = so.entity_id AND arr.rma_inc_id IS NOT NULL AND soa.address_type = 'shipping' AND "
			+ "soa.customer_id = :customer AND so.created_at > DATE_SUB(NOW(), INTERVAL 30 DAY) ORDER BY so.created_at DESC", nativeQuery = true)
	List<String> findOrderReturnsByCustomer(@Param("customer") Integer customer);
	
	@Query(value = "SELECT arr.rma_inc_id FROM sales_order_address soa , sales_order so, amasty_rma_request arr "
			+ "WHERE so.entity_id = soa.parent_id AND arr.order_id = so.entity_id AND arr.rma_inc_id IS NOT NULL AND soa.address_type = 'shipping' AND "
			+ "soa.telephone = :phone AND so.created_at > DATE_SUB(NOW(), INTERVAL 30 DAY) "
			+ " AND arr.status IN (:filter) ORDER BY so.created_at DESC", nativeQuery = true)
	List<String> findOrderReturnsByTelephoneAndStatus(@Param("phone") String telephone, @Param("filter") List<Integer> filter);
			
	@Query(value = "SELECT arr.rma_inc_id FROM sales_order_address soa , sales_order so, amasty_rma_request arr "
			+ "WHERE so.entity_id = soa.parent_id AND arr.order_id = so.entity_id AND arr.rma_inc_id IS NOT NULL AND soa.address_type = 'shipping' AND "
			+ "soa.customer_id = :customer AND so.created_at > DATE_SUB(NOW(), INTERVAL 30 DAY)"
			+ " AND arr.status IN (:filter) ORDER BY so.created_at DESC", nativeQuery = true)
	List<String> findOrderReturnsByCustomerAndStatus(@Param("customer") Integer customer, @Param("filter") List<Integer> filter);
	
	@Query(value = "select * from sales_order_address where parent_id = :orderId", nativeQuery = true)
	List<SalesOrderAddress> findByOrderId(@Param("orderId") Integer orderId);

	@Query(value = "SELECT so.increment_id as orderId, " +
			"COALESCE(ss.increment_id, sss.increment_id) as shipmentId, " +
			"so.created_at as createdAt " +
			"FROM sales_order_address soa " +
			"INNER JOIN sales_order so ON so.entity_id = soa.parent_id " +
			"LEFT JOIN split_seller_order sso ON sso.main_order_id = so.entity_id " +
			"LEFT JOIN sales_shipment ss ON ss.order_id = so.entity_id AND ss.increment_id IS NOT NULL " +
			"LEFT JOIN split_seller_shipment sss ON sss.seller_order_id = sso.entity_id AND sss.increment_id IS NOT NULL " +
			"WHERE soa.address_type = 'shipping' " +
			"AND so.status NOT IN ('delivered', 'closed', 'payment_failed', 'refunded', 'rto') " +
			"AND soa.telephone = :telephone " +
			"AND so.created_at > :thresholdTimestamp " +
			"ORDER BY createdAt DESC", nativeQuery = true)
	List<Object[]> findUndeliveredShipmentsByTelephone(@Param("telephone") String telephone, 
			@Param("thresholdTimestamp") long thresholdTimestampInMillis);

	@Query(value = "SELECT so.increment_id as orderId, " +
			"COALESCE(ss.increment_id, sss.increment_id) as shipmentId, " +
			"so.created_at as createdAt " +
			"FROM sales_order so " +
			"LEFT JOIN split_seller_order sso ON sso.main_order_id = so.entity_id " +
			"LEFT JOIN sales_shipment ss ON ss.order_id = so.entity_id AND ss.increment_id IS NOT NULL " +
			"LEFT JOIN split_seller_shipment sss ON sss.seller_order_id = sso.entity_id AND sss.increment_id IS NOT NULL " +
			"WHERE so.customer_id = :customerId " +
			"AND so.status NOT IN ('delivered', 'closed', 'payment_failed', 'refunded', 'rto') " +
			"AND so.created_at > :thresholdTimestamp " +
			"ORDER BY createdAt DESC", nativeQuery = true)
	List<Object[]> findUndeliveredShipmentsByCustomer(@Param("customerId") Integer customerId, 
			@Param("thresholdTimestamp") long thresholdTimestampInMillis);

}