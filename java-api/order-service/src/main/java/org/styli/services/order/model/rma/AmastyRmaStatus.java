package org.styli.services.order.model.rma;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Amasty RMA Request Table
 */
@Table(name = "amasty_rma_status")
@Getter
@Setter
@Entity
public class AmastyRmaStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "status_id", insertable = false, nullable = false)
    private Integer statusId;

    @Column(name = "title", nullable = false)
    private String title = "";

    @Column(name = "is_enabled", nullable = false, columnDefinition = "BIT")
    private Integer isEnabled = 1;

    @Column(name = "is_initial", nullable = false, columnDefinition = "BIT")
    private Integer isInitial;

    @Column(name = "auto_event", columnDefinition = "SMALLINT")
    private Integer autoEvent = 0;

    @Column(name = "state", nullable = false, columnDefinition = "SMALLINT")
    private Integer state = 0;

    @Column(name = "grid", nullable = false, columnDefinition = "SMALLINT")
    private Integer grid = 0;

    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    @Column(name = "color", nullable = false)
    private String color = "0";

    @Column(name = "is_deleted", nullable = false, columnDefinition = "BIT")
    private Integer isDeleted;

    @Column(name = "status_code")
    private String statusCode;

    @OneToMany(mappedBy = "amastyRmaStatus", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private Set<AmastyRmaStatusStore> amastyRmaStatusStores = new HashSet<>();

}