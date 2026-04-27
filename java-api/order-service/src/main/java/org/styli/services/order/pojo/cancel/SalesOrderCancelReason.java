package org.styli.services.order.pojo.cancel;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Order Cancel Reason
 */
@Entity
@Getter
@Setter
@Table(name = "sales_order_cancel_reason")
public class SalesOrderCancelReason implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reason_id", insertable = false, nullable = false)
    private Integer reasonId;

    @Column(name = "title")
    private String title;

    @Column(name = "status", nullable = false, columnDefinition = "SMALLINT")
    private Integer status = 0;

    @Column(name = "sort_order", nullable = false, columnDefinition = "SMALLINT")
    private Integer sortOrder = 0;

    @OneToMany(mappedBy = "salesOrderCancelReason", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private Set<SalesOrderCancelReasonStore> salesOrderCancelReasonStores = new HashSet<>();

}