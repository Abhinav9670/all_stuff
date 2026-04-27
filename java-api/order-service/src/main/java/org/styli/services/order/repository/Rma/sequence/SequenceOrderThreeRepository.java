package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceOrderThree;

public interface SequenceOrderThreeRepository
        extends JpaRepository<SequenceOrderThree, Integer>, JpaSpecificationExecutor<SequenceOrderThree> {

    SequenceOrderThree findFirstByOrderBySequenceValueDesc();
}