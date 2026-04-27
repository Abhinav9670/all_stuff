package org.styli.services.order.utility;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

public class OrderConstants {
	private static final String CASHONDELIVERY = "cashondelivery";

	private static final String TAMARA_INSTALLMENTS_3 = "tamara_installments_3";

	private static final String TABBY_INSTALLMENTS = "tabby_installments";

	public static final String APPLE_PAY = "apple_pay";

	public static final String MD_PAYFORT_CC_VAULT = "md_payfort_cc_vault";

	public static final String MD_PAYFORT = "md_payfort";

	public static final String PAYFORT_FORT_CC = "payfort_fort_cc";

	
	private static final String BAHRAIN = "Bahrain";

	private static final String QATAR = "Qatar";
	
	private static final String OMAN = "Oman";

	private static final String KUWAIT = "Kuwait";

	private static final String UNITED_ARAB_EMIRATES = "United Arab Emirates";

	private static final String SAUDI_ARABIA = "Saudi Arabia";
	
	public static final ObjectMapper mapper = new ObjectMapper();
	
    public static final String NEW_ORDER_STATE = "new";
    public static final String CANCELED_ORDER_STATE = "closed";
    public static final String PENDING_PAYMENT_ORDER_STATE = "pending_payment";
    public static final String CLOSED_ORDER_STATUS = "closed";
	public static final String CANCELLED_ORDER_STATUS = "cancelled";
    public static final String ORDER_STATE_COMPLETE = "complete";
	public static final String SPLIT_ORDER_CANCELLED_ORDER_STATUS = "cancelled";
	public static final String SPLIT_ORDER_CANCELED_ORDER_STATUS = "canceled";

    public static final String PENDING_PAYMENT_ORDER_STATUS = "pending_payment";
    public static final String PENDING_ORDER_STATUS = "pending";
    public static final String PROCESSING_ORDER_STATUS = "processing";
    public static final String SHIPPED_ORDER_STATUS = "shipped";
    public static final String PACKED_ORDER_STATUS = "packed";
    public static final String DELIVERED_ORDER_STATUS = "delivered";
	public static final String INWARD_MIDMILE_ORDER_STATUS = "Inward_midmile";
	public static final String OUTWARD_MIDMILE_ORDER_STATUS = "outward_midmile";
	public static final String RECEIVED_ORDER_STATUS = "received";
    public static final String UNDELIVERED_ORDER_STATUS = "undelivered";
    public static final String CANCELED_ORDER_STATUS = "payment_canceled";
    public static final String FAILED_ORDER_STATUS = "payment_failed";
    public static final String REFUNDED_ORDER_STATUS = "refunded";
    public static final String ORDER_STATUS_PAYMENT_HOLD = "payment_hold";
    public static final String ORDER_STATE_PAYMENT_HOLD = "holded";
    public static final String ORDER_STATUS_RTO = "rto";

    public static final String PAYMENT_INFORMATION_NEW_CARD = "Credit/Debit Card";
    public static final String PAYMENT_INFORMATION_SAVED_CARD = "Stored Cards (Payfort)";
    public static final String PAYMENT_INFORMATION_COD = "Cash On Delivery";
    public static final String PAYMENT_METHOD_TYPE_FREE = "free";
    public static final String PAYMENT_INFORMATION_COD_INSTRUCTIONS = "Non Refundable fee of  8 SAR will be applied";
    
    public static final String ORDER_ITEM_CONFIGURABLE_TYPE = "configurable";
    
    public static final String ORDER_PUSH_OMS_TYPE = "SO";
    public static final Integer ORDER_PUSH_OMS_LOCATION_CODE = 110;
    public static final String ORDER_PUSH_OMS_OTHER_LOCATION_CODE = "111";
    public static final String ORDER_PUSH_OMS_QC_STATUS= "PASS";
    public static final String ORDER_PUSH_OMS_PAYMENT_COD= "COD";
    public static final String ORDER_PUSH_OMS_NOT_PAYMENT_COD= "NCOD";
    
