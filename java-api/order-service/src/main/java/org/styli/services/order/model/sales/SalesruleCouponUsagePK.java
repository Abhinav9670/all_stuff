package org.styli.services.order.model.sales;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

/**
 * @author Umesh, 10/04/2020
 * @project product-service
 */

@Embeddable
@Data
public class SalesruleCouponUsagePK implements Serializable {

    @Column(name = "coupon_id", insertable = false, nullable = false)
    private Integer couponId;

    @Column(name = "customer_id", insertable = false, nullable = false)
    private Integer customerId;

}
