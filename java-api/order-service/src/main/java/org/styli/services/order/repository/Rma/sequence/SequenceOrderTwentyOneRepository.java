package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceOrderThirteen;
import org.styli.services.order.model.rma.sequence.SequenceOrderTwentyOne;

public interface SequenceOrderTwentyOneRepository
		extends JpaRepository<SequenceOrderTwentyOne, Integer>, JpaSpecificationExecutor<SequenceOrderTwentyOne> {

	SequenceOrderTwentyOne findFirstByOrderBySequenceValueDesc();
}