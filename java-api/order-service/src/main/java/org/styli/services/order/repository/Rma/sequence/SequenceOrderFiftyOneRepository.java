package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceOrderFiftyOne;

public interface SequenceOrderFiftyOneRepository
		extends JpaRepository<SequenceOrderFiftyOne, Integer>, JpaSpecificationExecutor<SequenceOrderFiftyOne> {

	SequenceOrderFiftyOne findFirstByOrderBySequenceValueDesc();
}