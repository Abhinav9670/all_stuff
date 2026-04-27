package org.styli.services.order.pojo;

import lombok.Data;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.service.impl.CalculateRefundAmountResponse;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class RefundPaymentInfo {
	private String statusCode;
	private String statusMsg;
	private BigDecimal totalAmountToShowInSMS;
	private String paymentMethod;
	private Integer orderId;
	private String returnIncrementId;
	private AmastyRmaRequest rmaRequest;
	private SalesOrder order;
	private RefundAmountObject refundAmountDetails;
	private BigDecimal totalRefundGiftVoucherAmount;
	private Map<String,BigDecimal> mapSkuList;
	private CalculateRefundAmountResponse calculateRefundAmountResponse;
	private Stores store;
	private BigDecimal taxFactor;
	private String fortId;
}
