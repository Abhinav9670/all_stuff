package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WmsDetails {

    @JsonProperty("WMS_OLD_ORDER_SELLER_SKU_MINUTE")
    private Integer wmsOldOrderSellerSkuMinute;

	@JsonProperty("WMS_DISPATCH_DAY_NUMBER")
	private Integer wmsdispatchDaysNumber;
	
	@JsonProperty("WMS_DISPATCH_HOUR_NUMBER")
	private Integer wmsdispatchHoursNumber;
	
	@JsonProperty("WMS_ORDER_PUSH_MINUTE")
	private Integer wmsOrderPushMinutes;
	
	@JsonProperty("WMS_ORDER_CANCEL_PUSH_MINUTE")
	private Integer wmsOrderCancelPushMinutes;

	@JsonProperty("SELLER_CENTRAL_AWB_FAILED_RETRY_MINUTE")
	private Integer sellerCentralAwbFailedRetryMinutes;
	
	@JsonProperty("CHECK_BNPLAMOUNT_DIFFERENCE")
	private boolean checkBNPLAmountDifference;
	
	@JsonProperty("IS_NEW_INVOICE_ENCODE")
	private boolean newInVoiceEncode;
	
	@JsonProperty("WMS_ORDER_UNHOLD_PUSH_MINUTE")
	private Integer wmsOrderUnholdPushMinutes;
	
	@JsonProperty("WMS_CANCEL_ORDER_WMS_STATUS_CHECK_MINUTES")
	private Integer wmsCancelOrderWmsStatusCheckMinutes;
	
	@JsonProperty("WMS_HOLD_ORDER_PUSH_MINUTE")
	private Integer wmsHoldOrderPushMinutes;
	
	@JsonProperty("WMS_ORDER_HOLD_FALSE_HOURS")
	private Integer wmsOrderHoldFalseHours;
	
	@JsonProperty("techSupportEmail")
	private String techSupportEmail;
	
	@JsonProperty("REFUND_EMAIL_DAYS_FROM_START")
	private Integer RefundEmailDaysFromStart;
	
	@JsonProperty("REFUND_EMAIL_DAYS_FROM_END")
	private Integer RefundEmailDaysFromEnd;
		
}
