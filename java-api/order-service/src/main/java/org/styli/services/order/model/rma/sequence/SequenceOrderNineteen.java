package org.styli.services.order.model.rma.sequence;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "sequence_order_19")
@Getter
@Setter
public class SequenceOrderNineteen implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(insertable = false, name = "sequence_value", nullable = false)
  private Integer sequenceValue;
  
}