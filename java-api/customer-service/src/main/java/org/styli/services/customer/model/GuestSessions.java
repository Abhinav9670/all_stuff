package org.styli.services.customer.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 29/04/2022 - 12:03 PM
 */

@Entity
@Table(name = "guest_sessions")
@Getter
@Setter
public class GuestSessions implements Serializable  {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id")
    private Integer entityId;

    @Column(name = "device_id")
    private String deviceId;
    
    @Column(name = "client_version")
    private String clientVersion;
    
    @Column(name = "platform")
    private String platform;
    
    @Column(name = "store_id", columnDefinition = "SMALLINT")
    private Integer storeId;

    @Column(name = "user_footprint")
    private String userFootprint;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

}
