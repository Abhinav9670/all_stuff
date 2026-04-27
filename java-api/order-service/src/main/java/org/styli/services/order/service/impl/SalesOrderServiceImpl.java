package org.styli.services.order.service.impl;

import com.google.common.collect.Lists;
import io.grpc.netty.shaded.io.netty.util.internal.StringUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.db.product.exception.ForbiddenException;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.exception.BadRequestException;
import org.styli.services.order.helper.ExternalQuoteHelper;
import org.styli.services.order.helper.KafkaBrazeHelper;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.OrderKeyDetails;
import org.styli.services.order.pojo.PendingPaymentToFailedResponse;
import org.styli.services.order.pojo.braze.BrazePendingPaymentEvent;
import org.styli.services.order.pojo.braze.BrazePendingPaymentPush;
import org.styli.services.order.pojo.kafka.BulkWalletUpdate;
import org.styli.services.order.pojo.oms.DispatchUpdateRequest;
import org.styli.services.order.pojo.order.OrderEmailRequest;
import org.styli.services.order.pojo.order.UpdateOrderResponseDTO;
import org.styli.services.order.pojo.projection.ReferralOrderProjection;
import org.styli.services.order.pojo.request.*;
import org.styli.services.order.pojo.request.Order.*;
import org.styli.services.order.pojo.response.*;
import org.styli.services.order.pojo.response.Coupon.ProductPromotions;
import org.styli.services.order.pojo.response.Coupon.ProductPromotionsDTO;
import org.styli.services.order.pojo.response.Customer;
import org.styli.services.order.pojo.response.Order.*;
import org.styli.services.order.pojo.response.V3.CustomerOrdersResponseV2DTO;
import org.styli.services.order.pojo.response.external.*;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.repository.SalesOrder.*;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.service.*;
import org.styli.services.order.service.impl.child.*;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;
import org.styli.services.order.utility.UtilityConstant;
import org.styli.services.order.utility.consulValues.FeatureBasedFlag;
import org.styli.services.order.utility.consulValues.PromoRedemptionValues;

import javax.validation.Valid;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author ajitkumar
 *
 */
/**
 * @author ajitkumar
 *
 */
@Component
public class SalesOrderServiceImpl implements SalesOrderService {

    private static final Log LOGGER = LogFactory.getLog(SalesOrderServiceImpl.class);
    
    public static final String PENDING_PAYMENT_ORDER_STATUS = "pending_payment";
    private static final String REFERRAL_SERVICE_OFF = "Referal Service off!";

    @Autowired
    MulinHelper mulinHelper;

    @Autowired
    StaticComponents staticComponents;

    @Autowired
    SalesOrderRepository salesOrderRepository;

    @Autowired
    SplitSalesOrderRepository splitSalesOrderRepository;

    @Autowired
    CommonServiceImpl commonService;
    
    @Autowired
    SubSalesOrderRepository subsalesOrderRepository;
    
    @Autowired
    SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

    @Autowired
    OrderEntityConverter orderEntityConverter;

    @Autowired
    CustomerEntityRepository customerEntityRepository;

    @Autowired
    ConfigService configService;

    @Autowired
    AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;


    @Autowired
    OrderHelper orderHelper;


    @Autowired
    SalesOrderGridRepository salesOrderGridRepository;
    
    @Autowired
    ProxyOrderRepository proxyOrderRepository;

    @Autowired
    KafkaBrazeHelper kafkaBrazeHelper;

    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;

    @Value("${promo.coupon.list.url}")
    private String couponlisturl;

    @Value("${quote.converter.flag}")
    private String quoteConverterFlag;

    @Value("${magento.integration.token}")
    private String magentoIntegrationToken;

    @Value("${magento.base.url}")
    private String magentoBaseUrl;
    
    @Value("${msite.domain.url}")
    private String msiteDomainUrl;


    @Value("${referral.base.url}")
    private String referralBaseUrl;

    @Autowired
    CustomerService customerService;

    @Autowired
    OmsorderentityConverter omsorderentityConverter;

    @Autowired
    AmastyStoreCreditRepository amastyStoreCreditRepository;

    @Autowired
    GetOrderCount getOrderCount;

    @Autowired
    GetOrderList getOrderList;

    @Autowired
    GetOrderById getOrderById;

	@Autowired
	public OrderShipmentHelper orderShipmentHelper;

    @Autowired
    public OrderSplitShipmentHelper orderSplitShipmentHelper;
	
	@Autowired
	CustomerEntityRepository customerEntity;
	
	@Autowired
	GetFailedOrderList getFailedOrderList;

	 @Value("${order.jwt.flag}")
	  String jwtFlag;
	 
	 @Autowired
		@Lazy
		KafkaServiceImpl kafkaService;
	 
	 @Autowired
		SalesOrderCancelServiceImpl salesOrderCancelServiceImpl;
	 
	 @Value("${auth.internal.jwt.token}")
		private String authInternalJwtToken;
	 
	 @Autowired
	 ExternalQuoteHelper externalQuoteHelper;
	 
	 @Autowired
	 @Lazy
	 EASServiceImpl eASServiceImpl;
	 
