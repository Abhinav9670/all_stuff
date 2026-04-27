package org.styli.services.order.repository.Rma;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;

import java.util.List;

public interface AmastyRmaRequestItemRepository
        extends JpaRepository<AmastyRmaRequestItem, Integer>, JpaSpecificationExecutor<AmastyRmaRequestItem> {

    List<AmastyRmaRequestItem> findByOrderItemId(Integer orderItemId);
    
    List<AmastyRmaRequestItem> findByRequestItemId(Integer requestItemId);
    
    List<AmastyRmaRequestItem> findByRequestId(Integer requestId);

}