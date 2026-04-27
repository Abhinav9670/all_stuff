package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.sales.SalesInvoiceGrid;

public interface SalesInvoiceGridRepository extends JpaRepository<SalesInvoiceGrid, Integer>, JpaSpecificationExecutor<SalesInvoiceGrid> {

	SalesInvoiceGrid findByOrderId(Integer orderId);

}