package org.styli.services.order.repository.SalesOrder;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.SalesOrder.SalesCreditmemoGrid;

public interface SalesCreditmemoGridRepository
        extends JpaRepository<SalesCreditmemoGrid, Integer>, JpaSpecificationExecutor<SalesCreditmemoGrid> {
	List<SalesCreditmemoGrid> findByOrderId(Integer orderId);

}