package org.styli.services.order.service;

import java.util.List;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.stereotype.Service;
import org.styli.services.order.pojo.response.BulkShipmentResponse;
import org.styli.services.order.pojo.response.OmsOrderoutboundresponse;
import org.styli.services.order.pojo.response.Order.OmsUnfulfilmentResponse;
import org.styli.services.order.pojo.OrderunfulfilmentRequest;
import org.styli.services.order.pojo.request.Order.OrderViewRequest;
import org.styli.services.order.pojo.response.V3.GetShipmentV3Response;


@Service
public interface SellerOrderService {
    public OmsOrderoutboundresponse crerateSellerShipment(@Valid OrderViewRequest request);
    public BulkShipmentResponse createBulkSellerShipmentsV2(@Valid OrderViewRequest request);
    public OmsUnfulfilmentResponse createSellerCancellation(OrderunfulfilmentRequest request, Map<String, String> httpRequestHeadrs);
    public GetShipmentV3Response getSellerShipmentV3(String orderCode, String shipmentCode);
    public void updateAwbFailedShipments(Map<String, String> httpRequestHeaders);
}
