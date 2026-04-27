package org.styli.services.order.model.rma;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;

/**
 * Amasty RMA Reason Stores Table
 */
@Getter
@Setter
@Entity
@Table(name = "amasty_rma_reason_store")
public class AmastyRmaReasonStore implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reason_store_id", insertable = false, nullable = false)
    private Integer reasonStoreId;

    @Column(name = "reason_id", nullable = false)
    private Integer reasonId;

    @Column(name = "store_id", nullable = false, columnDefinition = "SMALLINT")
    private Integer storeId;

    @Column(name = "label", nullable = false)
    private String label = "";

    @ManyToOne
    @JoinColumn(name = "reason_id", nullable = false, insertable = false, updatable = false)
    @JsonIgnore
    private AmastyRmaReason amastyRmaReason;

}