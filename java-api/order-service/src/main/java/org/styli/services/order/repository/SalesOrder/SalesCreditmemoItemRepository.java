package org.styli.services.order.repository.SalesOrder;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.sales.SalesCreditmemoItem;

public interface SalesCreditmemoItemRepository
        extends JpaRepository<SalesCreditmemoItem, Integer>, JpaSpecificationExecutor<SalesCreditmemoItem> {
	
	List<SalesCreditmemoItem> findByParentId(Integer parentId);

}