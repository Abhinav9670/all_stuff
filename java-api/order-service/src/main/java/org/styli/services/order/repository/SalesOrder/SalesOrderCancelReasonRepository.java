package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.pojo.cancel.SalesOrderCancelReason;

import java.util.List;

public interface SalesOrderCancelReasonRepository
        extends JpaRepository<SalesOrderCancelReason, Integer>, JpaSpecificationExecutor<SalesOrderCancelReason> {

    List<SalesOrderCancelReason> findByStatusOrderBySortOrderAsc(Integer status);

}