package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.sales.StatusChangeHistory;

import java.util.List;

public interface StatusChaneHistoryRepository
        extends JpaRepository<StatusChangeHistory, Integer>, JpaSpecificationExecutor<StatusChangeHistory> {

	
	List<StatusChangeHistory> findByOrderId(String orderId);

	StatusChangeHistory findBySplitOrderIncrementId(String splitOrderIncrementId);
}