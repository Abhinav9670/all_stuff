package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoFiftyOne;

public interface SequenceCreditmemoFiftyOneRepository
        extends JpaRepository<SequenceCreditmemoFiftyOne, Integer>, JpaSpecificationExecutor<SequenceCreditmemoFiftyOne> {

	SequenceCreditmemoFiftyOne findFirstByOrderBySequenceValueDesc();

}