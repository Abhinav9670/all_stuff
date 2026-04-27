package org.styli.services.order.pojo.request.Order;

import java.util.List;

import org.styli.services.order.pojo.CustomerStatus;
import org.styli.services.order.pojo.response.CustomerAddrees;
import org.styli.services.order.pojo.response.OrderTotal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL)
public class OrdersDetailsResponsedto {

	private Integer orderId;
	
	private String email;

	private String phoneNumber;
	
	private String status;
	
	private String statusLabel;
	
	private String shippingDescription;
	
	private String storeId;
	
	private Integer customerId;
	
	private Integer shippingAddressId;
	
	private String incrementId;
	
	private String orderIncrementId;
	
	private String shippingMethod;
	
	private String createdAt;
	
	private String updatedAt;
	
	private String orderCreatedAt;
	
	private String orderUpdatedAt;
	
	private String estimatedDeliveryTime;
	
	private String itemCount;
	
	private OrderTotal totals;
	
	private String quoteId;
	
	private PaymentInformation paymentInformation;
	
	private CustomerAddrees shippingAddress;
	
	private List<OmsProduct> products;
	
	private Integer callToActionFlag;
	
	private Integer statusStepValue;
	
	private Integer statusColorStepValue;
	
	private String shippingUrl;
	
	private String deliveredAt;
	
	private List<OrderHistory> histories;
		
	private String customerGroup;

	private CustomerStatus customerActiveStatus;

	private String customerIp;
	
	private String purchasedFrom;
	
	private String source;
	
	private String clientVersion;
	
	private Boolean isInvoicedGenerated ;
	
	private Boolean isShipmentGenerated ;
	
	private Integer shipmentId;
	
	private Integer invoiceId;
	
	private Boolean hasWmsPushed;

	private String storeCreditBalance;
	
	private String clientSource;
	
	private String warehouseId;

	private int archived = 0;
	
	private boolean hasGiftProduct;
	
	private boolean isFirstFreeShippingOrder;
	
    private boolean canRetryPayment;
    
    private String paymentExpiresAt;
    
    private String zatcaQrCode;
    
    private String zatcaStatus;
    
    private String extOrderId;

	private List<OrderId> orderIds;

	private List<OmsProduct> localProducts;

	private List<OmsProduct> globalProducts;

	private List<OrderDetailsResponse> orders;

	private Boolean splitOrder = false;

	private TrackingDetails trackings;
}
