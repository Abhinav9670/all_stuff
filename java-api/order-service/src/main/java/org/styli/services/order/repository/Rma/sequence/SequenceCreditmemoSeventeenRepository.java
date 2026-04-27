package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoSeventeen;

public interface SequenceCreditmemoSeventeenRepository
        extends JpaRepository<SequenceCreditmemoSeventeen, Integer>, JpaSpecificationExecutor<SequenceCreditmemoSeventeen> {

	SequenceCreditmemoSeventeen findFirstByOrderBySequenceValueDesc();

}