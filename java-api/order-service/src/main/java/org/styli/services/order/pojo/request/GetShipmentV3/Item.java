package org.styli.services.order.pojo.request.GetShipmentV3;

import lombok.Data;

@Data
public class Item {
    private String sku;
    private String description;
    private String quantity;
    private String price;
}
