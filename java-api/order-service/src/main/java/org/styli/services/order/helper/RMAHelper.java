package org.styli.services.order.helper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.pojo.cancel.MagentoAPIResponse;
import org.styli.services.order.pojo.order.MagentoReturnAWBRequest;
import org.styli.services.order.pojo.order.RMAOrderRequest;
import org.styli.services.order.pojo.order.RMAOrderV2Request;
import org.styli.services.order.repository.Rma.AmastyRmaRequestItemRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyRmaTrackingRepository;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.utility.Constants;

/**
 * @author Umesh, 30/05/2020
 * @project product-service
 */

@Component
public class RMAHelper {

    private static final Log LOGGER = LogFactory.getLog(RMAHelper.class);

    @Value("${magento.integration.token}")
    private String magentoIntegrationToken;

    @Value("${magento.base.url}")
    private String magentoBaseUrl;

    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;

    @Autowired
    AmastyRmaRequestRepository amastyRmaRequestRepository;

    @Autowired
    ConfigService configService;

    @Autowired
    AmastyRmaRequestItemRepository amastyRmaRequestItemRepository;

    @Autowired
    AmastyRmaTrackingRepository amastyRmaTrackingRepository;

    @Autowired
    OrderHelper orderHelper;

    @Value("${env}")
    private String env;

    public void deleteReturnRequest(Integer requestId) {

        try {
            amastyRmaRequestRepository.deleteById(requestId);
        } catch (Exception e) {
            LOGGER.error("Could not delete rma with ID: " + requestId);
        }

    }

    public AmastyRmaRequest createReturnRequest(RMAOrderRequest request, SalesOrder order) {

        AmastyRmaRequest amastyRmaRequest = new AmastyRmaRequest();
        amastyRmaRequest.setOrderId(order.getEntityId());
        amastyRmaRequest.setStoreId(request.getStoreId());
        amastyRmaRequest.setCreatedAt(new Timestamp(new Date().getTime()));
        amastyRmaRequest.setModifiedAt(new Timestamp(new Date().getTime()));
        amastyRmaRequest.setStatus(4);
        amastyRmaRequest.setCustomerId(request.getCustomerId());
        String customerName = order.getCustomerFirstname() + " " + order.getCustomerLastname();
        amastyRmaRequest.setCustomerName(customerName);
        amastyRmaRequest.setUrlHash("");
        amastyRmaRequest.setManagerId(0);
        amastyRmaRequest.setCustomFields("");
        amastyRmaRequest.setRating(0);
        amastyRmaRequest.setIsCreatedByAdmin("0");
        amastyRmaRequest.setRmaIncId("");
        amastyRmaRequestRepository.saveAndFlush(amastyRmaRequest);

        Integer requestId = amastyRmaRequest.getRequestId();
        // Sending storeId to prevent appending storeId at the beginning
        String rmaIncId = orderHelper.generateIncrementId(requestId, 1);
        rmaIncId = "R" + rmaIncId;
        amastyRmaRequest.setRmaIncId(rmaIncId);
        amastyRmaRequestRepository.saveAndFlush(amastyRmaRequest);

        return amastyRmaRequest;
    }

    public void createReturnRequestItems(RMAOrderRequest request, AmastyRmaRequest amastyRmaRequest,
                                         SalesOrderItem childItem) {

        AmastyRmaRequestItem amastyRmaRequestItem = new AmastyRmaRequestItem();
        amastyRmaRequestItem.setRequestId(amastyRmaRequest.getRequestId());
        amastyRmaRequestItem.setOrderItemId(childItem.getItemId());
        amastyRmaRequestItem.setQty(new BigDecimal(request.getReturnQuantity()));
        amastyRmaRequestItem.setRequestQty(new BigDecimal(request.getReturnQuantity()));
        amastyRmaRequestItem.setReasonId(request.getReasonId());
        amastyRmaRequestItem.setConditionId(2);
        amastyRmaRequestItem.setResolutionId(2);
        amastyRmaRequestItem.setItemStatus(1);
        amastyRmaRequestItemRepository.saveAndFlush(amastyRmaRequestItem);

    }

