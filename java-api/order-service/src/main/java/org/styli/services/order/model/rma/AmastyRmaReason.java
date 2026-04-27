package org.styli.services.order.model.rma;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Amasty RMA Return Reasons Table
 */
@Table(name = "amasty_rma_reason")
@Getter
@Setter
@Entity
public class AmastyRmaReason implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reason_id", insertable = false, nullable = false)
    private Integer reasonId;

    @Column(name = "title", nullable = false)
    private String title = "";

    @Column(name = "payer", nullable = false, columnDefinition = "SMALLINT")
    private Integer payer = 0;

    @Column(name = "status", nullable = false, columnDefinition = "SMALLINT")
    private Integer status = 0;

    @Column(name = "position", nullable = false, columnDefinition = "SMALLINT")
    private Integer position = 0;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "BIT")
    private Integer isDeleted;

    @OneToMany(mappedBy = "amastyRmaReason", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private Set<AmastyRmaReasonStore> amastyRmaReasonStores = new HashSet<>();

}