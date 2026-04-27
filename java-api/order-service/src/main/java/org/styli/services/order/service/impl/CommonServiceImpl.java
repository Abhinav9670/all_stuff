package org.styli.services.order.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.db.product.config.ConfigProperties;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.CoreConfigDataServicePojo;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.helper.OrderHelperV2;
import org.styli.services.order.model.Store;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.rma.MagentoReturnDropOffAPIResponse;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SubSalesOrder;
import org.styli.services.order.pojo.AttributeValue;
import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.RedisObject;
import org.styli.services.order.pojo.ShukranLedgerData;
import org.styli.services.order.pojo.cancel.MagentoReturnDropOffRequest;
import org.styli.services.order.pojo.order.RefundShukranBurnedBody;
import org.styli.services.order.pojo.order.ShukranClawbackRequestBody;
import org.styli.services.order.pojo.request.LockAndUnlockShukranRequest;
import org.styli.services.order.pojo.request.LockUnlockHttpRequestBody;
import org.styli.services.order.pojo.response.*;
import org.styli.services.order.pojo.response.Order.OrderResponse;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.StoreRepository;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.service.CommonService;
import org.styli.services.order.service.CoreConfigDataService;
import org.styli.services.order.service.RedisService;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;

@Component
public class CommonServiceImpl implements CommonService {

    private static final Log LOGGER = LogFactory.getLog(CommonServiceImpl.class);

    @Autowired
    StoreRepository storeRepository;

    @Autowired
    @Lazy
    EASServiceImpl easService;

    @Autowired
    CoreConfigDataService coreConfigDataService;

    @Autowired
    StaticComponents staticComponents;

    @Autowired
    OrderHelperV2 orderHelperV2;

    @Autowired
    RedisService redisService;

    @Autowired
    ConfigProperties configProperties;
    
    @Autowired
    OrderHelper  orderHelper;

    @Autowired
    CustomerEntityRepository customerEntityRepository;

    @Autowired
    SalesOrderRepository salesOrderRepository;

    @Autowired
    SalesOrderGridRepository salesOrderGridRepository;

    @Value("${auth.internal.header.bearer.token}")
    private String internalAuthBearerToken;

    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;

    @Override
    public List<Store> getAllStores() {
        return storeRepository.findAll();
    }

    @Override
    public Store findStoreByStoreId(final Integer storeId) {
        return storeRepository.findByStoreId(storeId);
    }

    @Override
    public List<Store> findByWebsiteId(final Integer websiteId) {
        return storeRepository.findByWebSiteId(websiteId);
    }

    @Override
    public List<Stores> getStoresArray() {
        return Constants.getStoresList();
    }

    @Override
    public CoreConfigDataServicePojo getCoreConfigDataService(final Integer storeId, final Integer websiteId,
            final String storeCode) throws NotFoundException {
        final CoreConfigDataServicePojo pojo = new CoreConfigDataServicePojo();
        pojo.setStoreDetailsResponseDTO(coreConfigDataService.getStoreDetails(storeId));
        pojo.setStoreCurrency(coreConfigDataService.getStoreCurrency(storeId));
        pojo.setCurrencyConversionRate(coreConfigDataService.getCurrencyConversionRate(websiteId));

        pojo.setStoreLanguage(coreConfigDataService.getStoreLanguage(storeId));

        pojo.setStoreShipmentCharges(coreConfigDataService.getStoreShipmentCharges(websiteId));
        pojo.setStoreShipmentChargesThreshold(coreConfigDataService.getStoreShipmentChargesThreshold(websiteId));

        pojo.setCodCharges(coreConfigDataService.getStoreShipmentCharges(websiteId));
        pojo.setTaxPercentage(coreConfigDataService.getTaxPercentage(websiteId));
        pojo.setRMAThresholdInHours(coreConfigDataService.getRMAThresholdInHours(websiteId, storeCode));
        pojo.setCatalogcurrencyConversionRate(coreConfigDataService.getCatalogCurrencyConversionRate(websiteId));

        return pojo;
    }