    public MagentoAPIResponse returnAWBInitiationFromMagento(AmastyRmaRequest amastyRmaRequest) {

        MagentoAPIResponse resp = new MagentoAPIResponse();

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
        requestHeaders.set("Authorization", "Bearer " + magentoIntegrationToken);

        MagentoReturnAWBRequest magentoReturnAWBRequest = new MagentoReturnAWBRequest();
        magentoReturnAWBRequest.setRequestId(amastyRmaRequest.getRequestId());

        HttpEntity<MagentoReturnAWBRequest> requestBody = new HttpEntity<>(magentoReturnAWBRequest, requestHeaders);
        String url = magentoBaseUrl + "/rest/V1/create-return-awb";
        // String url = "https://dev.stylifashion.com" + "/rest/V1/create-return-awb";

        try {
            ResponseEntity<MagentoAPIResponse[]> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
                    MagentoAPIResponse[].class);

            if (response.getStatusCode() == HttpStatus.OK) {
                MagentoAPIResponse[] responseArray = response.getBody();
                if (responseArray != null && responseArray.length > 0) {
                    return responseArray[0];
                }
            }
        } catch (RestClientException e) {
            resp.setStatus(false);
            resp.setStatusCode(500);
            resp.setStatusMsg(e.getMessage());
            return resp;
        }

