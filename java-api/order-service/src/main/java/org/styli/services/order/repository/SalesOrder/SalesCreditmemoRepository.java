package org.styli.services.order.repository.SalesOrder;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.sales.SalesInvoice;
import org.springframework.transaction.annotation.Transactional;

public interface SalesCreditmemoRepository
        extends JpaRepository<SalesCreditmemo, Integer>, JpaSpecificationExecutor<SalesCreditmemo> {

    //@Query(value = "SELECT * from sales_creditmemo WHERE rma_number='?1'", nativeQuery = true)
    List<SalesCreditmemo> findByRmaNumber(String rmaNumber);

    List<SalesCreditmemo> findByOrderId(Integer entityId);

    List<SalesCreditmemo> findBySplitOrderId(Integer splitOrderId);

    @Query(value = "SELECT * FROM sales_creditmemo WHERE order_id = :orderId and split_order_id is null", nativeQuery = true)
    List<SalesCreditmemo> findByOrderIdAndSplitOrderIdIsNull(@Param("orderId") Integer orderId);
    
    SalesCreditmemo findByIncrementId(String incrementId);
    
    @Query(value = "SELECT * FROM sales_creditmemo WHERE zatca_status != 'REPORTED' AND zatca_status IS NOT NULL AND created_at BETWEEN (NOW() - INTERVAL :monthsAgo MONTH) AND NOW() LIMIT :limit OFFSET :offset", nativeQuery = true)
	List<SalesCreditmemo> findByZatcaNotGenerated(@Param("limit") Integer limit, @Param("offset") Integer offset, @Param("monthsAgo") Integer monthsAgo);
	
	@Modifying
	@Transactional
    @Query(value = "update sales_creditmemo SC set SC.zatca_status = :zatcaStatus WHERE SC.increment_id = :incrementId", nativeQuery = true)
    int updateZatcaStatus(@Param("zatcaStatus") String zatcaStatus, @Param("incrementId") String incrementId);
	
        
}