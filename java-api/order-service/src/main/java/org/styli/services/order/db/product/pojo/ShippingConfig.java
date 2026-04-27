package org.styli.services.order.db.product.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShippingConfig {

    private LocalConfig local;
    private GlobalConfig global;
    private BigDecimal minimumShippingCharge;
    private BigDecimal consolidatedOrderFreeShippingThreshold;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocalConfig {
        private BigDecimal localShipmentAmount;
        private BigDecimal localShipmentThreshold;
        private String shipmentMapping;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GlobalConfig {
        private BigDecimal globalShipmentAmount;
        private BigDecimal globalShipmentThreshold;
        private String shipmentMapping;
    }
}
