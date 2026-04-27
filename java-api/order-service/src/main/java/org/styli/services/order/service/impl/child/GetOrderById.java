package org.styli.services.order.service.impl.child;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.validation.Valid;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.*;
import org.styli.services.order.model.SalesOrder.AddressChangeHistory;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabel;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabelPK;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.*;
import org.styli.services.order.pojo.cancel.CancelOrderRequest;
import org.styli.services.order.pojo.consul.oms.base.AddressChangeAttributes;
import org.styli.services.order.pojo.customeAddressResponse.CustomerAddrees;
import org.styli.services.order.pojo.customeAddressResponse.CustomerAddreesResponse;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.request.GetShipmentV3.GetShipmentV3Request;
import org.styli.services.order.pojo.request.NavikAddressUpdateDTO;
import org.styli.services.order.pojo.request.OtsTrackingRequest;
import org.styli.services.order.pojo.request.Order.OrderStatus;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.request.Order.OrderViewRequest;
import org.styli.services.order.pojo.request.Order.OrdersDetailsResponsedto;
import org.styli.services.order.pojo.request.Order.OrderupdateRequest;
import org.styli.services.order.pojo.response.NavikAddressUpdateResponse;
import org.styli.services.order.pojo.response.OmsOrderupdateresponse;
import org.styli.services.order.pojo.response.OrderStatusResponse;
import org.styli.services.order.pojo.response.OtsTrackingResponse;
import org.styli.services.order.pojo.response.Order.*;
import org.styli.services.order.pojo.response.PayfortReposne;
import org.styli.services.order.pojo.response.UpdateOrderResponse;
import org.styli.services.order.pojo.response.V3.NavikResponse;
import org.styli.services.order.pojo.response.V3.SplitOrderResponse;
import org.styli.services.order.pojo.tabby.TabbyPayment;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyRmaTrackingRepository;
import org.styli.services.order.repository.SalesOrder.*;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.service.SalesOrderServiceV3;
import org.styli.services.order.service.SplitSalesOrderService;
import org.styli.services.order.service.impl.CommonServiceImpl;
import org.styli.services.order.service.impl.EASServiceImpl;
import org.styli.services.order.service.impl.SalesOrderCancelServiceImpl;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;
import org.styli.services.order.utility.PaymentUtility;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy.SelfInjection.Split;

import org.styli.services.order.utility.consulValues.MailPatternConfigs;
import org.styli.services.order.utility.consulValues.ServiceConfigs;

@Component
public class GetOrderById {

	private static final Log LOGGER = LogFactory.getLog(GetOrderById.class);

	@Autowired
	StaticComponents staticComponents;

	@Autowired
	OrderEntityConverter orderEntityConverter;

	@Autowired
	OmsorderentityConverter omsorderentityConverter;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;



	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;

	@Autowired
	OmsOrderupdateresponse omsOrderupdateresponse;

	@Autowired
	SalesOrderGridRepository salesOrderGridRepository;

	@Autowired
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;


	@Autowired
	SalesOrderCancelServiceImpl salesOrderCancelServiceImpl;

	@Autowired
	RefundHelper refundHelper;

	@Autowired
	PaymentRefundHelper paymentDtfRefundHelper;

	@Autowired
	OrderHelper orderHelper;

	@Autowired
	OrderHelperV2 orderHelperV2;

	@Autowired
	private PaymentUtility paymentUtility;

	@Autowired
	private ProxyOrderRepository proxyOrderRepository;

	@Autowired
	@Lazy
	private EASServiceImpl eASServiceImpl;

	@Autowired
	private CommonServiceImpl commonService;

	@Autowired
	private SalesOrderServiceV3 salesOrderServiceV3;

	@Autowired
	private ShipmentTrackerRepository shipmentTrackerRepository;

	@Autowired
	SalesOrderStatusLabelRepository salesOrderStatusLabelRepository;

	@Autowired
	private EmailHelper emailHelper;

	@Autowired
	private AddressChangeHistoryRepository addressChangeHistoryRepository;

	@Autowired
	SmsHelper smsHelper;

	@Autowired
	SplitSalesOrderService splitSalesOrderService;

	@Autowired
	AmastyRmaRequestRepository amastyRmaRequestRepository;

	@Autowired
	AmastyRmaTrackingRepository amastyRmaTrackingRepository;

	@Autowired
	SplitSellerOrderRepository splitSellerOrderRepository;

	@Autowired
	ConfigService configService;

	@Value("${region.value}")
	private String region;

	@Value("${jwt.salt.new.secret}")
	private String jwtsaltNewSecret;

	@Value("${jwt.salt.old.secret}")
	private String jwtsaltOldSecret;

	@Value("${auth.internal.header.bearer.token}")
	private String internalHeaderBearerToken;

	private static final String KEY_ADDRESS_CHANGE_MAIL = "addressChange";
	private static final String KEY_ADDRESS_CHANGE_SMS = "addressChangeSmsBody";

	public static final ObjectMapper mapper = new ObjectMapper();

	public OrderResponseDTO get(OrderViewRequest request, StaticComponents staticComponents,
			OrderEntityConverter orderEntityConverter, SalesOrder order,
			Map<String, String> headerRequest, RestTemplate restTemplate, MulinHelper mulinHelper, Stores store, String xClientVersion) {
		OrderResponseDTO orderResponseDTO = new OrderResponseDTO();
		ErrorType error = new ErrorType();


		if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			orderResponseDTO.setStatus(false);
			orderResponseDTO.setStatusCode("202");
			orderResponseDTO.setStatusMsg("Store not found!");
			return orderResponseDTO;
		}

