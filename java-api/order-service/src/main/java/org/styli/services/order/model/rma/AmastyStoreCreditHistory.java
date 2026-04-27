package org.styli.services.order.model.rma;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Amasty Store Credit History Table
 */
@Entity
@Table(name = "amasty_store_credit_history")
@Setter
@Getter
public class AmastyStoreCreditHistory implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(insertable = false, name = "history_id", nullable = false)
  private Integer historyId;

  @Column(name = "customer_history_id", nullable = false)
  private Integer customerHistoryId;

  @Column(name = "customer_id", nullable = false)
  private Integer customerId;

  @Column(name = "is_deduct", nullable = false, columnDefinition = "BIT")
  private Integer deduct;

  @Column(name = "difference", nullable = false)
  private BigDecimal difference;

  @Column(name = "store_credit_balance", nullable = false)
  private BigDecimal storeCreditBalance;

  @Column(name = "action", nullable = false, columnDefinition = "SMALLINT")
  private Integer action;

  @Column(name = "action_data")
  private String actionData;

  @Column(name = "message", columnDefinition = "TEXT")
  private String message;

  @Column(name = "created_at", nullable = false)
  private Timestamp createdAt;

  @Column(name = "store_id", nullable = false, columnDefinition = "SMALLINT")
  private Integer storeId;

}