    @Override
    public Map<String, String> getAttributeLabels() {
        return staticComponents.getLabelMap();
    }

    @Override
    public CustomerEntity findByEmail(String email) {
        //return customerEntityRepository.findByEmail(email);
    	CustomerEntity customerEntity = new CustomerEntity();
		customerEntity = orderHelper.getCustomerDetails(null, email);
		return customerEntity;
    }

    @Override
    public Map<Integer, String> getAttrMap() {
        return staticComponents.getAttrMap();
    }



    @Override
    public List<SalesOrder> findByCustomerEmailSalesOrder(String email) {
        return salesOrderRepository.findByCustomerEmail(email);
    }

    @Override
    public List<SalesOrderGrid> findByCustomerEmailSalesOrderGrid(String email) {
        return salesOrderGridRepository.findByCustomerEmail(email);
    }



    @Override
	public CustomerEntity findByEntityId(Integer entityId) {
		CustomerEntity customerEntity = new CustomerEntity();
		customerEntity = orderHelper.getCustomerDetails(entityId, null);
		return customerEntity;
	}

	@Override
	public Map<String, AttributeValue> getAttributeStatus() {

		return staticComponents.getAttrStatusMap();
	}

    @Override
    public LockAndUnlockShukranResponse lockAndUnlockShukran(LockAndUnlockShukranRequest request){
        LockAndUnlockShukranResponse lockAndUnlockShukranResponse= new LockAndUnlockShukranResponse();
        String shukranResponse= lockUnlockShukranData(request.getProfileId(),request.getPoints(), request.getCartId(), request.getIsLock(), null, null, "", "");

        if(shukranResponse.equals("api passed")){
            lockAndUnlockShukranResponse.setStatusCode("200");
            lockAndUnlockShukranResponse.setStatusMsg("Success");
            lockAndUnlockShukranResponse.setStatus(true);
        }else{
            lockAndUnlockShukranResponse.setStatusCode("400");
            lockAndUnlockShukranResponse.setStatusMsg("ERROR");
            lockAndUnlockShukranResponse.setStatus(false);
        }
        return lockAndUnlockShukranResponse;
    }

    /**
     * Unlocks Shukran points for customers with canceled or failed orders.
     *
     * This method retrieves all canceled and failed sales orders from the repository,
     * filters for orders where Shukran points were not locked, and attempts to unlock
     * Shukran points associated with each order. It then updates the response status
     * based on the success or failure of the operation.
     *
     * @return a {@link LockAndUnlockShukranResponse} object containing the operation's status,
     *         including HTTP status code, success flag, and message describing the result.
     *
     * @implNote This method logs key steps, including the size of the order list to process.
     *           In case of an exception, an error message is logged, and a failure response
     *           is returned.
     *
     * @see SalesOrderRepository#findCanceledAndFailedOrders
     * @see #lockUnlockShukranData
     */
    @Override
    public LockAndUnlockShukranResponse unlockShukranPoints() {
        LockAndUnlockShukranResponse lockAndUnlockShukranResponse= new LockAndUnlockShukranResponse();
        try {
           LOGGER.info("In unlock shukran points");
           Integer minutesAgo = 1440;
            if(null!=Constants.orderCredentials.getShukranUnlockOrderTimeLimitInMinutes() &&
                    Constants.orderCredentials.getShukranUnlockOrderTimeLimitInMinutes()> 0){
                minutesAgo= Constants.orderCredentials.getShukranUnlockOrderTimeLimitInMinutes();
            }
           List<String> statusList = Arrays.asList(OrderConstants.CANCELED_ORDER_STATUS, OrderConstants.FAILED_ORDER_STATUS,
                   OrderConstants.CANCELED_ORDER_STATE,OrderConstants.ORDER_STATUS_RTO);
           List<SalesOrder> salesOrderList = salesOrderRepository.findCanceledAndFailedOrders(statusList,minutesAgo);
           LOGGER.info("In unlock shukran points salesOrderList to unlock : size : "+(null!=salesOrderList?salesOrderList.size():0));
           salesOrderList.stream()
                   .map(SalesOrder::getSubSalesOrder)
                   .filter(subSalesOrder -> subSalesOrder != null &&
                           (subSalesOrder.getShukranLocked() == null || subSalesOrder.getShukranLocked() == 0) && StringUtils.isNotBlank(subSalesOrder.getShukranCardNumber()) && StringUtils.isNotEmpty(subSalesOrder.getShukranCardNumber()) && StringUtils.isNotBlank(subSalesOrder.getCustomerProfileId()) && StringUtils.isNotEmpty(subSalesOrder.getCustomerProfileId()) && subSalesOrder.getShukranLinked())
                   .forEach(subSalesOrder ->
                           lockUnlockShukranData(subSalesOrder.getCustomerProfileId(),
                                   String.valueOf(subSalesOrder.getTotalShukranCoinsBurned()),
                                   subSalesOrder.getQuoteId(),
                                   false, null, null, "", "")
                   );
           lockAndUnlockShukranResponse.setStatusCode("200");
           lockAndUnlockShukranResponse.setStatusMsg("Success");
           lockAndUnlockShukranResponse.setStatus(true);
           return lockAndUnlockShukranResponse;
       } catch (Exception e) {
           LOGGER.info("Exception In unlock shukran points",e);
            lockAndUnlockShukranResponse.setStatusCode("400");
            lockAndUnlockShukranResponse.setStatusMsg("An error occurred: " + e.getMessage());
            lockAndUnlockShukranResponse.setStatus(false);
           return lockAndUnlockShukranResponse;
       }
    }