	 @Autowired
	 @Lazy
	 private PaymentService paymentService;
    @Autowired
    private SplitSalesOrderService splitSalesOrderService;


    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO fetchOrderById(Map<String, String> headerRequest, OrderViewRequest request, String xClientVersion) {

    	String decoded = null;

		 SalesOrder order = null;
			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
					.findAny().orElse(null);
		if (ObjectUtils.isEmpty(request.getOrderId()) && ObjectUtils.isNotEmpty(request.getCode())) {
			decoded = new String(Base64.getDecoder().decode(request.getCode()));
			request.setOrderId(Integer.parseInt(decoded));
		}
		// If customerId is null (guest user), fetch by orderId only
		if (request.getCustomerId() == null) {
			order = salesOrderRepository.findByEntityId(request.getOrderId());
		} else {
			order = salesOrderRepository.findByEntityIdAndCustomerId(request.getOrderId(), request.getCustomerId());
		}
        
        return getOrderById.get(request, staticComponents, orderEntityConverter, order, headerRequest, restTemplate, mulinHelper,store, xClientVersion );
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseV2 fetchOrderByIdV2(Map<String, String> headerRequest, OrderViewRequest request, String xClientVersion) {

        OrderResponseDTO orderResponseDTO = fetchOrderById(headerRequest, request, xClientVersion);
        String decoded = null;

        SalesOrder order = null;
        List<Stores> stores = Constants.getStoresList();
        Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
                .findAny().orElse(null);
        if (ObjectUtils.isEmpty(request.getOrderId()) && ObjectUtils.isNotEmpty(request.getCode())) {
            decoded = new String(Base64.getDecoder().decode(request.getCode()));
            request.setOrderId(Integer.parseInt(decoded));
        }
		// If customerId is null (guest user), fetch by orderId only
		if (request.getCustomerId() == null) {
			order = salesOrderRepository.findByEntityId(request.getOrderId());
		} else {
			order = salesOrderRepository.findByEntityIdAndCustomerId(request.getOrderId(), request.getCustomerId());
		}

        return getOrderById.getV2(orderResponseDTO,request, staticComponents, orderEntityConverter, order, headerRequest, restTemplate, mulinHelper,store, xClientVersion );
    }


    @Override
    @Transactional(readOnly = true)
    public SalesOrder getOrderByEntityId(Integer orderId, Integer storeId) {
        return salesOrderRepository.findByEntityId(orderId);
    }

	@Transactional(readOnly = true)
    public SplitSalesOrder getOrderByEntityIdForSplitOrder(Integer orderId, Integer storeId) {
        return splitSalesOrderRepository.findByEntityId(orderId);
    }
    
    @Transactional(readOnly = true)
    public SalesOrder getOrderByEntityIdAndEmail(Integer orderId, String email) {
        return salesOrderRepository.findByEntityIdAndCustomerEmail(orderId,email);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesOrderGrid getOrderGridByEntityId(Integer orderId, Integer storeId) {
        return salesOrderGridRepository.findByEntityId(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerOrdersResponseDTO getCustomerOrders(OrderListRequest request) {


        return getOrderList.get(request, staticComponents, salesOrderRepository, orderEntityConverter,
                customerEntityRepository, configService, mulinHelper, restTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerOrdersResponseV2DTO getCustomerOrdersV3(OrderListRequest request) {


        return getOrderList.getV3(request, staticComponents, salesOrderRepository, orderEntityConverter,
                customerEntityRepository, configService, mulinHelper, restTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerOrdersResponseDTO getCustomerOrdersFailed(OrderListRequest request) {


        return getFailedOrderList.get(request, staticComponents, salesOrderRepository, orderEntityConverter,
                customerEntityRepository, configService, mulinHelper, restTemplate );
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerOrdersCountResponseDTO getCustomerOrdersCountAll(OrderListRequest request, Boolean forOrderPage) {

        // GetOrderCount getOrderCount = new GetOrderCount();
        return getOrderCount.get(request, staticComponents, salesOrderRepository, customerEntityRepository,
                configService, forOrderPage);

    }

    @Override
    @Transactional(readOnly = true)
    public CreditHistoryResponse getStoreCreditHistory(@Valid OrderStoreCreditRequest request) {

        GetStoreCreditHistory storeCreditHistory = new GetStoreCreditHistory();
        return storeCreditHistory.get(request,
                amastyStoreCreditHistoryRepository,
                staticComponents,
                omsorderentityConverter,
                amastyStoreCreditRepository);

    }

    @Override
    @Transactional(readOnly = true)
    public ProductPromotionsDTO getAllcouponList(GetPromosRequest request) {

        ProductPromotionsDTO resp = new ProductPromotionsDTO();

        Integer storeId = request.getStoreId();
        String customerId = null != request.getCustomerId() ? request.getCustomerId():"";
        String customerEmail = null != request.getCustomerEmail() ? request.getCustomerEmail():"";

        List<Stores> stores = Constants.getStoresList();
        Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(storeId)).findAny()
                .orElse(null);

        if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
            resp.setStatus(false);
            resp.setStatusCode("205");
            resp.setStatusMsg(Constants.STORE_NOT_FOUND_MSG);
            return resp;
        }


        List<ProductPromotions> totalPromoList = new ArrayList<ProductPromotions>();

        List<ProductPromotions> externalPromoList = getExternalPromoList(store.getStoreId(), customerId, customerEmail);

        totalPromoList.addAll(externalPromoList);

        resp.setResponse(totalPromoList);
        resp.setStatus(true);
        resp.setStatusCode("200");
        resp.setStatusMsg("Promotions fetched successfully.");

        return resp;
    }

    /**
     * @param storeId
     * @return
     */
    private List<ProductPromotions> getExternalPromoList(String storeId, String customerId, String customerEmail) {

        List<ProductPromotions> response = new ArrayList<>();

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
        requestHeaders.add("HTTP_X_API_KEY", Constants.COUPON_EXTERNAL_LIST_URL_HEADER);

		CustomCouponValidationV4Request requestParam = new CustomCouponValidationV4Request();
		boolean isCohortBasedCouponEnable = isCBCEnable();
		HttpMethod promoReqMethod = HttpMethod.GET;
		if(isCohortBasedCouponEnable){
			requestParam.setCustomerEmail(customerEmail);
			requestParam.setCustomerId(customerId);
			requestParam.setStoreId(storeId);
			promoReqMethod = HttpMethod.POST;
		}

        HttpEntity<CustomCouponValidationV4Request> requestBody = new HttpEntity<>(requestParam, requestHeaders);
        // String url = "{url}/v1/coupon?store_id={store}";
        
        String url = getCouponListNodeJSUrl();
        Map<String, Object> parameters = new HashMap<>();

        parameters.put("store", storeId);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

        LOGGER.info("Coupon GET URL:" + builder.buildAndExpand(parameters).toUri());

        try {

            ResponseEntity<CustomCouponListResponse> responseBody = restTemplate.exchange(
                    builder.buildAndExpand(parameters).toUri(), promoReqMethod, requestBody,
                    CustomCouponListResponse.class);

            LOGGER.info("response coupon:" + responseBody);

            if (responseBody.getStatusCode() == HttpStatus.OK) {
                CustomCouponListResponse body = responseBody.getBody();
                if (body != null && body.getCode() != null && body.getCode().equals(200)) {

                    List<CustomCouponData> dataList = body.getData();

                    if (CollectionUtils.isNotEmpty(dataList)) {

                        for (CustomCouponData data : dataList) {

                            ProductPromotions promo = new ProductPromotions();

                            BeanUtils.copyProperties(data, promo);
                            promo.setSource("external");
                            response.add(promo);
                        }
                    }

                }
            }

        } catch (RestClientException e) {

            LOGGER.error("Exception occurred:" + e.getMessage());

        }

        return response;
    }
    
    private String getCouponListNodeJSUrl() {
  	  
  	  PromoRedemptionValues values = Constants.getPromoRedemptionUrl();
  	  String url = values.getDefaultCouponListEndpoint();
  	  LOGGER.info("Inside getCouponListNodeJSUrl: url is " + url);
  	return url;
  }

    private boolean isCBCEnable() {
		FeatureBasedFlag values = Constants.getFeatureBasedFlag();
		boolean cohortBasedCouponEnable = values.isCohortBasedCoupon();
		LOGGER.info("Inside isCohortBasedCouponEnable: status" + cohortBasedCouponEnable);
		return cohortBasedCouponEnable;
	}

    /**
     *
     */
    @Override
    public String getOrderIncrementId(Integer storeId) {

        return orderHelper.getIncrementId(storeId);
    }



    @Override
    @Transactional
    public UpdateOrderResponseDTO updateOrder(UpdateOrderRequest request) {
        UpdateOrderResponseDTO resp = new UpdateOrderResponseDTO();

        if (request.getOrderId() == null || request.getStatus() == null || request.getFortId() == null) {
            resp.setStatus(false);
            resp.setStatusCode("202");
            resp.setStatusMsg("Error: Parameters missing!");

            ErrorType error = new ErrorType();
            error.setErrorCode("202");
            error.setErrorMessage("Parameters missing!");

            resp.setError(error);
            return resp;
        }

        SalesOrder order = salesOrderRepository.findByEntityIdAndCustomerId(request.getOrderId(),request.getCustomerId());
        // SalesOrderGrid orderGrid =
        // salesOrderGridRepository.findByEntityId(request.getOrderId());

        if (order == null) {
            resp.setStatus(false);
            resp.setStatusCode("201");
            resp.setStatusMsg("Error: Order was not found for given parameters!");

            ErrorType error = new ErrorType();
            error.setErrorCode("201");
            error.setErrorMessage("Order was not found for given parameters!");

            resp.setError(error);
            return resp;
        }

        boolean firstOrderByCustomer = orderHelper.checkFirstOrderByCustomer(order.getCustomerEmail());
        resp.setStatus(true);
        resp.setStatusCode("200");
        resp.setStatusMsg("Order Status Updated successfully!");
        resp.setResponse("Order Status Updated successfully!");
        resp.setFirstOrderByCustomer(firstOrderByCustomer);

        return resp;

    }

    @Override
    public UpdateOrderResponseDTO sendEmailForOrder(OrderEmailRequest request) {

        UpdateOrderResponseDTO resp = new UpdateOrderResponseDTO();

        if (request.getOrderId() == null) {
            resp.setStatus(false);
            resp.setStatusCode("201");
            resp.setStatusMsg("Incorrect/Empty parameters!");
        }

        Integer orderId = request.getOrderId();

        try {
            String url = magentoBaseUrl + "/rest/V1/orders/" + orderId + "/emails";
            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.setContentType(MediaType.APPLICATION_JSON);
            requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
            requestHeaders.set("Authorization", "Bearer " + magentoIntegrationToken);

            String requestStr = "";
            HttpEntity<String> requestBody = new HttpEntity<>(requestStr, requestHeaders);
            restTemplate.exchange(url, HttpMethod.POST, requestBody, String.class);

            resp.setStatus(true);
            resp.setStatusCode("200");
            resp.setStatusMsg("Order Email sent Successfully!");
            return resp;
        } catch (ResourceAccessException e) {
            LOGGER.error("Could not access Magento's order email API for orderId: " + orderId);
            resp.setStatus(false);
            resp.setStatusCode("203");
            resp.setStatusMsg("Order Email Error! Could not access Magento's order email API for orderId: " + orderId);

            ErrorType errorType = new ErrorType();
            errorType.setErrorCode("203");
            errorType.setErrorMessage(e.getMessage());
            resp.setError(errorType);

            return resp;
        } catch (Exception e) {
            LOGGER.error("Error while sending order email of orderId: " + orderId);

            resp.setStatus(false);
            resp.setStatusCode("202");
            resp.setStatusMsg("Order Email Error! Could not send email for orderId: " + orderId);

            ErrorType errorType = new ErrorType();
            errorType.setErrorCode("202");
            errorType.setErrorMessage(e.getMessage());
            resp.setError(errorType);

            return resp;
        }
    }

    @Override
    public CustomerStoreCreditResponse getCustomerStoreCredit(StoreCreditRequest storeRequest
    		,Map<String, String> requestHeader) {

        List<Stores> stores = Constants.getStoresList();
        CustomerStoreCreditResponse customerStoreCreditResponse = new CustomerStoreCreditResponse();

        Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(storeRequest.getStoreId()))
                .findAny().orElse(null);

        if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
            customerStoreCreditResponse.setStatus(false);
            customerStoreCreditResponse.setStatusCode("202");
            customerStoreCreditResponse.setStatusMsg("Store not found!");
            customerStoreCreditResponse.setCustomerId(storeRequest.getCustomerId());
            return customerStoreCreditResponse;
        }
        
        CustomerEntity customer = orderHelper.getCustomerDetails(storeRequest.getCustomerId() , null);
        
        String appSource = requestHeader.get(UtilityConstant.APP_HEADER_APP_SOURCE);
        String appVersion = requestHeader.get(UtilityConstant.APP_HEADER_APP_VERSION);
        
        if (StringUtils.isNotBlank(appSource) && StringUtils.isNotBlank(appVersion)
				&& UtilityConstant.APPSOURCELIST.contains(appSource)
				&& null != customer) {

			LOGGER.info("APP version:" + appVersion);

			String appVersionInInt = appVersion.replace(".", "");

			LOGGER.info("truncated app version number:" + appVersion);

			if (StringUtils.isNumeric(appVersionInInt)
					&& Integer.valueOf(appVersionInInt) >= UtilityConstant.APP_VERSION_NUMBER) {

				if ("1".equals(jwtFlag) && null == customer.getJwtToken()) {

					LOGGER.info("JWT token is null:" + customer.getEntityId());
					throw new BadRequestException("403", Constants.EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);
				}
				if ("1".equals(jwtFlag) && (null == customer.getJwtToken()
						|| (null != customer.getJwtToken() && customer.getJwtToken().equals(0)))) {

					LOGGER.info("JWT token not changed with zero:" + customer.getEntityId());
					
					throw new BadRequestException("403", Constants.EXCEPTION, Constants.HEADDER_X_HEADER_TOKEN_NOT_MATCHING_MESSAGE);

				}

			}

		}

        List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
                .findByCustomerId(storeRequest.getCustomerId());
        AmastyStoreCredit amastyStoreCredit = amastyStoreCredits.size() > 0 ? amastyStoreCredits.get(0) : null;

        if (amastyStoreCredit != null && null != amastyStoreCredit.getStoreCredit()) {

            LOGGER.info("conversion rate:" + store.getCurrencyConversionRate());
            BigDecimal convertedStoreCredit = amastyStoreCredit.getStoreCredit()
                    .divide(store.getCurrencyConversionRate(), 4, RoundingMode.HALF_UP);
            CustomerStoreCredit customerStoreCredit = new CustomerStoreCredit();

            DecimalFormat df = new DecimalFormat(".##");

            customerStoreCredit.setStoreCredit(new BigDecimal(df.format(convertedStoreCredit)));

            customerStoreCreditResponse.setResponse(customerStoreCredit);
            customerStoreCreditResponse.setStatus(true);
            customerStoreCreditResponse.setStatusCode("200");
            customerStoreCreditResponse.setStatusMsg("Store credit fetched successfully!");
            return customerStoreCreditResponse;
        } else {
            customerStoreCreditResponse.setStatus(false);
            customerStoreCreditResponse.setStatusCode("201");
            customerStoreCreditResponse.setStatusMsg("No store credit wallet found for customer!");
            return customerStoreCreditResponse;
        }
    }

    @Override
    public BankCouponsresponse getBankCouponsList(@Valid GetPromosRequest request,Map<String, String> requestHeader) {

        BankCouponsresponse response = new BankCouponsresponse();
        List<BankCouponsresponseBody> couponList = new ArrayList<>();
        
        String appSource = requestHeader.get(UtilityConstant.APP_HEADER_APP_SOURCE);
        String appVersion = requestHeader.get(UtilityConstant.APP_HEADER_APP_VERSION);
        
        if (StringUtils.isNotBlank(appSource) && StringUtils.isNotBlank(appVersion)
				&& UtilityConstant.APPSOURCELIST.contains(appSource)) {

			LOGGER.info("APP version:" + appVersion);

			String appVersionInInt = appVersion.replace(".", "");

			LOGGER.info("truncated app version number:" + appVersion);

			if (StringUtils.isNumeric(appVersionInInt)
					&& Integer.valueOf(appVersionInInt) <= UtilityConstant.APP_VERSION_NUMBER) {

				response.setStatus(true);
				response.setStatusCode("200");
				response.setStatusMsg("for this version bank offers are not available");
				
				return response;
			}

		}										

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
        requestHeaders.add("HTTP_X_API_KEY", Constants.COUPON_EXTERNAL_LIST_URL_HEADER);

        HttpEntity<CustomCouponValidationV4Request> requestBody = new HttpEntity<>(requestHeaders);
       // String url = "{url}/v4/bank-offers?store_id={store}";
        
        String url = getBankOffersNodeJSUrl();
         	
        Map<String, Object> parameters = new HashMap<>();
        if (StringUtil.isNullOrEmpty(Constants.getPromoBaseUrl()))
            parameters.put("url", couponlisturl);
        else
            parameters.put("url", Constants.getPromoBaseUrl());
        parameters.put("store", request.getStoreId());

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

        LOGGER.info("Coupon BANK GET URL:" + builder.buildAndExpand(parameters).toUri());

        try {

            ResponseEntity<CustomBankCouponListResponse> responseBody = restTemplate.exchange(
                    builder.buildAndExpand(parameters).toUri(), HttpMethod.GET, requestBody,
                    CustomBankCouponListResponse.class);

            LOGGER.info("response :" + responseBody);

            if (responseBody.getStatusCode() == HttpStatus.OK) {
                CustomBankCouponListResponse body = responseBody.getBody();
                if (body != null && body.getCode().equals(200)) {

                    List<BankCouponData> dataList = body.getData();

                    if (CollectionUtils.isNotEmpty(dataList)) {

                        for (BankCouponData data : dataList) {

							BankCouponsresponseBody promo = new BankCouponsresponseBody();
							
							String imageUrl = data.getImageUrl();
							
							if(StringUtils.isNotBlank(imageUrl) && imageUrl.startsWith("//")) {
								
								imageUrl = "https:"+imageUrl;
								data.setImageUrl(imageUrl);
							}

							BeanUtils.copyProperties(data, promo);
							promo.setPercentage(data.getOfferMessage());
							promo.setTermsCond(data.getTermsCond());
							couponList.add(promo);
						}
					}

                }
            }

        } catch (RestClientException e) {

            LOGGER.error("Exception occurred during bank offer fetch:" + e.getMessage());

            response.setStatus(false);
            response.setStatusCode("201");
            response.setStatusMsg("Error in bank promo fetch!");

        }

        if (CollectionUtils.isNotEmpty(couponList)) {

            response.setResponse(couponList);

        }

        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg("fetched successfully");

        return response;
    }
    
    private String getBankOffersNodeJSUrl() {
  	  
  	  PromoRedemptionValues values = Constants.getPromoRedemptionUrl();
        String url = null;
        if (values != null) {
            url = values.getDefaultBankOffersEndpoint();
        }
        LOGGER.info("Inside getBankOffersNodeJSUrl: url is " + url);
  	return url;
  }

	@Override
	public CustomerOrdersResponseDTO getReferalOrderList(Map<String, String> httpRequestHeadrs) {
		
		CustomerOrdersResponseDTO payload = new CustomerOrdersResponseDTO();
		
		if (Objects.nonNull(Constants.disabledServices)  
				&& Constants.disabledServices.isReferralDisabled()) {
			LOGGER.info(REFERRAL_SERVICE_OFF);
			payload.setStatusCode("202");
			payload.setStatus(false);
			payload.setStatusMsg(REFERRAL_SERVICE_OFF);
			return payload;
		}
		
		try {
		
		Integer hours= 1;
	if( null != Constants.orderCredentials.getOrderDetails().getReferralOrderLastHours()){
			hours = Constants.orderCredentials.getOrderDetails().getReferralOrderLastHours();
		}
				
		// getReferralDeliveredOrders returns only required columns (customerId, entityId, storeId, createdAt, deliveredAt, grandTotal, amstorecreditAmount, customerEmail) via ReferralOrderProjection
		List<ReferralOrderProjection> deliveredOrders = salesOrderRepository.getReferralDeliveredOrders(hours);
		List<Integer> customerIds = deliveredOrders.stream()
				.map(ReferralOrderProjection::getCustomerId)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		List<Customer> referralCustomers = customerService.findReferralCustomers(customerIds);
		List<Integer> referralCustomerIds = referralCustomers.stream().map(Customer::getCustomerId)
				.collect(Collectors.toList());
		List<ReferralOrderProjection> salesOrderList = deliveredOrders.stream()
				.filter(ord -> referralCustomerIds.contains(ord.getCustomerId())).collect(Collectors.toList());
		
		if(CollectionUtils.isNotEmpty(salesOrderList)) {
			
			List<ReferalOrderData> orderList = new ArrayList<>();
			for (ReferralOrderProjection row : salesOrderList) {
				ReferalOrderData data = new ReferalOrderData();
				Optional.ofNullable(row.getCustomerId()).ifPresent(id -> data.setCustomer_id(id.toString()));
				Optional.ofNullable(row.getEntityId()).ifPresent(id -> data.setOrder_id(id.toString()));
				Optional.ofNullable(row.getStoreId()).ifPresent(id -> data.setStore_id(id.toString()));
				Optional.ofNullable(row.getCreatedAt()).ifPresent(date -> data.setOrder_date(date.toString()));
				Optional.ofNullable(row.getDeliveredAt()).ifPresent(date -> data.setDelivered_date(date.toString()));
				// Use amstorecreditAmount (order currency) with grandTotal for same-currency total only; do not mix with base currency
				Optional.ofNullable(row.getGrandTotal()).ifPresent(grandTotal -> {
					BigDecimal storeCreditOrderCurrency = row.getAmstorecreditAmount();
					data.setGrand_total(storeCreditOrderCurrency != null ? grandTotal.add(storeCreditOrderCurrency).toString() : grandTotal.toString());
				});
				Optional.ofNullable(row.getCustomerEmail()).ifPresent(data::setEmail_id);
				orderList.add(data);
				
				
			}
			
			payload.setOrderDetails(orderList);
			
			HttpHeaders requestHeaders = new HttpHeaders();
	        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
	        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
	        requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);

	        HttpEntity<CustomerOrdersResponseDTO> requestBody = new HttpEntity<>(payload,requestHeaders);
	        String url = referralBaseUrl+"/saveOrderDetails";
	        Map<String, Object> parameters = new HashMap<>();
	        


	        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

	        LOGGER.info("referralBaseUrl :" + builder.buildAndExpand(parameters).toUri());
	        LOGGER.info("Referral  URL:" + builder.buildAndExpand(parameters).toUri());

	        try {

	            ResponseEntity<ReferralOrderListResponse> responseBody = restTemplate.exchange(
	            		url, HttpMethod.POST, requestBody,
	                    ReferralOrderListResponse.class);

	            LOGGER.info("response referal:" + responseBody);

	            if (responseBody.getStatusCode() == HttpStatus.OK) {
	            	ReferralOrderListResponse body = responseBody.getBody();
	                if (body != null && body.getCode().equals(200)) {


	        			payload.setStatus(true);
	        			payload.setStatusCode("200");
	        			payload.setStatusMsg("pushed Successfully");
	        			
	        			return payload;

	                }
	            }

	        } catch (RestClientException e) {

	            LOGGER.error("Exception occurred during bank offer fetch:" + e.getMessage());

	            payload.setStatus(false);
	            payload.setStatusCode("201");
	            payload.setStatusMsg("Error in bank promo fetch!");

	        }
			
			
			
		}else {
			
			payload.setStatus(true);
			payload.setStatusCode("201");
			payload.setStatusMsg("No Data Found");
		}
		
		}catch(Exception ex) {
			
			LOGGER.error("exception occoured for referal order fetch"+ex.getMessage());
			
			payload.setStatus(false);
			payload.setStatusCode("501");
			payload.setStatusMsg("exception occoured!!");
		}
		
		return payload;
	}
	
	/**
	 * return list of store credit
	 * param list  customerId and storeId
	 */
	@Override
	@Transactional(readOnly = true)
	public CustomerStoreCreditResponseList getCustomerStoreCreditList(StoreCreditListRequest storeRequest) {

		 List<Stores> stores = Constants.getStoresList();
		 CustomerStoreCreditResponseList customerStoreCreditResponse = new CustomerStoreCreditResponseList();

	        Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(storeRequest.getStoreId()))
	                .findAny().orElse(null);

	        if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
	            customerStoreCreditResponse.setStatus(false);
	            customerStoreCreditResponse.setStatusCode("202");
	            customerStoreCreditResponse.setStatusMsg("Store not found!");
	            return customerStoreCreditResponse;
	        }

	        List<CustomerStoreCredit> storeCreditList = new ArrayList<CustomerStoreCredit>();

	        for(Integer customerId : storeRequest.getCustomerIds()) {

	            CustomerStoreCredit customerStoreCredit = new CustomerStoreCredit();

	        	CustomerEntity customer = orderHelper.getCustomerDetails(customerId,null);

	        	if(null !=customer) {

		        List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
		                .findByCustomerId(customerId);
		        AmastyStoreCredit amastyStoreCredit = amastyStoreCredits.size() > 0 ? amastyStoreCredits.get(0) : null;



		        if (amastyStoreCredit != null && null != amastyStoreCredit.getStoreCredit()) {

		            LOGGER.info("conversion rate:" + store.getCurrencyConversionRate());
		            BigDecimal convertedStoreCredit = amastyStoreCredit.getStoreCredit()
		                    .divide(store.getCurrencyConversionRate(), 4, RoundingMode.HALF_UP);


		            DecimalFormat df = new DecimalFormat(".##");

		            customerStoreCredit.setStoreCredit(new BigDecimal(df.format(convertedStoreCredit)));
		            customerStoreCredit.setCustomerId(customerId);
		            customerStoreCredit.setStoreId(storeRequest.getStoreId());
		            storeCreditList.add(customerStoreCredit);

		            customerStoreCreditResponse.setResponse(storeCreditList);


		        } else {

		        	customerStoreCredit.setCustomerId(customerId);
		        	customerStoreCredit.setStoreCredit(BigDecimal.valueOf(0));
		        	customerStoreCredit.setStoreId(storeRequest.getStoreId());

		        	 storeCreditList.add(customerStoreCredit);
		        }

	        }else {

	        	customerStoreCredit.setCustomerId(customerId);
	        	customerStoreCredit.setMessage("Invalid customer Id !");

	        	 storeCreditList.add(customerStoreCredit);
	        }

	        }

	        if(CollectionUtils.isNotEmpty(storeCreditList)) {
	        customerStoreCreditResponse.setStatus(true);
            customerStoreCreditResponse.setStatusCode("200");
            customerStoreCreditResponse.setStatusMsg("Store credit fetched successfully!");

	        }else {

	        	customerStoreCreditResponse.setStatus(false);
	            customerStoreCreditResponse.setStatusCode("203");
	            customerStoreCreditResponse.setStatusMsg("Store credit not found!");
	        }


            return customerStoreCreditResponse;

	}

	/**
	 * request parameter
	 * response  order list
	 */
	@Override
	@Transactional(readOnly = true)
	public CustomerOrdersResponseDTO getCustomerOmsOrders(OmsOrderListRequest request) {
        return getOrderList.getOmsOrderList(request, staticComponents, salesOrderGridRepository, orderEntityConverter,
				customerEntityRepository, configService);
	}


	/**
	 * @param parameter order id and WebSite id will be optional
	 * @return will return with order details
	 */
	@Override
	@Transactional(readOnly = true)
	public OmsOrderresponsedto getOmsOrderDetails(@Valid OrderViewRequest request) {
        SalesOrder order = getOrderByEntityId(request.getOrderId(), request.getStoreId());
        List<SplitSalesOrder> splitSalesOrders = new ArrayList<>();
        if (order == null) {
            OmsOrderresponsedto resp = new OmsOrderresponsedto();
            resp.setStatus(false);
            resp.setStatusCode("201");
            resp.setStatusMsg("Error: Order was not found!");
            ErrorType error = new ErrorType();
            error.setErrorCode("201");
            error.setErrorMessage("Order was not found!");
            resp.setError(error);
            return resp;
        }
        if (Objects.equals(OrderConstants.IS_SPLIT_ORDER, order.getIsSplitOrder())) {
            splitSalesOrders = splitSalesOrderService.findByOrderId(request.getOrderId());
        }
        OmsOrderresponsedto response =  getOrderById.getOmsorderdetails(request, order, splitSalesOrders, restTemplate,
                mulinHelper, amastyStoreCreditRepository);

		return response;
	}

	/**
	 * @param request OrderViewRequest
	 * @return order shipping information
	 */
	@Override
	public OmsOrderresponsedto getOmsOrderShippingDetails(@Valid OrderViewRequest request) {

		 	SalesOrder order = getOrderByEntityId(request.getOrderId(), request.getStoreId());
		 	
	        OmsOrderresponsedto response =  getOrderById.getOmShipmentOderdetails(request, order, mulinHelper);

			return response;
	}

	/**
     * @param request OrderViewRequest
	 *@return order invoice response
	 */
	@Override
	public OmsOrderresponsedto getOmsOrderInvoiceDetails(@Valid OrderViewRequest request) {
		SalesOrder order = null;
		List<SplitSalesOrder> splitSalesOrders = new ArrayList<>();
		
		if (Constants.orderCredentials != null && Constants.orderCredentials.getWms().isNewInVoiceEncode()) {
			order = getOrderByEntityIdAndEmail(request.getOrderId(), request.getCustomerEmail());
		} else {
			order = getOrderByEntityId(request.getOrderId());
		}
		
		if (null != order && Objects.equals(OrderConstants.IS_SPLIT_ORDER, order.getIsSplitOrder())) {
			splitSalesOrders = splitSalesOrderService.findByOrderId(request.getOrderId());
		}
		
		return getOrderById.getOmsInvoiceordetails(request, order, splitSalesOrders, mulinHelper);
	}

	@Override
	@Transactional
	public OmsOrderupdateresponse omsStatusupdate(@Valid OrderupdateRequest request) {
		if(!request.getIsSplitOrder()) {
			SalesOrder order = getOrderByEntityId(request.getOrderId(), request.getStoreId());
			return  getOrderById.omsUpdateOrder(request, order);
		}
		else {
            List<SplitSalesOrder> splitSalesOrderList = splitSalesOrderRepository.findByOrderId(request.getOrderId());
            SplitSalesOrder order = splitSalesOrderList.stream().filter(o -> o.getEntityId().equals(request.getSplitOrderId())).findFirst().orElse(null);

            return getOrderById.omsUpdateOrderSplit(request, order);
		}
	}

	@Override
	@Transactional
	public OmsOrderupdateresponse omsOrderaddressupdate(@Valid OrderupdateRequest request, Map<String, String> requestHeader) {

		SalesOrder order = getOrderByEntityId(request.getOrderId(), request.getStoreId());
        SalesOrderGrid grid = getOrderGridByEntityId(request.getOrderId(), request.getStoreId());

		return  getOrderById.omsUpdateOrderAddress(request, order, grid, requestHeader);
	}

	@Override
	public OmsOrderoutboundresponse crerateShipment(@Valid OrderViewRequest request) {
        boolean isSplitOrder = false;
		// Validate that packboxDetailsList is present
		if (request.getPackboxDetailsList() == null || request.getPackboxDetailsList().isEmpty()) {
			OmsOrderoutboundresponse errorResponse = new OmsOrderoutboundresponse();
			errorResponse.setStatus(false);
			errorResponse.setHasError(true);
			errorResponse.setStatusCode("400");
			errorResponse.setStatusMsg("Bad Request");
			errorResponse.setErrorMessage("packboxDetailsList is required and cannot be empty");
			return errorResponse;
		}

        if (request.getOrderCode().toUpperCase().contains("G")) {
            isSplitOrder = true;
        }
        if (request.getOrderCode().toUpperCase().contains("L") || request.getOrderCode().toUpperCase().contains("E")) {
            isSplitOrder = true;
        }
        // If order is split order, then use split shipment helper
        if (isSplitOrder) {
            SplitSalesOrder splitSalesOrder = splitSalesOrderRepository.findByIncrementId(request.getOrderCode());
            return orderSplitShipmentHelper.createSplitOrderShipment(request,splitSalesOrder);
        }
        // If order is not a split order, then use regular shipment helper
        else {
            SalesOrder order = salesOrderRepository.findByIncrementId(request.getOrderCode());
            // If order is split order and sending normal increment id
            if (Objects.equals(order.getIsSplitOrder(), 1)) {
                LOGGER.info("As it is split order, Use split order increment id for shipment creation : " + request.getOrderCode()
                        + "is split orer : " + order.getIsSplitOrder());
                return getOmsOrderErrorresponse();
            }
            //If order is not split order, then use regular shipment helper
            return orderShipmentHelper.createorderShipment(request, order);
        }
	}

    private OmsOrderoutboundresponse getOmsOrderErrorresponse() {
        OmsOrderoutboundresponse omsOrdershipmentresponse = new OmsOrderoutboundresponse();
        omsOrdershipmentresponse.setStatus(false);
        omsOrdershipmentresponse.setStatusCode("202");
        omsOrdershipmentresponse.setStatusMsg("As it is split order, Use split order increment id for shipment creation");
        omsOrdershipmentresponse.setHasError(true);
        omsOrdershipmentresponse.setErrorMessage("As it is split order, Use split order increment id for shipment creation");
        return omsOrdershipmentresponse;
    }

    @Override
	public Integer findOrderId(String paymentId) {
		return subsalesOrderRepository.findOrderId(paymentId);
	}

	@Override
    @Transactional(readOnly = true)
    public SalesOrder getOrderByEntityId(Integer orderId) {
        return salesOrderRepository.findByEntityId(orderId);
    }

	

	@Override
	public SalesOrder findSalesOrderByPaymentId(String paymentId) {
		List<SubSalesOrder> subSalesOrders = subsalesOrderRepository.findByPaymentId(paymentId);
		Optional<SubSalesOrder> salesOrder = subSalesOrders.stream().findFirst();
		if(salesOrder.isPresent()) {
			return salesOrder.get().getSalesOrder();
		}
		return null;
	}

	@Override
	public List<SalesOrder> findSalesOrdeForTabbyPayment() {
		List<String> paymentmethods = Arrays.asList(PaymentCodeENUM.TABBY_IMSTALLMENTS.getValue(),
				PaymentCodeENUM.TABBY_PAYLATER.getValue());
		String ordersMinutesAgo = Constants.orderCredentials.getTabby().getOrdersMinutesAgo();
		String ordersHoursAgo = Constants.orderCredentials.getTabby().getOrdersHoursAgo();
		return salesOrderRepository.findTabbyOrders(paymentmethods, ordersMinutesAgo, ordersHoursAgo,
				PENDING_PAYMENT_ORDER_STATUS);
	}

	@Override
	public List<ProxyOrder> findPendingProxyOrders() {
		String ordersMinutesAgo= Constants.orderCredentials.getTamara().getOrdersMinutesAgo();
		String ordersHoursAgo = Constants.orderCredentials.getTamara().getOrdersHoursAgo();
		return proxyOrderRepository.findPendingPaymentOrders(ordersMinutesAgo,ordersHoursAgo);
	}

	@Override
	public SalesOrder findSalesOrderByIncrementId(String incrementId) {
		return salesOrderRepository.findByIncrementId(incrementId);
	}
	public List<SalesOrder> findByEntityId(List<Integer> incrementIds) {
		return salesOrderRepository.findByEntityIdIn(incrementIds);
	}

	@Override
	public List<SalesOrder> findSalesOrdeForCfPayment() {
		String paymentmethod = PaymentConstants.CASHFREE;
		String ordersMinutesAgo = Constants.orderCredentials.getTabby().getOrdersMinutesAgo();
		String ordersHoursAgo = Constants.orderCredentials.getTabby().getOrdersHoursAgo();
		return salesOrderRepository.findCashfreeOrders(paymentmethod, ordersMinutesAgo, ordersHoursAgo,
				PENDING_PAYMENT_ORDER_STATUS);
		
	}

	@Override
	public OrderResponseDTO orderPaybleDetail(Map<String, String> requestHeader, OrderViewRequest request) {
		
		
		 SalesOrder order = null;
			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
					.findAny().orElse(null);
			
		if(null != store && store.isHoldOrder()) {
			
			 List<Integer> storeIds = configService.getWebsiteStoresByStoreId(request.getStoreId());
			
			  order = salesOrderRepository.findPendingOrderList(request.getCustomerId() , storeIds);
		}		 
		 
	        return getOrderById.get(request, staticComponents, orderEntityConverter, order, requestHeader, restTemplate, mulinHelper,store, "" );
	}

	@Override
	public PendingPaymentToFailedResponse makePaymentPendingOrdersToPaymentFailed() {

		PendingPaymentToFailedResponse response = new PendingPaymentToFailedResponse();
		List<SalesOrder> orderList = salesOrderRepository.findPendingPaymentOrderWithinMinutes();

		if (CollectionUtils.isNotEmpty(orderList)) {
			LOGGER.info("makePaymentPendingOrdersToPaymentFailed : list is not empty for pending payment to payment failed at :: " + orderList.size());
			
			for (SalesOrder order : orderList) {
				try {
					LOGGER.info("makePaymentPendingOrdersToPaymentFailed : order going to make failed:"+order.getEntityId()+"increment id:"+order.getIncrementId());
					SubSalesOrder subSalesOrder = order.getSubSalesOrder();
					LOGGER.info("makePaymentPendingOrdersToPaymentFailed : Fetched order for processing: Entity ID = " + order.getEntityId() + ", Increment ID = " + order.getIncrementId() + ", Order Status = " + order.getStatus() +
	                        ", Order Expiration Time = " + (subSalesOrder != null ? subSalesOrder.getOrderExpiredAt() : "N/A"));
					order.setStatus(OrderConstants.FAILED_ORDER_STATUS);
					order.setState(OrderConstants.FAILED_ORDER_STATUS);

                    List<Stores> stores = Constants.getStoresList();
                    Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
                            .orElse(null);
                    if(subSalesOrder != null && subSalesOrder.getTotalShukranCoinsBurned() != null && subSalesOrder.getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0 && subSalesOrder.getShukranLocked()!=null && subSalesOrder.getShukranLocked().equals(0)){
                        commonService.lockUnlockShukranData(subSalesOrder.getCustomerProfileId(),subSalesOrder.getTotalShukranCoinsBurned().toString(), subSalesOrder.getQuoteId(), false, order, store, "Refund Shukran Burned Points On Failed Order", "");
                        order.getSubSalesOrder().setShukranLocked(1);
                    }

					updateOrderStatusHistory(order, OrderConstants.PAYMENT_FAILED_BY_CRON_MESSAGE,
							OrderConstants.ORDER_STATUS_HISTORY_ENTITY, order.getStatus());

					saveOrderGrid(order, OrderConstants.FAILED_ORDER_STATUS);

					salesOrderRepository.saveAndFlush(order);
					
					if (null != order.getAmstorecreditBaseAmount()) {

						releaseStoreCredit(order, order.getAmstorecreditAmount());
						String stylicreditMsg = OrderConstants.STYLI_CREDIT_FAILED_MSG + order.getBaseCurrencyCode() + ""
								+ order.getAmstorecreditBaseAmount();
						updateOrderStatusHistory(order, stylicreditMsg, OrderConstants.ORDER2, order.getStatus());

					}
                    if(subSalesOrder!= null) {
                        subSalesOrder.setRetryPayment(0);
                    }
					order.setRetryPayment(0);
					order.setWmsStatus(2);	
					salesOrderRepository.saveAndFlush(order);
					orderHelper.releaseInventoryQty(order, new HashMap<>(), true, OrderConstants.RELEASE_DTF_FAILED_CALL);
					failStatusOnwards(order);
					// EAS to be implement for payment fail.If Earn Service flag ON!.
					if ((Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
						eASServiceImpl.publishCancelOrderToKafka(order, 0.0);
					}
					//If BNPL order mark fail in proxy_order
					if(subSalesOrder != null && Objects.nonNull(subSalesOrder.getPaymentId())){
						paymentService.failProxyOrderByOrderId(order.getIncrementId());
					}
				} catch (Exception e) {
					LOGGER.info("makePaymentPendingOrdersToPaymentFailed : Error in updating pending payment order to failed for order: " + order.getEntityId());
				}
			}
		} else {

			LOGGER.info("list is  empty");
			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("No record found !!");
		}
		return response;
	}
	
	public void failStatusOnwards(SalesOrder order) {
		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getExternalCouponRedemptionTrackingId()
				&& StringUtils.isNotEmpty(order.getSubSalesOrder().getExternalCouponRedemptionTrackingId())
				&& StringUtils.isNotBlank(order.getSubSalesOrder().getExternalCouponRedemptionTrackingId())) {

			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId()))
					.findAny().orElse(null);

			salesOrderCancelServiceImpl.cancelReedmeExternalCoupon(store, order, false, false);

		}
	}
	
	
	/**
	 * @param request
	 * @param order
	 */
	public void updateOrderStatusHistory(SalesOrder order, String message, String entity, String status) {
		SalesOrderStatusHistory sh = new SalesOrderStatusHistory();

		LOGGER.info("History set");

		sh.setParentId(order.getEntityId());
		sh.setCustomerNotified(0);
		sh.setVisibleOnFront(1);
		sh.setComment("Order updated with message: " + message);
		sh.setCreatedAt(new Timestamp(new Date().getTime()));
		sh.setStatus(status);
		sh.setEntityName(entity);

		salesOrderStatusHistoryRepository.saveAndFlush(sh);
	}
	
