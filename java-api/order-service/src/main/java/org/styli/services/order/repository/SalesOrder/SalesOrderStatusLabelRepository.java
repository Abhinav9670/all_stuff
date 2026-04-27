package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabel;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabelPK;

public interface SalesOrderStatusLabelRepository
        extends JpaRepository<SalesOrderStatusLabel, Integer>, JpaSpecificationExecutor<SalesOrderStatusLabel> {

    SalesOrderStatusLabel findById(SalesOrderStatusLabelPK id);

}