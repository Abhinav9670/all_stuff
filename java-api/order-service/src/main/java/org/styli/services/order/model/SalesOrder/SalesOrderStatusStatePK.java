package org.styli.services.order.model.SalesOrder;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
@Data
public class SalesOrderStatusStatePK implements Serializable {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    @Column(insertable = false, name = "status", nullable = false)
    private String status;

    @Column(name = "state", insertable = false, nullable = false)
    private String state;
}
