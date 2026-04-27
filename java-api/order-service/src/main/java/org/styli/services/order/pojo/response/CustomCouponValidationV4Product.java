package org.styli.services.order.pojo.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Created on 03-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class CustomCouponValidationV4Product implements Serializable {

    private static final long serialVersionUID = -4236186909089959167L;

    private String parentProductId;
    private String productId;
    private Integer quantity;
    private BigDecimal rowTotalInclTax;
    private BigDecimal discountPercent;
    private BigDecimal discount;
    private BigDecimal discountAmount;
    private BigDecimal priceInclTax;
    private BigDecimal discountTaxCompensationAmount;
    private BigDecimal rowTotal;
    private BigDecimal price;
    private BigDecimal taxPercent;
    private BigDecimal taxAmount;
    private PriceDetails prices;
}
