package org.styli.services.customer.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 04/05/2022 - 11:17 AM
 */

@Entity
@Table(name = "customer_logs")
@Getter
@Setter
public class CustomerLogs implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id")
    private Integer entityId;

    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "guest_id")
    private Integer guestId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "action")
    private String action;

    @Column(name = "created_at")
    private Timestamp createdAt;

}
