package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.sales.SalesOrderPayment;

public interface SalesOrderPaymentRepository
        extends JpaRepository<SalesOrderPayment, Integer>, JpaSpecificationExecutor<SalesOrderPayment> {

    @Query(value = "select * from sales_order_payment where parent_id=:parentId", nativeQuery = true)
    SalesOrderPayment findByParentId(@Param("parentId") Integer parentId);

}