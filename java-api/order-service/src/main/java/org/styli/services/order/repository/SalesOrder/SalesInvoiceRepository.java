package org.styli.services.order.repository.SalesOrder;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.sales.SalesInvoice;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Integer>, JpaSpecificationExecutor<SalesInvoice> {

	SalesInvoice findByEntityId(Integer entityId);

	@Query(value = "SELECT * FROM sales_invoice WHERE zatca_status != 'REPORTED' AND zatca_status IS NOT NULL AND created_at BETWEEN (NOW() - INTERVAL :monthsAgo MONTH) AND NOW() LIMIT :limit OFFSET :offset", nativeQuery = true)
	List<SalesInvoice> findByZatcaNotGenerated(@Param("limit") Integer limit, @Param("offset") Integer offset, @Param("monthsAgo") Integer monthsAgo);
	
	@Query(value = "select * from sales_invoice where increment_id = :incrementId", nativeQuery = true)
	SalesInvoice getInvoiceEntityId(@Param("incrementId") String incrementId);
	
	@Modifying
    @Query(value = "update sales_invoice SI set SI.zatca_status = :zatcaStatus WHERE SI.increment_id = :incrementId", nativeQuery = true)
    int updateZatcaStatus(@Param("zatcaStatus") String zatcaStatus, @Param("incrementId") String incrementId);
    
    @Query(value = "select count(*) from sales_invoice where order_id = :orderId and split_order_id is null", nativeQuery = true)
    long countByOrderIdAndSplitNull(@Param("orderId") Integer orderId);

    @Query(value = "select count(*) from sales_invoice where order_id = :orderId and split_order_id = :splitOrderId", nativeQuery = true)
    long countByOrderIdAndSplit(@Param("orderId") Integer orderId, @Param("splitOrderId") Integer splitOrderId);
        
}