	/**
	 * @param order
	 * @param message
	 */
	public void saveOrderGrid(SalesOrder order, String message) {
		SalesOrderGrid salesorderGrid = salesOrderGridRepository.findByEntityId(order.getEntityId());
		salesorderGrid.setStatus(message);
		salesOrderGridRepository.saveAndFlush(salesorderGrid);
	}

	public void sendBrazeNotificatonForPendingOrder() {
		try {
			OrderKeyDetails orderDetails = Constants.orderCredentials.getOrderDetails();
			
			List<Stores> stores = Constants.getStoresList();

	        
			LOGGER.info("query timing:"+orderDetails.getPendingOrderNotificationInMins());
			
			List<SubSalesOrder> expiredOrders = subsalesOrderRepository.findExpiredOrder(orderDetails.getPendingOrderNotificationInMins());
			
			

			List<SubSalesOrder> firstNotifications = expiredOrders.stream()
					.filter(ord -> notificationAt(orderDetails, ord.getFirstNotificationAt()))
					.collect(Collectors.toList());

			List<SubSalesOrder> secondNotifications = expiredOrders.stream()
					.filter(ord -> notificationAt(orderDetails, ord.getSecondNotificationAt()))
					.collect(Collectors.toList());

			LOGGER.info("first notification:"+firstNotifications + " second notification:" + secondNotifications);

			List<BrazePendingPaymentEvent> events = new ArrayList<>();
			
			if(CollectionUtils.isNotEmpty(firstNotifications)) {
				
				LOGGER.info("first notification is not empty");
			}if(CollectionUtils.isNotEmpty(secondNotifications)) {
				
				LOGGER.info("second notification is not empty");
			}

			for (SubSalesOrder order : firstNotifications) {

				SalesOrder salesOrder = order.getSalesOrder();
				
				Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(salesOrder.getStoreId()))
		                .findAny().orElse(null);

				BrazePendingPaymentPush attribute = new BrazePendingPaymentPush();

				BrazePendingPaymentEvent event = new BrazePendingPaymentEvent();
                event.setName("pending_payment");
                event.setExternalId(String.valueOf(salesOrder.getCustomerId()));
                event.setTime(String.valueOf(Instant.now()));

				attribute.setCustomerId(salesOrder.getCustomerId());
				attribute.setOrderId(salesOrder.getEntityId());
				for (SalesOrderAddress shippingAddress : salesOrder.getSalesOrderAddress()) {
					if (shippingAddress.getAddressType().equalsIgnoreCase(Constants.QUOTE_ADDRESS_TYPE_SHIPPING)
							&& null != shippingAddress.getTelephone()) {
						
						String phnNumber = shippingAddress.getTelephone().replaceAll("\\s","");;
						attribute.setPhoneNumber((phnNumber.substring(1)));
					}
				}
				attribute.setWhatsAppTemplate(
						orderDetails.getPendingOrderNotfcnDetails().getOrderPendingFirstWhtsAppTemplate());
				attribute.setTotalItems(salesOrder.getTotalItemCount());
				if (null != salesOrder.getDiscountAmount()) {
					attribute.setSavingDiscount(salesOrder.getDiscountAmount().abs().intValue());
				}
				attribute.setCurrency(store.getStoreCurrency());
				if (null != salesOrder.getEntityId() && null != salesOrder.getEntityId().toString()) {
					String orderIdEncoded = Base64.getEncoder()
							.encodeToString(salesOrder.getEntityId().toString().getBytes());
					attribute.setDeepLink(msiteDomainUrl + "/retry/order/?code=" + orderIdEncoded);
				}

				attribute.setStoreId(salesOrder.getStoreId());
				event.setProperties(attribute);
				if(null != salesOrder.getSubSalesOrder()
						&& null != salesOrder.getSubSalesOrder().getOrderExpiredAt()){
					Date expireDate = new Date(salesOrder.getSubSalesOrder().getOrderExpiredAt().getTime());
					Date currentDate = new Date();  
					
					long difference = expireDate.getTime() - currentDate.getTime();
					
					long hours = TimeUnit.MILLISECONDS.toHours(difference);
					
					attribute.setHoursLeft(String.valueOf(hours));
					
				}
				events.add(event);
			}
			for (SubSalesOrder order : secondNotifications) {

				SalesOrder salesOrder = order.getSalesOrder();
				
				Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(salesOrder.getStoreId()))
		                .findAny().orElse(null);

				BrazePendingPaymentPush attribute = new BrazePendingPaymentPush();

				BrazePendingPaymentEvent event = new BrazePendingPaymentEvent();
                event.setName("pending_payment");
                event.setExternalId(String.valueOf(salesOrder.getCustomerId()));
                event.setTime(String.valueOf(Instant.now()));

				attribute.setCustomerId(salesOrder.getCustomerId());
				attribute.setOrderId(salesOrder.getEntityId());
				
				for (SalesOrderAddress shippingAddress : salesOrder.getSalesOrderAddress()) {
					if (shippingAddress.getAddressType().equalsIgnoreCase(Constants.QUOTE_ADDRESS_TYPE_SHIPPING)) {
						String phnNumber = shippingAddress.getTelephone().replaceAll("\\s","");;
						attribute.setPhoneNumber((phnNumber.substring(1)));
					}
				}
				attribute.setWhatsAppTemplate(
						orderDetails.getPendingOrderNotfcnDetails().getOrderPendingSecopndWhtsAppTemplate());
				attribute.setTotalItems(salesOrder.getTotalItemCount());
				if(null != salesOrder.getDiscountAmount()) {
					attribute.setSavingDiscount(salesOrder.getDiscountAmount().abs().intValue());
				}
				attribute.setCurrency(store.getStoreCurrency());

				if (null != salesOrder.getEntityId() && null != salesOrder.getEntityId().toString()) {
					String orderIdEncoded = Base64.getEncoder()
							.encodeToString(salesOrder.getEntityId().toString().getBytes());
					attribute.setDeepLink(msiteDomainUrl + "/retry/order/?code=" + orderIdEncoded);
				}

				attribute.setStoreId(salesOrder.getStoreId());
				event.setProperties(attribute);
				if(null != salesOrder.getSubSalesOrder()
						&& null != salesOrder.getSubSalesOrder().getOrderExpiredAt()){
					Date expireDate = new Date(salesOrder.getSubSalesOrder().getOrderExpiredAt().getTime());
					Date currentDate = new Date();  
					
					long difference = expireDate.getTime() - currentDate.getTime();
					
					long hours = TimeUnit.MILLISECONDS.toHours(difference);
					
					attribute.setHoursLeft(String.valueOf(hours));
					
				}
				events.add(event);
			}

