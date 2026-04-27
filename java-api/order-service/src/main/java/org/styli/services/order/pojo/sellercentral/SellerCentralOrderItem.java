package org.styli.services.order.pojo.sellercentral;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Timestamp;


@Getter
@Setter
public class SellerCentralOrderItem {


    private Integer itemId;
    private String mainOrderId;
    private String splitOrderId;
    private String sellerOrderId;
    private Integer storeId;
    private String sku;
    private String sellerId;
    private String sellerName;
    private String warehouseId;
    private String shipmentType;
    private BigDecimal qtyCanceled = BigDecimal.ZERO;
    private BigDecimal qtyShipped = BigDecimal.ZERO;
    private BigDecimal qtyOrdered = BigDecimal.ZERO;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Integer cancelledBy;
    private BigDecimal sellingPrice;
    private BigDecimal discount;
    private BigDecimal actualPrice;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private String productName;
    private String imageUrl;
    private String vendorSku;
}