		ObjectMapper mapper = new ObjectMapper();
		if (order != null) {

			try {
				if (null != order.getCustomerIsGuest() && order.getCustomerIsGuest() == 1
						&& request.getCustomerId() == null) {

					String jwtToken = null;
					String uuid = null;
					String headerEmail = null;

					// Extract JWT token and X-Header-Token from headers
					for (Map.Entry<String, String> entry : headerRequest.entrySet()) {
						String k = entry.getKey();
						String v = entry.getValue();
						if ("Token".equalsIgnoreCase(k) && null != v && v.length() > 3) {
							jwtToken = v.substring(4);
						}
						if ("X-Header-Token".equalsIgnoreCase(k) && null != v && v.length() > 3) {
							headerEmail = getEmailFromHeader(v);
						}
					}

					// Extract UUID from JWT token
					try {
						Claims body = Jwts.parser().setSigningKey(jwtsaltOldSecret).parseClaimsJws(jwtToken).getBody();

						if (null != body.get("uuid")) {
							LOGGER.info("view uuid " + body.get("uuid"));
							uuid = (String) body.get("uuid");
						}
					} catch (Exception e) {
						try {
							LOGGER.info("Error in getting uuid from jwt token of user from old secret key "
									+ e.getMessage());

							Claims body = Jwts.parser().setSigningKey(jwtsaltNewSecret).parseClaimsJws(jwtToken)
									.getBody();
							if (null != body.get("uuid")) {
								LOGGER.info("view uuid new " + body.get("uuid"));
								uuid = (String) body.get("uuid");
							}

						} catch (Exception secondEx) {
							LOGGER.info("Error in getting uuid from jwt token of user from new secret key "
									+ secondEx.getMessage());
						}
					}

					// Validate UUID match (if UUID exists in both JWT and order)
					boolean uuidValidationPassed = false;
					boolean shouldFallbackToEmail = false;

					if (null != uuid && null != order.getUuid()) {
						// Both UUIDs exist, check if they match
						if (order.getUuid().equalsIgnoreCase(uuid)) {
							uuidValidationPassed = true;
							LOGGER.info("UUID validation passed for guest order. JWT UUID: " + uuid
									+ ", Order UUID: " + order.getUuid());
						} else {
							// UUIDs don't match - validation failed
							LOGGER.info("UUID validation failed for guest order. JWT UUID: " + uuid
									+ ", Order UUID: " + order.getUuid());
							// UUID validation failed and order.uuid is not null, return error
							orderResponseDTO.setStatus(false);
							orderResponseDTO.setStatusCode("201");
							orderResponseDTO.setStatusMsg("Error: Guest order cannot be fetched!");

							error.setErrorCode("201");
							error.setErrorMessage("Guest order cannot be fetched!");

							orderResponseDTO.setError(error);
							return orderResponseDTO;
						}
					} else if (null != uuid && null == order.getUuid()) {
						// UUID exists in JWT but order.uuid is null - UUID check fails, fall back to email validation
						shouldFallbackToEmail = true;
						LOGGER.info("UUID check failed: UUID exists in JWT (" + uuid 
								+ ") but order.uuid is null. Falling back to email validation.");
					}

					// Email validation: X-Header-Token must match order.customer_email
					// This is mandatory, or used as fallback when UUID check fails and order.uuid is null
					if (!uuidValidationPassed || shouldFallbackToEmail) {
						if (StringUtils.isBlank(headerEmail) || StringUtils.isBlank(order.getCustomerEmail())
								|| (headerEmail != null && !headerEmail.equalsIgnoreCase(order.getCustomerEmail()))) {

							String validationType = shouldFallbackToEmail ? " (UUID fallback - order.uuid is null)" : "";
							LOGGER.info("Email validation failed for guest order" + validationType 
									+ ". X-Header-Token: " + headerEmail
									+ ", Order customer_email: " + order.getCustomerEmail());

							orderResponseDTO.setStatus(false);
							orderResponseDTO.setStatusCode("201");
							orderResponseDTO.setStatusMsg("Error: Guest order cannot be fetched!");

							error.setErrorCode("201");
							error.setErrorMessage("Guest order cannot be fetched!");

							orderResponseDTO.setError(error);
							return orderResponseDTO;
						} else {
							// Email validation passed
							if (shouldFallbackToEmail) {
								LOGGER.info("Email validation passed (UUID fallback). X-Header-Token: " + headerEmail
										+ " matches Order customer_email: " + order.getCustomerEmail());
							}
						}
					}

				}
			} catch (Exception e) {
				LOGGER.info("Error in uuid check in order " + e.getMessage());
			}

			orderResponseDTO.setStatus(true);
			orderResponseDTO.setStatusCode("200");
			orderResponseDTO.setStatusMsg("Order Fetched successfully!");
			Map<String, ProductResponseBody> productsFromMulin = mulinHelper
					.getMulinProductsFromOrder(Collections.singletonList(order), restTemplate);
			OrderResponse orderResponseBody = orderEntityConverter.convertOrder(order, true, mapper,
					request.getStoreId(), productsFromMulin, xClientVersion, store.isSecondRefund());

			if (null != store.getTaxPercentage()) {
				orderResponseBody.setTaxPercent(store.getTaxPercentage().toString());
			}
			orderResponseDTO.setResponse(orderResponseBody);
		} else {
			orderResponseDTO.setStatus(false);
			orderResponseDTO.setStatusCode("201");
			orderResponseDTO.setStatusMsg("Error: Order was not found!");

			error.setErrorCode("201");
			error.setErrorMessage("Order was not found!");

			orderResponseDTO.setError(error);
		}
		return orderResponseDTO;

	}

    public OrderResponseV2 getV2(OrderResponseDTO orderResponseDTO,OrderViewRequest request, StaticComponents staticComponents,
                                 OrderEntityConverter orderEntityConverter, SalesOrder order,
                                 Map<String, String> headerRequest, RestTemplate restTemplate, MulinHelper mulinHelper, Stores store, String xClientVersion) {
        OrderResponseV2 orderResponseV2 = new OrderResponseV2();
        ErrorType error = new ErrorType();


        if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			orderResponseV2.setStatus(false);
			orderResponseV2.setStatusCode("202");
            orderResponseV2.setStatusMsg("Store not found!");
            return orderResponseV2;
        }

        if (order != null) {
			OrderResponse orderResponse = orderResponseDTO.getResponse();
            orderResponseV2.setStatus(true);
            orderResponseV2.setStatusCode("200");
            orderResponseV2.setStatusMsg("Order Fetched successfully!");
            Map<String, ProductResponseBody> productsFromMulin = mulinHelper
                    .getMulinProductsFromOrder(Collections.singletonList(order), restTemplate);
			OrderDetailsV2 response = mapper.convertValue(orderResponse, OrderDetailsV2.class);
			orderEntityConverter.addOrderAddress(order,orderResponse,true);
			SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
			List<SplitOrderDTO> splitOrderDTOS = new ArrayList<>();
			List<SplitSalesOrder> splitOrders = null;
			if (Objects.equals(1, order.getIsSplitOrder())) {
				splitOrders = splitSalesOrderService.findByOrderId(order.getEntityId());
				if(!splitOrders.isEmpty()) {
					response.setTotalSplitOrderCount(splitOrders.size());
					
					// Use main order values (which came from quote service) to match get totals API
					response.setShippingAmount(formatShippingAmount(order.getShippingAmount()));
					response.setGlobalShippingAmount(formatShippingAmount(order.getGlobalShippingAmount()));
					
					int splitCount = 0;
					for (SplitSalesOrder splitOrder : splitOrders) {
						splitCount++;
						splitOrderDTOS.add(convertSplitOrder(splitCount,splitOrder,orderEntityConverter,orderResponse,productsFromMulin,xClientVersion));
					}
				} else {
					splitOrderDTOS.addAll(splitOrderInRuntime(order,orderEntityConverter,orderResponse,productsFromMulin,xClientVersion));
					response.setTotalSplitOrderCount(splitOrderDTOS.size());
					// For runtime split orders, use main order values
					response.setGlobalShippingAmount(formatShippingAmount(order.getGlobalShippingAmount()));
				}
			} else {
				response.setTotalSplitOrderCount(1);
				splitOrderDTOS.add(convertNormalOrder(order,orderEntityConverter,orderResponse,productsFromMulin,xClientVersion));
				// For non-split orders, use main order values
				response.setShippingAmount(formatShippingAmount(order.getShippingAmount()));
				response.setGlobalShippingAmount(formatShippingAmount(order.getGlobalShippingAmount()));
			}
				response.setSplitOrders(splitOrderDTOS);
			// Pass split orders to getOrderPayment to avoid redundant database calls
			getOrderPayment(response,salesOrderPayment,order,splitOrders,xClientVersion);
			// Set response.codCharges to match payments.codCharges (sum from all split orders for split orders)
			BigDecimal codCharges = calculateCodChargesForPayment(order, splitOrders);
			response.setCodCharges(formatShippingAmount(codCharges));
			orderResponseV2.setResponse(response);
	    } else {
            orderResponseV2.setStatus(false);
            orderResponseV2.setStatusCode("201");
            orderResponseV2.setStatusMsg("Error: Order was not found!");

            error.setErrorCode("201");
            error.setErrorMessage("Order was not found!");

            orderResponseV2.setError(error);
        }
        return orderResponseV2;

    }

    public OmsOrderresponsedto getOmsorderdetails(OrderViewRequest request, SalesOrder order, List<SplitSalesOrder> splitSalesOrders,
			RestTemplate restTemplate, MulinHelper mulinHelper,
			AmastyStoreCreditRepository amastyStoreCreditRepository) {
		OmsOrderresponsedto omsOrderresponsedto = new OmsOrderresponsedto();
		ErrorType error = new ErrorType();

		if (request.getOrderId() == null) {

			omsOrderresponsedto.setStatus(false);
			omsOrderresponsedto.setStatusCode("201");
			omsOrderresponsedto.setStatusMsg("order_id  not found.");
			return omsOrderresponsedto;
		}

		Stores store = null;
		List<Stores> stores = Constants.getStoresList();
		if (CollectionUtils.isNotEmpty(stores)) {
			store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
					.orElse(null);
		} else {

			omsOrderresponsedto.setStatus(false);
			omsOrderresponsedto.setStatusCode("209");
			omsOrderresponsedto.setStatusMsg("store id not found.");
			return omsOrderresponsedto;
		}

		if (order != null) {

			omsOrderresponsedto.setStatus(true);
			omsOrderresponsedto.setStatusCode("200");
			omsOrderresponsedto.setStatusMsg("Order Fetched successfully!");
			Map<String, ProductResponseBody> productsFromMulin = mulinHelper
					.getMulinProductsFromOrder(Collections.singletonList(order), restTemplate);
			OrdersDetailsResponsedto orderResponse ;


			Boolean showSellerCancelled = request.getShowSellerCancelled();

			if (Boolean.FALSE.equals(showSellerCancelled)) {
				LOGGER.info("getOmsorderdetails : showSellerCancelled is set to false ");
			    orderResponse = omsorderentityConverter.convertOrderObjectOldFormat(
			        order,
					splitSalesOrders,
			        productsFromMulin,
			        store,
			        "Email",
			        request.getRmaItemQtyProcessed()
			    );
			} else {
				LOGGER.info("getOmsorderdetails : showSellerCancelled is set to null ");
			    orderResponse = omsorderentityConverter.convertOrderObjectOldFormat(
			        order,
					splitSalesOrders,
			        productsFromMulin,
			        store,
			        "Order",
			        request.getRmaItemQtyProcessed()
			    );
			}

			if (request.getFetchStoreCreditBalance())
				fetchStoreCreditBalance(orderResponse, store, amastyStoreCreditRepository);

			omsOrderresponsedto.setResponse(orderResponse);
		} else {
			omsOrderresponsedto.setStatus(false);
			omsOrderresponsedto.setStatusCode("201");
			omsOrderresponsedto.setStatusMsg("Error: Order was not found!");

			error.setErrorCode("201");
			error.setErrorMessage("Order was not found!");

			omsOrderresponsedto.setError(error);
		}
		return omsOrderresponsedto;

	}

	private void fetchStoreCreditBalance(OrdersDetailsResponsedto orderResponse, Stores store,
			AmastyStoreCreditRepository amastyStoreCreditRepository) {
		BigDecimal storeCreditBalance = BigDecimal.ZERO;
		try {
			if (orderResponse.getCustomerId() != null) {
				List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
						.findByCustomerId(orderResponse.getCustomerId());
				AmastyStoreCredit amastyStoreCredit = amastyStoreCredits.size() > 0 ? amastyStoreCredits.get(0) : null;
				if (amastyStoreCredit != null && null != amastyStoreCredit.getStoreCredit()) {
					storeCreditBalance = amastyStoreCredit.getStoreCredit().divide(store.getCurrencyConversionRate(),
							4, RoundingMode.HALF_UP);
				}
			}
		} catch (Exception e) {
			System.out.println("Exception while fetching storeCredit: fetchStoreCreditBalance");
		}
		orderResponse.setStoreCreditBalance(String.valueOf(storeCreditBalance));

	}

	public OmsOrderresponsedto getOmShipmentOderdetails(OrderViewRequest request, SalesOrder order,
			MulinHelper mulinHelper) {
		OmsOrderresponsedto omsOrderresponsedto = new OmsOrderresponsedto();
		ErrorType error = new ErrorType();

		if (request.getOrderId() == null) {
			omsOrderresponsedto.setStatus(false);
			omsOrderresponsedto.setStatusCode("201");
			omsOrderresponsedto.setStatusMsg("order_id  not found.");
			return omsOrderresponsedto;
		}

		Stores store = findStore();
		if (order != null) {
			List<SplitSalesOrder> splitSalesOrderList = new ArrayList<>();
			if(Objects.equals(OrderConstants.IS_SPLIT_ORDER, order.getIsSplitOrder())) {
				splitSalesOrderList = splitSalesOrderService.findByOrderId(request.getOrderId());
			}
			omsOrderresponsedto.setStatus(true);
			omsOrderresponsedto.setStatusCode("200");
			omsOrderresponsedto.setStatusMsg("Order Fetched successfully!");
			Map<String, ProductResponseBody> productsFromMulin = mulinHelper
					.getMulinProductsFromOrder(Collections.singletonList(order), restTemplate);
			OrdersDetailsResponsedto orderResponse = omsorderentityConverter.convertOrderObjectOldFormat(order, splitSalesOrderList,
					productsFromMulin, store, "Shipment", request.getRmaItemQtyProcessed());

			if (orderResponse == null) {
				omsOrderresponsedto.setStatus(false);
				omsOrderresponsedto.setStatusCode("202");
				omsOrderresponsedto.setStatusMsg("Error: Shipment was not found!");
				error.setErrorCode("201");
				error.setErrorMessage("Shipment was not found!");
			} else
				omsOrderresponsedto.setResponse(orderResponse);
		} else {
			omsOrderresponsedto.setStatus(false);
			omsOrderresponsedto.setStatusCode("201");
			omsOrderresponsedto.setStatusMsg("Error: Order was not found!");

			error.setErrorCode("201");
			error.setErrorMessage("Order was not found!");

			omsOrderresponsedto.setError(error);
		}
		return omsOrderresponsedto;

	}

	public OmsOrderresponsedto getOmsInvoiceordetails(OrderViewRequest request, SalesOrder order, List<SplitSalesOrder> splitSalesOrders,
			MulinHelper mulinHelper) {
		OmsOrderresponsedto omsOrderresponsedto = new OmsOrderresponsedto();
		ErrorType error = new ErrorType();

		if (request.getOrderId() == null) {
			omsOrderresponsedto.setStatus(false);
			omsOrderresponsedto.setStatusCode("201");
			omsOrderresponsedto.setStatusMsg("order_id  not found.");
			return omsOrderresponsedto;
		}
		int storeId = "IN".equalsIgnoreCase(region) ? 51 : 1;
		Stores store = Constants.getStoresList().stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(storeId))
				.findAny().orElse(null);

		if (order != null) {

			omsOrderresponsedto.setStatus(true);
			omsOrderresponsedto.setStatusCode("200");
			omsOrderresponsedto.setStatusMsg("Order Fetched successfully!");
			Map<String, ProductResponseBody> productsFromMulin = mulinHelper
					.getMulinProductsFromOrder(Collections.singletonList(order), restTemplate);
			OrdersDetailsResponsedto orderResponse = omsorderentityConverter.convertOrderObject(order, splitSalesOrders,
					productsFromMulin, store, "Invoice", request.getRmaItemQtyProcessed());

			if (orderResponse == null) {
				omsOrderresponsedto.setStatus(false);
				omsOrderresponsedto.setStatusCode("202");
				omsOrderresponsedto.setStatusMsg("Error: Invoice was not found!");
				error.setErrorCode("201");
				error.setErrorMessage("Invoice was not found!");
			} else
				omsOrderresponsedto.setResponse(orderResponse);
		} else {

			omsOrderresponsedto.setStatus(false);
			omsOrderresponsedto.setStatusCode("201");
			omsOrderresponsedto.setStatusMsg("Error: Order was not found!");

			error.setErrorCode("201");
			error.setErrorMessage("Order was not found!");

			omsOrderresponsedto.setError(error);
		}
		return omsOrderresponsedto;

	}

	@Transactional
	public OmsOrderupdateresponse omsUpdateOrder(@Valid OrderupdateRequest request, SalesOrder order) {

		String paymentMethod = null;
		String fortId = null;
		String incrementId = null;
		BigDecimal storeCreditAmount = null;
		SalesOrderGrid grid = null;
		BigDecimal totalPaid = null;
		String orderCurrentStats = order.getStatus();
		Integer storeId = order.getStoreId();
		BigDecimal paidStoreCreditAmount = BigDecimal.ZERO;
		Map<String, BigDecimal> skumapList = new HashMap<>();
		List<SalesOrderItem> salesItemList = new ArrayList<>();
		BigDecimal calculatedOnlineAmount = BigDecimal.ZERO;
		BigDecimal totalVoucherToRefund = BigDecimal.ZERO;
		BigDecimal amountToCaptureAndRefundForOrderCancellation = BigDecimal.ZERO;

		boolean flagGrid = false;

		if (null != order) {
			//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
			//Find import fee of order based on available items of order
			BigDecimal currentOrderValue = paymentDtfRefundHelper.findCurrentOrderValue(order);

			SubSalesOrder subSalesOrder = order.getSubSalesOrder();

			SalesOrderPayment orderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);

			if (OrderConstants.checkPaymentMethod(orderPayment.getMethod())
					&& order.getCustomerId() == null) {
				omsOrderupdateresponse.setStatus(false);
				omsOrderupdateresponse.setStatusCode("201");
				omsOrderupdateresponse.setStatusMsg("This is the guest user we can't proceed");
				omsOrderupdateresponse.setOrderId(request.getOrderId());
				return omsOrderupdateresponse;
			}

			if (null != request.getOrderStatus() && StringUtils.isNotBlank(request.getOrderStatus().getValue())) {
				order.setState(request.getOrderStatus().getValue());
				order.setStatus(request.getOrderStatus().getValue());

				SalesOrderGrid orderGrid = salesOrderGridRepository.findByEntityId(order.getEntityId());

				orderGrid.setStatus(request.getOrderStatus().getValue());
				salesOrderGridRepository.saveAndFlush(orderGrid);

			}

			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(storeId)).findAny()
					.orElse(null);

			if (null != order && CollectionUtils.isNotEmpty(order.getSalesOrderPayment())) {
				incrementId = order.getIncrementId();
				for (SalesOrderPayment payment : order.getSalesOrderPayment()) {
					paymentMethod = payment.getMethod();
					fortId = payment.getCcTransId();
				}
			}

			if (null != paymentMethod && null != store && null != request.getOrderStatus()
					&& StringUtils.isNotBlank(request.getOrderStatus().getValue()) && null != orderCurrentStats
					&& orderCurrentStats.equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)) {
				String memoIncrementId = refundHelper.getIncrementId(order.getStoreId());
				if (memoIncrementId == null) {
					omsOrderupdateresponse.setStatus(false);
					omsOrderupdateresponse.setStatusCode("204");
					omsOrderupdateresponse.setStatusMsg("Could not create increment ID for credit memo!");
					return omsOrderupdateresponse;
				}
				CancelDetails details = new CancelDetails();
				//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
				//Find import fee of order based on available items of order
				details.setCurrentOrderValue(currentOrderValue);
				BigDecimal getProductCancelAmount = paymentDtfRefundHelper.getCancelAmount(order, skumapList,
						salesItemList);

				calculatedOnlineAmount = paymentDtfRefundHelper.cancelPercentageCalculation(order,
						getProductCancelAmount, order.getAmstorecreditAmount(), details, true, paymentMethod,
						totalVoucherToRefund);


				if (!paymentMethod.equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())) {

					boolean retuenOnlineflag = true;

					if (null != Constants.orderCredentials.getOrderDetails()
							&& Constants.orderCredentials.getOrderDetails().getCancelReturnToStyliCredit()
									.intValue() == 1) {

						retuenOnlineflag = false;
					}

					if (OrderConstants.checkPaymentMethod(orderPayment.getMethod())
							&& null != order.getPayfortAuthorized()
							&& String.valueOf(order.getPayfortAuthorized()).equalsIgnoreCase(Constants.ONE)) {
						retuenOnlineflag = true;
					}

					LOGGER.info("GetOrderById - omsUpdateOrder retuenOnlineflag: " + retuenOnlineflag);
					if (OrderConstants.checkPaymentMethod(paymentMethod) && retuenOnlineflag) {// payfort

						RefundPaymentRespone response = null;
						PayfortConfiguration configuration = new PayfortConfiguration();
						paymentDtfRefundHelper.getPayfortConfDetails(order.getStoreId().toString(), paymentMethod, configuration);
						try {
							LOGGER.info("configuration:" + mapper.writeValueAsString(configuration));
						} catch (JsonProcessingException e) {
							LOGGER.error("error during write configuration:" + e.getMessage());
						}

						// flag payfortRefundOnSellerCancellation to check consul true or false:
						boolean payfortRefundOnSellerCancellation = Constants.orderCredentials.getPayfort()
								.isPayfortRefundOnSellerCancellation() || false;
						boolean isMadaTransaction = false;

						if (order != null && order.getEntityId() != null) {
							int count = salesOrderRepository.checkIfMadaTransaction(order.getEntityId());
							isMadaTransaction = count > 0;
						}
						LOGGER.info("GetOrderById — Order " + order.getIncrementId() + " consul flag is : "
								+ payfortRefundOnSellerCancellation);
						LOGGER.info("GetOrderById — Order " + order.getIncrementId() + " is MADA transaction: "
								+ isMadaTransaction);

						boolean isSellerCancelDoneBefore = false;
						if (order != null && order.getEntityId() != null) {
							int sellerCancelCount = salesOrderRepository.checkIfSellerCancelExists(order.getEntityId());
							isSellerCancelDoneBefore = sellerCancelCount > 0;
						}
						LOGGER.info("GetOrderById — Order " + order.getIncrementId()
								+ " has previous seller cancellations: " + isSellerCancelDoneBefore);

						if (payfortRefundOnSellerCancellation && isMadaTransaction && isSellerCancelDoneBefore) {
							LOGGER.info(
									"GetOrderById — Order {} meets all conditions: Payfort Refund on Seller Cancellation is enabled, is a MADA transaction, and has previous seller cancellations."
											+ order.getIncrementId());

							LOGGER.info("GetOrderById — Initiating Payfort Capture Status Check for Order {}"
									+ order.getIncrementId());
							PayfortPaymentCaptureStatusCheckRequest statusCheckRequest = paymentUtility
									.preparePayfortPaymentCaptureStatusCheckRequest(configuration, order);
							PayfortCaptureStatusCheckResponse statusCheckResponse = paymentUtility
									.callPayfortStatusCheckApi(statusCheckRequest);

							if (statusCheckResponse != null && statusCheckResponse.getRefundedAmount() != null
									&& statusCheckResponse.getAuthorizedAmount() != null) {
								String authorizedAmountStr = statusCheckResponse.getAuthorizedAmount();
								String refundedAmountStr = statusCheckResponse.getRefundedAmount();

								BigDecimal authorizedAmount = new BigDecimal(authorizedAmountStr);
								BigDecimal refundedAmount = new BigDecimal(refundedAmountStr);

								amountToCaptureAndRefundForOrderCancellation = authorizedAmount.subtract(refundedAmount);

								LOGGER.info("GetOrderById — Remaining Amount to Capture & Refund: {}"
										+ amountToCaptureAndRefundForOrderCancellation);

								LOGGER.info("GetOrderById — Preparing capture request for Order {}" + order.getIncrementId());
								configuration.setMultiplier(1);
								PayfortPaymentCaptureRequest captureRequest = paymentUtility.preparePayfortCaptureRequest(
										configuration, order, amountToCaptureAndRefundForOrderCancellation, fortId);

								LOGGER.info("GetOrderById — Sending capture request to Payfort for Order {}"
										+ order.getIncrementId());
								PayfortReposne captureResponse = paymentUtility
										.triggerPayfortPaymentCaptureRestApiCall(captureRequest, order, configuration);

								if (captureResponse != null && captureResponse.isStatus()) {
									LOGGER.info(
											"GetOrderById — Payfort capture successful for Order {}. Proceeding with refund."
													+ order.getIncrementId());
									BigDecimal refundamount = amountToCaptureAndRefundForOrderCancellation.divide(new BigDecimal("100"));
									response = paymentDtfRefundHelper.payfortRefundcall(order,
											refundamount, fortId, paymentMethod);
								} else {
									LOGGER.error("GetOrderById — Payfort capture failed for Order {}. Skipping refund."
											+ order.getIncrementId());
								}
							} else {
								LOGGER.info(
										"GetOrderById — Payfort capture status response is incomplete or null for Order {}"
												+ order.getIncrementId());
							}
							calculatedOnlineAmount = amountToCaptureAndRefundForOrderCancellation;
							response.setStatus(true);
							response.setStatusCode("200");

						}
		                else if (null != order.getPayfortAuthorized()
								&& String.valueOf(order.getPayfortAuthorized()).equalsIgnoreCase(Constants.ONE)) {
							LOGGER.info("GetOrderById - omsUpdateOrder - perform payfortVoidAuthorizationcall");
							response = paymentDtfRefundHelper.payfortVoidAuthorizationcall(order, fortId,
									paymentMethod);
						} else {
							LOGGER.info("GetOrderById - omsUpdateOrder - perform payfortRefundcall");
							response = paymentDtfRefundHelper.payfortRefundcall(order, calculatedOnlineAmount,
									fortId, paymentMethod);
						}

						if (!response.getStatusCode().equals("200")) {
							omsOrderupdateresponse.setStatus(false);
							omsOrderupdateresponse.setStatusCode("206");
							omsOrderupdateresponse
									.setStatusMsg("cancel refund api failed with message: " + response.getStatusMsg());
							return omsOrderupdateresponse;
						}
					} else if (OrderConstants.checkBNPLPaymentMethods(paymentMethod)) {

						ProxyOrder proxyOrder = proxyOrderRepository
								.findByPaymentId(order.getSubSalesOrder().getPaymentId());

						if (Objects.nonNull(proxyOrder)) {
							paymentUtility.initiateClose(order, calculatedOnlineAmount.toString(), paymentMethod);
							orderHelperV2.updateProxyOrderStatusByPaymentId(order.getSubSalesOrder().getPaymentId(),
									OrderConstants.CLOSED_ORDER_STATUS);
						} else {
							paymentUtility.initiateRefund(order, calculatedOnlineAmount.toString(), paymentMethod);
						}
					} else if (PaymentConstants.CASHFREE.equalsIgnoreCase(paymentMethod)) {
						paymentUtility.initiateRefund(order, calculatedOnlineAmount.toString(), paymentMethod);
					} else {
						if (null != details.getAmasyStoreCredit()) {

							paidStoreCreditAmount = details.getAmasyStoreCredit().add(calculatedOnlineAmount);

						} else {

							paidStoreCreditAmount = calculatedOnlineAmount;
						}

						details.setAmasyStoreCredit(paidStoreCreditAmount);
						details.setTotalOnliineCancelAMount(BigDecimal.ZERO);
						calculatedOnlineAmount = BigDecimal.ZERO;
					}

					if (details.getAmasyStoreCredit() != null
							&& !(details.getAmasyStoreCredit().compareTo(BigDecimal.ZERO) == 0)) {

						LOGGER.info("inside store credit:" + details.getAmasyStoreCredit());

						BigDecimal amastyCreditAmount = details.getAmasyStoreCredit();
						BigDecimal baseStoreCredit = amastyCreditAmount.multiply(order.getStoreToBaseRate())
								.setScale(4, RoundingMode.HALF_UP);

						refundHelper.releaseStoreCredit(order, baseStoreCredit);
					}

					storeCreditAmount = details.getAmasyStoreCredit();
					order = setCreditMemo(order, paymentMethod, storeCreditAmount, store,
							calculatedOnlineAmount, skumapList, salesItemList);
					if (null != details.getAmasyStoreCredit()) {

						totalPaid = order.getGrandTotal().add(details.getAmasyStoreCredit());
					} else {
						totalPaid = order.getGrandTotal();
					}

				} else if (null != order.getAmstorecreditAmount()) {

					BigDecimal calculatedStoreCreditAmount = paymentDtfRefundHelper.getCancelledStoreCredit(order,
							store, details.getAmasyStoreCredit(), getProductCancelAmount, true, paymentMethod);
					BigDecimal baseStoreCredit = calculatedStoreCreditAmount.multiply(order.getStoreToBaseRate())
							.setScale(4, RoundingMode.HALF_UP);
					refundHelper.releaseStoreCredit(order, baseStoreCredit);

					order = setCreditMemo(order, paymentMethod, calculatedStoreCreditAmount, store, null, skumapList,
							salesItemList);
					flagGrid = true;
				}

			} else if (null != order.getAmstorecreditAmount()) {

				CancelDetails details = new CancelDetails();

				BigDecimal getProductCancelAmount = paymentDtfRefundHelper.getCancelAmount(order, skumapList,
						salesItemList);

				calculatedOnlineAmount = paymentDtfRefundHelper.cancelPercentageCalculation(order,
						getProductCancelAmount, order.getAmstorecreditAmount(), details, true, paymentMethod,
						totalVoucherToRefund);
				//SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
				//Find import fee of order based on available items of order
				BigDecimal calculatedStoreCreditAmount = paymentDtfRefundHelper.getCancelledStoreCreditWithCurrentOrderValue(order, store,
						details.getAmasyStoreCredit(), getProductCancelAmount, true, paymentMethod,currentOrderValue);
				BigDecimal baseStoreCredit = calculatedStoreCreditAmount.multiply(order.getStoreToBaseRate())
						.setScale(4, RoundingMode.HALF_UP);
				refundHelper.releaseStoreCredit(order, baseStoreCredit);
			}
			if (null != order.getStatus() && (order.getStatus().equalsIgnoreCase(OrderConstants.CLOSED_ORDER_STATUS) || order.getStatus().equalsIgnoreCase(OrderConstants.FAILED_ORDER_STATUS))) {
				order.setWmsStatus(2);
				orderHelper.updateStatusHistory(order, false, false, false, true, false);
			}
			if (!flagGrid) {

				grid = refundHelper.cancelOrderGrid(order, true, paymentMethod);

				if (grid != null) {
					salesOrderGridRepository.saveAndFlush(grid);
				}
			}

			String message = OrderConstants.CANCELLED_MSG_ADMIN;
			order = refundHelper.cancelStatusHistory(order, false, totalPaid, message);

			orderHelper.releaseInventoryQty(order, new HashMap<String, BigDecimal>(), false,
					OrderConstants.RELEASE_INVENTORY_ADMIN);
			order.setUpdatedAt(new Timestamp(new Date().getTime()));

			if (null != order.getSubSalesOrder()) {

				order.getSubSalesOrder().setRetryPayment(0);
				order.getSubSalesOrder().setOrderExpiredAt(null);
				order.getSubSalesOrder().setFirstNotificationAt(null);
				order.getSubSalesOrder().setSecondNotificationAt(null);
				order.setRetryPayment(0);
				if(order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0 && order.getSubSalesOrder().getShukranLocked().equals(0)){
					commonService.lockUnlockShukranData(order.getSubSalesOrder().getCustomerProfileId(), order.getSubSalesOrder().getTotalShukranCoinsBurned().toString(), order.getSubSalesOrder().getQuoteId(), false, order, store, "Shukran Burned Points Unlocked Due To Admin Cancellation", "");
					order.getSubSalesOrder().setShukranLocked(1);
				}

			}
			salesOrderRepository.saveAndFlush(order);
			cancelSellerOrdersByOMS(order.getSplitSellerOrders());

			// EAS to be implement for OMS cancel
			eASServiceImpl.publishCancelOrderToKafka(order, 0.0);

			omsOrderupdateresponse.setStatus(true);
			omsOrderupdateresponse.setStatusCode("200");
			omsOrderupdateresponse.setStatusMsg("Order Updated Successfully");
			omsOrderupdateresponse.setOrderId(request.getOrderId());
		}
		if (null != request.getComment()) {
			SalesOrderStatusHistory history = new SalesOrderStatusHistory();
			history.setParentId(order.getEntityId());
			if (null != request.getOrderStatus()) {
				history.setStatus(request.getOrderStatus().getValue());
			} else {
				history.setStatus(order.getStatus());
			}

			history.setCustomerNotified(0);
			history.setVisibleOnFront(1);
			history.setComment(request.getComment());
			history.setCreatedAt(new Timestamp(new Date().getTime()));

			salesOrderStatusHistoryRepository.saveAndFlush(history);
		}

		List<SplitSalesOrder> splitSalesOrders = order.getSplitSalesOrders().stream().toList();
		if(!splitSalesOrders.isEmpty()){
            int updatedCount = splitSalesOrderRepository.updateStatusAndStateByOrderId(
                    request.getOrderStatus().getValue(),
                    request.getOrderStatus().getValue(),
                    new Timestamp(new Date().getTime()),
                    order.getEntityId()
            );
			LOGGER.info("Updated " + updatedCount + " split orders with status: " + request.getOrderStatus().getValue());
		}

		return omsOrderupdateresponse;
	}

    @Transactional
	public OmsOrderupdateresponse omsUpdateOrderSplit(@Valid OrderupdateRequest request, SplitSalesOrder order) {

		String paymentMethod = null;
		String fortId = null;
		String incrementId = null;
		BigDecimal storeCreditAmount = null;
		SalesOrderGrid grid = null;
		BigDecimal totalPaid = null;
		String orderCurrentStats = order.getStatus();
		Integer storeId = order.getStoreId();
		BigDecimal paidStoreCreditAmount = BigDecimal.ZERO;
		Map<String, BigDecimal> skumapList = new HashMap<>();
		List<SplitSalesOrderItem> salesItemList = new ArrayList<>();
		BigDecimal calculatedOnlineAmount = BigDecimal.ZERO;
		BigDecimal totalVoucherToRefund = BigDecimal.ZERO;
		BigDecimal amountToCaptureAndRefundForOrderCancellation = BigDecimal.ZERO;

		boolean flagGrid = false;

		List<SplitSalesOrder> splitSalesOrders = order.getSalesOrder().getSplitSalesOrders().stream().toList();
		boolean shouldParentOrderClose = splitSalesOrders.stream().filter(e -> e.getStatus().equals(OrderConstants.CLOSED_ORDER_STATUS) || e.getStatus().equals(OrderConstants.CANCELLED_ORDER_STATUS) || e.getStatus().equals(OrderConstants.FAILED_ORDER_STATUS)).count() >= splitSalesOrders.size() - 1;

		SalesOrder parentOrder = order.getSalesOrder();

		if (null != order) {

			SplitSubSalesOrder subSalesOrder = order.getSplitSubSalesOrder();

			SplitSalesOrderPayment orderPayment = order.getSplitSalesOrderPayments().stream().findFirst().orElse(null);

			if (OrderConstants.checkPaymentMethod(orderPayment.getMethod())
					&& order.getCustomerId() == null) {
				omsOrderupdateresponse.setStatus(false);
				omsOrderupdateresponse.setStatusCode("201");
				omsOrderupdateresponse.setStatusMsg("This is the guest user we can't proceed");
				omsOrderupdateresponse.setOrderId(request.getOrderId());
				return omsOrderupdateresponse;
			}

			if (null != request.getOrderStatus() && StringUtils.isNotBlank(request.getOrderStatus().getValue())) {
				order.setState(request.getOrderStatus().getValue());
				order.setStatus(request.getOrderStatus().getValue());

				if(shouldParentOrderClose){
					parentOrder.setState(request.getOrderStatus().getValue());
					parentOrder.setStatus(request.getOrderStatus().getValue());
					SalesOrderGrid orderGrid = salesOrderGridRepository.findByEntityId(parentOrder.getEntityId());

					orderGrid.setStatus(request.getOrderStatus().getValue());
					salesOrderGridRepository.saveAndFlush(orderGrid);
				}

			}

			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(storeId)).findAny()
					.orElse(null);
            if (null != order.getSalesOrder() && CollectionUtils.isNotEmpty(order.getSalesOrder().getSalesOrderPayment())) {
				incrementId = order.getSalesOrder().getIncrementId();
				for (SalesOrderPayment payment : order.getSalesOrder().getSalesOrderPayment()) {
					paymentMethod = payment.getMethod();
					fortId = payment.getCcTransId();
				}
			}

			if (null != paymentMethod && null != store && null != request.getOrderStatus()
					&& StringUtils.isNotBlank(request.getOrderStatus().getValue()) && null != orderCurrentStats
					&& orderCurrentStats.equalsIgnoreCase(OrderConstants.PROCESSING_ORDER_STATUS)) {
				String memoIncrementId = refundHelper.getIncrementId(order.getStoreId());
				if (memoIncrementId == null) {
					omsOrderupdateresponse.setStatus(false);
					omsOrderupdateresponse.setStatusCode("204");
					omsOrderupdateresponse.setStatusMsg("Could not create increment ID for credit memo!");
					return omsOrderupdateresponse;
				}
				CancelDetails details = new CancelDetails();

                BigDecimal getProductCancelAmount = paymentDtfRefundHelper.getCancelAmountForSplitOrder(order, skumapList,
                        salesItemList);

                calculatedOnlineAmount = paymentDtfRefundHelper.cancelPercentageCalculationForSplitOrder(order,
                        getProductCancelAmount, order.getAmstorecreditAmount(), details, true, paymentMethod,
                        totalVoucherToRefund);

				if (!paymentMethod.equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())) {

					boolean retuenOnlineflag = true;

					if (null != Constants.orderCredentials.getOrderDetails()
							&& Constants.orderCredentials.getOrderDetails().getCancelReturnToStyliCredit()
									.intValue() == 1) {

						retuenOnlineflag = false;
					}

					if (OrderConstants.checkPaymentMethod(orderPayment.getMethod())
							&& null != order.getPayfortAuthorized()
							&& String.valueOf(order.getPayfortAuthorized()).equalsIgnoreCase(Constants.ONE)) {
						retuenOnlineflag = true;
					}

					LOGGER.info("GetOrderById - omsUpdateOrder retuenOnlineflag: " + retuenOnlineflag);
					if (OrderConstants.checkPaymentMethod(paymentMethod) && retuenOnlineflag) {// payfort
						if (shouldParentOrderClose){
							RefundPaymentRespone response = null;
							PayfortConfiguration configuration = new PayfortConfiguration();
							paymentDtfRefundHelper.getPayfortConfDetails(parentOrder.getStoreId().toString(), paymentMethod, configuration);
							try {
								LOGGER.info("configuration:" + mapper.writeValueAsString(configuration));
							} catch (JsonProcessingException e) {
								LOGGER.error("error during write configuration:" + e.getMessage());
							}

							// flag payfortRefundOnSellerCancellation to check consul true or false:
							boolean payfortRefundOnSellerCancellation = Constants.orderCredentials.getPayfort()
									.isPayfortRefundOnSellerCancellation() || false;
							boolean isMadaTransaction = false;

							if (parentOrder != null && parentOrder.getEntityId() != null) {
								int count = salesOrderRepository.checkIfMadaTransaction(parentOrder.getEntityId());
								isMadaTransaction = count > 0;
							}
							LOGGER.info("GetOrderById — Order " + parentOrder.getIncrementId() + " consul flag is : "
									+ payfortRefundOnSellerCancellation);
							LOGGER.info("GetOrderById — Order " + parentOrder.getIncrementId() + " is MADA transaction: "
									+ isMadaTransaction);

							boolean isSellerCancelDoneBefore = false;
							if (order != null && order.getEntityId() != null) {
								int sellerCancelCount = salesOrderRepository.checkIfSellerCancelExists(parentOrder.getEntityId());
								isSellerCancelDoneBefore = sellerCancelCount > 0;
							}
							LOGGER.info("GetOrderById — Order " + order.getIncrementId()
									+ " has previous seller cancellations: " + isSellerCancelDoneBefore);

							if (payfortRefundOnSellerCancellation && isMadaTransaction && isSellerCancelDoneBefore) {
								LOGGER.info(
										"GetOrderById — Order {} meets all conditions: Payfort Refund on Seller Cancellation is enabled, is a MADA transaction, and has previous seller cancellations."
												+ order.getIncrementId());

								LOGGER.info("GetOrderById — Initiating Payfort Capture Status Check for Order {}"
										+ order.getIncrementId());
								PayfortPaymentCaptureStatusCheckRequest statusCheckRequest = paymentUtility
										.preparePayfortPaymentCaptureStatusCheckRequest(configuration, parentOrder);
								PayfortCaptureStatusCheckResponse statusCheckResponse = paymentUtility
										.callPayfortStatusCheckApi(statusCheckRequest);

								if (statusCheckResponse != null && statusCheckResponse.getRefundedAmount() != null
										&& statusCheckResponse.getAuthorizedAmount() != null) {
									String authorizedAmountStr = statusCheckResponse.getAuthorizedAmount();
									String refundedAmountStr = statusCheckResponse.getRefundedAmount();

									BigDecimal authorizedAmount = new BigDecimal(authorizedAmountStr);
									BigDecimal refundedAmount = new BigDecimal(refundedAmountStr);

									amountToCaptureAndRefundForOrderCancellation = authorizedAmount.subtract(refundedAmount);

									LOGGER.info("GetOrderById — Remaining Amount to Capture & Refund: {}"
											+ amountToCaptureAndRefundForOrderCancellation);

									LOGGER.info("GetOrderById — Preparing capture request for Order {}" + order.getIncrementId());
									configuration.setMultiplier(1);
									PayfortPaymentCaptureRequest captureRequest = paymentUtility.preparePayfortCaptureRequest(
											configuration, parentOrder, amountToCaptureAndRefundForOrderCancellation, fortId);

									LOGGER.info("GetOrderById — Sending capture request to Payfort for Order {}"
											+ order.getIncrementId());
									PayfortReposne captureResponse = paymentUtility
											.triggerPayfortPaymentCaptureRestApiCall(captureRequest, parentOrder, configuration);

									if (captureResponse != null && captureResponse.isStatus()) {
										LOGGER.info(
												"GetOrderById — Payfort capture successful for Order {}. Proceeding with refund."
														+ parentOrder.getIncrementId());
										BigDecimal refundamount = amountToCaptureAndRefundForOrderCancellation.divide(new BigDecimal("100"));
										response = paymentDtfRefundHelper.payfortRefundcall(parentOrder,
												refundamount, fortId, paymentMethod);
									} else {
										LOGGER.error("GetOrderById — Payfort capture failed for Order {}. Skipping refund."
												+ parentOrder.getIncrementId());
									}
								} else {
									LOGGER.info(
											"GetOrderById — Payfort capture status response is incomplete or null for Order {}"
													+ order.getIncrementId());
								}
								calculatedOnlineAmount = amountToCaptureAndRefundForOrderCancellation;
								response.setStatus(true);
								response.setStatusCode("200");

							}
							else if (null != order.getPayfortAuthorized()
									&& String.valueOf(order.getPayfortAuthorized()).equalsIgnoreCase(Constants.ONE)) {
								LOGGER.info("GetOrderById - omsUpdateOrder - perform payfortVoidAuthorizationcall");
								response = paymentDtfRefundHelper.payfortVoidAuthorizationcall(parentOrder, fortId,
										paymentMethod);
							} else {
								LOGGER.info("GetOrderById - omsUpdateOrder - perform payfortRefundcall");
								response = paymentDtfRefundHelper.payfortRefundcall(parentOrder, calculatedOnlineAmount,
										fortId, paymentMethod);
							}

							if (!response.getStatusCode().equals("200")) {
								omsOrderupdateresponse.setStatus(false);
								omsOrderupdateresponse.setStatusCode("206");
								omsOrderupdateresponse
										.setStatusMsg("cancel refund api failed with message: " + response.getStatusMsg());
								return omsOrderupdateresponse;
							}

					  }
					} else if (OrderConstants.checkBNPLPaymentMethods(paymentMethod)) {

						if (shouldParentOrderClose) {
							ProxyOrder proxyOrder = proxyOrderRepository
							.findByPaymentId(parentOrder.getSubSalesOrder().getPaymentId());

							if (Objects.nonNull(proxyOrder)) {
								paymentUtility.initiateClose(parentOrder, calculatedOnlineAmount.toString(), paymentMethod);
								orderHelperV2.updateProxyOrderStatusByPaymentId(parentOrder.getSubSalesOrder().getPaymentId(),
										OrderConstants.CLOSED_ORDER_STATUS);
							} else {
								paymentUtility.initiateRefund(parentOrder, calculatedOnlineAmount.toString(), paymentMethod);
							}
						}
					} else if (PaymentConstants.CASHFREE.equalsIgnoreCase(paymentMethod)) {
						if (shouldParentOrderClose){
							paymentUtility.initiateRefund(parentOrder, calculatedOnlineAmount.toString(), paymentMethod);
						}
					} else {
						if (null != details.getAmasyStoreCredit()) {

							paidStoreCreditAmount = details.getAmasyStoreCredit().add(calculatedOnlineAmount);

						} else {

							paidStoreCreditAmount = calculatedOnlineAmount;
						}

						details.setAmasyStoreCredit(paidStoreCreditAmount);
						details.setTotalOnliineCancelAMount(BigDecimal.ZERO);
						calculatedOnlineAmount = BigDecimal.ZERO;
					}

					if (details.getAmasyStoreCredit() != null
							&& !(details.getAmasyStoreCredit().compareTo(BigDecimal.ZERO) == 0)) {

						LOGGER.info("inside store credit:" + details.getAmasyStoreCredit());

						BigDecimal amastyCreditAmount = details.getAmasyStoreCredit();
						BigDecimal baseStoreCredit = amastyCreditAmount.multiply(order.getStoreToBaseRate())
								.setScale(4, RoundingMode.HALF_UP);

						refundHelper.releaseStoreCreditForSplitOrder(order, baseStoreCredit);
					}

					storeCreditAmount = details.getAmasyStoreCredit();
					order = setCreditMemoForSplitOrder(order, parentOrder, paymentMethod, storeCreditAmount, store,
							calculatedOnlineAmount, skumapList, new ArrayList<>(), shouldParentOrderClose);
					if (null != details.getAmasyStoreCredit()) {

						totalPaid = order.getGrandTotal().add(details.getAmasyStoreCredit());
					} else {
						totalPaid = order.getGrandTotal();
					}

				} else if (null != order.getAmstorecreditAmount()) {

					BigDecimal calculatedStoreCreditAmount = paymentDtfRefundHelper.getCancelledStoreCreditForSplitOrder(order,
							store, details.getAmasyStoreCredit(), getProductCancelAmount, true, paymentMethod);
					BigDecimal baseStoreCredit = calculatedStoreCreditAmount.multiply(order.getStoreToBaseRate())
							.setScale(4, RoundingMode.HALF_UP);
					refundHelper.releaseStoreCreditForSplitOrder(order, baseStoreCredit);

					order = setCreditMemoForSplitOrder(order, parentOrder, paymentMethod, calculatedStoreCreditAmount, store, null, skumapList,
							new ArrayList<>(), shouldParentOrderClose);
					flagGrid = true;
				}

			} else if (null != order.getAmstorecreditAmount()) {

				CancelDetails details = new CancelDetails();

                BigDecimal getProductCancelAmount = paymentDtfRefundHelper.getCancelAmountForSplitOrder(order, skumapList,
                        salesItemList);

                calculatedOnlineAmount = paymentDtfRefundHelper.cancelPercentageCalculationForSplitOrder(order,
                        getProductCancelAmount, order.getAmstorecreditAmount(), details, true, paymentMethod,
                        totalVoucherToRefund);

				BigDecimal calculatedStoreCreditAmount = paymentDtfRefundHelper.getCancelledStoreCreditForSplitOrder(order, store,
						details.getAmasyStoreCredit(), getProductCancelAmount, true, paymentMethod);
				BigDecimal baseStoreCredit = calculatedStoreCreditAmount.multiply(order.getStoreToBaseRate())
						.setScale(4, RoundingMode.HALF_UP);
				refundHelper.releaseStoreCreditForSplitOrder(order, baseStoreCredit);
			}
			if (null != order.getStatus() && (order.getStatus().equalsIgnoreCase(OrderConstants.CLOSED_ORDER_STATUS) || order.getStatus().equalsIgnoreCase(OrderConstants.FAILED_ORDER_STATUS))) {
				order.setWmsStatus(2);
				if(shouldParentOrderClose){
					parentOrder.setWmsStatus(2);
					orderHelper.updateStatusHistory(parentOrder, false, false, false, true, false);
				}
			}
			if (!flagGrid && shouldParentOrderClose) {

				grid = refundHelper.cancelOrderGrid(parentOrder, true, paymentMethod);

				if (grid != null) {
					salesOrderGridRepository.saveAndFlush(grid);
				}
			}

			String message = OrderConstants.CANCELLED_MSG_ADMIN;
			if(shouldParentOrderClose){
				parentOrder = refundHelper.cancelStatusHistory(parentOrder, false, totalPaid, message);
			}

			orderHelper.releaseInventoryQtyForSplitOrder(order, new HashMap<String, BigDecimal>(), false,
					OrderConstants.RELEASE_INVENTORY_ADMIN);
			order.setUpdatedAt(new Timestamp(new Date().getTime()));

			if (shouldParentOrderClose && null != parentOrder.getSubSalesOrder()) {

				parentOrder.getSubSalesOrder().setRetryPayment(0);
				parentOrder.getSubSalesOrder().setOrderExpiredAt(null);
				parentOrder.getSubSalesOrder().setFirstNotificationAt(null);
				parentOrder.getSubSalesOrder().setSecondNotificationAt(null);
				parentOrder.setRetryPayment(0);
				if(parentOrder.getSubSalesOrder().getTotalShukranCoinsBurned() != null && parentOrder.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0 && parentOrder.getSubSalesOrder().getShukranLocked().equals(0)){
					commonService.lockUnlockShukranData(parentOrder.getSubSalesOrder().getCustomerProfileId(), parentOrder.getSubSalesOrder().getTotalShukranCoinsBurned().toString(), parentOrder.getSubSalesOrder().getQuoteId(), false, parentOrder, store, "Shukran Burned Points Unlocked Due To Admin Cancellation", "");
					parentOrder.getSubSalesOrder().setShukranLocked(1);
				}
			}

			if(null != order.getSplitSubSalesOrder()){
				order.getSplitSubSalesOrder().setRetryPayment(0);
				order.getSplitSubSalesOrder().setOrderExpiredAt(null);
				order.getSplitSubSalesOrder().setFirstNotificationAt(null);
				order.getSplitSubSalesOrder().setSecondNotificationAt(null);
				order.setRetryPayment(0);
					if(order.getSplitSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSplitSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO)>0 && order.getSplitSubSalesOrder().getShukranLocked().equals(0)){
						commonService.lockUnlockShukranDataForSplit(order.getSplitSubSalesOrder().getCustomerProfileId(), order.getSplitSubSalesOrder().getTotalShukranCoinsBurned().toString(), order.getSplitSubSalesOrder().getQuoteId(), false, order, store, "Shukran Burned Points Unlocked Due To Admin Cancellation", "");
						parentOrder.getSubSalesOrder().setShukranLocked(1);
					}
			}


			salesOrderRepository.saveAndFlush(parentOrder);
			splitSalesOrderRepository.saveAndFlush(order);
			cancelSellerOrdersByOMS(order.getSplitSellerOrders());

			// EAS to be implement for OMS cancel
			eASServiceImpl.publishCancelOrderToKafkaForSplitOrder(order, 0.0);

			omsOrderupdateresponse.setStatus(true);
			omsOrderupdateresponse.setStatusCode("200");
			omsOrderupdateresponse.setStatusMsg("Order Updated Successfully");
			omsOrderupdateresponse.setOrderId(request.getOrderId());
		}

		SalesOrderStatusHistory splitOrderhistory = new SalesOrderStatusHistory();
        splitOrderhistory.setParentId(parentOrder.getEntityId());
        splitOrderhistory.setSplitOrderId(order.getEntityId());
		if (null != request.getOrderStatus()) {
            splitOrderhistory.setStatus(request.getOrderStatus().getValue());
		} else {
            splitOrderhistory.setStatus(order.getStatus());
		}

        splitOrderhistory.setCustomerNotified(0);
        splitOrderhistory.setVisibleOnFront(1);
        splitOrderhistory.setComment(request.getComment());
        splitOrderhistory.setCreatedAt(new Timestamp(new Date().getTime()));

		salesOrderStatusHistoryRepository.saveAndFlush(splitOrderhistory);


		if (null != request.getComment() && shouldParentOrderClose) {
			SalesOrderStatusHistory history = new SalesOrderStatusHistory();
			history.setParentId(parentOrder.getEntityId());
			if (null != request.getOrderStatus()) {
				history.setStatus(request.getOrderStatus().getValue());
			} else {
				history.setStatus(parentOrder.getStatus());
			}

			history.setCustomerNotified(0);
			history.setVisibleOnFront(1);
			history.setComment(request.getComment());
			history.setCreatedAt(new Timestamp(new Date().getTime()));

			salesOrderStatusHistoryRepository.saveAndFlush(history);
		}

		return omsOrderupdateresponse;
	}

	private void cancelSellerOrdersByOMS(Set<SplitSellerOrder> sellerOrders) {
for (SplitSellerOrder sellerOrder : sellerOrders) {
			orderHelper.cancelSellerOrderAndItems(sellerOrder, OrderConstants.CANCELLED_BY_OMS, OrderConstants.WMS_STATUS_PUSH_TO_WMS, Collections.emptyMap());
		}
	}

	private SalesOrder setCreditMemo(SalesOrder order, String paymentMethod, BigDecimal storeCreditAmount, Stores store,
			BigDecimal cancelAmount, Map<String, BigDecimal> skumapList, List<SalesOrderItem> salesItemList) {
		SalesOrderGrid grid;
		// String memoIncrementId;
		// memoIncrementId = refundHelper.getIncrementId(order.getStoreId());
		grid = refundHelper.cancelOrderGrid(order, true, paymentMethod);
		// SalesCreditmemo memo = refundHelper.createCreditMemo(order, memoIncrementId,
		// store, cancelAmount,
		// storeCreditAmount,paymentMethod,skumapList,true,new HashMap<>());
		// refundHelper.createCancelCreditmemoItems(order,
		// memo,skumapList,salesItemList);
		// refundHelper.createCreditmemoComment(memo, storeCreditAmount);
		// refundHelper.createCreditmemoGrid(order, memo, memoIncrementId, grid,
		// order.getBaseGrandTotal());

		if (grid != null) {
			salesOrderGridRepository.saveAndFlush(grid);
		}
		CancelOrderRequest canCelReq = new CancelOrderRequest();
		canCelReq.setCustomerId(order.getCustomerId());
		canCelReq.setStoreId(order.getStoreId());
		canCelReq.setOrderId(order.getEntityId());
		order = refundHelper.cancelOrderObject(canCelReq, order, false, paymentMethod);
		order = refundHelper.cancelOrderItems(order, false);
		return order;
	}

	private SplitSalesOrder setCreditMemoForSplitOrder(SplitSalesOrder order, SalesOrder parentOrder, String paymentMethod, BigDecimal storeCreditAmount, Stores store,
			BigDecimal cancelAmount, Map<String, BigDecimal> skumapList, List<SalesOrderItem> salesItemList, boolean shouldParentOrderClose) {
		SalesOrderGrid grid = null;
		// String memoIncrementId;
		// memoIncrementId = refundHelper.getIncrementId(order.getStoreId());
		if(shouldParentOrderClose){
			grid = refundHelper.cancelOrderGrid(parentOrder, true, paymentMethod);
		}

		if (grid != null) {
			salesOrderGridRepository.saveAndFlush(grid);
		}
		CancelOrderRequest canCelReq = new CancelOrderRequest();
		canCelReq.setCustomerId(order.getCustomerId());
		canCelReq.setStoreId(order.getStoreId());
		canCelReq.setOrderId(order.getEntityId());

		order = refundHelper.cancelOrderObjectForSplitOrder(canCelReq, order, false, paymentMethod);
		order = refundHelper.cancelOrderItemsForSplitOrder(order, false);

		if(!shouldParentOrderClose){
			parentOrder = refundHelper.cancelOrderObjectV2(canCelReq, parentOrder, order, false, paymentMethod);
			parentOrder = refundHelper.cancelOrderItemsV2(parentOrder, order, false);
		} else {
			parentOrder = refundHelper.cancelOrderObject(canCelReq, parentOrder, false, paymentMethod);
			parentOrder = refundHelper.cancelOrderItems(parentOrder, false);
		}

		return order;
	}

	public OmsOrderupdateresponse omsUpdateOrderAddress(@Valid OrderupdateRequest request,
			SalesOrder order,
			SalesOrderGrid grid, Map<String, String> requestHeader) {

		LinkedHashMap<String, AddressChangeAttributes> addressChangeConfig = Constants.orderCredentials.getAddressChangeFlagMap();
		List<SalesOrderStatusHistory> salesOrderStatusHistory =  salesOrderStatusHistoryRepository.findByParentId(order.getEntityId());
		AddressChangeAttributes addressChangeAttributes = addressChangeConfig.get(request.getCountry());
		boolean terminalStatus = (null != addressChangeAttributes && salesOrderStatusHistory.stream().filter(e -> null!=e.getFinalStatus() && e.getFinalStatus().equalsIgnoreCase(addressChangeAttributes.getTerminalStatus())).count()>=1);
		if ((requestHeader != null && requestHeader.get("token") != null) && (Constants.CLOSED.equals(order.getStatus()) || Constants.DELIVERED.equals(order.getStatus())
				|| Constants.REFUNDED.equals(order.getStatus()) || terminalStatus)) {
			omsOrderupdateresponse.setStatus(false);
			omsOrderupdateresponse.setStatusCode("206");
			omsOrderupdateresponse.setStatusMsg("Address can not be changed for this order !");
			return omsOrderupdateresponse;
		}

		if (ObjectUtils.isEmpty(order) || CollectionUtils.isEmpty(order.getSalesOrderAddress())) {
			omsOrderupdateresponse.setStatus(false);
			omsOrderupdateresponse.setStatusCode("206");
			omsOrderupdateresponse.setStatusMsg("Invalid Order !");
			return omsOrderupdateresponse;
		}

		if (StringUtils.isNotBlank(request.getMobileNumber())) {

			String str = Constants.MOBILE_NUMBER_VALIDATION_REGEX;

			if (!Pattern.compile(str).matcher(request.getMobileNumber()).matches()) {

				omsOrderupdateresponse.setStatus(false);
				omsOrderupdateresponse.setStatusCode("208");
				omsOrderupdateresponse.setStatusMsg("invalid mobile number format!");
				return omsOrderupdateresponse;
			}

		}

		SalesOrderAddress existingAddress = order.getSalesOrderAddress().stream()
				.filter(e -> e.getEntityId().equals(request.getOrderAddressId()))
				.findFirst().orElse(null);

		if (ObjectUtils.isEmpty(existingAddress)) {
			omsOrderupdateresponse.setStatus(false);
			omsOrderupdateresponse.setStatusCode("205");
			omsOrderupdateresponse.setStatusMsg("Invalid Address ID");
			return omsOrderupdateresponse;
		}

		// if(StringUtils.isNotBlank(request.getFirstName())) {
		//
		// String str= Constants.CUSTOMER_NAME_VALIDATION_REGEX;
		//
		//
		// if (!Pattern.compile(str).matcher(request.getFirstName().trim()).matches()) {
		//
		// omsOrderupdateresponse.setStatus(false);
		// omsOrderupdateresponse.setStatusCode("208");
		// omsOrderupdateresponse.setStatusMsg("invalid Customer first Name");
		// return omsOrderupdateresponse;
		// }
		//
		// }if(StringUtils.isNotBlank(request.getLastName())) {
		//
		// String str= Constants.CUSTOMER_NAME_VALIDATION_REGEX;
		//
		//
		// if (!Pattern.compile(str).matcher(request.getLastName().trim()).matches()) {
		//
		// omsOrderupdateresponse.setStatus(false);
		// omsOrderupdateresponse.setStatusCode("208");
		// omsOrderupdateresponse.setStatusMsg("invalid Customer last Name!");
		// return omsOrderupdateresponse;
		// }
		//
		// }


		omsOrderupdateresponse = new OmsOrderupdateresponse();
		UpdateOrderResponse updateAddressResponse = new UpdateOrderResponse();
		SalesOrderAddress archivedAddress = existingAddress;

		StringBuilder sbAddress = new StringBuilder();
		if (StringUtils.isNotBlank(request.getBuildingNumber()))
			sbAddress.append(request.getBuildingNumber()).append("\n");
		if (StringUtils.isNotBlank(request.getStreetAddress()))
			sbAddress.append(request.getStreetAddress()).append("\n");
        if (StringUtils.isNotBlank(request.getUnitNumber()))
            sbAddress.append(request.getUnitNumber()).append("\n");
        if (StringUtils.isNotBlank(request.getPostalCode()))
            sbAddress.append(request.getPostalCode()).append("\n");
        if (StringUtils.isNotBlank(request.getShortAddress()))
            sbAddress.append(request.getShortAddress());

		// Call Navik address update API and customer address update API
		List<SalesShipmentTrack> salesShipmentTracks = shipmentTrackerRepository.findByOrderId(request.getOrderId());
		SalesShipmentTrack salesShipmentTrack = salesShipmentTracks!=null && !salesShipmentTracks.isEmpty() ? salesShipmentTracks.get(0) : null;
		if (requestHeader != null && requestHeader.get("token") != null &&
				(null != salesShipmentTrack && salesShipmentTrack.getTitle() != null)) {

			NavikAddressUpdateDTO navikAddressUpdateDTO = getNavikAddressUpdateDTO(request, order, sbAddress, salesShipmentTrack.getTrackNumber());

			NavikAddressUpdateResponse navikAddressUpdateResponse = new NavikAddressUpdateResponse();
			try {
				navikAddressUpdateResponse = salesOrderServiceV3.updateNavikAddress(navikAddressUpdateDTO);

				LOGGER.info("Navik address update response :: " + navikAddressUpdateResponse);
				if (!navikAddressUpdateResponse.getStatus()) {
					LOGGER.info("Error while changing customer address in Navik !");
					omsOrderupdateresponse.setStatus(false);
					omsOrderupdateresponse.setStatusCode("206");
					omsOrderupdateresponse.setStatusMsg("Error while changing order address in navik !");
					return omsOrderupdateresponse;
				}
			} catch (JsonProcessingException e) {
				LOGGER.info("Error in json parsing! " + e.getMessage());
				omsOrderupdateresponse.setStatus(false);
				omsOrderupdateresponse.setStatusCode("206");
				omsOrderupdateresponse.setStatusMsg("Error while changing order address in navik !");
				return omsOrderupdateresponse;
			}
		}

		SalesOrder salesOrder = new SalesOrder();
		existingAddress.setCustomerAddressId(request.getAddressId());
		if (StringUtils.isNotBlank(request.getCity()) && request.getCity().contains("\\"))
			existingAddress.setCity(request.getCity().replace("\\", ""));
		else
			existingAddress.setCity(request.getCity());


		existingAddress.setStreet(request.getStreetAddress());

		// API-3952
		if (StringUtils.isNotBlank(request.getStreetAddress()))
			existingAddress.setStreetActual(request.getStreetAddress());

		if (StringUtils.isNotBlank(request.getArea()))
			existingAddress.setArea(request.getArea());
		if (StringUtils.isNotBlank(request.getBuildingNumber()))
			existingAddress.setBuildingNumber(request.getBuildingNumber());
		existingAddress.setNearestLandmark(request.getLandMark());
        existingAddress.setKsaAddressComplaint(request.getKsaAddressComplaint());
        existingAddress.setShortAddress(request.getShortAddress());
        existingAddress.setPostalCode(request.getPostalCode());
        existingAddress.setKsaAddressComplaint(request.getKsaAddressComplaint());
		existingAddress.setRegion(request.getRegion());
		existingAddress.setRegionId(request.getRegionId());
		existingAddress.setTelephone(request.getMobileNumber());
		existingAddress.setFirstname(request.getFirstName());
		existingAddress.setLastname(request.getLastName());
        existingAddress.setUnitNumber(request.getUnitNumber());
        existingAddress.setShortAddress(request.getShortAddress());
        existingAddress.setPostalCode(request.getPostalCode());
        existingAddress.setKsaAddressComplaint(request.getKsaAddressComplaint());
	existingAddress.setBuildingNumber(request.getBuildingNumber());		order.getSalesOrderAddress().add(existingAddress);
			salesOrder = salesOrderRepository.saveAndFlush(order);

		if (ObjectUtils.isNotEmpty(grid)) {
			grid.setBillingName(request.getFirstName() + " " + request.getLastName());
			grid.setShippingName(request.getFirstName() + " " + request.getLastName());
			salesOrderGridRepository.saveAndFlush(grid);
		}




			if(requestHeader!=null && requestHeader.get("token")!=null && salesOrder!=null && salesOrder.getEntityId()!=null) {

				// Inserting the address change log
				createAddressChangeLog(archivedAddress, request.getOrderId());

				// Prepare the headers for the Customer address update API
				HttpHeaders headers = new HttpHeaders();
				headers.setContentType(MediaType.APPLICATION_JSON);
				requestHeader.forEach(headers::add);

				// Create the request body for the Customer address update API
				CustomerAddrees customerAddress = new CustomerAddrees();
				customerAddress.setCustomerId(request.getCustomerId());
				customerAddress.setAddressId(request.getAddressId());
				customerAddress.setCity(request.getCity());
				customerAddress.setRegion(request.getRegion());
				customerAddress.setRegionId(request.getRegionId());
				customerAddress.setArea(request.getArea());
				customerAddress.setLandMark(request.getLandMark());
				customerAddress.setBuildingNumber(request.getBuildingNumber());
				customerAddress.setStreetAddress(request.getStreetAddress());
				customerAddress.setMobileNumber(request.getMobileNumber());
				customerAddress.setFirstName(request.getFirstName());
				customerAddress.setLastName(request.getLastName());
				customerAddress.setDefaultAddress(request.isDefaultAddress());
				customerAddress.setCountry(request.getCountry());
				customerAddress.setFax(request.getFax());
				customerAddress.setPostCode(request.getPostCode());
				customerAddress.setTelephone(request.getTelephone());
				customerAddress.setStoreId(request.getStoreId());
				customerAddress.setEmail(request.getEmail());
				customerAddress.setLatitude(request.getLatitude());
				customerAddress.setLongitude(request.getLongitude());
				customerAddress.setFormattedAddress(request.getFormattedAddress());
				customerAddress.setIsMobileVerified(request.getIsMobileVerified());
				customerAddress.setSource(request.getSource());
				customerAddress.setClientVersion(request.getClientVersion());
				customerAddress.setIsSignUpOtpEnabled(request.getIsSignUpOtpEnabled());
                customerAddress.setUnitNumber(request.getUnitNumber());
                customerAddress.setShortAddress(request.getShortAddress());
                customerAddress.setPostalCode(request.getPostalCode());
                customerAddress.setKsaAddressComplaint(request.getKsaAddressComplaint());
            customerAddress.setBuildingNumber(request.getBuildingNumber());

				// Populate customerAddress with the necessary data from the order
				HttpEntity<CustomerAddrees> requestEntity = new HttpEntity<>(customerAddress, headers);
				// Call the Customer address update API
				// Prepare to call the Customer address update API
				GetOrderConsulValues orderCredentials = Constants.orderCredentials;
				OrderKeyDetails orderKeyDetails = orderCredentials.getOrderDetails();
				String customerAddressUpdateUrl = orderKeyDetails.getCustomerServiceBaseUrl() + "/rest/customer/auth/address";
				LOGGER.info("Customer address update request url :: " + customerAddressUpdateUrl);
				LOGGER.info("Customer address update request body :: " + requestEntity.getBody());

				try {
					restTemplate.put(customerAddressUpdateUrl, requestEntity);
				} catch (RestClientException e) {
					// Handle exception
					LOGGER.info("Error while updating Customer address :: " + e.getMessage());
					omsOrderupdateresponse.setStatus(false);
					omsOrderupdateresponse.setStatusCode("206");
					omsOrderupdateresponse.setStatusMsg("Error while changing customer address !");
				}
			}


			if(salesOrder.getEntityId()!=null) {
				Map<String, String> addressChangeMailPatterns = MailPatternConfigs.getMailPatternMap(KEY_ADDRESS_CHANGE_MAIL + "Content");
				Map<String, String> addressChangeSubject = MailPatternConfigs.getMailPatternMap(KEY_ADDRESS_CHANGE_MAIL + "Subject");

				String baseUrl = ServiceConfigs.getUrl("websiteHost");
				String storeLink = baseUrl + "/" + request.getCountry().toLowerCase() + "/" + request.getLangCode();
				String subject = addressChangeSubject.get(request.getLangCode());
				String content = addressChangeMailPatterns.get(request.getLangCode())
						.replace("{{storeLink}}", storeLink)
						.replace("[User Name]", request.getFirstName() + " " + request.getLastName())
						.replace("[Order ID]", String.valueOf(salesOrder.getIncrementId()))
						.replace("[Updated Address]", (request.getBuildingNumber() != null ? request.getBuildingNumber() + ", ": "")
								+ request.getStreetAddress() + ", " + request.getLandMark() + ", "
								+ request.getArea() + ", " + request.getCity() + ", " + request.getRegion())
						.replace("[Updated Phone Number]", request.getMobileNumber())
						.replace("[Platform Name]", "Styli");

				if (StringUtils.isNotBlank(request.getEmail())) {
					emailHelper.sendEmail(
							request.getEmail(), request.getFirstName() + " " + request.getLastName(), content, EmailHelper.CONTENT_TYPE_HTML, subject, request.getLangCode());
				} else {
					LOGGER.info("Email is blank, not sending email");
				}
				int unicode = ("ar".equalsIgnoreCase(request.getLangCode())) ? 1 : 0;
				Map<String, String> addressChangeSmsBody = MailPatternConfigs.getMailPatternMap(KEY_ADDRESS_CHANGE_SMS);
				String smsBody = addressChangeSmsBody.get(request.getLangCode());
				smsBody = smsBody.replace("[Order ID]", String.valueOf(salesOrder.getIncrementId()))
						.replace("[Updated Address]", (request.getBuildingNumber() != null ? request.getBuildingNumber() + ", ": "")
								+request.getStreetAddress()+", "
								+request.getLandMark()+", "
								+request.getArea()+", "
								+request.getCity()+", "
								+request.getRegion()+", ")
						.replace("[Phone Number]",request.getMobileNumber());
				if (StringUtils.isNotBlank(request.getMobileNumber())) {
					smsHelper.sendSMS(request.getMobileNumber(), smsBody, unicode);
				} else {
					LOGGER.info("Mobile number is blank, not sending SMS");
				}
			}
			updateAddressResponse.setCustomerAddressId(request.getAddressId());
        	updateAddressResponse.setArea(request.getArea());
			updateAddressResponse.setFirstName(request.getFirstName());
			updateAddressResponse.setLastName(request.getLastName());
			updateAddressResponse.setOrderId(request.getOrderId());
			updateAddressResponse.setOrderAddressId(request.getOrderAddressId());
			updateAddressResponse.setCity(request.getCity());
			updateAddressResponse.setRegion(request.getRegion());
			updateAddressResponse.setRegionId(request.getRegionId());
			updateAddressResponse.setMobileNumber(request.getMobileNumber());
			updateAddressResponse.setStreetAddress(request.getStreetAddress());
			updateAddressResponse.setCountry(request.getCountry());
			updateAddressResponse.setStoreId(request.getStoreId());
			updateAddressResponse.setTelephone(request.getTelephone());
			updateAddressResponse.setBuildingNumber(request.getBuildingNumber());
			updateAddressResponse.setLandMark(request.getLandMark());
            updateAddressResponse.setUnitNumber(request.getUnitNumber());
            updateAddressResponse.setShortAddress(request.getShortAddress());
            updateAddressResponse.setPostalCode(request.getPostalCode());
            updateAddressResponse.setKsaAddressComplaint(request.getKsaAddressComplaint());
        updateAddressResponse.setBuildingNumber(request.getBuildingNumber());


		omsOrderupdateresponse.setResponse(updateAddressResponse);
		omsOrderupdateresponse.setStatus(true);
		omsOrderupdateresponse.setStatusCode("200");
		omsOrderupdateresponse.setStatusMsg("Order Updated Successfully");
		omsOrderupdateresponse.setOrderId(request.getOrderId());
		return omsOrderupdateresponse;
	}

	@NotNull
	private static NavikAddressUpdateDTO getNavikAddressUpdateDTO(OrderupdateRequest request, SalesOrder order, StringBuilder sbAddress, String awb) {
		NavikAddressUpdateDTO navikAddressUpdateDTO = new NavikAddressUpdateDTO();
		navikAddressUpdateDTO.setAwb(awb);
		navikAddressUpdateDTO.setDeliveryType("FORWARD");
		navikAddressUpdateDTO.setArea(request.getArea());
		navikAddressUpdateDTO.setAddress(sbAddress.toString());
		navikAddressUpdateDTO.setPhoneCode(null != request.getMobileNumber() && !request.getMobileNumber().isBlank() ? request.getMobileNumber().split(" ")[0] : null);
		navikAddressUpdateDTO.setPhoneNumber(null != request.getMobileNumber() && !request.getMobileNumber().isBlank() ? request.getMobileNumber().split(" ")[1] : null);
		navikAddressUpdateDTO.setClient("styli");
		return navikAddressUpdateDTO;
	}

	/**
	 * This defines which store time-zone should be picked to be display date on OMS
	 * - UI
	 * 
	 * @return
	 */
	private Stores findStore() {
		int storeId = "IN".equalsIgnoreCase(region) ? 51 : 1;
		return Constants.getStoresList().stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(storeId)).findAny()
				.orElse(null);
	}

	private void createAddressChangeLog(SalesOrderAddress existingAddress, Integer orderId) {
		AddressChangeHistory addressChangeHistory = new AddressChangeHistory();
		addressChangeHistory.setAddressId(existingAddress.getCustomerAddressId());
		addressChangeHistory.setOrderId(orderId);
		addressChangeHistory.setCity(existingAddress.getCity());
		addressChangeHistory.setRegion(existingAddress.getRegion());
		addressChangeHistory.setRegionId(existingAddress.getRegionId());
		addressChangeHistory.setArea(existingAddress.getArea());
		addressChangeHistory.setNearestLandmark(existingAddress.getNearestLandmark());
        addressChangeHistory.setKsaAddressComplaint(existingAddress.getKsaAddressComplaint());
        addressChangeHistory.setShortAddress(existingAddress.getShortAddress());
        addressChangeHistory.setUnitNumber(existingAddress.getUnitNumber());
        addressChangeHistory.setPostalCode(existingAddress.getPostalCode());
		addressChangeHistory.setStreet(existingAddress.getStreet());
		addressChangeHistory.setTelephone(existingAddress.getTelephone());
		addressChangeHistory.setFirstname(existingAddress.getFirstname());
		addressChangeHistory.setLastname(existingAddress.getLastname());
		addressChangeHistory.setCountryId(existingAddress.getCountryId());
		addressChangeHistory.setOrderAddressId(existingAddress.getEntityId());
		addressChangeHistory.setEmail(existingAddress.getEmail());
		addressChangeHistory.setBuildingNumber(existingAddress.getBuildingNumber());
		addressChangeHistory.setPostcode(existingAddress.getPostcode());
		addressChangeHistory.setFax(existingAddress.getFax());
		addressChangeHistory.setLongitude(existingAddress.getLongitude());
		addressChangeHistory.setLatitude(existingAddress.getLatitude());
        addressChangeHistory.setUnitNumber(existingAddress.getUnitNumber());
        addressChangeHistory.setShortAddress(existingAddress.getShortAddress());
        addressChangeHistory.setPostalCode(existingAddress.getPostalCode());
        addressChangeHistory.setKsaAddressComplaint(existingAddress.getKsaAddressComplaint());
		addressChangeHistory.setStreetActual(existingAddress.getStreetActual());

		addressChangeHistory.setLastUpdatedDate(new Timestamp(new Date().getTime()));

		addressChangeHistoryRepository.saveAndFlush(addressChangeHistory);
	}

	private SplitOrderDTO convertSplitOrder(int splitCount,SplitSalesOrder splitOrder,OrderEntityConverter orderEntityConverter,OrderResponse orderResponse,Map<String, ProductResponseBody> productsFromMulin,String xClientVersion) {
			SplitOrderDTO dto = new SplitOrderDTO();
			if (splitOrder.getSplitSubSalesOrder() != null) {
				dto.setQualifiedPurchase(splitOrder.getSplitSubSalesOrder().getQualifiedPurchase());
			}
			if(null != splitOrder.getSplitSubSalesOrder() && null != splitOrder.getSplitSubSalesOrder().getRatingStatus()) {
				dto.setIsRated(null != splitOrder.getSplitSubSalesOrder().getRatingStatus() && "1".equals(splitOrder.getSplitSubSalesOrder().getRatingStatus()) ? true : false);
			}
			dto.setSplitOrderId(splitOrder.getEntityId());
			dto.setSplitOrderCount(splitCount);
			dto.setSplitIncrementId(splitOrder.getIncrementId());
			String shipmentModeValue = orderEntityConverter.getShipmentModeOfOrder(splitOrder.getShipmentMode(),splitOrder.getStoreId());
			dto.setShipmentMode(shipmentModeValue);
			dto.setEmail(splitOrder.getCustomerEmail());
			// -------- SPLIT SELLER ORDER OVERALL STATUS LOGIC --------
		    String overallStatus = orderEntityConverter.resolveOverallStatusFromSplitSellerOrders(
				splitOrder.getEntityId(),splitOrder.getStatus(),splitOrder.getShipmentMode()
			);
			// Set final status based on split seller orders
			dto.setStatus(overallStatus);
		    SalesOrderStatusLabelPK key = new SalesOrderStatusLabelPK();
		    key.setStatus(overallStatus);
		    key.setStoreId(splitOrder.getStoreId());
			SalesOrderStatusLabel label = salesOrderStatusLabelRepository.findById(key);
			if (label != null) {
				dto.setStatusLabel(label.getLabel());
			} else {
				dto.setStatusLabel(overallStatus);
			}
			dto.setShippingDescription(splitOrder.getShippingDescription());
			dto.setStoreId(String.valueOf(splitOrder.getStoreId()));
			dto.setCustomerId(String.valueOf(splitOrder.getCustomerId()));
			dto.setDiscountAmount(splitOrder.getDiscountAmount().toString());
			dto.setGrandTotal(splitOrder.getGrandTotal().toString());
			dto.setBaseGrandTotal(splitOrder.getBaseGrandTotal().toString());
			dto.setShippingAmount(splitOrder.getShippingAmount().toString());
		    dto.setCodCharges(parseNullStr(splitOrder.getCashOnDeliveryFee()));
			dto.setSubtotal(splitOrder.getSubtotal().toString());
			dto.setCurrency(splitOrder.getOrderCurrencyCode());
			dto.setItemCount(String.valueOf(splitOrder.getTotalItemCount()));
			dto.setBillingAddressId(String.valueOf(splitOrder.getBillingAddressId()));
			if (null != splitOrder.getSplitSubSalesOrder() &&
				null != splitOrder.getSplitSubSalesOrder().getExternalQuoteId()) {
				dto.setQuoteId(splitOrder.getSplitSubSalesOrder().getExternalQuoteId().toString());
			}
			dto.setShippingAddressId(String.valueOf(splitOrder.getShippingAddressId()));
			dto.setIncrementId(splitOrder.getIncrementId());
			dto.setShippingMethod(splitOrder.getShippingMethod());
			dto.setCreatedAt(splitOrder.getCreatedAt().toString());
			dto.setUpdatedAt(splitOrder.getUpdatedAt().toString());
			updateEstimatedDeliveryTimeOfSplitOrder(splitOrder,splitOrder.getSplitSalesOrderItems().stream().toList(),dto);
			orderEntityConverter.addOtherinformationV2(splitOrder,orderResponse,true,productsFromMulin);
			dto.setSubtotal(orderResponse.getSubtotal());
			dto.setDiscountAmount(orderResponse.getDiscountAmount());
			orderEntityConverter.configureOrderTrackingV2(splitOrder,orderResponse);
			orderEntityConverter.setSplitOrderStatusCallToActionFlag(orderResponse, splitOrder,overallStatus);
			addCancelDateAndRTO(splitOrder, dto,orderEntityConverter);
			dto.setDeliveredAt(orderEntityConverter.convertTimezone(splitOrder.getDeliveredAt(), splitOrder.getStoreId()));
     		dto.setTrackings(orderResponse.getTrackings());
	    	dto.setProducts(orderResponse.getProducts());
			dto.setCancelProducts(orderResponse.getCancelProducts());
		    getSplitOrderInvoices(dto, splitOrder);
		    dto.setCallToActionFlag(orderResponse.getCallToActionFlag());
		    Map<String, Integer> statusStatesMap = staticComponents.getStatusStepMap();
			if (statusStatesMap != null) {
				dto.setStatusStepValue(OrderConstants.SPLIT_ORDER_CANCELLED_ORDER_STATUS.equalsIgnoreCase(overallStatus)?statusStatesMap.get(OrderConstants.CLOSED_ORDER_STATUS):statusStatesMap.get(overallStatus));
			}

			Map<String, Integer> statusColorsMap = staticComponents.getStatusColorsStepMap();
			if (statusColorsMap != null) {
				dto.setStatusColorStepValue(OrderConstants.SPLIT_ORDER_CANCELLED_ORDER_STATUS.equalsIgnoreCase(overallStatus)?statusColorsMap.get(OrderConstants.CLOSED_ORDER_STATUS):statusColorsMap.get(overallStatus));
			}

			dto.setImportFeesAmount(parseNullStr(splitOrder.getImportFee()));
			if (null != splitOrder.getSplitSubSalesOrder() && null != splitOrder.getSplitSubSalesOrder().getDonationAmount()) {
				dto.setDonationAmount(parseNullStr(splitOrder.getSplitSubSalesOrder().getDonationAmount()));
			}
		    dto.setShippingUrl(orderResponse.getShippingUrl());
		    List<RtoAutoRefund> rtoAutoRefund = orderHelper.getSplitOrderRtoAutoRefundList(splitOrder);
			if (Objects.nonNull(rtoAutoRefund) && !rtoAutoRefund.isEmpty()) {
				dto.setRto(true);
				dto.setRtoStatus(rtoAutoRefund.get(0).getStatus());
				dto.setRtoRefundAt(orderEntityConverter.convertTimezone(rtoAutoRefund.get(0).getRefundAt(), splitOrder.getStoreId()));
				dto.setRtoRefundAmount(parseNullStr(rtoAutoRefund.get(0).getRefundAmount()));
			}else if("rto".equals(splitOrder.getStatus())){
				dto.setRto(true);
				dto.setRtoStatus("pending");
			}
//			dto.setArchived(splitOrder.getArchived());
//			dto.setTaxPercent(splitOrder.getTaxPercent());
		// EAS coins added for response order details
		if(null != splitOrder.getSplitSubSalesOrder() && null != splitOrder.getSplitSubSalesOrder().getEasCoins()) {
			dto.setSpendCoin(splitOrder.getSplitSubSalesOrder().getEasCoins());
			dto.setCoinToCurrency(parseNullStr(splitOrder.getSplitSubSalesOrder().getEasValueInCurrency().toString()));
			dto.setCoinToBaseCurrency(parseNullStr(splitOrder.getSplitSubSalesOrder().getEasValueInBaseCurrency().toString()));
		}
		if(null != splitOrder.getSplitSubSalesOrder() && null != splitOrder.getSplitSubSalesOrder().getRetryPayment()
				&& splitOrder.getSplitSubSalesOrder().getRetryPayment().equals(1)) {
			dto.setCanRetryPayment(true);
		}
		Pair<Integer, String> rmaData = calculateSplitOrderRmaCountAndReturnFee(
				splitOrder.getEntityId(),
				splitOrder.getStoreId(),
				splitOrder.getSplitSubSalesOrder() != null ? splitOrder.getSplitSubSalesOrder().getTotalShukranCoinsBurned() : null,
				splitOrder.getOrderCurrencyCode(),
				xClientVersion
		);
		dto.setRmaCount(rmaData.getLeft());
		dto.setReturnFee(rmaData.getRight());
		dto.setIsSecondRefundTagOn(true); // Optional: based on version logic if needed


		return dto;

	}

	private List<SplitOrderDTO> splitOrderInRuntime(SalesOrder salesOrder,OrderEntityConverter orderEntityConverter,OrderResponse orderResponse,Map<String, ProductResponseBody> productsFromMulin,String xClientVersion) {

		List<SplitOrderDTO> splitOrderDTOS = new ArrayList<>();
		// ---- helpers -----------------------------------------------------------
		final BigDecimal ZERO = BigDecimal.ZERO;
		// Collect all leaf (non-configurable) items
		List<SalesOrderItem> allItems = Optional.ofNullable(salesOrder.getSalesOrderItem()).orElse(Collections.emptySet())
				.stream()
				.filter(item -> !OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE.equals(item.getProductType()))
				.collect(Collectors.toList());
		BigDecimal totalOrderRowTotal = sum(allItems, SalesOrderItem::getRowTotal);

		// ---- grouping key ------------------------------------------------------
		// If you later need to split by SKU, FFM, Seller Location, Delivery Flag, Last Mile, FC,
		// extend the key builder below (kept backward-compatible with shipmentType-only split).
		Function<SalesOrderItem, String> keyOf = item -> {
			String shipmentType = ObjectUtils.defaultIfNull(item.getShipmentType(), Constants.LOCAL_SHIPMENT);
			// Example to extend: concat more attributes with '|'
			return shipmentType + "|";
		};
		Map<String, List<SalesOrderItem>> groupedItems = allItems.stream()
				.collect(Collectors.groupingBy(keyOf));
		int splitCount = 0;
		Map<String, Integer> shipmentTypeCounter = new HashMap<>();
		for (Map.Entry<String, List<SalesOrderItem>> entry : groupedItems.entrySet()) {
			splitCount++;
			String splitKey = entry.getKey();
			List<SalesOrderItem> innerItems = entry.getValue();
			BigDecimal splitRowTotal = sum(innerItems, SalesOrderItem::getRowTotal);

			String shipmentMode = Optional.ofNullable(splitKey)
					.filter(s -> s.contains("|"))
					.map(s -> s.split("\\|"))
					.filter(arr -> arr.length > 0)
					.map(arr -> arr[0])
					.orElse("");
			String prefix = org.apache.commons.lang3.StringUtils.isNotBlank(shipmentMode) ? shipmentMode.substring(0, 1).toUpperCase() : "L";
			int count = shipmentTypeCounter.getOrDefault(prefix, 0) + 1;
			shipmentTypeCounter.put(prefix, count);
			// ---- compute group totals -----------------------------------------
			BigDecimal groupDiscount = innerItems.stream().map(i -> safe(i.getDiscountAmount().setScale(4, RoundingMode.HALF_UP)
					.setScale(4, RoundingMode.HALF_UP))).reduce(ZERO, BigDecimal::add);
			int groupQty = innerItems.stream()
					.filter(Objects::nonNull)
					.mapToInt(i -> i.getQtyOrdered() == null ? 0 : i.getQtyOrdered().intValue())
					.sum();
			BigDecimal groupSubtotal = innerItems.stream().map(i -> safe(i.getPriceInclTax().multiply(i.getQtyOrdered()).setScale(4, RoundingMode.HALF_UP)
					.setScale(4, RoundingMode.HALF_UP))).reduce(ZERO, BigDecimal::add);
			//SFP-1104 COD fee changes for Order service
			// As global cash on delivery is setting now, no need to split cod charges
			//BigDecimal codAlloc  = calculateSplitValue(totalOrderRowTotal,safe(salesOrder.getCashOnDeliveryFee()),splitRowTotal);
			BigDecimal importAlloc = calculateSplitValue(totalOrderRowTotal,safe(salesOrder.getImportFee()),splitRowTotal);
			BigDecimal amstorecreditAlloc = calculateSplitValue(totalOrderRowTotal,safe(salesOrder.getAmstorecreditAmount()),splitRowTotal);
			BigDecimal easCoinsAlloc = BigDecimal.ZERO;
			if (salesOrder.getSubSalesOrder() != null) {
				Integer coins = salesOrder.getSubSalesOrder().getEasCoins(); // Integer (nullable)
				BigDecimal coinsBD = coins == null ? BigDecimal.ZERO : BigDecimal.valueOf(coins.longValue());
				easCoinsAlloc = calculateSplitValue(totalOrderRowTotal, coinsBD, splitRowTotal)
						.setScale(0, RoundingMode.HALF_UP);
			}
			BigDecimal easCurrencyAlloc     =null!=salesOrder.getSubSalesOrder()?calculateSplitValue(totalOrderRowTotal,safe(salesOrder.getSubSalesOrder().getEasValueInCurrency()),splitRowTotal):BigDecimal.ZERO;
			BigDecimal easBaseCurrencyAlloc = null!=salesOrder.getSubSalesOrder()?calculateSplitValue(totalOrderRowTotal,safe(salesOrder.getSubSalesOrder().getEasValueInBaseCurrency()),splitRowTotal):BigDecimal.ZERO;
			BigDecimal shukranCoinsBurnedAlloc = null!=salesOrder.getSubSalesOrder()?calculateSplitValue(totalOrderRowTotal,safe(salesOrder.getSubSalesOrder().getTotalShukranCoinsBurned()),splitRowTotal):BigDecimal.ZERO;
			BigDecimal donationAmountAlloc =null!=salesOrder.getSubSalesOrder()?calculateSplitValue(totalOrderRowTotal,safe(salesOrder.getSubSalesOrder().getDonationAmount()),splitRowTotal):BigDecimal.ZERO;
			BigDecimal groupGrandTotal    = groupSubtotal.subtract(groupDiscount).subtract(amstorecreditAlloc)
					.add(Constants.GLOBAL_SHIPMENT.equalsIgnoreCase(shipmentMode) ? salesOrder.getGlobalShippingAmount():salesOrder.getShippingAmount()).add(importAlloc).setScale(4, RoundingMode.HALF_UP);

			SplitOrderDTO dto = new SplitOrderDTO();
			if (salesOrder.getSubSalesOrder() != null) {
				dto.setQualifiedPurchase(salesOrder.getSubSalesOrder().getQualifiedPurchase());
			}

			dto.setSplitOrderId(salesOrder.getEntityId());
			dto.setSplitOrderCount(splitCount);
			dto.setSplitIncrementId(salesOrder.getIncrementId() + "-" + prefix + shipmentTypeCounter.get(prefix));
			dto.setIncrementId(salesOrder.getIncrementId() + "-" + prefix + shipmentTypeCounter.get(prefix));
			String shipmentModeValue = orderEntityConverter.getShipmentModeOfOrder(shipmentMode,salesOrder.getStoreId());
			dto.setShipmentMode(shipmentModeValue);
			dto.setShippingAmount(Constants.GLOBAL_SHIPMENT.equalsIgnoreCase(shipmentMode) ? salesOrder.getGlobalShippingAmount()+"":salesOrder.getShippingAmount()+"");
			dto.setEmail(salesOrder.getCustomerEmail());
			if(null != salesOrder.getSubSalesOrder() && null != salesOrder.getSubSalesOrder().getRatingStatus()) {
				dto.setIsRated(null != salesOrder.getSubSalesOrder().getRatingStatus() && "1".equals(salesOrder.getSubSalesOrder().getRatingStatus()) ? true : false);
			}
			dto.setStatus(salesOrder.getStatus());
			SalesOrderStatusLabelPK key = new SalesOrderStatusLabelPK();
			key.setStatus(salesOrder.getStatus());
			key.setStoreId(salesOrder.getStoreId());
			SalesOrderStatusLabel label = salesOrderStatusLabelRepository.findById(key);
			if (label != null) {
				dto.setStatusLabel(label.getLabel());
			} else {
				dto.setStatusLabel(salesOrder.getStatus());
			}
			dto.setShippingDescription(salesOrder.getShippingDescription());
			dto.setStoreId(String.valueOf(salesOrder.getStoreId()));
			dto.setCustomerId(String.valueOf(salesOrder.getCustomerId()));
			// ---- per-split financials (strings, following your original DTO usage) ----
			dto.setDiscountAmount(groupDiscount.setScale(4, RoundingMode.HALF_UP).toString());
			dto.setGrandTotal(groupGrandTotal.toString());
			dto.setBaseGrandTotal(groupGrandTotal.toString());
			//SFP-1104 COD fee changes for Order service
			dto.setCodCharges(Constants.GLOBAL_SHIPMENT.equalsIgnoreCase(shipmentMode) ? parseNullStr(salesOrder.getGlobalCashOnDeliveryFee()):parseNullStr(salesOrder.getCashOnDeliveryFee()));
			dto.setSubtotal(groupSubtotal.setScale(4, RoundingMode.HALF_UP).toString());
			dto.setCurrency(salesOrder.getOrderCurrencyCode());
			dto.setItemCount(String.valueOf(groupQty));
			dto.setBillingAddressId(String.valueOf(salesOrder.getBillingAddressId()));
			if (null != salesOrder.getSubSalesOrder() &&
					null != salesOrder.getSubSalesOrder().getExternalQuoteId()) {
				dto.setQuoteId(salesOrder.getSubSalesOrder().getExternalQuoteId().toString());
			}
			dto.setShippingAddressId(String.valueOf(salesOrder.getShippingAddressId()));
			dto.setShippingMethod(salesOrder.getShippingMethod());
			dto.setCreatedAt(salesOrder.getCreatedAt().toString());
			dto.setUpdatedAt(salesOrder.getUpdatedAt().toString());
			// Estimated delivery time - split order level
			updateEstimatedDeliveryTime(salesOrder,innerItems, dto);
			orderEntityConverter.addOtherinformation(salesOrder,orderResponse,true,productsFromMulin);
			orderEntityConverter.configureOrderTracking(salesOrder,orderResponse);
			orderEntityConverter.setOrderStatusCallToActionFlag(orderResponse, salesOrder);
			dto.setTrackings(orderResponse.getTrackings());
			// Collect SKUs from SalesOrderItem list
			Set<String> innerSkus = innerItems.stream()
					.map(SalesOrderItem::getSku)
					.filter(Objects::nonNull)
					.collect(Collectors.toSet());
			List<OrderItem> groupProducts = Optional.ofNullable(orderResponse.getProducts()).orElse(Collections.emptyList())
					.stream()
					.filter(p -> p.getSku() != null && innerSkus.contains(p.getSku()))
					.collect(Collectors.toList());
			List<OrderItem> groupCancelProducts = Optional.ofNullable(orderResponse.getCancelProducts()).orElse(Collections.emptyList())
					.stream()
					.filter(p -> p.getSku() != null && innerSkus.contains(p.getSku()))
					.collect(Collectors.toList());
			dto.setProducts(groupProducts);
			dto.setCancelProducts(groupCancelProducts);
			getOrderInvoices(dto, salesOrder);
			dto.setCallToActionFlag(orderResponse.getCallToActionFlag());
			Map<String, Integer> statusStatesMap = staticComponents.getStatusStepMap();
			if (statusStatesMap != null) {
				dto.setStatusStepValue(statusStatesMap.get(salesOrder.getStatus()));
			}

			Map<String, Integer> statusColorsMap = staticComponents.getStatusColorsStepMap();
			if (statusColorsMap != null) {
				dto.setStatusColorStepValue(statusColorsMap.get(salesOrder.getStatus()));
			}
			// Import fee (pro-rated)
			dto.setImportFeesAmount(importAlloc.toString());
			// Donation amount if present at sub-split level (keep null-safe)
			if (salesOrder.getSubSalesOrder() != null && salesOrder.getSubSalesOrder().getDonationAmount() != null) {
				dto.setDonationAmount(donationAmountAlloc.toString());
			}
			dto.setShippingUrl(orderResponse.getShippingUrl());
			dto.setCanceledAt(orderResponse.getCanceledAt());
			dto.setDeliveredAt(orderEntityConverter.convertTimezone(salesOrder.getDeliveredAt(), salesOrder.getStoreId()));
			List<RtoAutoRefund> rtoAutoRefund = orderHelper.getRtoAutoRefundList(salesOrder);
			if (Objects.nonNull(rtoAutoRefund) && !rtoAutoRefund.isEmpty()) {
				dto.setRto(true);
				dto.setRtoStatus(rtoAutoRefund.get(0).getStatus());
				dto.setRtoRefundAt(orderEntityConverter.convertTimezone(rtoAutoRefund.get(0).getRefundAt(), salesOrder.getStoreId()));
				dto.setRtoRefundAmount(parseNullStr(rtoAutoRefund.get(0).getRefundAmount()));
			}else if("rto".equals(salesOrder.getStatus())){
				dto.setRto(true);
				dto.setRtoStatus("pending");
			}
//			dto.setArchived(splitOrder.getArchived());
//			dto.setTaxPercent(splitOrder.getTaxPercent());
			// EAS coins (pro-rated)
			if (salesOrder.getSubSalesOrder() != null && salesOrder.getSubSalesOrder().getEasCoins() != null) {
				dto.setSpendCoin(easCoinsAlloc.intValue());
				dto.setCoinToCurrency(easCurrencyAlloc.toString());
				dto.setCoinToBaseCurrency(easBaseCurrencyAlloc.toString());
			}
			if(null !=  salesOrder.getSubSalesOrder() && null !=  salesOrder.getSubSalesOrder().getRetryPayment()
					&&  salesOrder.getSubSalesOrder().getRetryPayment().equals(1)) {
				dto.setCanRetryPayment(true);
			}
			Pair<Integer, String> rmaData = calculateRmaCountAndReturnFee(
					salesOrder.getEntityId(),
					salesOrder.getStoreId(),
					shukranCoinsBurnedAlloc,
					salesOrder.getOrderCurrencyCode(),
					xClientVersion
			);
			dto.setRmaCount(rmaData.getLeft());
			dto.setReturnFee(rmaData.getRight());
			dto.setIsSecondRefundTagOn(true);

			splitOrderDTOS.add(dto);
		}

		return  splitOrderDTOS;
	}

	private SplitOrderDTO convertNormalOrder(SalesOrder salesOrder,OrderEntityConverter orderEntityConverter,OrderResponse orderResponse,Map<String, ProductResponseBody> productsFromMulin,String xClientVersion) {

		SplitOrderDTO dto = new SplitOrderDTO();
		if (salesOrder.getSubSalesOrder() != null) {
			dto.setQualifiedPurchase(salesOrder.getSubSalesOrder().getQualifiedPurchase());
		}
		if(null != salesOrder.getSubSalesOrder() && null != salesOrder.getSubSalesOrder().getRatingStatus()) {
			dto.setIsRated(null != salesOrder.getSubSalesOrder().getRatingStatus() && "1".equals(salesOrder.getSubSalesOrder().getRatingStatus()) ? true : false);
		}
		// For normal orders (no suffix like -L1, -G1), splitOrderId should be null
		dto.setSplitOrderId(null);
		dto.setSplitOrderCount(1);
		dto.setSplitIncrementId(salesOrder.getIncrementId());
		String shipmentModeValue = orderEntityConverter.getShipmentModeOfOrder(Constants.LOCAL_SHIPMENT,salesOrder.getStoreId());
		dto.setShipmentMode(shipmentModeValue);
		dto.setEmail(salesOrder.getCustomerEmail());
		dto.setStatus(salesOrder.getStatus());
		SalesOrderStatusLabelPK key = new SalesOrderStatusLabelPK();
		key.setStatus(salesOrder.getStatus());
		key.setStoreId(salesOrder.getStoreId());
		SalesOrderStatusLabel label = salesOrderStatusLabelRepository.findById(key);
		if (label != null) {
			dto.setStatusLabel(label.getLabel());
		} else {
			dto.setStatusLabel(salesOrder.getStatus());
		}
		dto.setShippingDescription(salesOrder.getShippingDescription());
		dto.setStoreId(String.valueOf(salesOrder.getStoreId()));
		dto.setCustomerId(String.valueOf(salesOrder.getCustomerId()));
		dto.setDiscountAmount(salesOrder.getDiscountAmount().toString());
		dto.setGrandTotal(salesOrder.getGrandTotal().toString());
		dto.setBaseGrandTotal(salesOrder.getBaseGrandTotal().toString());
		dto.setShippingAmount(salesOrder.getShippingAmount().toString());
		dto.setCodCharges(parseNullStr(salesOrder.getCashOnDeliveryFee()));
		dto.setSubtotal(salesOrder.getSubtotal().toString());
		dto.setCurrency(salesOrder.getOrderCurrencyCode());
		dto.setItemCount(String.valueOf(salesOrder.getTotalItemCount()));
		dto.setBillingAddressId(String.valueOf(salesOrder.getBillingAddressId()));
		if (null != salesOrder.getSubSalesOrder() &&
				null != salesOrder.getSubSalesOrder().getExternalQuoteId()) {
			dto.setQuoteId(salesOrder.getSubSalesOrder().getExternalQuoteId().toString());
		}		dto.setShippingAddressId(String.valueOf(salesOrder.getShippingAddressId()));
		dto.setIncrementId(salesOrder.getIncrementId());
		dto.setShippingMethod(salesOrder.getShippingMethod());
		dto.setCreatedAt(salesOrder.getCreatedAt().toString());
		dto.setUpdatedAt(salesOrder.getUpdatedAt().toString());
		updateEstimatedDeliveryTime(salesOrder,salesOrder.getSalesOrderItem().stream().toList(), dto);
		orderEntityConverter.addOtherinformation(salesOrder,orderResponse,true,productsFromMulin);
		orderEntityConverter.configureOrderTracking(salesOrder,orderResponse);
		orderEntityConverter.setOrderStatusCallToActionFlag(orderResponse, salesOrder);
		dto.setTrackings(orderResponse.getTrackings());
		dto.setProducts(orderResponse.getProducts());
		dto.setCancelProducts(orderResponse.getCancelProducts());
		getOrderInvoices(dto,salesOrder);
		dto.setCallToActionFlag(orderResponse.getCallToActionFlag());
		Map<String, Integer> statusStatesMap = staticComponents.getStatusStepMap();
		if (statusStatesMap != null) {
			dto.setStatusStepValue(statusStatesMap.get(salesOrder.getStatus()));
		}

		Map<String, Integer> statusColorsMap = staticComponents.getStatusColorsStepMap();
		if (statusColorsMap != null) {
			dto.setStatusColorStepValue(statusColorsMap.get(salesOrder.getStatus()));
		}

		dto.setImportFeesAmount(parseNullStr(salesOrder.getImportFee()));
		dto.setImportFeesAmount(parseNullStr(salesOrder.getImportFee()));
		if (null != salesOrder.getSubSalesOrder() && null != salesOrder.getSubSalesOrder().getDonationAmount()) {
			dto.setDonationAmount(parseNullStr(salesOrder.getSubSalesOrder().getDonationAmount()));
		}
		dto.setShippingUrl(orderResponse.getShippingUrl());
		dto.setCanceledAt(orderResponse.getCanceledAt());
		dto.setDeliveredAt(orderEntityConverter.convertTimezone(salesOrder.getDeliveredAt(), salesOrder.getStoreId()));
		List<RtoAutoRefund> rtoAutoRefund = orderHelper.getRtoAutoRefundList(salesOrder);
		if (Objects.nonNull(rtoAutoRefund) && !rtoAutoRefund.isEmpty()) {
			dto.setRto(true);
			dto.setRtoStatus(rtoAutoRefund.get(0).getStatus());
			dto.setRtoRefundAt(orderEntityConverter.convertTimezone(rtoAutoRefund.get(0).getRefundAt(), salesOrder.getStoreId()));
			dto.setRtoRefundAmount(parseNullStr(rtoAutoRefund.get(0).getRefundAmount()));
		} else if("rto".equals(salesOrder.getStatus())){
			dto.setRto(true);
			dto.setRtoStatus("pending");
		}
//		dto.setArchived(salesOrder.getArchived());
//		dto.setTaxPercent(salesOrder.getTaxPercent());
		// EAS coins added for response order details
		if(null != salesOrder.getSubSalesOrder() && null != salesOrder.getSubSalesOrder().getEasCoins()) {
			dto.setSpendCoin(salesOrder.getSubSalesOrder().getEasCoins());
			dto.setCoinToCurrency(parseNullStr(salesOrder.getSubSalesOrder().getEasValueInCurrency().toString()));
			dto.setCoinToBaseCurrency(parseNullStr(salesOrder.getSubSalesOrder().getEasValueInBaseCurrency().toString()));
		}
		if(null != salesOrder.getSubSalesOrder() && null != salesOrder.getSubSalesOrder().getRetryPayment()
				&& salesOrder.getSubSalesOrder().getRetryPayment().equals(1)) {
			dto.setCanRetryPayment(true);
		}
		Pair<Integer, String> rmaData = calculateRmaCountAndReturnFee(
				salesOrder.getEntityId(),
				salesOrder.getStoreId(),
				salesOrder.getSubSalesOrder() != null ? salesOrder.getSubSalesOrder().getTotalShukranCoinsBurned() : null,
				salesOrder.getOrderCurrencyCode(),
				xClientVersion
		);
		dto.setRmaCount(rmaData.getLeft());
		dto.setReturnFee(rmaData.getRight());
		dto.setIsSecondRefundTagOn(true);
		return dto;
	}

	private void getOrderInvoices(SplitOrderDTO splitOrderDTO,SalesOrder salesOrder) {
		List<String> invoices = new ArrayList<>();
		if (CollectionUtils.isNotEmpty(salesOrder.getSalesInvoices())) {
			for (SalesInvoice invoice : salesOrder.getSalesInvoices()) {
				if (invoice.getIncrementId() != null) {
					String encodeValue = null;
					if(null != Constants.orderCredentials && Constants.orderCredentials.getWms().isNewInVoiceEncode()) {
						encodeValue = salesOrder.getEntityId().toString().concat("#").concat(salesOrder.getCustomerEmail());
					}else {
						encodeValue = salesOrder.getEntityId().toString();
					}
					String encoded = Base64.getEncoder().encodeToString(encodeValue.getBytes());
					String invoiceUrl = Constants.orderCredentials.getOrderDetails().getOmsServicePublicBaseUrl() +"/v1/orders/generatePDF/" + encoded;
					invoices.add(invoiceUrl);
				}
			}
		}
		splitOrderDTO.setInvoices(invoices);
	}

	private void getSplitOrderInvoices(SplitOrderDTO splitOrderDTO,SplitSalesOrder splitSalesOrder) {
		List<String> invoices = new ArrayList<>();
		if (CollectionUtils.isNotEmpty(splitSalesOrder.getSalesInvoices())) {
			for (SalesInvoice invoice : splitSalesOrder.getSalesInvoices()) {
				if (invoice.getIncrementId() != null) {
					String encodeValue = null;
					if(null != Constants.orderCredentials && Constants.orderCredentials.getWms().isNewInVoiceEncode()) {
						encodeValue = splitSalesOrder.getSalesOrder().getEntityId().toString().concat("#").concat(splitSalesOrder.getEntityId().toString()).concat("#").concat(splitSalesOrder.getCustomerEmail());
					}else {
						encodeValue = splitSalesOrder.getSalesOrder().getEntityId().toString().concat("#").concat(splitSalesOrder.getEntityId().toString());
					}
					String encoded = Base64.getEncoder().encodeToString(encodeValue.getBytes());
					String invoiceUrl = Constants.orderCredentials.getOrderDetails().getOmsServicePublicBaseUrl() +"/v1/orders/generatePDF/" + encoded;
					invoices.add(invoiceUrl);
				}
			}
		}
		splitOrderDTO.setInvoices(invoices);
	}
	public String parseNullStr(Object val) {
		return (val == null) ? null : String.valueOf(val);
	}

	private void getOrderPayment(OrderDetailsV2 resp, SalesOrderPayment salesOrderPayment, SalesOrder salesOrder, List<SplitSalesOrder> splitOrders, String xClientVersion) {
		Payments payments = new Payments();
		// Set payment method and card details
		setPaymentMethodAndCardDetails(payments, salesOrderPayment);
		
		// Map basic SalesOrder fields to Payments DTO
		// Pass response to use split order DTOs as fallback if entity values are 0
		setBasicPaymentFields(payments, resp, salesOrder, splitOrders);
		
		// Set SubSalesOrder related fields
		setSubSalesOrderFields(payments, salesOrder);
		
		// Set RMA related fields
		setRmaFields(payments, salesOrder, xClientVersion);
		
		payments.setOrderAlreadyExists(false);
		
		// Set the payments object into the final response
		resp.setPayments(payments);
	}
	
	/**
	 * Sets payment method and card details from sales order payment.
	 */
	private void setPaymentMethodAndCardDetails(Payments payments, SalesOrderPayment salesOrderPayment) {
		if (salesOrderPayment == null) {
			return;
		}
		
		String paymentInformation = salesOrderPayment.getAdditionalInformation();
		payments.setPaymentMethod(salesOrderPayment.getMethod());
		
		// Try to parse standard payment information
		if (paymentInformation != null) {
			try {
				SalesOrderPaymentInformation paymentInfo = mapper.readValue(paymentInformation, SalesOrderPaymentInformation.class);
				payments.setCardDetails(parseNullStr(paymentInfo.getCardNumber()));
			} catch (IOException e) {
				LOGGER.error("Jackson mapping error: ", e);
			}
		}
		
		// Handle payment method-specific card details
		setPaymentMethodSpecificCardDetails(payments, salesOrderPayment, paymentInformation);
	}
	
	/**
	 * Sets card details based on specific payment methods (Tabby, Cashfree, etc.).
	 */
	private void setPaymentMethodSpecificCardDetails(Payments payments, SalesOrderPayment salesOrderPayment, String paymentInformation) {
		if (paymentInformation == null) {
			return;
		}
		
		String method = salesOrderPayment.getMethod();
		try {
			if (isTabbyPayment(method)) {
				payments.setCardDetails(OrderConstants.PAYMENT_TYPE);
			} else if (PaymentConstants.CASHFREE.equalsIgnoreCase(method)) {
				CashfreePaymentDTO paymetDTO = mapper.readValue(paymentInformation, CashfreePaymentDTO.class);
				payments.setCardDetails(paymetDTO.getCfOrderId());
			}
		} catch (Exception e) {
			LOGGER.error("Payment info parsing error: ", e);
		}
	}
	
	/**
	 * Checks if the payment method is Tabby (installments or pay later).
	 */
	private boolean isTabbyPayment(String method) {
		return PaymentCodeENUM.TABBY_IMSTALLMENTS.getValue().equalsIgnoreCase(method)
				|| PaymentCodeENUM.TABBY_PAYLATER.getValue().equalsIgnoreCase(method);
	}
	
	/**
	 * Sets basic payment fields from order response and sales order.
	 */
	private void setBasicPaymentFields(Payments payments, OrderDetailsV2 resp, SalesOrder salesOrder, List<SplitSalesOrder> splitOrders) {
		payments.setDiscountAmount(parseNullStr(resp.getDiscountAmount()));
		payments.setGrandTotal(parseNullStr(resp.getGrandTotal()));
		payments.setBaseGrandTotal(parseNullStr(resp.getBaseGrandTotal()));
		
		// Calculate and set shipping amounts - use shipment-level amounts from split orders for split orders
		Pair<BigDecimal, BigDecimal> shippingAmounts = calculateShippingAmountsForPayment(salesOrder, splitOrders, resp);
		String shippingAmount = formatShippingAmount(shippingAmounts.getLeft());
		String globalShippingAmount = formatShippingAmount(shippingAmounts.getRight());
		
		payments.setShippingAmount(shippingAmount);
		payments.setGlobalShippingAmount(globalShippingAmount);
		
		// Calculate and set COD charges - sum from all split orders for split orders
		BigDecimal codCharges = calculateCodChargesForPayment(salesOrder, splitOrders);
		payments.setCodCharges(formatShippingAmount(codCharges));
		
		payments.setSubtotal(parseNullStr(resp.getSubtotal()));
		payments.setCurrency(salesOrder.getOrderCurrencyCode());
		payments.setImportFeesAmount(parseNullStr(salesOrder.getImportFee()));
	}
	
	/**
	 * Sets SubSalesOrder related fields in payments object.
	 */
	private void setSubSalesOrderFields(Payments payments, SalesOrder salesOrder) {
			if (salesOrder.getSubSalesOrder() == null) {
				return;
			}
			payments.setDonationAmount(parseNullStr(salesOrder.getSubSalesOrder().getDonationAmount()));
			payments.setCoinToCurrency(parseNullStr(salesOrder.getSubSalesOrder().getEasValueInCurrency()));
			payments.setCoinToBaseCurrency(parseNullStr(salesOrder.getSubSalesOrder().getEasValueInBaseCurrency()));

			Integer retryPayment = salesOrder.getSubSalesOrder().getRetryPayment();
			payments.setCanRetryPayment(retryPayment != null && retryPayment.equals(1));
		}
	
	/**
	 * Sets RMA (Return Merchandise Authorization) related fields.
	 */
	private void setRmaFields(Payments payments, SalesOrder salesOrder, String xClientVersion) {
		BigDecimal totalShukranCoinsBurned = salesOrder.getSubSalesOrder() != null 
				? salesOrder.getSubSalesOrder().getTotalShukranCoinsBurned() 
				: null;
		
		Pair<Integer, String> rmaData = calculateRmaCountAndReturnFee(
				salesOrder.getEntityId(),
				salesOrder.getStoreId(),
				totalShukranCoinsBurned,
				salesOrder.getOrderCurrencyCode(),
				xClientVersion
		);
		payments.setRmaCount(rmaData.getLeft());
		payments.setReturnFee(rmaData.getRight());
	}

	/**
	 * Calculates shipping amounts for payment object.
	 * For split orders: Uses shipment-level amounts from SplitSalesOrder (sums EXPRESS and GLOBAL separately).
	 *                   Falls back to split order DTOs from response if entity values are 0.
	 * For non-split orders: Uses main order values.
	 * Returns a Pair where left is shippingAmount (EXPRESS) and right is globalShippingAmount (GLOBAL).
	 * 
	 * @param salesOrder The main sales order
	 * @param splitOrders The list of split orders (can be null or empty for non-split orders)
	 *                     Passed to avoid redundant database calls
	 * @param resp The response object containing split order DTOs (for fallback)
	 */
	private Pair<BigDecimal, BigDecimal> calculateShippingAmountsForPayment(SalesOrder salesOrder, List<SplitSalesOrder> splitOrders, OrderDetailsV2 resp) {
		if (isSplitOrderWithValidSplitOrders(salesOrder, splitOrders)) {
			// For split orders, use shipment-level amounts from SplitSalesOrder
			Pair<BigDecimal, BigDecimal> amounts = calculateShippingAmountsFromSplitOrders(splitOrders);
			
			// If entity values are 0, fall back to split order DTOs from response
			if (shouldUseSplitOrderDTOsForShipping(amounts, resp)) {
				return calculateShippingAmountsFromSplitOrderDTOs(resp.getSplitOrders());
			}
			return amounts;
		}
		
		// For non-split orders, use main order values
		return Pair.of(
			salesOrder.getShippingAmount() != null ? salesOrder.getShippingAmount() : BigDecimal.ZERO,
			salesOrder.getGlobalShippingAmount() != null ? salesOrder.getGlobalShippingAmount() : BigDecimal.ZERO
		);
	}
	
	/**
	 * Determines if we should use split order DTOs for shipping amounts (fallback when entity values are 0).
	 * Extracted to simplify complex conditional logic.
	 */
	private boolean shouldUseSplitOrderDTOsForShipping(Pair<BigDecimal, BigDecimal> amounts, OrderDetailsV2 resp) {
		boolean bothAmountsAreZero = amounts.getLeft().compareTo(BigDecimal.ZERO) == 0 
				&& amounts.getRight().compareTo(BigDecimal.ZERO) == 0;
		boolean hasSplitOrderDTOs = resp.getSplitOrders() != null && !resp.getSplitOrders().isEmpty();
		return bothAmountsAreZero && hasSplitOrderDTOs;
	}
	
	/**
	 * Calculates shipping amounts from split order DTOs (fallback when entity values are 0).
	 * Returns a Pair where left is shippingAmount (EXPRESS) and right is globalShippingAmount (GLOBAL).
	 */
	private Pair<BigDecimal, BigDecimal> calculateShippingAmountsFromSplitOrderDTOs(List<SplitOrderDTO> splitOrderDTOs) {
		BigDecimal expressShipping = BigDecimal.ZERO;
		BigDecimal globalShipping = BigDecimal.ZERO;
		
		for (SplitOrderDTO dto : splitOrderDTOs) {
			if (StringUtils.isNotBlank(dto.getShippingAmount())) {
				try {
					BigDecimal shippingAmount = new BigDecimal(dto.getShippingAmount());
					if (Constants.GLOBAL_SHIPMENT.equalsIgnoreCase(dto.getShipmentMode())) {
						globalShipping = globalShipping.add(shippingAmount);
					} else {
						expressShipping = expressShipping.add(shippingAmount);
					}
				} catch (NumberFormatException e) {
					LOGGER.warn("Invalid shipping amount format in split order DTO: " + dto.getShippingAmount());
				}
			}
		}
		
		return Pair.of(expressShipping, globalShipping);
	}

	/**
	 * Calculates shipping amounts from split orders.
	 * Returns a Pair where left is shippingAmount (EXPRESS) and right is globalShippingAmount (GLOBAL).
	 * Note: Scaling is handled by formatShippingAmount when the result is used.
	 */
	private Pair<BigDecimal, BigDecimal> calculateShippingAmountsFromSplitOrders(List<SplitSalesOrder> splitOrders) {
		Map<Boolean, BigDecimal> amountsByGlobal = splitOrders.stream()
				.filter(so -> so.getShippingAmount() != null)
				.collect(Collectors.partitioningBy(
						this::isGlobalShipment,
						Collectors.mapping(SplitSalesOrder::getShippingAmount, 
								Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
				));

		return Pair.of(
				amountsByGlobal.getOrDefault(false, BigDecimal.ZERO),
				amountsByGlobal.getOrDefault(true, BigDecimal.ZERO)
		);
	}
	
	/**
	 * Determines if a split order is a global shipment.
	 */
	private boolean isGlobalShipment(SplitSalesOrder splitOrder) {
		return Boolean.TRUE.equals(splitOrder.getHasGlobalShipment())
				|| (splitOrder.getShipmentMode() != null 
						&& Constants.GLOBAL_SHIPMENT.equalsIgnoreCase(splitOrder.getShipmentMode()));
	}
	
	/**
	 * Calculates COD charges for payment object.
	 * For split orders: Sums COD fees from all split orders (EXPRESS + GLOBAL).
	 *                   If sum is zero, falls back to main order's COD fee.
	 * For non-split orders: Uses main order's COD fee.
	 * 
	 * @param salesOrder The main sales order
	 * @param splitOrders The list of split orders (can be null or empty for non-split orders)
	 *                     Passed to avoid redundant database calls
	 */
	private BigDecimal calculateCodChargesForPayment(SalesOrder salesOrder, List<SplitSalesOrder> splitOrders) {
		if (isSplitOrderWithValidSplitOrders(salesOrder, splitOrders)) {
			// Sum COD fees from all split orders (both EXPRESS and GLOBAL)
			BigDecimal splitCodSum = splitOrders.stream()
					.filter(so -> so.getCashOnDeliveryFee() != null)
					.map(SplitSalesOrder::getCashOnDeliveryFee)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			
			// If split orders have COD fees, use the sum; otherwise fall back to main order
			if (splitCodSum.compareTo(BigDecimal.ZERO) > 0) {
				return splitCodSum;
			}
		}

		// Runtime split case (split order but splitOrders not persisted/loaded):
		// Use main order's local + global COD fees to keep response codCharges consistent
		// with split-order-level COD charges and with the details view expectation.
		if (salesOrder != null && Objects.equals(1, salesOrder.getIsSplitOrder())
				&& (splitOrders == null || splitOrders.isEmpty())) {
			BigDecimal localCod = salesOrder.getCashOnDeliveryFee() != null ? salesOrder.getCashOnDeliveryFee() : BigDecimal.ZERO;
			BigDecimal globalCod = salesOrder.getGlobalCashOnDeliveryFee() != null ? salesOrder.getGlobalCashOnDeliveryFee() : BigDecimal.ZERO;
			BigDecimal total = localCod.add(globalCod);
			if (total.compareTo(BigDecimal.ZERO) > 0) {
				return total;
			}
		}
		
		// For non-split orders or if split orders COD sum is zero, use main order value
		return salesOrder.getCashOnDeliveryFee() != null ? salesOrder.getCashOnDeliveryFee() : BigDecimal.ZERO;
	}
	
	/**
	 * Checks if the order is a split order with valid split orders list.
	 * Extracted to simplify complex conditional logic.
	 */
	private boolean isSplitOrderWithValidSplitOrders(SalesOrder salesOrder, List<SplitSalesOrder> splitOrders) {
		return Objects.equals(1, salesOrder.getIsSplitOrder()) 
				&& splitOrders != null 
				&& !splitOrders.isEmpty();
	}
	
	/**
	 * Formats BigDecimal to string with 4 decimal places (e.g., "0.0000").
	 */
	private String formatShippingAmount(BigDecimal amount) {
		if (amount == null) {
			return "0.0000";
		}
		return amount.setScale(4, RoundingMode.HALF_UP).toString();
	}

	private Pair<Integer, String> calculateRmaCountAndReturnFee(
			Integer entityId,
			Integer storeId,
			BigDecimal shukranCoinsBurned,
			String currencyCode,
			String xClientVersion
	) {
		int rmaCountVal = 0;
		double refundAmountToBeDeducted = 0.0;

		if (entityId != null && storeId != null) {
			if (Constants.orderCredentials.getBlockShukranSecondRefund()
					&& shukranCoinsBurned != null
					&& shukranCoinsBurned.compareTo(BigDecimal.ZERO) > 0) {
				refundAmountToBeDeducted = 0.0;
			} else {
				boolean isAppVersionSufficient = false;

				if (StringUtils.isNotBlank(xClientVersion)
						&& StringUtils.isNotBlank(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion())) {
					Long mobileAppVersion = Constants.decodeAppVersion(xClientVersion);
					Long secondReturnThresholdVersion = Constants.decodeAppVersion(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion());

					if (secondReturnThresholdVersion != null && mobileAppVersion != null
							&& secondReturnThresholdVersion <= mobileAppVersion) {
						isAppVersionSufficient = true;
					}
				}

				if (isAppVersionSufficient
						&& Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs() != null
						&& Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs() > 0) {

					Integer rmaClubbingHours = Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs();
					rmaCountVal = amastyRmaRequestRepository.getRMACount(entityId, rmaClubbingHours);

					if (rmaCountVal == 0) {
						String requestId = amastyRmaRequestRepository.getLastRequestId(entityId, rmaClubbingHours);
						if (StringUtils.isNotBlank(requestId)) {
							int trackingCount = amastyRmaTrackingRepository.getTrackingCountByRequestId(Integer.parseInt(requestId));
							if (trackingCount > 0) {
								rmaCountVal = 1;
							}
						}
					}

					Double refundValue = configService.getWebsiteRefundByStoreId(storeId);
					if (refundValue != null && refundValue > 0) {
						refundAmountToBeDeducted = refundValue;
					} else {
						rmaCountVal = 0;
					}
				}
			}
		}

		String returnFee = StringUtils.isNotBlank(currencyCode)
				? currencyCode + " " + refundAmountToBeDeducted
				: "" + refundAmountToBeDeducted;

		return Pair.of(rmaCountVal, returnFee);
	}

	private Pair<Integer, String> calculateSplitOrderRmaCountAndReturnFee(
			Integer entityId,
			Integer storeId,
			BigDecimal shukranCoinsBurned,
			String currencyCode,
			String xClientVersion
	) {
		int rmaCountVal = 0;
		double refundAmountToBeDeducted = 0.0;

		if (entityId != null && storeId != null) {
			if (Constants.orderCredentials.getBlockShukranSecondRefund()
					&& shukranCoinsBurned != null
					&& shukranCoinsBurned.compareTo(BigDecimal.ZERO) > 0) {
				refundAmountToBeDeducted = 0.0;
			} else {
				boolean isAppVersionSufficient = false;

				if (StringUtils.isNotBlank(xClientVersion)
						&& StringUtils.isNotBlank(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion())) {
					Long mobileAppVersion = Constants.decodeAppVersion(xClientVersion);
					Long secondReturnThresholdVersion = Constants.decodeAppVersion(Constants.orderCredentials.getPayfort().getSecondReturnThresholdVersion());

					if (secondReturnThresholdVersion != null && mobileAppVersion != null
							&& secondReturnThresholdVersion <= mobileAppVersion) {
						isAppVersionSufficient = true;
					}
				}

				if (isAppVersionSufficient
						&& Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs() != null
						&& Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs() > 0) {

					Integer rmaClubbingHours = Constants.orderCredentials.getNavik().getReturnAwbCreateClubbingHrs();
					rmaCountVal = amastyRmaRequestRepository.getRMACountOfSplitOrder(entityId, rmaClubbingHours);

					if (rmaCountVal == 0) {
						String requestId = amastyRmaRequestRepository.getLastRequestIdOfSplitOrder(entityId, rmaClubbingHours);
						if (StringUtils.isNotBlank(requestId)) {
							int trackingCount = amastyRmaTrackingRepository.getTrackingCountByRequestId(Integer.parseInt(requestId));
							if (trackingCount > 0) {
								rmaCountVal = 1;
							}
						}
					}

					Double refundValue = configService.getWebsiteRefundByStoreId(storeId);
					if (refundValue != null && refundValue > 0) {
						refundAmountToBeDeducted = refundValue;
					} else {
						rmaCountVal = 0;
					}
				}
			}
		}

		String returnFee = StringUtils.isNotBlank(currencyCode)
				? currencyCode + " " + refundAmountToBeDeducted
				: "" + refundAmountToBeDeducted;

		return Pair.of(rmaCountVal, returnFee);
	}

	private BigDecimal safe(BigDecimal value) {
		return value != null ? value : BigDecimal.ZERO;
	}

	private <T> BigDecimal sum(List<T> items, Function<T, BigDecimal> getter) {
		BigDecimal result = BigDecimal.ZERO;
		if (items != null) {
			for (T item : items) {
				if (item != null) {
					BigDecimal v = getter.apply(item);
					if (v != null) {
						result = result.add(v);
					}
				}
			}
		}
		return result;
	}

	private BigDecimal calculateSplitValue(BigDecimal totalOrderRowTotal, BigDecimal totalValue, BigDecimal splitRowTotal) {
		if (totalOrderRowTotal == null || totalOrderRowTotal.compareTo(BigDecimal.ZERO) == 0 || totalValue == null) {
			return BigDecimal.ZERO;
		}
		return totalValue.multiply(splitRowTotal).divide(totalOrderRowTotal, 4, RoundingMode.HALF_UP);
	}

	private void updateEstimatedDeliveryTime(SalesOrder salesOrder,List<SalesOrderItem> items, SplitOrderDTO dto) {
		if (null==items  || items.isEmpty()) {
			dto.setEstimatedDeliveryTime(salesOrder.getEstimatedDeliveryTime());
			dto.setMaximumEstimatedDeliveryTime(salesOrder.getEstimatedDeliveryTime());
			dto.setMinimumEstimatedDeliveryTime(salesOrder.getEstimatedDeliveryTime());
			return;
		}
		// Estimated delivery: reuse order's EDT fields if you have them; else null-safe
		Timestamp estDate = null;
		Timestamp maxEdt = null;
		Timestamp minEdt = null;
		for (SalesOrderItem item : items) {
			Timestamp parsedDate = item.getEstimatedDeliveryDate();
			if ((estDate == null || parsedDate.after(estDate)) && null != parsedDate) {
				estDate = parsedDate;
			}
			parsedDate = item.getMaxEstimatedDate();
			if ((maxEdt == null || parsedDate.after(maxEdt)) && null != parsedDate) {
				maxEdt = parsedDate;
			}
			parsedDate = item.getMinEstimatedDate();
			if ((minEdt == null || parsedDate.before(minEdt))  && null != parsedDate) {
				minEdt = parsedDate;
			}
		}
		dto.setEstimatedDeliveryTime(estDate);
		dto.setMaximumEstimatedDeliveryTime(maxEdt);
		dto.setMinimumEstimatedDeliveryTime(minEdt);
		if (null == estDate) {
			dto.setEstimatedDeliveryTime(salesOrder.getEstimatedDeliveryTime());
			dto.setMaximumEstimatedDeliveryTime(salesOrder.getEstimatedDeliveryTime());
			dto.setMinimumEstimatedDeliveryTime(salesOrder.getEstimatedDeliveryTime());
		}
	}

	private void updateEstimatedDeliveryTimeOfSplitOrder(SplitSalesOrder splitSalesOrder,List<SplitSalesOrderItem> items, SplitOrderDTO dto) {
		if (null==items  || items.isEmpty()) {
			dto.setEstimatedDeliveryTime(splitSalesOrder.getEstimatedDeliveryTime());
			dto.setMaximumEstimatedDeliveryTime(splitSalesOrder.getEstimatedDeliveryTime());
			dto.setMinimumEstimatedDeliveryTime(splitSalesOrder.getEstimatedDeliveryTime());
			return;
		}
		// Estimated delivery: reuse order's EDT fields if you have them; else null-safe
		Timestamp estDate = null;
		Timestamp maxEdt = null;
		Timestamp minEdt = null;
		for (SplitSalesOrderItem item : items) {
			Timestamp parsedDate = item.getEstimatedDeliveryDate();
			if ((estDate == null || parsedDate.after(estDate)) && null != parsedDate) {
				estDate = parsedDate;
			}
			parsedDate = item.getMaxEstimatedDate();
			if ((maxEdt == null || parsedDate.after(maxEdt)) && null != parsedDate) {
				maxEdt = parsedDate;
			}
			parsedDate = item.getMinEstimatedDate();
			if ((minEdt == null || parsedDate.before(minEdt))  && null != parsedDate) {
				minEdt = parsedDate;
			}
		}
		dto.setEstimatedDeliveryTime(estDate);
		dto.setMaximumEstimatedDeliveryTime(maxEdt);
		dto.setMinimumEstimatedDeliveryTime(minEdt);
		if (null == estDate) {
			dto.setEstimatedDeliveryTime(splitSalesOrder.getEstimatedDeliveryTime());
			dto.setMaximumEstimatedDeliveryTime(splitSalesOrder.getEstimatedDeliveryTime());
			dto.setMinimumEstimatedDeliveryTime(splitSalesOrder.getEstimatedDeliveryTime());
		}
	}

	private void addCancelDateAndRTO(SplitSalesOrder splitSalesOrder,SplitOrderDTO dto,OrderEntityConverter orderEntityConverter) {

		if (splitSalesOrder.getStatus().equalsIgnoreCase(OrderConstants.CANCELED_ORDER_STATE)) {
			List<SalesOrderStatusHistory> histories = salesOrderStatusHistoryRepository
					.findBySplitSalesOrderAndStatus(splitSalesOrder, OrderConstants.CANCELED_ORDER_STATE);
			if (CollectionUtils.isNotEmpty(histories)) {
				SalesOrderStatusHistory history = histories.get(0);
				dto.setCanceledAt(parseNullStr(history.getCreatedAt()));
			}
		}
		if (splitSalesOrder.getStatus().equalsIgnoreCase(OrderConstants.CLOSED_ORDER_STATUS)) {
			List<SalesOrderStatusHistory> histories = salesOrderStatusHistoryRepository
					.findBySplitSalesOrderAndStatus(splitSalesOrder, OrderConstants.CLOSED_ORDER_STATUS);
			if (CollectionUtils.isNotEmpty(histories)) {
				SalesOrderStatusHistory history = histories.get(0);
				dto.setCanceledAt(parseNullStr(history.getCreatedAt()));
			}
		}
		// Handle cancelled status for split orders
		if (splitSalesOrder.getStatus().equalsIgnoreCase(OrderConstants.SPLIT_ORDER_CANCELLED_ORDER_STATUS)) {
			List<SalesOrderStatusHistory> histories = salesOrderStatusHistoryRepository
					.findBySplitSalesOrderAndStatus(splitSalesOrder, OrderConstants.SPLIT_ORDER_CANCELLED_ORDER_STATUS);
			if (CollectionUtils.isNotEmpty(histories)) {
				SalesOrderStatusHistory history = histories.get(0);
				dto.setCanceledAt(parseNullStr(history.getCreatedAt()));
			}
		}

		if ( splitSalesOrder.getStatus().equalsIgnoreCase(OrderConstants.SPLIT_ORDER_CANCELED_ORDER_STATUS)) {
			List<SalesOrderStatusHistory> histories = salesOrderStatusHistoryRepository
					.findBySplitSalesOrderAndStatus(splitSalesOrder, OrderConstants.SPLIT_ORDER_CANCELED_ORDER_STATUS);
			if (CollectionUtils.isNotEmpty(histories)) {
				SalesOrderStatusHistory history = histories.get(0);
				dto.setCanceledAt(parseNullStr(history.getCreatedAt()));
			}
		}
		List<RtoAutoRefund> rtoAutoRefund = orderHelper.getSplitOrderRtoAutoRefundList(splitSalesOrder);
		if (Objects.nonNull(rtoAutoRefund) && !rtoAutoRefund.isEmpty()) {
			dto.setRto(true);
			dto.setRtoStatus(rtoAutoRefund.get(0).getStatus());
			dto.setRtoRefundAt(orderEntityConverter.convertTimezone(rtoAutoRefund.get(0).getRefundAt(), splitSalesOrder.getStoreId()));
			dto.setRtoRefundAmount(parseNullStr(rtoAutoRefund.get(0).getRefundAmount()));
		}else if("rto".equals(splitSalesOrder.getStatus())){
			dto.setRto(true);
			dto.setRtoStatus("pending");
		}
	}

	/**
	 * Extracts email from X-Header-Token by removing trailing numeric suffixes
	 * @param inputEmail The header token value
	 * @return The extracted email address
	 */
	private String getEmailFromHeader(String inputEmail) {
		String result = inputEmail;
		try {
			String[] chunks = inputEmail.split("_");
			if (chunks != null && chunks.length > 1) {
				ArrayList<String> chunksList = new ArrayList<>(Arrays.asList(chunks));
				for (int i = (chunksList.size() - 1); i > (-1); i--) {
					final String item = chunksList.get(i);
					if (StringUtils.isNumericSpace(item)) {
						chunksList.remove(i);
					} else {
						break;
					}
				}
				String value = String.join("_", chunksList);
				if (value != null) {
					result = value.trim();
				} else {
					result = "";
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error while getting Email from Header: " + e.getMessage());
			result = inputEmail;
		}
		return result;
	}
}
