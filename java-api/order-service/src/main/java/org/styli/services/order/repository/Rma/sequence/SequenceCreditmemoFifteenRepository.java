package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoFifteen;

public interface SequenceCreditmemoFifteenRepository
        extends JpaRepository<SequenceCreditmemoFifteen, Integer>, JpaSpecificationExecutor<SequenceCreditmemoFifteen> {

	SequenceCreditmemoFifteen findFirstByOrderBySequenceValueDesc();

}