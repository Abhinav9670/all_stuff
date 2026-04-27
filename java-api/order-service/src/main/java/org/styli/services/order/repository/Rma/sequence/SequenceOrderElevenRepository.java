package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceOrderEleven;

public interface SequenceOrderElevenRepository
        extends JpaRepository<SequenceOrderEleven, Integer>, JpaSpecificationExecutor<SequenceOrderEleven> {

    SequenceOrderEleven findFirstByOrderBySequenceValueDesc();
}