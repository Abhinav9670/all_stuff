package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoTwentyOne;

public interface SequenceCreditmemoTwentyOneRepository
        extends JpaRepository<SequenceCreditmemoTwentyOne, Integer>, JpaSpecificationExecutor<SequenceCreditmemoTwentyOne> {

	SequenceCreditmemoTwentyOne findFirstByOrderBySequenceValueDesc();

}