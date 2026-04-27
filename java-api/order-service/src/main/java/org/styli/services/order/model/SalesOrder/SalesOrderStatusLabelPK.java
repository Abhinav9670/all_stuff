package org.styli.services.order.model.SalesOrder;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

/**
 * @author Umesh, 21/05/2020
 * @project product-service
 */

@Embeddable
@Data
public class SalesOrderStatusLabelPK implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    @Column(name = "status", insertable = false, nullable = false)
    private String status;

    @Column(name = "store_id", insertable = false, nullable = false, columnDefinition = "SMALLINT")
    private Integer storeId;

}
