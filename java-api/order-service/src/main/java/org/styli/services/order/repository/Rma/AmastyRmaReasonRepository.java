package org.styli.services.order.repository.Rma;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.AmastyRmaReason;

import java.util.List;

public interface AmastyRmaReasonRepository
        extends JpaRepository<AmastyRmaReason, Integer>, JpaSpecificationExecutor<AmastyRmaReason> {

    AmastyRmaReason findByReasonId(Integer reasonId);

    List<AmastyRmaReason> findByStatusAndIsDeletedOrderByPositionAsc(Integer status, Integer isDeleted);

}