    public static final String ORDER_PUSH_OMS_DATE_FORMAT= "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public static final String NAVIK_SHIPMENT_OMS_DATE_FORMAT= "yyyy-MM-dd'T'HH:mm:ss";
    public static final String WMS_DEFAULT_TIME_ZONE_STRING= "+00:00";
    
    public static final Integer ORDER_PUSH_DISPATCH_DATE_NUMBER=2;
    public static final String MOVE_ORDER_STATE = "moved";
    
    public static final String PAYFORT_SUCCESS_ORDER_STATUS = "14";
    public static final String PAYFORT_HOLD_ORDER_STATUS = "15";
    
    public static final String PAYFORT_SUCCESS_RESPONSE_CODE = "14000";
    public static final String PAYFORT_HOLD_RESPONSE_CODE = "15777";
    
    public static final Integer WMS_ORDER_PUSH_TIME =5;
    public static final Integer WMS_ORDER_CANCEL_PUSH_TIME = Constants.orderCredentials.getWms().getWmsOrderCancelPushMinutes();
    
    public static final String PAYFORT_DTF_COMMMAND_NAME = "PURCHASE";
    public static final String WMS_ORDER_PUSH_ZIP = "123456";
    public static final String INCREMENT_PADDING = "%09d";
    public static final int STORE_ID_SA_AR = 3;
    public static final int STORE_ID_SA_EN = 1;
    public static final int STORE_ID_AE_AR = 11;
    public static final int STORE_ID_AE_EN = 7;
    public static final int STORE_ID_KW_AR = 13;
    public static final int STORE_ID_KW_EN = 12;
    
    public static final int STORE_ID_QA_AR = 17;
    public static final int STORE_ID_QA_EN = 15;
    public static final int STORE_ID_BH_AR = 21;
    public static final int STORE_ID_BH_EN = 19;
	public static final int STORE_ID_OM_AR = 25;
	public static final int STORE_ID_OM_EN = 23;
    public static final int STORE_ID_IN_EN = 51;

    public static final int CALL_TO_ACTION_FLAG_RETRY_PAYMENT = 1;
    public static final int CALL_TO_ACTION_FLAG_CANCEL = 2;
    public static final int CALL_TO_ACTION_FLAG_TRACK_SHIPMENT = 3;
    public static final int CALL_TO_ACTION_FLAG_REORDER = 4;
    public static final int CALL_TO_ACTION_FLAG_RESCHEDULE_DELIVERY = 5;
    
    public static final String ORDER_ADDRESS_TYPE_SHIPPING = "shipping";

	public static final String PRODUCT_TYPE_LOCAL = "local";

	public static final String PRODUCT_TYPE_GLOBAL = "global";

	public static final String LOCAL_ORDER_SUFFIX = "-L";

	public static final String GLOBAL_ORDER_SUFFIX = "-G";
    
	public static final Integer IS_SPLIT_ORDER = 1;

	// In a constants class
	public static final int CANCELLED_BY_WMS = 1;
	public static final int CANCELLED_BY_SELLER = 2;
	public static final int CANCELLED_BY_CUSTOMER = 3;
	public static final int CANCELLED_BY_SELLER_WITHOUT_MAIN_ORDER = 4;
public static final int CANCELLED_BY_OMS = 6;
	public static final int CANCELLED_BY_SYSTEM = 5;

	public static final Integer WMS_STATUS_PUSH_TO_WMS = 2;
	public static final Integer WMS_STATUS_PUSHED = 3;

