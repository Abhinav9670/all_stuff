package org.styli.services.order.repository.Rma.sequence;

import javax.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.styli.services.order.model.rma.sequence.SequenceOrderOne;

public interface SequenceOrderOneRepository
        extends JpaRepository<SequenceOrderOne, Integer>, JpaSpecificationExecutor<SequenceOrderOne> {
    // SequenceOrderOne findLastByOrderSequenceValueDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    SequenceOrderOne findFirstByOrderBySequenceValueDesc();
}