package org.styli.services.order.model.sales;

import java.io.Serializable;
import java.sql.Timestamp;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * Sales Flat Order Status History
 */
@Table(name = "sales_order_status_history")
@Entity
@Data
public class SalesOrderStatusHistory implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "entity_id", insertable = false, nullable = false)
  private Integer entityId;

  @Column(name = "parent_id", nullable = false)
  private Integer parentId;

  @Column(name = "split_order_id")
  private Integer splitOrderId;

  @Column(name = "is_customer_notified")
  private Integer customerNotified;

  @Column(name = "is_visible_on_front", nullable = false, columnDefinition = "SMALLINT")
  private Integer visibleOnFront;

  @Column(name = "comment", columnDefinition = "TEXT")
  private String comment;

  @Column(name = "status")
  private String status;

  @Column(name = "created_at", nullable = false)
  private Timestamp createdAt;

  @Column(name = "entity_name")
  private String entityName;

  @Column(name = "final_status")
  private String finalStatus;

  @ManyToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "parent_id", nullable = false, insertable = false, updatable = false)
  @JsonIgnore
  private SalesOrder salesOrder;

  @ManyToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "split_order_id", insertable = false, updatable = false)
  @JsonIgnore
  private SplitSalesOrder splitSalesOrder;
}