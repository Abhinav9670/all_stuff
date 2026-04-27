package org.styli.services.customer.model;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

@Table(name = "sequence_customer_entity")
@Data
@Entity
public class SequenceCustomerEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(insertable = false, name = "increment_id", nullable = false)
    private Long sequenceValue;
    
    @Version
    @Column(name = "version")
    private int version;

}