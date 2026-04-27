package org.styli.services.order.pojo.sellercentral;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import org.styli.services.order.pojo.response.V3.GetShipmentV3Response;

@Getter
@Setter
public class SellerCentralOrder {

    private String sellerOrderId;
    private String mainOrderId;
    private String splitOrderId;
    private String sellerId;
    private String warehouseId;
    private String shipmentMode;
    private Boolean hasGlobalShipment;
    private Timestamp orderCreatedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String status;
    private String fulfillmentBy;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private String shipFrom;
    private String shipTo;
    private String firstmileWarehouseName;
    private String midmileWarehouseId;
    private String midmileWarehouseName;
    private String lastmileWarehouseId;
    private String lastmileWarehouseName;
    private String currency;
    private Timelines timelines;
    private List<SellerCentralOrderItem> items;
    private String sku;
    private Integer cancelledBy;
    private String cancellationReason;
    private List<SellerCentralOrderAddress> addresses;
    private Boolean pickedUpByStyli = Boolean.FALSE;
    private String lastMileAddress;
    private String awbNumber;
    private String shippingLabelUrl;
    private String transporter;
    private GetShipmentV3Response shipmentResponse;

    @Getter
    @Setter
    public static class Timelines {
        private String accSla;
        private String packSla;
        private String shipSla;
        private String maxAccSla;
        private String maxPackSla;
        private String maxShipSla;
    }
}