    private static final String GMT3 = "GMT+03:00";
    private static final String GMT4 = "GMT+04:00";
	private static final String GMT5 = "GMT+05:30";
	public static final Map<Integer, String> timeZoneMap = new HashMap<Integer, String>() {
		private static final long serialVersionUID = 12345577;
		{
			put(1, GMT3);
			put(3, GMT3);
			put(7, GMT4);
			put(11, GMT4);
			put(12, GMT3);
			put(13, GMT3);
			put(15, GMT3);
			put(17, GMT3);
			put(19, GMT3);
			put(21, GMT3);
			put(23, GMT4);
			put(25, GMT4);
			put(51, GMT5);
		}
	};
    
    public static boolean checkPaymentMethod(String paymentMethod) {
    switch(paymentMethod)
    {
        case PAYFORT_FORT_CC:
           return true;
        case MD_PAYFORT:
        	 return true;
        case MD_PAYFORT_CC_VAULT:
        	 return true;
        case APPLE_PAY:
       	 return true;
        default:
        	 return false;
    }
    }
    
    public static BigDecimal checkPaymentMethodAmountRestn(String paymentMethod) {
        switch(paymentMethod)
        {
            case PAYFORT_FORT_CC:
               return Constants.orderCredentials.getOrderDetails().getOrderRestrictionDetails().getMdpayfortccvault();
            case MD_PAYFORT:
            	 return Constants.orderCredentials.getOrderDetails().getOrderRestrictionDetails().getMdpayfoirt();
            case MD_PAYFORT_CC_VAULT:
            	 return Constants.orderCredentials.getOrderDetails().getOrderRestrictionDetails().getMdpayfortccvault();
            case APPLE_PAY:
           	 return Constants.orderCredentials.getOrderDetails().getOrderRestrictionDetails().getApplepay();
            case TABBY_INSTALLMENTS:
              	 return Constants.orderCredentials.getOrderDetails().getOrderRestrictionDetails().getTabbyinstallment();
            case TAMARA_INSTALLMENTS_3:
              	 return Constants.orderCredentials.getOrderDetails().getOrderRestrictionDetails().getTamarainstallment();
            case CASHONDELIVERY:
             	 return Constants.orderCredentials.getOrderDetails().getOrderRestrictionDetails().getCashondelivery();
            default:
            	 return new BigDecimal(10000);
        }
        }
    
	public static boolean checkTabbyPaymentMethod(String paymentMethod) {
		switch (paymentMethod) {
		case PaymentConstants.TABBY_INSTALMENTS:
			return true;
		case PaymentConstants.TABBY_PAYLATER:
			return true;
		default:
			return false;
		}
	}
	
	public static boolean checkTamaraPaymentMethod(String paymentMethod) {
		switch (paymentMethod) {
		case PaymentConstants.TAMARA_INSTALMENTS_3:
			return true;
		case PaymentConstants.TAMARA_INSTALMENTS_6:
			return true;
		default:
			return false;
		}
	}
	
	public static boolean checkBNPLPaymentMethods(String paymentMethod) {
		switch (paymentMethod) {
		case PaymentConstants.TABBY_INSTALMENTS:
			return true;
		case PaymentConstants.TABBY_PAYLATER:
			return true;
		case PaymentConstants.TAMARA_INSTALMENTS_3:
			return true;
		case PaymentConstants.TAMARA_INSTALMENTS_6:
			return true;
		default:
			return false;
		}
	}
    
    public static final String PAYMENT_METHOD_COD = CASHONDELIVERY;
    
    
	public static String checkCountryName(String storeId) {
		switch (storeId) {
		case "1":
			return SAUDI_ARABIA;
		case "3":
			return SAUDI_ARABIA;
		case "7":
			return UNITED_ARAB_EMIRATES;
		case "11":
			return UNITED_ARAB_EMIRATES;
		case "12":
			return KUWAIT;
		case "13":
			return KUWAIT;
		case "15":
			return QATAR;
		case "17":
			return QATAR;
		case "19":
			return BAHRAIN;
		case "21":
			return BAHRAIN;
		case "23":
			return OMAN;
		case "25":
			return OMAN;
		default:
			return SAUDI_ARABIA;
		}
	}
	
