package org.styli.services.order.repository.Rma.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.sequence.SequenceOrderTwelve;

public interface SequenceOrderTwelveRepository
        extends JpaRepository<SequenceOrderTwelve, Integer>, JpaSpecificationExecutor<SequenceOrderTwelve> {

    SequenceOrderTwelve findFirstByOrderBySequenceValueDesc();
}