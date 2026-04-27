package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoSeven;

public interface SequenceCreditmemoSevenRepository
        extends JpaRepository<SequenceCreditmemoSeven, Integer>, JpaSpecificationExecutor<SequenceCreditmemoSeven> {

    SequenceCreditmemoSeven findFirstByOrderBySequenceValueDesc();

}