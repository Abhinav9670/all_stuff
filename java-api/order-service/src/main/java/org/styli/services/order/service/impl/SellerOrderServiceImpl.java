package org.styli.services.order.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.sales.SplitSellerOrder;
import org.styli.services.order.model.sales.SplitSellerShipment;
import org.styli.services.order.pojo.OrderunfulfilmentRequest;
import org.styli.services.order.pojo.request.Order.OrderViewRequest;
import org.styli.services.order.pojo.response.BulkShipmentResponse;
import org.styli.services.order.pojo.response.OmsOrderoutboundresponse;
import org.styli.services.order.pojo.response.Order.OmsUnfulfilmentResponse;
import org.styli.services.order.repository.SalesOrder.SplitSellerOrderRepository;
import org.styli.services.order.service.SellerOrderService;
import org.styli.services.order.service.impl.child.OrderShipmentHelper;
import org.styli.services.order.service.SalesOrderServiceV3;
import org.styli.services.order.pojo.response.V3.GetShipmentV3Response;
import org.styli.services.order.pojo.sellercentral.SellerCentralOrder;
import org.styli.services.order.utility.Constants;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@Component
public class SellerOrderServiceImpl implements SellerOrderService {

    private static final Log LOGGER = LogFactory.getLog(SellerOrderServiceImpl.class);

    @Autowired
    SplitSellerOrderRepository splitSellerOrderRepository;

    @Autowired
    OrderShipmentHelper orderShipmentHelper;

    @Autowired
    OrderHelper orderHelper;
    
    @Autowired
    SalesOrderServiceV3 salesOrderServiceV3;

    @Autowired
    PubSubServiceImpl pubSubServiceImpl;

    @Value("${pubsub.topic.seller.central.create.order}")
	private String sellerCentralCreateOrderTopic;

    @Override
    @Transactional
    public OmsOrderoutboundresponse crerateSellerShipment(@Valid OrderViewRequest request) {
        SplitSellerOrder splitSellerOrder = splitSellerOrderRepository.findByIncrementId(request.getOrderCode());
        return orderShipmentHelper.createSellerOrderShipment(request, splitSellerOrder);
    }

    @Override
    @Transactional
    public BulkShipmentResponse createBulkSellerShipmentsV2(@Valid OrderViewRequest request) {
        return orderShipmentHelper.createBulkSellerShipmentsV2(request);
    }

    @Override
    @Transactional
    public OmsUnfulfilmentResponse createSellerCancellation(OrderunfulfilmentRequest request, Map<String, String> httpRequestHeadrs) {
        SplitSellerOrder splitSellerOrder = splitSellerOrderRepository.findByIncrementId(request.getOrderCode());
        return orderHelper.cancelSellerOrder(splitSellerOrder, request, httpRequestHeadrs);
    }

    @Override
    @Transactional
    public GetShipmentV3Response getSellerShipmentV3(String orderCode, String shipmentCode) {
        return salesOrderServiceV3.getSellerShipmentV3(orderCode, shipmentCode);
    }

    @Override
    @Transactional
    public void updateAwbFailedShipments(Map<String, String> httpRequestHeaders) {
        List<SplitSellerOrder> splitSellerOrders = splitSellerOrderRepository.findByAwbFailed(
            Constants.orderCredentials.getWms().getSellerCentralAwbFailedRetryMinutes().intValue()
        );
        
        for (SplitSellerOrder splitSellerOrder : splitSellerOrders) {
            processAwbFailedOrder(splitSellerOrder);
        }
    }
    
    /**
     * Process a single AWB failed order
     */
    private void processAwbFailedOrder(SplitSellerOrder splitSellerOrder) {
        List<SplitSellerShipment> splitSellerShipments = splitSellerOrder.getSplitSellerShipments()
            .stream()
            .collect(Collectors.toList());
        
        if (splitSellerShipments.isEmpty()) {
            return;
        }
        
        String shipmentCode = splitSellerShipments.get(0).getIncrementId();
        GetShipmentV3Response shipmentResponse = salesOrderServiceV3.getSellerShipmentV3(
            splitSellerOrder.getIncrementId(), 
            shipmentCode
        );
        
        if (shipmentResponse.isHasError()) {
            LOGGER.error("Awb failed to create for order: " + splitSellerOrder.getIncrementId() + 
                " and shipment: " + shipmentCode);
            return;
        }
        
        handleAwbCreationSuccess(splitSellerOrder, shipmentCode, shipmentResponse);
    }
    
    /**
     * Handle successful AWB creation
     */
    private void handleAwbCreationSuccess(
            SplitSellerOrder splitSellerOrder, 
            String shipmentCode, 
            GetShipmentV3Response shipmentResponse) {
        
        splitSellerOrder.setAwbFailed(2);
        splitSellerOrderRepository.save(splitSellerOrder);
        LOGGER.info("Awb successfully created for order: " + splitSellerOrder.getIncrementId() + 
            " and shipment: " + shipmentCode);
        
        publishAwbToSellerCentral(
            splitSellerOrder.getIncrementId(),
            shipmentResponse
        );
    }
    
    /**
     * Publish AWB created event to Seller Central
     */
    private void publishAwbToSellerCentral(
            String sellerOrderId,
            GetShipmentV3Response shipmentResponse) {
        
        SellerCentralOrder sellerCentralOrder = new SellerCentralOrder();
        sellerCentralOrder.setSellerOrderId(sellerOrderId);
        sellerCentralOrder.setShipmentResponse(shipmentResponse);
        
        List<SellerCentralOrder> sellerCentralOrders = new ArrayList<>();
        sellerCentralOrders.add(sellerCentralOrder);
        
        Map<String, Object> requestPayload = new HashMap<>();
        requestPayload.put("type", "awb_created");
        requestPayload.put("payload", sellerCentralOrders);
        
        pubSubServiceImpl.publishSellerCentralPubSub(sellerCentralCreateOrderTopic, requestPayload);
    }
}
