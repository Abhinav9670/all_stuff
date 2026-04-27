package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceOrderSeven;

public interface SequenceOrderSevenRepository
        extends JpaRepository<SequenceOrderSeven, Integer>, JpaSpecificationExecutor<SequenceOrderSeven> {

    SequenceOrderSeven findFirstByOrderBySequenceValueDesc();
}