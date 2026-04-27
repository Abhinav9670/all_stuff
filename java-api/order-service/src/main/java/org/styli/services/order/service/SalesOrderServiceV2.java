package org.styli.services.order.service;

import java.util.Map;

import javax.validation.Valid;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestHeader;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.pojo.QuoteUpdateDTO;
import org.styli.services.order.pojo.order.AddStoreCreditRequest;
import org.styli.services.order.pojo.order.CreateReplicaQuoteV4Request;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
import org.styli.services.order.pojo.response.AddStoreCreditResponse;
import org.styli.services.order.pojo.response.Order.CreateOrderResponseDTO;

/**
 * @author Umesh, 24/09/2020
 * @project product-service
 */

@Service
public interface SalesOrderServiceV2 {

    CreateOrderResponseDTO convertQuoteToOrderV2(CreateOrderRequestV2 request, String tokenHeader, String incrementId,
            String xSource,Map<String, String> requestHeader, String xHeaderToken,String xClientVersion,String customerIp, String deviceId) throws NotFoundException;

  

    public Boolean authenticateCheck(@RequestHeader Map<String, String> requestHeader, Integer customerId);

	AddStoreCreditResponse addStoreCredit(@Valid AddStoreCreditRequest request);
	
	int updateRatingStatus(String ratingStatus, Integer orderId);
	public Boolean authenticateOrderCheck(@RequestHeader Map<String, String> requestHeader, Integer customerId);

	public QuoteUpdateDTO createQuoteReplica(CreateReplicaQuoteV4Request request, String tokenHeader, String deviceId);
	
	public QuoteUpdateDTO createRetryPaymentReplica(CreateReplicaQuoteV4Request request, String tokenHeader, String deviceId);

	AddStoreCreditResponse brazeWalletUpdate(Map<String, String> httpRequestHeaders, AddStoreCreditRequest request);

    AddStoreCreditResponse brazeAttributePush(Map<String, String> httpRequestHeaders);

	CreateOrderResponseDTO convertQuoteToOrderV3(@Valid CreateOrderRequestV2 request, String tokenHeader, String incrementId, String xSource, Map<String, String> requestHeader, String xHeaderToken, String xClientVersion, String ipAddress, String deviceId);
}
