package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Created on 16-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigProduct {

    private Integer productId;

    private String productName;

    private Boolean isVisible;

    private String sku;

    private Double price;

    private Double specialPrice;

    private Integer quantity=0;

    private Integer discount;

    private String size;

    private Integer sizeOrder;

    private Integer sizeOptionId;

    private Boolean productStatus;
}
