package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.sales.SalesPaymentTransaction;

public interface SalesPaymentTransactionRepository
        extends JpaRepository<SalesPaymentTransaction, Integer>, JpaSpecificationExecutor<SalesPaymentTransaction> {

}