	public static final ImmutableList<String> CANCEL_ORDER_PUSH_STATUS_LIST = 
			  ImmutableList.of("Closed", "Refunded","Canceled","Corrupted","Payfort Failed"
					  ,"Payment Canceled","Payment Failed","RTO","RTO Initiated");
	public static final ImmutableList<String> CANCEL_UNFULFILMENT_ORDER_PUSH_STATUS_LIST = 
			  ImmutableList.of(CANCELED_ORDER_STATE);
	
	public static final ImmutableList<String> CANCEL_WMS_ORDER_PUSH_STATUS_LIST = 
			  ImmutableList.of(CANCELED_ORDER_STATE , FAILED_ORDER_STATUS);
	
	public static final String PAYMENT_TYPE_CARD = "card";
	public static final String PAYMENT_MODE_CODE = MD_PAYFORT;
	
	public static final String PAYFORT_SUCCESS_MESSAGE = "Payfort Payment success!";
	
	public static final String TABBY_SUCCESS_MESSAGE = "Tabby Webhook Payment Success";
	
	public static final String TABBY_QUERY_SUCCESS_MESSAGE = "Tabby Query Payment Success";
	
	public static final String TAMARA_SUCCESS_MESSAGE = "Tamara Webhook Payment Success";
	
	public static final String TAMARA_QUERY_SUCCESS_MESSAGE = "Tamara Query Payment Success";
	
	public static final String PAYFORT_HOLD_MESSAGE = "Payfort Payment on hold!";
	
	public static final String PAYFORT_CANCEL_MESSAGE = "Payfort Payment failed!";
	
	public static final String PAYMENT_FAILED_BY_CRON_MESSAGE = "Payment failed by cron!";
	
	public static final String TABBY_CANCEL_MESSAGE = "Tabby Payment failed";
	
	public static final String ORDER_STATUS_HISTORY_ENTITY = "order";
	
	public static final String INVOICE_STATUS_HISTORY_ENTITY = "invoice";
	
	public static final String SHIPMENT_STATUS_HISTORY_ENTITY = "shipment";
	
	public static final String INVOICE_CREATE_MESSAGE = "Invoice created!";
	
	public static final String SHIPMENT_CREATE_MESSAGE = "Shipment created!";
	public static final String PAYFORT_REFUND_TYPE_CANCEL = "REFUND";
	
	public static final String HOLD_ORDER_CANCEL_MESSAGE = "Retry hold order failed!";
	
	public static final String ORDER_PUSHED_MSG  = "pushed successfully";
	
	public static String checkPaymentCard(String name) {
		switch (name) {
		case "MADA":
			return "MADA";
		case "VISA":
			return "VI";
		case "MASTERCARD":
			return "MC";
		
		default:
			return "";
		}
	}
	
	public static final ImmutableList<String> ORDER_PAYMENT_METHOD_LIST = 
			  ImmutableList.of(PAYFORT_FORT_CC, MD_PAYFORT_CC_VAULT,MD_PAYFORT,APPLE_PAY);
	

	public static final String SMS_TEMPLATE_COD_PARTIAL_UNFULFILMENT= "order_cod_partial_unfulfilment";
	
	public static final String SMS_TEMPLATE_PREPAID_PARTIAL_UNFULFILMENT= "order_prepaid_partial_unfulfilment";
	
	public static final String SMS_TEMPLATE_COD_FULLY_UNFULFILMENT= "order_cod_fully_unfulfilment";
	
	public static final String SMS_TEMPLATE_PREPAID_FULLY_UNFULFILMENT= "order_prepaid_fully_unfulfilment";
	
	public static final String SMS_TEMPLATE_COD_ORDER_CANCEL= "order_cod_cancel";
	
	public static final String SMS_TEMPLATE_PREPAID_ORDER_CANCEL= "order_prepaid_cancel";
	