    public String lockUnlockShukranData(String profileId, String points, String cartId, boolean isLock, SalesOrder order, Stores store, String reason, String apiName) {
        String returnResponse = "api failed";

        String redisToken= getRedisToken();
        if(StringUtils.isNotEmpty(Constants.orderCredentials.getShukranBaseUrl()) && StringUtils.isNotBlank(Constants.orderCredentials.getShukranBaseUrl()) && StringUtils.isNotEmpty(redisToken) && StringUtils.isNotBlank(redisToken) && StringUtils.isNotBlank(Constants.orderCredentials.getShukranLockUnlockUrl()) && StringUtils.isNotEmpty(Constants.orderCredentials.getShukranLockUnlockUrl())) {
            HttpHeaders requestHeaders= shukranHeaders(redisToken, false);

            LockUnlockHttpRequestBody requestBody = new LockUnlockHttpRequestBody();
            requestBody.setProfileId(profileId);
            requestBody.setPoints(points);
            requestBody.setCartId(cartId);
            requestBody.setAction(isLock ? "Lock" : "Unlock");

            HttpEntity<LockUnlockHttpRequestBody> requestBodyData = new HttpEntity<>(requestBody, requestHeaders);
            String url = Constants.orderCredentials.getShukranBaseUrl() + Constants.orderCredentials.getShukranLockUnlockUrl();

            try {
                ObjectMapper mapper = new ObjectMapper();
                LOGGER.info("shukran lock unlock request body " + mapper.writeValueAsString(requestBody) + "url " + url + (isLock ? " apiName " + apiName : ""));
                ResponseEntity<LockUnlockHttpResponseBody> response = restTemplate.exchange(url, HttpMethod.POST, requestBodyData, LockUnlockHttpResponseBody.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    returnResponse = "api passed";
                    try {
                        updateShukranLedger(points, isLock, order, store, reason);
                    } catch (Exception e) {
                        LOGGER.info("error in update shukran ledger"+ e.getMessage());
                    }
                }
            } catch (JsonProcessingException ex) {
                LOGGER.info("response body exception " + ex.getMessage());
            } catch (RestClientException e) {
                String errorMessage = e.getMessage();

                if (StringUtils.isNotBlank(errorMessage)) {
                    try {
                        errorMessage = errorMessage.replace("<EOL>", "").trim();
                        int jsonStartIndex = errorMessage.indexOf("{");
                        if (jsonStartIndex != -1) {
                            String jsonPart = errorMessage.substring(jsonStartIndex).trim();

                            if (jsonPart.endsWith("\"")) {
                                jsonPart = jsonPart.substring(0, jsonPart.length() - 1).trim();
                            }

                            if (jsonPart.startsWith("{") && jsonPart.endsWith("}")) {
                                JSONObject json = new JSONObject(jsonPart);
                                String message = json.optString("Message");

                                if ("Provided LMS Cart Id is already present for this profile. Please use different LMS Cart Id".equalsIgnoreCase(message)) {
                                    LOGGER.info("Repetition During Shukran Locking API: " + message);
                                }else if("No certificate is available for UnLock".equalsIgnoreCase(message)) {
                                    int orderId= order != null && order.getEntityId() != null ? order.getEntityId() : 0;
                                    LOGGER.info("Certification Error In Shukran API "+ message +" profile Id "+ profileId +" Cart Id " + cartId + " locking type "+ isLock + " order id "+ orderId);
                                } else {
                                    LOGGER.info("Exception During Shukran API: " + message);
                                }
                            } else {
                                LOGGER.info("Extracted content is not valid JSON: " + jsonPart);
                            }
                        } else {
                            LOGGER.info("No JSON content found in the error message: " + errorMessage);
                        }
                    } catch (Exception jsonParseException) {
                        LOGGER.info("Error parsing JSON from exception message: " + errorMessage, jsonParseException);
                    }
                } else {
                    LOGGER.info("Exception During Shukran API: " + errorMessage);
                }
            }
        }

        return returnResponse;
    }

