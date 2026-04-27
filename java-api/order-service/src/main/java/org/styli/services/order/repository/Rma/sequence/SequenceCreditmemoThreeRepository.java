package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoThree;

public interface SequenceCreditmemoThreeRepository
        extends JpaRepository<SequenceCreditmemoThree, Integer>, JpaSpecificationExecutor<SequenceCreditmemoThree> {

    SequenceCreditmemoThree findFirstByOrderBySequenceValueDesc();

}