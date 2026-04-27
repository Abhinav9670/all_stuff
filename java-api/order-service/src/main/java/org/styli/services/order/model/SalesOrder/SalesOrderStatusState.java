package org.styli.services.order.model.SalesOrder;

import java.io.Serializable;
import javax.persistence.*;

import lombok.Data;

@Entity
@Data
@Table(name = "sales_order_status_state")
public class SalesOrderStatusState implements Serializable {
    private static final long serialVersionUID = 1L;

    @EmbeddedId
    private SalesOrderStatusStatePK id;

    @Column(name = "is_default", nullable = false, columnDefinition = "SMALLINT")
    private Integer isDefault;

    @Column(name = "visible_on_front", nullable = false, columnDefinition = "SMALLINT")
    private Integer visibleOnFront = 0;

    @Column(name = "step", columnDefinition = "SMALLINT")
    private Integer step;

    @Column(name = "color_state", columnDefinition = "SMALLINT")
    private Integer colorStep;

}