package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceOrderTwentyFive;
import org.styli.services.order.model.rma.sequence.SequenceOrderTwentyThree;

public interface SequenceOrderTwentyFiveRepository
		extends JpaRepository<SequenceOrderTwentyFive, Integer>, JpaSpecificationExecutor<SequenceOrderTwentyFive> {

	SequenceOrderTwentyFive findFirstByOrderBySequenceValueDesc();
}