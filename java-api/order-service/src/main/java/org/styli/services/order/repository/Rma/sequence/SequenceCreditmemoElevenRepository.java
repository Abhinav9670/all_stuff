package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoEleven;

public interface SequenceCreditmemoElevenRepository
        extends JpaRepository<SequenceCreditmemoEleven, Integer>, JpaSpecificationExecutor<SequenceCreditmemoEleven> {

    SequenceCreditmemoEleven findFirstByOrderBySequenceValueDesc();

}