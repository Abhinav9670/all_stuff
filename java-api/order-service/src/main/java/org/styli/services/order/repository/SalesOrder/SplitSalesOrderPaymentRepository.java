package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SplitSalesOrderPayment;

public interface SplitSalesOrderPaymentRepository
        extends JpaRepository<SplitSalesOrderPayment, Integer>, JpaSpecificationExecutor<SplitSalesOrderPayment> {
}