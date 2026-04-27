package org.styli.services.customer.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 15/06/2022 - 12:23 PM
 */

@Entity
@Table(name = "delete_customers_events")
@Getter
@Setter
public class DeleteCustomersEventsEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id")
    private Integer entityId;

    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "task")
    private String task;

    @Column(name = "status")
    private Integer status;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "updated_at")
    private Date updatedAt;

}
