package org.styli.services.order.repository.SalesOrder;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.order.model.sales.RtoAutoRefund;

@Repository
public interface RtoAutoRefundRepository extends JpaRepository<RtoAutoRefund, Long> {

	RtoAutoRefund findByIncrementId(String incrementId);

	Page<RtoAutoRefund> findByStatusIn(List<String> list, Pageable page);
	
	List<RtoAutoRefund> findBySalesOrderEntityId(Integer entityId);

	List<RtoAutoRefund> findBySplitSalesOrderEntityId(Integer entityId);
}
