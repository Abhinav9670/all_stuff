package org.styli.services.customer.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import lombok.Data;

@Data
@Embeddable
public class ProductId implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    @Column(name = "product_id", columnDefinition = "INTEGER")
    // @Convert(converter = JpaConverterJson.class,attributeName = "productId")
    private Integer productId;

}