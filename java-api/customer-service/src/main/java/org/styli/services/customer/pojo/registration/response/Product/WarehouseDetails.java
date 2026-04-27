package org.styli.services.customer.pojo.registration.response.Product;

import lombok.Data;

import java.io.Serializable;
import java.sql.Timestamp;

@Data
class WarehouseDetails implements Serializable {
    private String warehouseId;
    private Integer min_sla;
    private Integer max_sla;
    private String estimated_date;
    private String min_estimated_date;
    private String max_estimated_date;
    private String fulfillment_mode;
    private boolean isExpressDelivery;
}