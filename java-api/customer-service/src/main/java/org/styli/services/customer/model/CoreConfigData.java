package org.styli.services.customer.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;

@Setter
@Getter
@Entity
@Table(name = "core_config_data")
public class CoreConfigData implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id", insertable = false, nullable = false)
    private Integer configId;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "scope_id", nullable = false)
    private Integer scopeId;

    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "value", columnDefinition = "TEXT")
    private String value;

}