    public String lockUnlockShukranDataForSplit(String profileId, String points, String cartId, boolean isLock, SplitSalesOrder order, Stores store, String reason, String apiName) {
        String returnResponse = "api failed";

        String redisToken= getRedisToken();
        if(StringUtils.isNotEmpty(Constants.orderCredentials.getShukranBaseUrl()) && StringUtils.isNotBlank(Constants.orderCredentials.getShukranBaseUrl()) && StringUtils.isNotEmpty(redisToken) && StringUtils.isNotBlank(redisToken) && StringUtils.isNotBlank(Constants.orderCredentials.getShukranLockUnlockUrl()) && StringUtils.isNotEmpty(Constants.orderCredentials.getShukranLockUnlockUrl())) {
            HttpHeaders requestHeaders= shukranHeaders(redisToken, false);

            LockUnlockHttpRequestBody requestBody = new LockUnlockHttpRequestBody();
            requestBody.setProfileId(profileId);
            requestBody.setPoints(points);
            requestBody.setCartId(cartId);
            requestBody.setAction(isLock ? "Lock" : "Unlock");

            HttpEntity<LockUnlockHttpRequestBody> requestBodyData = new HttpEntity<>(requestBody, requestHeaders);
            String url = Constants.orderCredentials.getShukranBaseUrl() + Constants.orderCredentials.getShukranLockUnlockUrl();

            try {
                ObjectMapper mapper = new ObjectMapper();
                LOGGER.info("shukran lock unlock request body " + mapper.writeValueAsString(requestBody) + "url " + url + (isLock ? " apiName " + apiName : ""));
                ResponseEntity<LockUnlockHttpResponseBody> response = restTemplate.exchange(url, HttpMethod.POST, requestBodyData, LockUnlockHttpResponseBody.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    returnResponse = "api passed";
                    try {
                        updateShukranLedgerForSplit(points, isLock, order, store, reason);
                    } catch (Exception e) {
                        LOGGER.info("error in update shukran ledger"+ e.getMessage());
                    }
                }
            } catch (JsonProcessingException ex) {
                LOGGER.info("response body exception " + ex.getMessage());
            } catch (RestClientException e) {
                String errorMessage = e.getMessage();

                if (StringUtils.isNotBlank(errorMessage)) {
                    try {
                        errorMessage = errorMessage.replace("<EOL>", "").trim();
                        int jsonStartIndex = errorMessage.indexOf("{");
                        if (jsonStartIndex != -1) {
                            String jsonPart = errorMessage.substring(jsonStartIndex).trim();

                            if (jsonPart.endsWith("\"")) {
                                jsonPart = jsonPart.substring(0, jsonPart.length() - 1).trim();
                            }

                            if (jsonPart.startsWith("{") && jsonPart.endsWith("}")) {
                                JSONObject json = new JSONObject(jsonPart);
                                String message = json.optString("Message");

                                if ("Provided LMS Cart Id is already present for this profile. Please use different LMS Cart Id".equalsIgnoreCase(message)) {
                                    LOGGER.info("Repetition During Shukran Locking API: " + message);
                                }else if("No certificate is available for UnLock".equalsIgnoreCase(message)) {
                                    int orderId= order != null && order.getEntityId() != null ? order.getEntityId() : 0;
                                    LOGGER.info("Certification Error In Shukran API "+ message +" profile Id "+ profileId +" Cart Id " + cartId + " locking type "+ isLock + " order id "+ orderId);
                                } else {
                                    LOGGER.info("Exception During Shukran API: " + message);
                                }
                            } else {
                                LOGGER.info("Extracted content is not valid JSON: " + jsonPart);
                            }
                        } else {
                            LOGGER.info("No JSON content found in the error message: " + errorMessage);
                        }
                    } catch (Exception jsonParseException) {
                        LOGGER.info("Error parsing JSON from exception message: " + errorMessage, jsonParseException);
                    }
                } else {
                    LOGGER.info("Exception During Shukran API: " + errorMessage);
                }
            }
        }

        return returnResponse;
    }

