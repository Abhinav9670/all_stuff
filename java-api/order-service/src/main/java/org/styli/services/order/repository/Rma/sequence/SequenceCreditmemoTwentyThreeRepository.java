package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoTwentyThree;

public interface SequenceCreditmemoTwentyThreeRepository
        extends JpaRepository<SequenceCreditmemoTwentyThree, Integer>, JpaSpecificationExecutor<SequenceCreditmemoTwentyThree> {

	SequenceCreditmemoTwentyThree findFirstByOrderBySequenceValueDesc();

}