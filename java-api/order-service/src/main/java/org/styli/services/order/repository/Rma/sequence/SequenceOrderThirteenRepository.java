package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceOrderThirteen;

public interface SequenceOrderThirteenRepository
		extends JpaRepository<SequenceOrderThirteen, Integer>, JpaSpecificationExecutor<SequenceOrderThirteen> {

	SequenceOrderThirteen findFirstByOrderBySequenceValueDesc();
}