    public void updateShukranLedger (String points, boolean isLock, SalesOrder order, Stores store, String reason){
        if(order != null && store != null) {
            try {
                BigDecimal pointsValue = new BigDecimal(points);
                BigDecimal pointsValueInCurrency = pointsValue.multiply(store.getShukranPointConversion()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal pointsValueInBaseCurrency = pointsValue.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
                ShukranLedgerData shukranLedgerData = orderHelperV2.createShukranLedgerData(order, pointsValue, pointsValueInCurrency, pointsValueInBaseCurrency, store, !isLock, reason);
                easService.updateShukranLedger(shukranLedgerData);
            } catch (Exception e) {
                LOGGER.info("Error In Update Shukran Ledger"+ e.getMessage());
            }
        }
    }

    public void updateShukranLedgerForSplit(String points, boolean isLock, SplitSalesOrder order, Stores store, String reason){
        if(order != null && store != null) {
            try {
                BigDecimal pointsValue = new BigDecimal(points);
                BigDecimal pointsValueInCurrency = pointsValue.multiply(store.getShukranPointConversion()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal pointsValueInBaseCurrency = pointsValue.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
                ShukranLedgerData shukranLedgerData = orderHelperV2.createShukranLedgerDataForSplit(order, pointsValue, pointsValueInCurrency, pointsValueInBaseCurrency, store, !isLock, reason);
                easService.updateShukranLedger(shukranLedgerData);
            } catch (Exception e) {
                LOGGER.info("Error In Update Shukran Ledger"+ e.getMessage());
            }
        }
    }

    public String clawbackShukranEarned(ShukranClawbackRequestBody shukranClawbackRequestBody) {
        String clawbackShukranEarnedResponse = "clawback api response failed";

        String redisToken= getRedisToken();
        if(ObjectUtils.isNotEmpty(shukranClawbackRequestBody) && StringUtils.isNotEmpty(redisToken) && StringUtils.isNotBlank(redisToken) && StringUtils.isNotEmpty(Constants.getShukranTransactionRTPRURL()) && StringUtils.isNotBlank(Constants.getShukranTransactionRTPRURL())) {
            HttpHeaders requestHeaders= shukranHeaders(redisToken, true);
            try {
                ObjectMapper mapper = new ObjectMapper();
                LOGGER.info("clawback api request headers "+ mapper.writeValueAsString(requestHeaders));
                 HttpEntity<ShukranClawbackRequestBody> requestBodyData = new HttpEntity<>(shukranClawbackRequestBody, requestHeaders);

                LOGGER.info("clawback api request body " + mapper.writeValueAsString(shukranClawbackRequestBody)+"url    "+ Constants.getShukranTransactionRTPRURL());
                ResponseEntity<LockUnlockHttpResponseBody> response = restTemplate.exchange(Constants.getShukranTransactionRTPRURL(), HttpMethod.POST, requestBodyData, LockUnlockHttpResponseBody.class);

                LOGGER.info("clawback api response "+ mapper.writeValueAsString(response));
                clawbackShukranEarnedResponse = "clawback api response passed";

            } catch (JsonProcessingException ex) {
                LOGGER.info("clawback response body exception " + ex.getMessage());
            } catch (RestClientException e) {
                LOGGER.info("ClawBack Exception During Shukran API " + e.getMessage());

            } catch (Exception e) {
                LOGGER.info("Error In ClawBack Shukran API " + e.getMessage());
            }
        }
        return clawbackShukranEarnedResponse;
    }

    public String refundShukranBurned(RefundShukranBurnedBody refundShukranBurnedBody) {
        String refundShukranBurnedResponse = "refund burned point api failed";

        String redisToken= getRedisToken();
        if(StringUtils.isNotEmpty(Constants.orderCredentials.getShukranBaseUrl()) && StringUtils.isNotBlank(Constants.orderCredentials.getShukranBaseUrl()) && ObjectUtils.isNotEmpty(refundShukranBurnedBody) && StringUtils.isNotEmpty(redisToken) && StringUtils.isNotBlank(redisToken) && StringUtils.isNotEmpty(Constants.orderCredentials.getShukranReturnUrl()) && StringUtils.isNotBlank(Constants.orderCredentials.getShukranReturnUrl())) {
            String url= Constants.orderCredentials.getShukranBaseUrl() + Constants.orderCredentials.getShukranReturnUrl();

            HttpHeaders requestHeaders= shukranHeaders(redisToken, false);
            HttpEntity<RefundShukranBurnedBody> requestBodyData = new HttpEntity<>(refundShukranBurnedBody, requestHeaders);

            try {
                ObjectMapper mapper = new ObjectMapper();
                LOGGER.info("refund burned point request body " + mapper.writeValueAsString(refundShukranBurnedBody));
                if(Constants.orderCredentials.getEnableShukranRefund()) {
                    ResponseEntity<LockUnlockHttpResponseBody> response = restTemplate.exchange(url, HttpMethod.POST, requestBodyData, LockUnlockHttpResponseBody.class);

                    LOGGER.info("refund burned point api response "+ mapper.writeValueAsString(response));
                    refundShukranBurnedResponse = "refund burned point api passed";
                }
            } catch (JsonProcessingException ex) {
                LOGGER.info("refund burned point response body exception " + ex.getMessage());
            } catch (RestClientException e) {
                LOGGER.info("Exception During refund burned point Shukran API " + e.getMessage());
            } catch (Exception e){
                LOGGER.info("Error During refund burned point Shukran API " + e.getMessage());
            }
        }
        return refundShukranBurnedResponse;
    }

    public BigDecimal customerShukranBalance(String profileId) {
        BigDecimal customerShukranBalanceResponse = null;

        String redisToken= getRedisToken();
        if(StringUtils.isNotEmpty(Constants.orderCredentials.getShukranBaseUrl()) && StringUtils.isNotBlank(Constants.orderCredentials.getShukranBaseUrl()) && StringUtils.isNotEmpty(profileId) && StringUtils.isNotBlank(profileId) && StringUtils.isNotEmpty(redisToken) && StringUtils.isNotBlank(redisToken) && StringUtils.isNotEmpty(Constants.orderCredentials.getGetCustomerShukranDataUrl()) && StringUtils.isNotBlank(Constants.orderCredentials.getGetCustomerShukranDataUrl()) && StringUtils.isNotEmpty(Constants.orderCredentials.getGetCustomerPointsDescriptionToMatch()) && StringUtils.isNotBlank(Constants.orderCredentials.getGetCustomerPointsDescriptionToMatch())) {
            String url= Constants.orderCredentials.getShukranBaseUrl() + Constants.orderCredentials.getGetCustomerShukranDataUrl().replace("{{profileId}}", profileId);
            LOGGER.info("customer shukran balance url "+ url);
            HttpHeaders requestHeaders= shukranHeaders(redisToken, false);
            HttpEntity<String> entity = new HttpEntity<>(requestHeaders);

            try {
                ObjectMapper mapper = new ObjectMapper();
                LOGGER.info("customer shukran balance profile id " + profileId);

                    ResponseEntity<CustomerShukranPoints> response = restTemplate.exchange(url, HttpMethod.GET, entity, CustomerShukranPoints.class);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        CustomerShukranPoints customerShukranPoints = response.getBody();

                        if(customerShukranPoints != null && !customerShukranPoints.getPointsBalance().isEmpty()){
                            LOGGER.info("customer shukran points" +mapper.writeValueAsString(customerShukranPoints.getPointsBalance()));
                            customerShukranBalanceResponse = customerShukranPoints.getPointsBalance().stream()
                                    .filter(p -> StringUtils.isNotBlank(p.getPointTypeShortDescription())
                                            && StringUtils.isNotEmpty(p.getPointTypeShortDescription()) && p.getPointTypeShortDescription().equalsIgnoreCase(Constants.orderCredentials.getGetCustomerPointsDescriptionToMatch())
                                            && p.getPointAmount() != null)
                                    .map(CustomerShukranPointsBalance::getPointAmount)  // Extract the pointAmount
                                    .findFirst()                        // Get the first match
                                    .orElse(null);
                            return customerShukranBalanceResponse;
                        }


                }
            } catch (JsonProcessingException ex) {
                LOGGER.info("customer Shukran Balance response body exception " + ex.getMessage());
            } catch (RestClientException e) {
                LOGGER.info("Exception During customer Shukran Balance API " + e.getMessage());
            } catch (Exception e){
                LOGGER.info("Error During customer Shukran Balance API " + e.getMessage());
            }
        }
        return customerShukranBalanceResponse;
    }

    public String getRedisToken(){
        String token=null;
        if(Constants.orderCredentials != null && StringUtils.isNotEmpty(Constants.getGlobalRedisKey()) && StringUtils.isNotBlank(Constants.getGlobalRedisKey())) {
            LOGGER.info("Shukran Data: " + Constants.getGlobalRedisKey() + "url0 " + Constants.orderCredentials.getShukranBaseUrl());
            RedisObject redisObject = redisService.getData(Constants.getGlobalRedisKey(), RedisObject.class);
            ObjectMapper mapper = new ObjectMapper();
            try {
                LOGGER.info("redis object data+ " + mapper.writeValueAsString(redisObject));
            } catch (JsonProcessingException e) {
                LOGGER.info(" redis object error " + e.getMessage());
            }
            if (ObjectUtils.isNotEmpty(redisObject) && StringUtils.isNotEmpty(redisObject.getAccessToken()) && StringUtils.isNotBlank(redisObject.getAccessToken())) {
                token = redisObject.getAccessToken();
            }
        }
        return token;
    }

    public HttpHeaders shukranHeaders(String redisToken, Boolean isClawBack){
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.add("Program-Code", Constants.getShukranProgramCode());
        requestHeaders.add("content-type", "application/json");
        requestHeaders.add("Authorization", "OAuth " + redisToken);
        requestHeaders.add("Source-Application", Constants.getShukranSourceApplication());
        requestHeaders.add("Accept-Language", "en-US");
        if(isClawBack && StringUtils.isNotEmpty(internalAuthBearerToken) && StringUtils.isNotBlank(internalAuthBearerToken)) {
            String authToken= internalAuthBearerToken;
            if (internalAuthBearerToken.contains(",")) {
                List<String> authTokenList = Arrays.asList(internalAuthBearerToken.split(","));
                authToken= authTokenList.get(0);
            }
            requestHeaders.add("authorization-token", authToken);
        }
        return requestHeaders;
    }

}
