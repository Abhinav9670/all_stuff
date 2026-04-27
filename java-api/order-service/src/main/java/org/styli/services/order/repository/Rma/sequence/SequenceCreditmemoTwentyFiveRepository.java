package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoTwentyFive;

public interface SequenceCreditmemoTwentyFiveRepository
        extends JpaRepository<SequenceCreditmemoTwentyFive, Integer>, JpaSpecificationExecutor<SequenceCreditmemoTwentyFive> {

	SequenceCreditmemoTwentyFive findFirstByOrderBySequenceValueDesc();

}