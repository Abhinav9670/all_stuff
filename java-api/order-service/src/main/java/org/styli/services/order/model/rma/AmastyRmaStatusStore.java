package org.styli.services.order.model.rma;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;

/**
 * Amasty RMA Status Stores Table
 */
@Table(name = "amasty_rma_status_store")
@Getter
@Setter
@Entity
public class AmastyRmaStatusStore implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "status_store_id", insertable = false, nullable = false)
    private Integer statusStoreId;

    @Column(name = "status_id", nullable = false)
    private Integer statusId;

    @Column(name = "store_id", nullable = false, columnDefinition = "SMALLINT")
    private Integer storeId;

    @Column(name = "label", nullable = false)
    private String label = "";

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "send_email_to_customer", nullable = false, columnDefinition = "BIT")
    private Integer sendEmailToCustomer;

    @Column(name = "customer_email_template", nullable = false)
    private Integer customerEmailTemplate;

    @Column(name = "customer_custom_text", nullable = false, columnDefinition = "TEXT")
    private String customerCustomText;

    @Column(name = "send_email_to_admin", nullable = false, columnDefinition = "BIT")
    private Integer sendEmailToAdmin;

    @Column(name = "admin_email_template", nullable = false)
    private Integer adminEmailTemplate;

    @Column(name = "admin_custom_text", nullable = false, columnDefinition = "TEXT")
    private String adminCustomText;

    @Column(name = "send_to_chat", nullable = false, columnDefinition = "BIT")
    private Integer sendToChat;

    @Column(name = "chat_message", nullable = false, columnDefinition = "TEXT")
    private String chatMessage;

    @ManyToOne
    @JoinColumn(name = "status_id", nullable = false, insertable = false, updatable = false)
    @JsonIgnore
    private AmastyRmaStatus amastyRmaStatus;

}