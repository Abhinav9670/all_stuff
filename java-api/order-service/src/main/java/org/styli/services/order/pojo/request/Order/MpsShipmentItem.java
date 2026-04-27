package org.styli.services.order.pojo.request.Order;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MpsShipmentItem {

    @JsonProperty("skuDescription")
    private String skuDescription;

    private Integer quantity;
    private Double price;
    private String sku;
}
