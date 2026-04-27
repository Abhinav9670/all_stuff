package org.styli.services.order.model.rma.sequence;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Data
@Table(name = "sequence_creditmemo_7")
public class SequenceCreditmemoSeven implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(insertable = false, name = "sequence_value", nullable = false)
    private Integer sequenceValue;

}