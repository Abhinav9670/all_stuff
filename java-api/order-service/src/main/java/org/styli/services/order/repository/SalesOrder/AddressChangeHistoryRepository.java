package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.styli.services.order.model.SalesOrder.AddressChangeHistory;
import org.styli.services.order.model.sales.ProxyOrder;

import java.util.List;

/**
 * @author Biswabhusan Pradhan <biswabhusan.pradhan@landmarkgroup.com>
 */
public interface AddressChangeHistoryRepository extends JpaRepository<AddressChangeHistory, Long> {
    List<AddressChangeHistory> findByOrderId(Integer entityId);
}
