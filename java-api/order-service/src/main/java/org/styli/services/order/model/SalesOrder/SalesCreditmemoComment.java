package org.styli.services.order.model.SalesOrder;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Sales Flat Creditmemo Comment
 */
@Data
@Table(name = "sales_creditmemo_comment")
@Entity
public class SalesCreditmemoComment implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id", insertable = false, nullable = false)
    private Integer entityId;

    @Column(name = "parent_id", nullable = false)
    private Integer parentId;

    @Column(name = "is_customer_notified")
    private Integer isCustomerNotified;

    @Column(name = "is_visible_on_front", nullable = false, columnDefinition = "SMALLINT")
    private Integer isVisibleOnFront;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

}