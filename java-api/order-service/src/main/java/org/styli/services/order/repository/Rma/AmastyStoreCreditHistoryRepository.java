package org.styli.services.order.repository.Rma;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

public interface AmastyStoreCreditHistoryRepository
        extends JpaRepository<AmastyStoreCreditHistory, Integer>, JpaSpecificationExecutor<AmastyStoreCreditHistory> {
    List<AmastyStoreCreditHistory> findByCustomerId(Integer customerId);

    List<AmastyStoreCreditHistory> findByCustomerIdOrderByHistoryIdDesc(Integer customerId);

    List<AmastyStoreCreditHistory> findByCreatedAtGreaterThan(Timestamp date);
    
    List<AmastyStoreCreditHistory> findByActionDataAndAction(String actionData, int action);
}