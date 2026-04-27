package org.styli.services.order.pojo.cancel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;

/**
 * Order Cancel Reason Store
 */
@Table(name = "sales_order_cancel_reason_store")
@Getter
@Setter
@Entity
public class SalesOrderCancelReasonStore implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reason_store_id", insertable = false, nullable = false)
    private Integer reasonStoreId;

    @Column(name = "reason_id", nullable = false)
    private Integer reasonId;

    @Column(name = "store_id", nullable = false, columnDefinition = "SMALLINT")
    private Integer storeId;

    @Column(name = "label")
    private String label;

    @ManyToOne
    @JoinColumn(name = "reason_id", nullable = false, insertable = false, updatable = false)
    @JsonIgnore
    private SalesOrderCancelReason salesOrderCancelReason;

}