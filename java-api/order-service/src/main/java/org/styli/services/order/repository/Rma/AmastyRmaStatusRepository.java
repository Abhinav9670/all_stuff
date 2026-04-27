package org.styli.services.order.repository.Rma;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.styli.services.order.model.rma.AmastyRmaStatus;

public interface AmastyRmaStatusRepository
        extends JpaRepository<AmastyRmaStatus, Integer>, JpaSpecificationExecutor<AmastyRmaStatus> {

    AmastyRmaStatus findByStatusId(Integer statusId);

    @Query(value = "select status_id from amasty_rma_status so where so.status_code in ?1", nativeQuery = true)
    List<Integer> findByStatusCode(List<String> statusCodes);

    @Query(value = "select color from amasty_rma_status where status_id = ?1", nativeQuery = true)
    String findColorByStatusId(String statusCode);

}