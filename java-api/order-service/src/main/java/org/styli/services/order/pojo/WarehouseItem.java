package org.styli.services.order.pojo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WarehouseItem {
    private String incrementId;
    private Integer storeId;
    private Integer warehouseId;
    private Integer itemId;
    private String sku;
    private BigDecimal qtyOrdered;
    private String productType;

}