        resp.setStatus(false);
        resp.setStatusCode(500);
        resp.setStatusMsg("Unknown error occurred!");
        return resp;

    }

    public MagentoAPIResponse returnAWBCancellationFromMagento(AmastyRmaRequest amastyRmaRequest) {

        MagentoAPIResponse resp = new MagentoAPIResponse();

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
        requestHeaders.set("Authorization", "Bearer " + magentoIntegrationToken);

        MagentoReturnAWBRequest magentoReturnAWBRequest = new MagentoReturnAWBRequest();
        magentoReturnAWBRequest.setRequestId(amastyRmaRequest.getRequestId());

        HttpEntity<MagentoReturnAWBRequest> requestBody = new HttpEntity<>(magentoReturnAWBRequest, requestHeaders);
        String url = magentoBaseUrl + "/rest/V1/cancel-return-awb";
        // String url = "https://dev.stylifashion.com" + "/rest/V1/cancel-return-awb";

        try {
            ResponseEntity<MagentoAPIResponse[]> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
                    MagentoAPIResponse[].class);

            if (response.getStatusCode() == HttpStatus.OK) {
                MagentoAPIResponse[] responseArray = response.getBody();
                if (responseArray != null && responseArray.length > 0) {
                    return responseArray[0];
                }
            }
        } catch (RestClientException e) {
            resp.setStatus(false);
            resp.setStatusCode(500);
            resp.setStatusMsg(e.getMessage());
            return resp;
        }

        resp.setStatus(false);
        resp.setStatusCode(500);
        resp.setStatusMsg("Unknown error occurred!");
        return resp;

    }

    public AmastyRmaRequest createReturnRequestV2(RMAOrderV2Request request, SalesOrder order, String xClientVersion) {
        return createReturnRequestV2(request, order, null, xClientVersion);
    }

    public AmastyRmaRequest createReturnRequestV2(RMAOrderV2Request request, SalesOrder order, SplitSalesOrder splitOrder, String xClientVersion) {

        AmastyRmaRequest amastyRmaRequest = new AmastyRmaRequest();
        amastyRmaRequest.setOrderId(order.getEntityId());
        amastyRmaRequest.setStoreId(request.getStoreId());
        
        // Set split order ID if provided
        if (request.getSplitOrderId() != null) {
            amastyRmaRequest.setSplitOrderId(request.getSplitOrderId());
        }

        if(request.getReturnIncPayfortId()!=null) {
            amastyRmaRequest.setReturnIncPayfortId(request.getReturnIncPayfortId());
        }
        amastyRmaRequest.setCreatedAt(new Timestamp(new Date().getTime()));
        amastyRmaRequest.setModifiedAt(new Timestamp(new Date().getTime()));
        amastyRmaRequest.setStatus(4);
        amastyRmaRequest.setCustomerId(request.getCustomerId());
        String customerName = order.getCustomerFirstname() + " " + order.getCustomerLastname();
        amastyRmaRequest.setCustomerName(customerName);
        amastyRmaRequest.setUrlHash("");
        amastyRmaRequest.setManagerId(0);
        amastyRmaRequest.setCustomFields("");
        amastyRmaRequest.setRating(0);
//        Magento used to store admin ids, currently we are storing 1 and 0
        String isCreatedByAdmin = request.getOmsRequest() ? "1": "0";
        amastyRmaRequest.setIsCreatedByAdmin(isCreatedByAdmin);
        amastyRmaRequest.setRmaIncId("");
        if(null != request.getIsDropOffRequest() && request.getIsDropOffRequest()) {
            amastyRmaRequest.setReturnType(1);

        }else {
            amastyRmaRequest.setReturnType(0);

        }
        amastyRmaRequest.setRmaPaymentMethod(request.getRmaPaymentMethod());
        double refundAmountToBeDeducted = 0.0;
        Integer rmaCountVal= 0;

        // Check if this is a split order flow
        boolean isSplitOrderFlow = request.getSplitOrderId() != null;
        
        if(Constants.orderCredentials.getBlockShukranSecondRefund()) {
            BigDecimal totalShukranBurnedValue = BigDecimal.ZERO;
            
            if (isSplitOrderFlow && splitOrder != null) {
                // For split orders, use the split order's Shukran data
                if (splitOrder.getSplitSubSalesOrder() != null && splitOrder.getSplitSubSalesOrder().getTotalShukranBurnedValueInCurrency() != null) {
                    totalShukranBurnedValue = splitOrder.getSplitSubSalesOrder().getTotalShukranBurnedValueInCurrency();
                }
            } else {
                // For normal orders, use the main order's Shukran data
                if (order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency() != null) {
                    totalShukranBurnedValue = order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency();
                }
            }
            
            if (totalShukranBurnedValue.compareTo(BigDecimal.ZERO) > 0) {
                refundAmountToBeDeducted = 0.0;
            }
        } else if (amastyRmaRequest.getOrderId() != null && amastyRmaRequest.getStoreId() != null && amastyRmaRequest.getReturnFee() != null && amastyRmaRequest.getReturnFee() <= 0 && Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs() != null && Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs() > 0) {
            Integer rmaClubbingHours = Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs();
            
            // Check if this is a split order flow - if so, check split order count instead of order count
            if (isSplitOrderFlow && amastyRmaRequest.getSplitOrderId() != null) {
                rmaCountVal = amastyRmaRequestRepository.getSplitOrderRMACount(amastyRmaRequest.getSplitOrderId(), rmaClubbingHours);
                if (rmaCountVal != null && rmaCountVal == 0) {
                    String requestId = amastyRmaRequestRepository.getLastSplitOrderRequestId(amastyRmaRequest.getSplitOrderId(), rmaClubbingHours);
                    if (StringUtils.isNotEmpty(requestId) && StringUtils.isNotBlank(requestId)) {
                        int trackingCount = amastyRmaTrackingRepository.getTrackingCountByRequestId(Integer.parseInt(requestId));
                        if (trackingCount > 0) {
                            rmaCountVal = 1;
                        }
                    }
                }
            } else {
                // For normal orders, use the existing order-level logic
                rmaCountVal = amastyRmaRequestRepository.getRMACount(amastyRmaRequest.getOrderId(), rmaClubbingHours);
                if (rmaCountVal != null && rmaCountVal == 0) {
                    String requestId = amastyRmaRequestRepository.getLastRequestId(request.getOrderId(), rmaClubbingHours);
                    if (StringUtils.isNotEmpty(requestId) && StringUtils.isNotBlank(requestId)) {
                        int trackingCount = amastyRmaTrackingRepository.getTrackingCountByRequestId(Integer.parseInt(requestId));
                        if (trackingCount > 0) {
                            rmaCountVal = 1;
                        }
                    }
                }
            }

            if (rmaCountVal != null && rmaCountVal >= 1) {
                boolean isAppVersionSufficient = false;
                if (StringUtils.isNotBlank(xClientVersion) && StringUtils.isNotEmpty(xClientVersion) && StringUtils.isNotBlank(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion()) && StringUtils.isNotEmpty(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion())) {
                    Long mobileAppVersion = Constants.decodeAppVersion(xClientVersion);
                    Long secondReturnThresholdVersion = Constants.decodeAppVersion(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion());
                    if (secondReturnThresholdVersion != null && mobileAppVersion != null && secondReturnThresholdVersion <= mobileAppVersion) {
                        isAppVersionSufficient = true;
                    }
                }
                Double refundValue = configService.getWebsiteRefundByStoreId(amastyRmaRequest.getStoreId());

                if (isAppVersionSufficient && refundValue != null && refundValue > 0) {

                    refundAmountToBeDeducted = refundValue;

                }
            }
        }

        amastyRmaRequest.setReturnFee(refundAmountToBeDeducted);


        amastyRmaRequestRepository.saveAndFlush(amastyRmaRequest);

        Integer requestId = amastyRmaRequest.getRequestId();
        // Sending storeId to prevent appending storeId at the beginning
        String rmaIncId = orderHelper.generateIncrementId(requestId, 1);
        String rstring = "R";
        if(StringUtils.isNotBlank(env) && !env.equalsIgnoreCase("live")) {

            rstring = "R1";
        }
        rmaIncId = rstring + rmaIncId;
        amastyRmaRequest.setRmaIncId(rmaIncId);
        amastyRmaRequestRepository.saveAndFlush(amastyRmaRequest);

        return amastyRmaRequest;

    }
}