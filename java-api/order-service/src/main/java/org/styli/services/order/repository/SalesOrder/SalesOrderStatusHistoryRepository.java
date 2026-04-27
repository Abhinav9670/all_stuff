package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.sales.SalesOrderStatusHistory;
import org.styli.services.order.model.sales.SplitSalesOrder;

import java.util.List;

public interface SalesOrderStatusHistoryRepository
        extends JpaRepository<SalesOrderStatusHistory, Integer>, JpaSpecificationExecutor<SalesOrderStatusHistory> {

    List<SalesOrderStatusHistory> findByParentIdAndStatus(Integer parentId, String status);

    @Query(value = "select * from sales_order_status_history where parent_id = :entityId  ORDER BY entity_id DESC LIMIT 1", nativeQuery = true)
	SalesOrderStatusHistory findByParentIdOrderByEntityIdDesc(@Param("entityId") Integer entityId);

    List<SalesOrderStatusHistory> findByParentId(Integer parentId);

    List<SalesOrderStatusHistory> findBySplitSalesOrderAndStatus(SplitSalesOrder splitSalesOrder, String status);
}