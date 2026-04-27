package org.styli.services.order.model.sales;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

@Entity
@Table(name = "seller_back_order")
@Getter
@Setter
public class SellerBackOrder implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id", insertable = false, nullable = false)
    private Integer entityId;

    @Column(name = "seller_id", nullable = false)
    private Integer sellerId;

    @Column(name = "back_order_incrementid", nullable = false, length = 64)
    private String backOrderIncrementid;

    @Column(name = "status", nullable = false, length = 16)
    private String status; // OPEN, CLOSED

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "closed_at")
    private Timestamp closedAt;
}