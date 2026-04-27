package org.styli.services.order.model.rma.sequence;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

@Table(name = "sequence_creditmemo_3")
@Data
@Entity
public class SequenceCreditmemoThree implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(insertable = false, name = "sequence_value", nullable = false)
    private Integer sequenceValue;

}