			if (CollectionUtils.isNotEmpty(events)) {
				Lists.partition(events, 50).stream()
						.forEach(eventChunk -> kafkaBrazeHelper.sendPendingPaymentToBraze(eventChunk));
			}

		} catch (Exception e) {
			LOGGER.error("Error in processing payment hold orders. Error : " + e.getMessage());
		}

	}

	private boolean notificationAt(OrderKeyDetails orderDetails, Timestamp notificationAt) {
        Calendar inLastXmins = Calendar.getInstance();
        inLastXmins.add(Calendar.MINUTE, -orderDetails.getPendingOrderNotificationInMins());
        Date lastMins = inLastXmins.getTime();

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(notificationAt.getTime());
        Date cal = calendar.getTime();

        return cal.after(lastMins) && calendar.before(Calendar.getInstance());
    }
	
	 private void releaseStoreCredit(SalesOrder order, BigDecimal storeCreditAmount) {

		List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
				.findByCustomerId(order.getCustomerId());
		AmastyStoreCredit amastyStoreCredit = !amastyStoreCredits.isEmpty() ? amastyStoreCredits.get(0) : null;
		if (amastyStoreCredit != null) {
			long currTime = new Date().getTime() / 1000;
			BulkWalletUpdate bulkWalletUpdate = new BulkWalletUpdate();
			bulkWalletUpdate.setEmail(order.getCustomerEmail());
			bulkWalletUpdate.setStore_id(order.getStoreId());
			bulkWalletUpdate.setAmount_to_be_refunded(storeCreditAmount);
			bulkWalletUpdate.setOrder_no(order.getIncrementId());
			bulkWalletUpdate.setInitiatedBy("java-api");
			bulkWalletUpdate.setInitiatedTime(String.valueOf(currTime));
			bulkWalletUpdate.setJobId("JAVA Service");

			LOGGER.info("releaseStoreCredit => " + bulkWalletUpdate.toString());
			kafkaService.publishSCToKafka(bulkWalletUpdate);
		}
	}
	 
	 
	 @Override
		public PendingPaymentToFailedResponse makefailedNonHoldOrders() {

			PendingPaymentToFailedResponse response = new PendingPaymentToFailedResponse();
			Integer lastPendingHours = 1440; //NON_HOLD_ORDERS_PENDING_PAYMENT_SINCE_IN_MINUTE
			if(null != Constants.orderCredentials.getOrderDetails().getNonHoldOrdersPendingSinceInMinute()) {
				
				lastPendingHours = Constants.orderCredentials.getOrderDetails().getNonHoldOrdersPendingSinceInMinute();
			}
			List<SalesOrder> orderList = salesOrderRepository.findPendingPaymentOrdersTomakeFailed(lastPendingHours);

			if (CollectionUtils.isNotEmpty(orderList)) {

				LOGGER.info("list pending payment to payment failed at :: " + orderList.size());
				for (SalesOrder order : orderList) {


					try {
						LOGGER.info("order going to make fail for which exipre date has not set:"+order.getEntityId()+"increment id:"+order.getIncrementId());	
						order.setStatus(OrderConstants.FAILED_ORDER_STATUS);
						order.setState(OrderConstants.FAILED_ORDER_STATUS);
                        List<Stores> stores = Constants.getStoresList();
                        Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
                                .orElse(null);
						SubSalesOrder subSalesOrder = order.getSubSalesOrder();
						subSalesOrder.setRetryPayment(0);
                        if(subSalesOrder.getTotalShukranCoinsBurned()!= null && subSalesOrder.getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0 && subSalesOrder.getShukranLocked() != null && subSalesOrder.getShukranLocked().equals(0)){
                            commonService.lockUnlockShukranData(subSalesOrder.getCustomerProfileId(), subSalesOrder.getTotalShukranCoinsBurned().toString(), subSalesOrder.getQuoteId(), false, order, store, "Refund Shukran Burned Points On Failed Order", "");
                            subSalesOrder.setShukranLocked(1);
                        }
						order.setRetryPayment(0);

						updateOrderStatusHistory(order, OrderConstants.PAYMENT_FAILED_BY_CRON_MESSAGE,
								OrderConstants.ORDER_STATUS_HISTORY_ENTITY, order.getStatus());

						saveOrderGrid(order, OrderConstants.FAILED_ORDER_STATUS);

						salesOrderRepository.saveAndFlush(order);
						
						if (null != order.getAmstorecreditBaseAmount()) {

							releaseStoreCredit(order, order.getAmstorecreditAmount());
							String stylicreditMsg = OrderConstants.STYLI_CREDIT_FAILED_MSG + order.getBaseCurrencyCode() + ""
									+ order.getAmstorecreditBaseAmount();
							updateOrderStatusHistory(order, stylicreditMsg, OrderConstants.ORDER2, order.getStatus());

						}
						subSalesOrder.setRetryPayment(0);
						order.setRetryPayment(0);
						salesOrderRepository.saveAndFlush(order);
						orderHelper.releaseInventoryQty(order, new HashMap<>(), true, OrderConstants.RELEASE_DTF_FAILED_CALL);
						failStatusOnwards(order);
						// EAS to be implement for payment fail.If Earn Service flag ON!.
						if ((Objects.isNull(Constants.disabledServices) || !Constants.disabledServices.isEarnDisabled()) && (Objects.isNull(Constants.orderCredentials) || Constants.orderCredentials.getStyliCash())) {
							eASServiceImpl.publishCancelOrderToKafka(order, 0.0);
						}
						//If BNPL order mark fail in proxy_order
						if(Objects.nonNull(subSalesOrder.getPaymentId())){
							paymentService.failProxyOrderByOrderId(order.getIncrementId());
						}
					} catch (Exception e) {
						LOGGER.info("Error in updating pending payment order to failed for order: " + order.getEntityId());
					}
				}
			} else {

				LOGGER.info("list is  empty");
				response.setStatus(true);
				response.setStatusCode("200");
				response.setStatusMsg("No record found !!");
			}
			return response;
		}

    @Override
    @Transactional(readOnly = true)
    public CustomerOrdersResponseDTO getCustomerOrdersV2(OrderListRequestV2 request) {
        return getOrderList.getOrdersV2(request, salesOrderRepository, orderEntityConverter,
                configService, mulinHelper, restTemplate);
    }

    @Override
    @Transactional
    public void updateDispatchCancelAllowed(DispatchUpdateRequest request) {
        if (!isValidDispatchUpdateRequest(request)) {
            LOGGER.warn("DispatchUpdateRequest or ShipmentLines is null");
            return;
        }

        List<DispatchUpdateRequest.ShipmentLine> shipmentLines = 
                request.getShipmentLines().getShipmentLine();
		LOGGER.warn("DispatchUpdateRequest: " + request.toString());
        for (DispatchUpdateRequest.ShipmentLine shipmentLine : shipmentLines) {
            processShipmentLine(shipmentLine);
        }
    }

    private void processShipmentLine(DispatchUpdateRequest.ShipmentLine shipmentLine) {
        if (StringUtils.isBlank(shipmentLine.getOrderNo())) {
            LOGGER.warn("OrderNo is blank for ShipmentLine: " + shipmentLine.getItemID());
            return;
        }

        String orderNo = shipmentLine.getOrderNo();
        try {
            if (orderNo.contains("-L1")) {
                updateSplitSalesOrderCancelAllowed(orderNo);
            } else {
                updateSalesOrderCancelAllowed(orderNo);
            }
        } catch (Exception e) {
            LOGGER.error("Error updating is_cancel_allowed for OrderNo: " + orderNo, e);
        }
    }

    private void updateSplitSalesOrderCancelAllowed(String orderNo) {
        SplitSalesOrder splitOrder = splitSalesOrderRepository.findByIncrementId(orderNo);
        if (splitOrder != null) {
            splitOrder.setIsCancelAllowed(false);
            splitSalesOrderRepository.saveAndFlush(splitOrder);
            LOGGER.info("Updated is_cancel_allowed to false for SplitSalesOrder with incrementId: " + orderNo);
        } else {
            LOGGER.warn("SplitSalesOrder not found for incrementId: " + orderNo);
        }
    }

    private void updateSalesOrderCancelAllowed(String orderNo) {
        SalesOrder salesOrder = salesOrderRepository.findByIncrementId(orderNo);
        if (salesOrder != null) {
            salesOrder.setIsCancelAllowed(false);
            salesOrderRepository.saveAndFlush(salesOrder);
            LOGGER.info("Updated is_cancel_allowed to false for SalesOrder with incrementId: " + orderNo);
        } else {
            LOGGER.warn("SalesOrder not found for OrderNo: " + orderNo);
        }
    }

    @Override
    public Map<String, Object> processDispatchUpdate(String xmlPayload, String authorizationToken) {
        // Check authorization if enabled
        if (Constants.orderCredentials.isInternalAuthEnable() || Constants.orderCredentials.isExternalAuthEnable()) {
            if (!isAuthorizedForDispatchUpdate(authorizationToken)) {
                LOGGER.info("You're not authenticated to make dispatch update request.");
                throw new ForbiddenException();
            }
        }

        try {
            // Parse XML using JAXB
            JAXBContext jaxbContext = JAXBContext.newInstance(DispatchUpdateRequest.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            DispatchUpdateRequest request = (DispatchUpdateRequest) unmarshaller.unmarshal(
                    new java.io.StringReader(xmlPayload));

            // Process the request
            updateDispatchCancelAllowed(request);

            Map<String, Object> response = new HashMap<>();
            response.put("status", true);
            response.put("statusCode", "200");
            response.put("statusMsg", "Dispatch update processed successfully");
            return response;
        } catch (JAXBException e) {
            LOGGER.error("Error parsing XML payload", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", false);
            response.put("statusCode", "400");
            response.put("statusMsg", "Invalid XML format: " + e.getMessage());
            return response;
        } catch (Exception e) {
            LOGGER.error("Error processing dispatch update", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", false);
            response.put("statusCode", "500");
            response.put("statusMsg", "Error processing dispatch update: " + e.getMessage());
            return response;
        }
    }

    private boolean isAuthorizedForDispatchUpdate(String authorizationToken) {
        boolean isInternalAuthEnabled = Constants.orderCredentials.isInternalAuthEnable();
        boolean isExternalAuthEnabled = Constants.orderCredentials.isExternalAuthEnable();
        
        if (isInternalAuthEnabled && configService.checkAuthorizationInternal(authorizationToken)) {
            return true;
        }
        
        return isExternalAuthEnabled && configService.checkAuthorizationExternal(authorizationToken);
    }

    private boolean isValidDispatchUpdateRequest(DispatchUpdateRequest request) {
        if (request == null) {
            return false;
        }
        if (request.getShipmentLines() == null) {
            return false;
        }
        return request.getShipmentLines().getShipmentLine() != null;
    }


}
