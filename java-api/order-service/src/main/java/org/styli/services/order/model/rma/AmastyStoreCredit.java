package org.styli.services.order.model.rma;

import java.io.Serializable;
import java.math.BigDecimal;
import javax.persistence.*;

import lombok.Getter;
import lombok.Setter;

/**
 * Amasty Store Credit Table
 */
@Table(name = "amasty_store_credit")
@Setter
@Getter
@Entity
public class AmastyStoreCredit implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(insertable = false, name = "store_credit_id", nullable = false)
  private Integer storeCreditId;

  @Column(name = "customer_id", nullable = false)
  private Integer customerId;

  @Column(name = "store_credit", nullable = false)
  private BigDecimal storeCredit;

  @Column(name = "returnable_amount", nullable = false)
  private BigDecimal returnableAmount = BigDecimal.ZERO;

  @Version
  @Column(name = "version")
  private int version;

}