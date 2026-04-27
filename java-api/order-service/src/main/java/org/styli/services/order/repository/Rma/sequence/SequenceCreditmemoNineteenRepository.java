package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoNineteen;

public interface SequenceCreditmemoNineteenRepository
        extends JpaRepository<SequenceCreditmemoNineteen, Integer>, JpaSpecificationExecutor<SequenceCreditmemoNineteen> {

	SequenceCreditmemoNineteen findFirstByOrderBySequenceValueDesc();

}