	public static final String SMS_TEMPLATE_RETURN_CREATE = "return_create";
	
	
	public static final String SMS_TEMPLATE_RETURN_AWB_CREATE = "return_awb_create";
	
	public static final String SMS_TEMPLATE_RETURN_DROP_OFF =  "dropoff_sms";
	
	public static final String SMS_TEMPLATE_RTO_REFUND_INITIATE =  "rto_refund_initiated";
	
	public static final String SMS_TEMPLATE_PAYMENT_FAILED_ORDER_CANCEL= "order_payment_hold_cancel";
	
	public static final String REFUND_TYPE_CANCEL = "cancel";
	
	public static final String REFUND_TYPE_RETURN = "return";
	public static final String PAYFORT_REFUND_CONSTANT_SUCCESS_STATUS = "06";
	public static final String DROPOFF_ADDRESS = "Drop-off At SMSA SSC C\\/O Retail Cart Styli";
	public static final Integer CREATE_AWB_MINUTES_AGO = 15;

	public static final Integer RMA_RETRUN_CLUBBING_TIME_HRS = 24;

	public static final Integer RMA_RETRUN_AWB_CREATI_TIME_MINUTE = 15;
	
	public static final Integer RMA_RETRUN_AWB_CREATE_LIMIT = 50;

	public static final Integer QUERY_DTF_FETCH_TIMING_IN_MINUTE = 2400;
	
	public static final String PAYFORT_DTF_QUERY_SUCCESS_TRANSACTION_CODE= "14000";
	
	public static final String PAYFORT_DTF_QUERY_SUCCESS_TRANSACTION_STATUS= "14";
	
    public static final String PAYFORT_DTF_QUERY_HOLD_TRANSACTION_CODE= "15777";
	
	public static final String PAYFORT_DTF_QUERY_HOLD_TRANSACTION_STATUS= "15";
	
	 public static final Map<Integer, Boolean> CHECKENGLISHSTORE = new HashMap<Integer, Boolean>(){
	        /**
			 * 
			 */
			private static final long serialVersionUID = 12345577;

			{
	            put(1, true);
	            put(3, false);
	            put(7, true);
	            put(11,false);
	            put(12,true);
	            put(13,false);
	            put(15,true);
	            put(17,false);
	            put(19,true);
	            put(21,false);
				put(23,true);
				put(25,false);
	            put(51,true);
	        }
	    };

	public static final String AUTH_BEARER_HEADER = "authorization-token";
	public static final String REFUND_STRING = "REFUND";

	public static final String SELLER_CANCELLED_MSG = "Seller cancellation pushed";

	public static final String REFUND_ERROR_MESSAGE = "Wrong calculation during refund store credit";

	public static final String CANCELLED_MSG_CUSTOMER = "Cancel initiated by customer";

	public static final String CANCELLED_MSG_ADMIN = "Cancel initiated by Admin";
	
	public static final String THE_AUTHORIZED_AMOUNT_IS = "The Authorized Amount is";
	
	public static final String STYLI_CREDIT_FAILED_MSG = "Styli credit amount refunded from payment failed :";
	public static final String ORDER2 = "order";
	
	public static final String PAYFORT_QUERY_RESPONSE_CODE_IS= "PayFort Query Response code is:";
	
	
	public static final String AMOUNT_REFUND_TO_STYLI_CREDIT_MSG = "Amount refunded to styli credit :";
	
	public static final String AND_ONLINE_REFUND = " online refunded  ";
	
	public static final String WE_REFUND_STYLI_CREDIT = "We refunded styli credit ";

	public static final String SHIPMENT_RELEASE_INVENTORY = "create shipment";

	public static final String RELEASE_QUERY_CALL = "payfort query failed";
	public static final String RELEASE_DTF_FAILED_CALL = "payfort dtf failed";
	
