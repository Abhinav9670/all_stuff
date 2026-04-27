package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.SalesOrder.SalesCreditmemoComment;

public interface SalesCreditmemoCommentRepository
        extends JpaRepository<SalesCreditmemoComment, Integer>, JpaSpecificationExecutor<SalesCreditmemoComment> {

}