package org.styli.services.order.pojo.request.Order;

import java.util.List;


import org.styli.services.order.pojo.response.OrderTotal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL)
public class OrderDetailsResponse {

    private Integer orderId;
    
    private String orderIncrementId;
    
    private Integer invoiceId;
    
    private String invoiceIncrementId;
    
    private Boolean isInvoiceGenerated;
    
    private TrackingDetails trackings;
    
    private OrderTotal totals;
    
    private List<OmsProduct> products;
    
    private List<OrderHistory> histories;
    
    private Integer splitOrderId;
    
    private String estimatedDeliveryDate;
} 