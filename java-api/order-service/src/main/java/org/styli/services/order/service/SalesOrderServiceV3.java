package org.styli.services.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.styli.services.order.exception.RollbackException;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesShipment;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SplitSellerOrder;
import org.styli.services.order.pojo.OrderunfulfilmentRequest;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.WarehouseItem;
import org.styli.services.order.pojo.oms.BankSubmitFormRequest;
import org.styli.services.order.pojo.oms.BankSwiftCodeMapperResponse;
import org.styli.services.order.pojo.order.OmsRtoCodRequest;
import org.styli.services.order.pojo.order.OmsRtoCodResponse;
import org.styli.services.order.pojo.order.RMAUpdateV2Request;
import org.styli.services.order.pojo.payFortRefund;
import org.styli.services.order.pojo.recreate.RecreateOrder;
import org.styli.services.order.pojo.recreate.RecreateOrderResponseDTO;
import org.styli.services.order.pojo.request.NavikAddressUpdateDTO;
import org.styli.services.order.pojo.response.Customer;
import org.styli.services.order.pojo.response.NavikAddressUpdateResponse;
import org.styli.services.order.pojo.response.Order.OmsOrderresponsedto;
import org.styli.services.order.pojo.response.Order.OmsUnfulfilmentResponse;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;
import org.styli.services.order.pojo.response.V3.GetInvoiceV3Response;
import org.styli.services.order.pojo.response.V3.GetShipmentV3Response;

import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Service
public interface SalesOrderServiceV3 {

    GetShipmentV3Response getShipmentV3(String orderCode, String shipmentCode);

    GetShipmentV3Response getSellerShipmentV3(String orderCode, String shipmentCode);

    GetInvoiceV3Response getInvoiceV3(String orderCode, String shipmentCode);

    List<SalesOrder> orderpushTowms();

	List<SplitSalesOrder> orderpushTowmsV2();

	List<SplitSellerOrder> orderpushTowmsV3();

	List<SalesOrder> orderpushTowmsForApparel();

    SplitSellerOrder getSalesOrderForSellerOrder(SplitSellerOrder splitSellerOrder);

	Map<Integer,List<WarehouseItem>> orderWmsCancel();

	List<SplitSalesOrder> orderWmsCancelForSplitOrder();

	List<SplitSellerOrder> orderWmsCancelForSellerOrder();

	OmsUnfulfilmentResponse updateUnfulfilmentOrder(@Valid OrderunfulfilmentRequest request,Map<String, String> httpRequestHeadrs);

	@SuppressWarnings("rawtypes")
	ResponseEntity dtfCall(Map<String, String> httpRequestHeadrs,  Map<String, String> requestObject);

	RefundPaymentRespone payfortRefundCall(Map<String, String> httpRequestHeadrs, @Valid payFortRefund request);

    OrderResponseDTO rmaUpdateVersionTwo(RMAUpdateV2Request request);
       
    GetShipmentV3Response getReturnShipment(Map<String, String> httpRequestHeadrs,String requestId);


	GetShipmentV3Response rmaAwbCreation(Map<String, String> httpRequestHeadrs);
	RecreateOrderResponseDTO recreateOrder(Map<String, String> httpRequestHeaders, RecreateOrder request, String incrementId, SalesOrder order);
	
	RecreateOrderResponseDTO recreateOrderForSplitOrder(Map<String, String> httpRequestHeaders, RecreateOrder request, String incrementId, SplitSalesOrder order);
	
	void findSalesOrdersAndSalesGrid(Customer customer);
	
	void sendSms(String  amastyRmaRequestId, String type, String template
			,OrderResponseDTO resp);
	
	void sendCancelOrderSmsAndEMail(OrderunfulfilmentRequest request, String totalCodCancelledAmount);
	
	void sendCancelOrderSmsAndEMail(Integer orderId,boolean isRefund);

    void sendCancelOrderSmsAndEMailForSplit(Integer orderId,boolean isRefund);

    GetShipmentV3Response rmaAwbDropOffCreation(Map<String, String> httpRequestHeadrs);

    BankSwiftCodeMapperResponse getBankSwiftCodes(Map<String, String> httpRequestHeaders);

    BankSwiftCodeMapperResponse submitBankReturnRequest(Map<String, String> httpRequestHeaders, BankSubmitFormRequest request) throws RollbackException;

	void createDropOff(String  amastyRmaRequestId, String type, String template
			,OrderResponseDTO resp);

	OmsOrderresponsedto orderWmsUnhold();
	
	String getFileForCaptureDropoffMailProcessing(String directoryName, List<SalesOrder> list);
	
	void deleteDirectory(File file) throws IOException;

	OmsOrderresponsedto updateWmsOrderCancel();

	List<SalesOrder> payfortQueryFetch();

	RefundPaymentRespone payfortQueryUpdate(SalesOrder order, String deviceId);

	Object getTrackingData(String waybill);
	
	org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse getTrackingDataByIncrementId(String incrementId);
	
	OmsOrderresponsedto orderWmsHoldFalse();
	
	OmsOrderresponsedto findOrdersNotRefunded();

	OmsRtoCodResponse CreateOmsRtoOrderZatca(Map<String, String> httpRequestHeaders, @Valid OmsRtoCodRequest request);

	NavikAddressUpdateResponse updateNavikAddress(NavikAddressUpdateDTO navikAddressRequest)
			throws JsonProcessingException;
		
	void processDummyReturnShipment(String returnIncrementId);

	void processDummyReturnShipment(String returnIncrementId, String csvFileName);
	
	String processDummyReturnShipmentWithCsvContent(List<String> returnIncrementIds);
	
	/**
	 * Sends Braze notification for dangerous goods shipments
	 * Can be called immediately after shipment creation (without Navik response)
	 */
	void sendDangerousGoodsBrazeNotification(SalesOrder order, SalesShipment salesShipment);
}
