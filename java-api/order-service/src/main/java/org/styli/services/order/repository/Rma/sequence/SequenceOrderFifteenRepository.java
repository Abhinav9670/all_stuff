package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceOrderFifteen;
import org.styli.services.order.model.rma.sequence.SequenceOrderThirteen;

public interface SequenceOrderFifteenRepository
		extends JpaRepository<SequenceOrderFifteen, Integer>, JpaSpecificationExecutor<SequenceOrderFifteen> {

	SequenceOrderFifteen findFirstByOrderBySequenceValueDesc();
}