package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoOne;

public interface SequenceCreditmemoOneRepository
        extends JpaRepository<SequenceCreditmemoOne, Integer>, JpaSpecificationExecutor<SequenceCreditmemoOne> {

    SequenceCreditmemoOne findFirstByOrderBySequenceValueDesc();

}