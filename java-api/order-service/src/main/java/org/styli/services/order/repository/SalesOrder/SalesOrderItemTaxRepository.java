package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.sales.SalesOrderItemTax;

public interface SalesOrderItemTaxRepository
		extends JpaRepository<SalesOrderItemTax, Integer>, JpaSpecificationExecutor<SalesOrderItemTax> {
	
	SalesOrderItemTax findBySalesOrderItemTaxId(Integer salesOrderItemTaxId);
}
