package org.styli.services.order.pojo.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * @author Umesh, 30/04/2020
 * @project product-service
 */

@Data
public class CustomCouponValidationV4ResponseBody implements Serializable {

    private static final long serialVersionUID = 8559586037483638002L;

    // private Double storeCreditBalance;
    // private Double storeCreditApplied;
    // private Double codCharges;

    private BigDecimal subtotalWithDiscount;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private List<CustomCouponValidationV4Discount> discounts;
    private List<CustomCouponValidationV4Product> products;

}