	public static final String RELEASE_TABBY_FAILED_CALL = "tabby payment failed";
	
	public static final String RELEASE_TAMARA_FAILED_CALL = "tamara payment failed";


	public static final String RELEASE_SELLER_CANCELLATION = "seller cancellation";

	public static final String RELEASE_INVENTORY_ADMIN = "admin cancellation";

	public static final String RELEASE_CUSTOMER_CANCELLATION = "customer cancellation";
	
	public static final String AND_PAYFORT_ERROR = " Payfort message  ";
	
	public static final String PAYMENT_TYPE = "Tabby";
	  
	public static final String RESPONSE_MESSAGE = "Tabby Payment";

	public static final String OMS_STANDARD_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
	
	public static final String FORWARD_AWB_CREATED_MESSAGE = "Forward Awb Created Successfully ";
	
	public static final String RETURN_AWB_CREATED_MESSAGE = "Return Awb Created Successfully ";
	
	public static final String SMS_TEMPLATE_TABBY_INSTALLMENTS_ORDER_CANCEL= "order_tabby_installment_cancel";
	
	public static final String SMS_TEMPLATE_TABBY_PAYLATER_ORDER_CANCEL= "order_tabby_paylater_cancel";
	
	public static final String TABBY_ORDER_CREATED = "Created";
	
	public static final String SMS_REFUND_TEMPLATE_COD_FREE = "refund_completed_cod";
	
	public static final String SMS_REFUND_TEMPLATE_PREPAID_CARD = "refund_completed_online";
	
	public static final String SMS_REFUND_TEMPLATE_TABBY = "refund_completed_tabby_ins";
	
	public static final String SMS_REFUND_TEMPLATE_TABBY_PAYLATER = "refund_completed_tabby_bnpl";
	
	public static final String CF_SUCCESS_MESSAGE = "Cashfree Webhook Payment Success";
	
	public static final String CF_QUERY_SUCCESS_MESSAGE = "Cashfree Query Payment Success";
	
	SimpleDateFormat formatter = new SimpleDateFormat(OMS_STANDARD_TIME_FORMAT);
	
	public static final String CLOSE_RMA_ORDER = "close";
	public static final String CREATE_RMA_ORDER = "create";
	
	public static final String SHUKRAN_PAYMENT="shukran_payment";
	public static final String SHUKRAN_REFUND_REASON_CANCEL_ORDER = "Refund Shukran Burned Points On Cancel Order";
	public static final String PACKING_STARTED_CANCELLATION_UNAVAILABLE_MSG = "Packing has started, so cancellation is no longer available";
	
	public static final String CAPTURE = "CAPTURE";
	public static final String AUTHORIZATION = "AUTHORIZATION";
	public static final String VOID_AUTHORIZATION = "VOID_AUTHORIZATION";
	public static final String PAYFORT_AUTHORIZATION_CAPTURE = "payfort authorization capture";
	public static final String PAYFORT_AUTHORIZATION_CAPURE_FAIL = "capture failed";
	public static final String PAYFORT_AUTHORIZATION_DTF_SUCCESS_ORDER_STATUS = "02";
	public static final String PAYFORT_AUTHORIZATION_DTF_RESPONSE_CODE = "02000";
	public static final String PAYFORT_VOID_AUTHORIZATION_SUCCESS_STATUS = "08";
	public static final String PAYFORT_VOID_AUTHORIZATION_SUCCESS_RESPONSE_CODE = "08000";
	public static final String CAPTURE_PAYMENT_STATUS = "04";
	public static final String CAPTURE_ALREADY_DONE_PAYMENT_STATUS = "00";
	public static final String CAPTURE_ALREADY_DONE_PAYMENT_CODE = "00042";
	public static final ImmutableList<String> PAYMENT_SUCCESS_CODES = 
			  ImmutableList.of("02000","08000","14000","AUTHORIZED","authorized","approved");
	
}
