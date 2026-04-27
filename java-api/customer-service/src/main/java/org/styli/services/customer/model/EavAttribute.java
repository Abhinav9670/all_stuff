package org.styli.services.customer.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "eav_attribute")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
// @JsonIgnoreProperties(value = {"createdAt", "updatedAt","productDetails"},
// allowGetters = true)
public class EavAttribute implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -5437131094542657693L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @org.hibernate.annotations.Type(type = "int")
    @Column(name = "attribute_id", columnDefinition = "SMALLINT")
    private Integer attributeId;

    @org.hibernate.annotations.Type(type = "int")
    @Column(name = "entity_type_id", columnDefinition = "SMALLINT")
    private Integer entityTypeId; /* need to create mapping entity */

    @Column(name = "attribute_code")
    private String attributeCode;

    @Column(name = "attribute_model")
    private String attributeModel;

    @Column(name = "backend_model")
    private String backEndModel;

    @Column(name = "backend_type")
    private String backEndType;

    @Column(name = "backend_table")
    private String backEndTable;

    @Column(name = "frontend_model")
    private String frontEndModel;

    @Column(name = "frontend_input")
    private String frontEndInput;

    @Column(name = "frontend_label")
    private String frontEndLabel;

    @Column(name = "frontend_class")
    private String frontEndClass;

    @Column(name = "source_model")
    private String sourceModel;

    @org.hibernate.annotations.Type(type = "int")
    @Column(name = "is_required", columnDefinition = "SMALLINT")
    private int isRequired;

    @org.hibernate.annotations.Type(type = "int")
    @Column(name = "is_user_defined", columnDefinition = "SMALLINT")
    private Integer isUserDefined;

    @Column(name = "default_value", length = 65535, columnDefinition = "text")
    private String defaultValue;

    @org.hibernate.annotations.Type(type = "int")
    @Column(name = "is_unique", columnDefinition = "SMALLINT")
    private int isUnique;

    @Column(name = "note")
    private String note;

    

}
