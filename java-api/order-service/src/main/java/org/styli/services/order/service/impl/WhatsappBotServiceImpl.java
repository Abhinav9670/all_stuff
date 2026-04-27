package org.styli.services.order.service.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Scope;
import javax.annotation.PostConstruct;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabel;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabelPK;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaStatus;
import org.styli.services.order.model.rma.AmastyRmaTracking;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderAddress;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesShipment;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrderPayment;
import org.styli.services.order.model.sales.SplitSellerShipment;
import org.styli.services.order.model.sales.SplitSellerShipmentTrack;
import org.styli.services.order.pojo.GenericApiResponse;
import org.styli.services.order.pojo.consul.oms.base.CarrierCode;
import org.styli.services.order.pojo.consul.oms.base.OmsBaseConfigs;
import org.styli.services.order.pojo.consul.oms.base.TranslationItem;
import org.styli.services.order.pojo.response.Order.OrderAddress;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderDetailRequest;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderDetailResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderListRequest;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderListResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileReturnDetailResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileShipmentListResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileShipmentDetailResponse;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderAddressRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusLabelRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SplitSellerShipmentRepository;
import org.styli.services.order.repository.SalesOrder.SplitSellerShipmentTrackRepository;
import org.styli.services.order.repository.SalesOrder.ShipmentTrackerRepository;
import org.styli.services.order.repository.SalesShipmentRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyRmaStatusRepository;
import org.styli.services.order.repository.Rma.AmastyRmaTrackingRepository;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.service.WhatsappBotService;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.UtilityConstant;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.TimeZone;
import java.util.Set;

/**
 * Created on 27-Oct-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Component
@Scope("singleton")
public class WhatsappBotServiceImpl implements WhatsappBotService {
    private static final Log LOGGER = LogFactory.getLog(WhatsappBotServiceImpl.class);

    /// 30 days of interval
    private long orderInterval = Constants.FRESHDESK_ORDER_INTERVAL_30_DAYS;

    @Autowired
    ConfigService configService;

    @Autowired
    SalesOrderRepository salesOrderRepository;

    @Autowired
    SalesOrderAddressRepository salesOrderAddressRepository;

    @Autowired
    SalesOrderStatusLabelRepository salesOrderStatusLabelRepository;

    @Autowired
    StaticComponents staticComponents;

    @Autowired

    OrderHelper orderHelper;


    @Value("${shipping.navik.base.url:}")
    private String shippingNavikBaseUrl;
    @PostConstruct
    private void validateShippingNavikBaseUrl() {
        if (StringUtils.isBlank(shippingNavikBaseUrl)) {
            LOGGER.error("shippingNavikBaseUrl is not configured! Tracking URLs will not work properly.");
        } else {
            LOGGER.info("shippingNavikBaseUrl configured: " + shippingNavikBaseUrl);
        }
    }
    
    @Autowired
    private AmastyRmaRequestRepository rmaRequestRepository;
    
    @Autowired
    private SalesOrderRMAServiceImpl orderRMAService;
    
    @Autowired
    private AmastyRmaStatusRepository amastyRmaStatusRepository;
    
    @Autowired
    private AmastyRmaTrackingRepository rmaTrackingRepository;
    
    @Autowired
    private SalesCreditmemoRepository creditmemoRepository;
    
    @Autowired
    private OrderEntityConverter orderEntityConverter;

    @Autowired
    SalesShipmentRepository salesShipmentRepository;

    @Autowired
    SplitSellerShipmentRepository splitSellerShipmentRepository;

    @Autowired
    SplitSellerShipmentTrackRepository splitSellerShipmentTrackRepository;

    @Autowired
    SplitSalesOrderRepository splitSalesOrderRepository;
    
    @Autowired
    ShipmentTrackerRepository shipmentTrackerRepository;

    @Override
    public GenericApiResponse<MobileOrderListResponse> getMobileOrderList(
            MobileOrderListRequest requestBody, Map<String, String> requestHeader, String authorizationToken) {
        GenericApiResponse<MobileOrderListResponse> finalResponse = new GenericApiResponse<>();
        try {
            if(!configService.checkAuthorizationExternal(authorizationToken)) {
                finalResponse.setStatus(false);
                finalResponse.setStatusCode("401");
                finalResponse.setStatusMsg(Constants.MISSING_OR_WRONG_TOKEN);
                return finalResponse;
            }
            final Instant nowInstant = Instant.now();
            final long now = nowInstant.toEpochMilli();

            final List<Stores> stores = Constants.getStoresList();

            Stores store = getStoreFromNumber(requestBody.getPhoneNo(), stores);

            String formattedNumber = getFormatterNumber(requestBody.getPhoneNo(), store);

            List<String> entries = salesOrderAddressRepository
                    .findCurrentOrdersByTelephone(formattedNumber, now - orderInterval);
            String resultMode = Constants.RESULT_MODE_SHIPPING;

            if(CollectionUtils.isEmpty(entries)) {
                CustomerEntity customerEntity = orderHelper
                        .getCustomerDetails(null, null, formattedNumber);
                if(customerEntity != null &&
                        customerEntity.getEntityId() != null &&
                        customerEntity.getEntityId() > 0) {
                    entries = salesOrderRepository.findCurrentOrdersByCustomer(
                                    customerEntity.getEntityId(), now - orderInterval);
                    resultMode = Constants.RESULT_MODE_CUSTOMER;
                }
            }

            MobileOrderListResponse response = new MobileOrderListResponse();

            LinkedHashMap<String, String> idsObject = new LinkedHashMap<>();
            ArrayList<String> idsList = new ArrayList<>();
            if(CollectionUtils.isNotEmpty(entries)) {

                int length = (entries.size() < 3)? entries.size() : 3;
                if(Constants.getOmsBaseConfigs() != null &&
                        Constants.getOmsBaseConfigs().getWhatsappOrderConfig() != null &&
                        Constants.getOmsBaseConfigs().getWhatsappOrderConfig().getOrderListCount() != null &&
                        Constants.getOmsBaseConfigs().getWhatsappOrderConfig().getOrderListCount() <= entries.size()) {
                    length = Constants.getOmsBaseConfigs().getWhatsappOrderConfig().getOrderListCount();
                }
                for(int i = 0; i < length; i++) {
                    final String id = entries.get(i);
                    idsObject.put("id"+ (i+1), id);
                    idsList.add(id);
                }
                response.setOrderCount(String.valueOf(length));
                response.setIdObject(idsObject);
                response.setIncrementIds(idsList);
                response.setIdsString(String.join("\n", idsList));
                response.setResultMode(resultMode);
            } else {
                response.setOrderCount(String.valueOf(0));
                response.setIdObject(idsObject);
                response.setIncrementIds(idsList);
                response.setIdsString(StringUtils.EMPTY);
            }
            finalResponse.setStatus(true);
            finalResponse.setStatusCode("200");
			finalResponse.setStatusMsg(Constants.SUCCESS_MSG);
            finalResponse.setResponse(response);

        } catch (Exception e) {
            LOGGER.error("getMobileOrderList error: "+ e.getMessage());
            finalResponse.setStatus(false);
            finalResponse.setStatusCode("201");
            finalResponse.setStatusMsg(Constants.UNKNOWN_ERROR);
            return finalResponse;
        }

        return finalResponse;
    }

    @Override
    public GenericApiResponse<MobileOrderDetailResponse> getMobileOrderDetails(
            MobileOrderDetailRequest requestBody, Map<String, String> requestHeader, String authorizationToken) {
        GenericApiResponse<MobileOrderDetailResponse> finalResponse = new GenericApiResponse<>();
        try {
            final Instant nowInstant = Instant.now();
            final long now = nowInstant.toEpochMilli();
            if(!configService.checkAuthorizationExternal(authorizationToken)) {
                finalResponse.setStatus(false);
                finalResponse.setStatusCode("401");
                finalResponse.setStatusMsg(Constants.MISSING_OR_WRONG_TOKEN);
                return finalResponse;
            }

            SalesOrder salesOrder = salesOrderRepository.findByIncrementId(requestBody.getId());

            if(salesOrder == null) {
                finalResponse.setStatus(false);
                finalResponse.setStatusCode("202");
                finalResponse.setStatusMsg("No orders found!");
                return finalResponse;
            }

            MobileOrderDetailResponse response = new MobileOrderDetailResponse();

            response.setIncrementId(salesOrder.getIncrementId());


            if(StringUtils.isNotEmpty(salesOrder.getStatus())) {
                response.setStatus(salesOrder.getStatus());
            }

            String orderStatus = getStatusLabel(salesOrder);
            if(StringUtils.isNotBlank(orderStatus)) {
                response.setOrderStatus(orderStatus);
            }

            final Integer stepValue = decodeStepValue(salesOrder);
            if(stepValue != null) {
                response.setStepValue(stepValue);
            }

            final Integer colorValue = decodeColorValue(salesOrder);
            if(colorValue != null) {
                response.setColorValue(colorValue);
            }
            response.setDeliveryDayCount(0);
            if(salesOrder.getEstimatedDeliveryTime() != null) {
                final long estimatedMillis = salesOrder.getEstimatedDeliveryTime().getTime();
                LocalDate estiDate = LocalDate.ofEpochDay(estimatedMillis / (24 * 60 * 60 * 1000));
                LocalDate nowDate = LocalDate.ofEpochDay(now / (24 * 60 * 60 * 1000));

                if (nowDate.isBefore(estiDate)) {
                    Duration difference = Duration.between(nowDate.atStartOfDay(), estiDate.atStartOfDay());
                    long noOfDays = difference.toDays();
                    if (noOfDays > 0) {
                        response.setDeliveryDayCount((int) noOfDays);
                    }
                } else if (nowDate.isAfter(estiDate)) {
                    response.setDeliveryDayCount(-1);
                } else { // Dates are equal
                    response.setDeliveryDayCount(0);
                }

				String estimatedDeliveryDate = formatEstimatedDeliveryDateUtc(salesOrder.getEstimatedDeliveryTime());
				if (StringUtils.isNotBlank(estimatedDeliveryDate))
					response.setEstimatedDeliveryTime(estimatedDeliveryDate);
				
            }

            SalesOrderPayment salesOrderPayment = salesOrder.getSalesOrderPayment().stream().findFirst()
                    .orElse(null);
            if(salesOrderPayment!=null) {
            	String paymentMode = salesOrderPayment.getMethod();
                if(Constants.getOmsBaseConfigs()!=null &&
                        Constants.getOmsBaseConfigs().getConfigs()!= null &&
                        MapUtils.isNotEmpty(Constants.getOmsBaseConfigs()
                                .getConfigs().getPaymentMethodTranslations()) &&
                        Constants.getOmsBaseConfigs().getConfigs()
                                .getPaymentMethodTranslations().get(paymentMode) != null) {
                    final TranslationItem translationItem = Constants.getOmsBaseConfigs()
                            .getConfigs().getPaymentMethodTranslations().get(paymentMode);
                    List<Stores> stores = Constants.getStoresList();
                    Optional<Stores> store = stores.stream().filter(
                            e -> Integer.valueOf(e.getStoreId()).equals(salesOrder.getStoreId())
                    ).findAny();
                    Stores orderStore = null;
                    if (store.isPresent()) orderStore = store.get();
                    String lang = "en";
                    if (orderStore != null && StringUtils.isNotBlank(orderStore.getStoreLanguage())) {
                        String[] chunk = orderStore.getStoreLanguage().split("_");
                        lang = (chunk.length > 0 && StringUtils.isNotBlank(chunk[0])) ? chunk[0].toLowerCase() : lang;
                    }
                    paymentMode = (StringUtils.isNotEmpty(translationItem.getValueOf(lang)))
                            ? translationItem.getValueOf(lang) : paymentMode;
                }
                response.setPaymentMode(paymentMode);
            }


            if(salesOrder.getGrandTotal() != null) {
                DecimalFormat df = new DecimalFormat();
                df.setMaximumFractionDigits(2);
                df.setMinimumFractionDigits(2);
                df.setGroupingUsed(false);
                final String currency = (StringUtils.isNotBlank(salesOrder.getOrderCurrencyCode()))
                        ? salesOrder.getOrderCurrencyCode() : StringUtils.EMPTY;
                response.setAmount(currency + " " + df.format(salesOrder.getGrandTotal()));

            }


            // Set tracking URL for main order
            setMainOrderTrackingUrl(salesOrder, response);

            if(salesOrder != null && CollectionUtils.isNotEmpty( salesOrder.getSalesOrderAddress())) {
                setShippingAddressAndPhoneNo(salesOrder, response);
            }

            // Set shipment information
            List<MobileOrderDetailResponse.ShipmentDetail> shipments = new ArrayList<>();
            boolean isSplitOrder = salesOrder.getIsSplitOrder() != null && salesOrder.getIsSplitOrder() == 1;
            
            List<SplitSalesOrder> splitOrders = null;
            if (isSplitOrder) {
                splitOrders = splitSalesOrderRepository.findByOrderId(salesOrder.getEntityId());
            }
            if (CollectionUtils.isNotEmpty(splitOrders)) {
                // For split orders, create shipment details from each split order
                for (SplitSalesOrder splitOrder : splitOrders) {
                    MobileOrderDetailResponse.ShipmentDetail shipmentDetail = createShipmentDetailFromSplitOrderForOrderDetail(splitOrder, salesOrder, salesOrder.getStoreId(), now);
                    shipments.add(shipmentDetail);
                }
            } else {
                // For normal orders, or split orders with no split records, create one shipment from the main order
                MobileOrderDetailResponse.ShipmentDetail shipmentDetail = createShipmentDetailFromOrderForOrderDetail(salesOrder, salesOrder.getStoreId(), now);
                shipments.add(shipmentDetail);
            }
            response.setShipments(shipments);
            response.setShipmentCount(shipments.size());

            finalResponse.setStatus(true);
            finalResponse.setStatusCode("200");
            finalResponse.setStatusMsg(Constants.SUCCESS_MSG);
            finalResponse.setResponse(response);

        } catch (Exception e) {
            LOGGER.error("getMobileOrderDetails error: "+ e.getMessage());
            finalResponse.setStatus(false);
            finalResponse.setStatusCode("201");
            finalResponse.setStatusMsg(Constants.UNKNOWN_ERROR);
            return finalResponse;
        }
        return finalResponse;
    }

    private Stores getStoreFromNumber(String mobileNumber, List<Stores> stores) {
        Stores result = null;
        try {
            if(StringUtils.isNotBlank(mobileNumber) && CollectionUtils.isNotEmpty(stores)) {
                String number = mobileNumber.trim()
                        .replace(" ", StringUtils.EMPTY)
                        .replace("-", StringUtils.EMPTY);
                number = ((number.startsWith("+")) ? number : "+" + number);
                for (final Stores store : stores) {
                    if(StringUtils.isNotBlank(store.getCountryCode()) &&
                            number.startsWith(store.getCountryCode()) &&
                            number.substring(store.getCountryCode().length()).length() >= 8) {
                        result = store;
                        break;
                    }
                }

            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    private String getFormatterNumber(String mobileNumber, Stores store) {
        String result = mobileNumber;
        try {
            if(StringUtils.isNotBlank(mobileNumber) && store != null &&
                    StringUtils.isNotBlank(store.getCountryCode())) {
                result = result.trim()
                        .replace(" ", StringUtils.EMPTY)
                        .replace("-", StringUtils.EMPTY)
                        .replaceFirst(Pattern.quote(store.getCountryCode()), StringUtils.EMPTY);
                result = store.getCountryCode() + " " + result;
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }

    private String getStatusLabel(SalesOrder salesOrder) {
        String result = StringUtils.EMPTY;
        try{
            SalesOrderStatusLabelPK key = new SalesOrderStatusLabelPK();
            key.setStatus(salesOrder.getStatus());
            key.setStoreId(salesOrder.getStoreId());
            SalesOrderStatusLabel label = salesOrderStatusLabelRepository.findById(key);
            if (label != null) {
                result = label.getLabel();
            } else {
                result = salesOrder.getStatus();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return result;
    }
    
    /**
     * Maps English store IDs to their Arabic counterparts
     * @param currentStoreId The current store ID
     * @return Arabic store ID, or the same ID if already Arabic, or null if not found
     */
    private Integer getArabicStoreId(Integer currentStoreId) {
        if (currentStoreId == null) {
            return null;
        }
        // If already an Arabic store, return as-is
        if (Constants.FRESHDESK_ARABIC_STORE_IDS.contains(currentStoreId)) {
            return currentStoreId;
        }
        // Otherwise, map English to Arabic
        return Constants.FRESHDESK_ARABIC_STORE_ID_MAP.get(currentStoreId);
    }
    
    /**
     * Gets Arabic status label for a given status and store ID
     * @param status The status code
     * @param storeId The store ID
     * @return Arabic label, or empty string if not found
     */
    private String getArabicStatusLabel(String status, Integer storeId) {
        if (StringUtils.isBlank(status) || storeId == null) {
            return StringUtils.EMPTY;
        }
        try {
            Integer arabicStoreId = getArabicStoreId(storeId);
            if (arabicStoreId != null) {
                SalesOrderStatusLabelPK key = new SalesOrderStatusLabelPK();
                key.setStatus(status);
                key.setStoreId(arabicStoreId);
                SalesOrderStatusLabel label = salesOrderStatusLabelRepository.findById(key);
                if (label != null && StringUtils.isNotBlank(label.getLabel())) {
                    return label.getLabel();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error getting Arabic status label for status: " + status + ", storeId: " + storeId, e);
        }
        return StringUtils.EMPTY;
    }
    
    private String getStatusLabelAr(SalesOrder salesOrder) {
        if (salesOrder == null || StringUtils.isBlank(salesOrder.getStatus())) {
            return StringUtils.EMPTY;
        }
        return getArabicStatusLabel(salesOrder.getStatus(), salesOrder.getStoreId());
    }
    
    private String getStatusLabelArForSplitOrder(SplitSalesOrder splitOrder, Integer storeId) {
        if (splitOrder == null || StringUtils.isBlank(splitOrder.getStatus()) || storeId == null) {
            return StringUtils.EMPTY;
        }
        return getArabicStatusLabel(splitOrder.getStatus(), storeId);
    }
    
    private String getStatusAr(String status, Integer storeId) {
        return getArabicStatusLabel(status, storeId);
    }

    /**
     * Translates payment mode based on store language
     * @param paymentMethod The payment method code
     * @param storeId The store ID
     * @return Translated payment mode, or original if translation not found
     */
    private String translatePaymentMode(String paymentMethod, Integer storeId) {
        if (StringUtils.isBlank(paymentMethod) || storeId == null) {
            return paymentMethod;
        }
        try {
            OmsBaseConfigs omsBaseConfigs = Constants.getOmsBaseConfigs();
            if (omsBaseConfigs == null || omsBaseConfigs.getConfigs() == null ||
                    MapUtils.isEmpty(omsBaseConfigs.getConfigs().getPaymentMethodTranslations())) {
                return paymentMethod;
            }
            
            TranslationItem translationItem = omsBaseConfigs.getConfigs()
                    .getPaymentMethodTranslations().get(paymentMethod);
            if (translationItem == null) {
                return paymentMethod;
            }
            
            List<Stores> stores = Constants.getStoresList();
            Optional<Stores> storeOpt = stores.stream()
                    .filter(e -> Integer.valueOf(e.getStoreId()).equals(storeId))
                    .findAny();
            
            String lang = "en";
            if (storeOpt.isPresent()) {
                Stores orderStore = storeOpt.get();
                if (StringUtils.isNotBlank(orderStore.getStoreLanguage())) {
                    String[] chunk = orderStore.getStoreLanguage().split("_");
                    lang = (chunk.length > 0 && StringUtils.isNotBlank(chunk[0])) 
                            ? chunk[0].toLowerCase() : lang;
                }
            }
            
            String translated = translationItem.getValueOf(lang);
            return StringUtils.isNotEmpty(translated) ? translated : paymentMethod;
        } catch (Exception e) {
            LOGGER.error("Error translating payment mode: " + paymentMethod, e);
            return paymentMethod;
        }
    }

    /**
     * Formats amount with currency
     * @param amount The amount to format
     * @param currencyCode The currency code
     * @return Formatted amount string (e.g., "AED 212.02")
     */
    private String formatAmount(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            return StringUtils.EMPTY;
        }
        try {
            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(2);
            df.setMinimumFractionDigits(2);
            df.setGroupingUsed(false);
            String currency = StringUtils.isNotBlank(currencyCode) ? currencyCode : StringUtils.EMPTY;
            return currency + " " + df.format(amount);
        } catch (Exception e) {
            LOGGER.error("Error formatting amount: " + amount, e);
            return StringUtils.EMPTY;
        }
    }

    /**
     * Formats estimated delivery timestamp as date-only (dd MMM yyyy) in UTC.
     * Uses UTC so the calendar day does not roll to the next day for times near midnight
     * (e.g. 26 Feb 23:59:33 stays "26 Feb 2026" instead of "27 Feb 2026" in store timezone).
     */
    private String formatEstimatedDeliveryDateUtc(Timestamp estimatedDelivery) {
        if (estimatedDelivery == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.FRESHDESK_DATE_FORMAT_DD_MMM_YYYY, Locale.ENGLISH);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(estimatedDelivery.getTime());
    }

    /**
     * Calculates delivery day count and formats estimated delivery date
     * @param estimatedDelivery The estimated delivery timestamp
     * @param now Current timestamp in milliseconds
     * @return Array with [deliveryDayCount, formattedDate]
     */
    private Object[] calculateDeliveryDayCountAndDate(Timestamp estimatedDelivery, long now) {
        int deliveryDayCount = 0;
        String formattedDate = null;
        
        if (estimatedDelivery != null) {
            try {
                long estimatedMillis = estimatedDelivery.getTime();
                LocalDate estiDate = LocalDate.ofEpochDay(estimatedMillis / (24 * 60 * 60 * 1000));
                LocalDate nowDate = LocalDate.ofEpochDay(now / (24 * 60 * 60 * 1000));
                
                if (nowDate.isBefore(estiDate)) {
                    Duration difference = Duration.between(nowDate.atStartOfDay(), estiDate.atStartOfDay());
                    long noOfDays = difference.toDays();
                    if (noOfDays > 0) {
                        deliveryDayCount = (int) noOfDays;
                    }
                } else if (nowDate.isAfter(estiDate)) {
                    deliveryDayCount = -1;
                }
                
                formattedDate = formatEstimatedDeliveryDateUtc(estimatedDelivery);
            } catch (Exception e) {
                LOGGER.error("Error calculating delivery day count", e);
            }
        }
        
        return new Object[]{deliveryDayCount, formattedDate};
    }

    /**
     * Determines shipment types (local/global) from split orders
     * @param splitOrders List of split orders
     * @return boolean array [hasLocalShipment, hasGlobalShipment]
     */
    private boolean[] determineShipmentTypes(List<SplitSalesOrder> splitOrders) {
        boolean hasLocalShipment = false;
        boolean hasGlobalShipment = false;
        
        if (CollectionUtils.isNotEmpty(splitOrders)) {
            for (SplitSalesOrder splitOrder : splitOrders) {
                if (splitOrder.getHasGlobalShipment() != null && splitOrder.getHasGlobalShipment()) {
                    hasGlobalShipment = true;
                } else {
                    hasLocalShipment = true;
                }
            }
        }
        
        return new boolean[]{hasLocalShipment, hasGlobalShipment};
    }
    
    /**
     * Sets shipment IDs on ShipmentInfo based on shipment types
     * @param shipmentInfo The shipment info to populate
     * @param orderId The order ID
     * @param hasLocalShipment Whether local shipment exists
     * @param hasGlobalShipment Whether global shipment exists
     */
    private void setShipmentIds(MobileShipmentListResponse.ShipmentInfo shipmentInfo, String orderId, 
            boolean hasLocalShipment, boolean hasGlobalShipment) {
        if (hasLocalShipment && hasGlobalShipment) {
            // Both exist - use shipmentId1 and shipmentId2
            shipmentInfo.setShipmentId1(orderId + Constants.FRESHDESK_SUFFIX_LOCAL);
            shipmentInfo.setShipmentId2(orderId + Constants.FRESHDESK_SUFFIX_GLOBAL);
        } else if (hasLocalShipment) {
            // Only local exists
            shipmentInfo.setShipmentId(orderId + Constants.FRESHDESK_SUFFIX_LOCAL);
        } else if (hasGlobalShipment) {
            // Only global exists
            shipmentInfo.setShipmentId(orderId + Constants.FRESHDESK_SUFFIX_GLOBAL);
        }
    }
    
    /**
     * Creates ShipmentInfo for list response based on order type
     * @param salesOrder The sales order
     * @param orderId The order increment ID
     * @return ShipmentInfo object, or null if order is invalid
     */
    private MobileShipmentListResponse.ShipmentInfo createShipmentInfoForList(SalesOrder salesOrder, String orderId) {
        if (salesOrder == null || StringUtils.isBlank(orderId)) {
            return null;
        }
        
        MobileShipmentListResponse.ShipmentInfo shipmentInfo = new MobileShipmentListResponse.ShipmentInfo();
        shipmentInfo.setOrderId(orderId);
        
        boolean isSplitOrder = salesOrder.getIsSplitOrder() != null && salesOrder.getIsSplitOrder() == 1;
        
        if (isSplitOrder) {
            List<SplitSalesOrder> splitOrders = splitSalesOrderRepository.findByOrderId(salesOrder.getEntityId());
            boolean[] shipmentTypes = determineShipmentTypes(splitOrders);
            setShipmentIds(shipmentInfo, orderId, shipmentTypes[0], shipmentTypes[1]);
        } else {
            // For normal (non-split) orders, use orderId as shipmentId without suffix
            shipmentInfo.setShipmentId(orderId);
        }
        
        return shipmentInfo;
    }

    /**
     * Sets tracking URL for main order response
     */
    private void setMainOrderTrackingUrl(SalesOrder salesOrder, MobileOrderDetailResponse response) {
        // Get tracking data for main order
        LinkedHashMap<String, String> shippingData = getTrackingData(salesOrder);
        if (MapUtils.isNotEmpty(shippingData)) {
            if (StringUtils.isNotBlank(shippingData.get(Constants.SHIPPING_TITLE))) {
                response.setShippingTitle(shippingData.get(Constants.SHIPPING_TITLE));
            }
            if (StringUtils.isNotBlank(shippingData.get(Constants.SHIPPING_URL))) {
                response.setShippingUrl(shippingData.get(Constants.SHIPPING_URL));
            }
        }
        
        // For split orders: if main order has no URL, check if a split order has unique tracking
        boolean isSplitOrder = salesOrder.getIsSplitOrder() != null && salesOrder.getIsSplitOrder() == 1;
        if (isSplitOrder && StringUtils.isBlank(response.getShippingUrl())) {
            setSplitOrderTrackingUrlIfUnique(salesOrder, response);
        }
    }
    
    /**
     * Sets main order URL from split order if split order has unique tracking
     */
    private void setSplitOrderTrackingUrlIfUnique(SalesOrder salesOrder, MobileOrderDetailResponse response) {
        List<SplitSalesOrder> splitOrders = splitSalesOrderRepository.findByOrderId(salesOrder.getEntityId());
        if (CollectionUtils.isEmpty(splitOrders)) {
            return;
        }
        // Check if any split order has its own unique tracking (splitSalesOrderId is set)
        for (SplitSalesOrder splitOrder : splitOrders) {
            SalesShipmentTrack splitTrack = findFirstValidTrackingForSplitOrder(splitOrder);
            if (hasUniqueSplitOrderTracking(splitTrack)) {
                String trackingUrl = getTrackingUrlForSplitOrder(splitOrder, salesOrder.getStoreId());
                if (StringUtils.isNotBlank(trackingUrl)) {
                    response.setShippingUrl(trackingUrl);
                    LOGGER.info("Set main response shippingUrl from split order (unique tracking) for order: " + salesOrder.getIncrementId() + Constants.FRESHDESK_LOG_SPLIT_ORDER_ID + splitOrder.getEntityId() + Constants.FRESHDESK_LOG_URL + trackingUrl);
                    break; // Use first split order with unique tracking
                }
            }
        }
    }
    
    /**
     * Checks if split order track has unique tracking (not from main order)
     */
    private boolean hasUniqueSplitOrderTracking(SalesShipmentTrack splitTrack) {
        return splitTrack != null && 
               StringUtils.isNotBlank(splitTrack.getTrackNumber()) &&
               splitTrack.getSplitSalesOrderId() != null && 
               splitTrack.getSplitSalesOrderId() > 0;
    }
    
    /**
     * Finds first valid tracking number from shipment tracks by querying repository
     * For split orders, returns the first available tracking from any split order
     */
    private SalesShipmentTrack findFirstValidTracking(SalesOrder salesOrder) {
        if (salesOrder == null || salesOrder.getEntityId() == null) {
            return null;
        }
        List<SalesShipmentTrack> tracks = shipmentTrackerRepository.findByOrderId(salesOrder.getEntityId());
        if (CollectionUtils.isEmpty(tracks)) {
            LOGGER.debug("No tracking records found for order ID: " + salesOrder.getEntityId());
            return null;
        }
        // Single loop: prefer main order tracking, fallback to split order tracking
        SalesShipmentTrack firstSplitTrack = null;
        for (SalesShipmentTrack track : tracks) {
            if (StringUtils.isNotBlank(track.getTrackNumber())) {
                if (track.getSplitSalesOrderId() == null || track.getSplitSalesOrderId() == 0) {
                    // Found main order tracking - return immediately
                    LOGGER.debug("Found main order tracking for order ID: " + salesOrder.getEntityId() + Constants.FRESHDESK_LOG_TRACK_NUMBER + track.getTrackNumber());
                    return track;
                } else if (firstSplitTrack == null) {
                    // Store first split order track as fallback
                    firstSplitTrack = track;
                }
            }
        }
        if (firstSplitTrack != null) {
            LOGGER.debug("Found split order tracking for order ID: " + salesOrder.getEntityId() + Constants.FRESHDESK_LOG_TRACK_NUMBER + firstSplitTrack.getTrackNumber() + Constants.FRESHDESK_LOG_SPLIT_ORDER_ID + firstSplitTrack.getSplitSalesOrderId());
            return firstSplitTrack;
        }
        LOGGER.debug("No valid tracking number found for order ID: " + salesOrder.getEntityId());
        return null;
    }
    
    /**
     * Finds first valid tracking number from split order shipment tracks by querying repository
     */
    private SalesShipmentTrack findFirstValidTrackingForSplitOrder(SplitSalesOrder splitOrder) {
        if (splitOrder == null || splitOrder.getEntityId() == null) {
            return null;
        }
        // First, try to get tracking via SalesShipment (parent_id approach)
        SalesShipmentTrack trackByParent = findTrackingByShipmentParent(splitOrder);
        if (trackByParent != null) {
            return trackByParent;
        }
        // Fallback: Query tracks for the main order and filter by split order ID
        return findTrackingByOrderId(splitOrder);
    }
    
    /**
     * Finds tracking via SalesShipment parent_id approach
     */
    private SalesShipmentTrack findTrackingByShipmentParent(SplitSalesOrder splitOrder) {
        try {
            SalesShipment shipment = salesShipmentRepository.findBySplitOrderId(splitOrder.getEntityId());
            if (shipment == null || shipment.getEntityId() == null) {
                return null;
            }
            List<SalesShipmentTrack> tracksByParent = shipmentTrackerRepository.findByParentId(shipment.getEntityId());
            if (CollectionUtils.isEmpty(tracksByParent)) {
                return null;
            }
            for (SalesShipmentTrack track : tracksByParent) {
                if (StringUtils.isNotBlank(track.getTrackNumber())) {
                            LOGGER.debug("Found tracking via shipment parent_id for split order ID: " + splitOrder.getEntityId() + Constants.FRESHDESK_LOG_TRACK_NUMBER + track.getTrackNumber());
                    return track;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error querying shipment for split order ID: " + splitOrder.getEntityId(), e);
        }
        return null;
    }
    
    /**
     * Finds tracking by querying main order and filtering by split order ID
     */
    private SalesShipmentTrack findTrackingByOrderId(SplitSalesOrder splitOrder) {
        if (splitOrder.getSalesOrder() == null || splitOrder.getSalesOrder().getEntityId() == null) {
            LOGGER.debug("Split order has no associated sales order" + Constants.FRESHDESK_LOG_SPLIT_ORDER_ID + splitOrder.getEntityId());
            return null;
        }
        List<SalesShipmentTrack> tracks = shipmentTrackerRepository.findByOrderId(
            splitOrder.getSalesOrder().getEntityId());
        if (CollectionUtils.isEmpty(tracks)) {
            LOGGER.debug("No tracking records found for split order, main order ID: " + splitOrder.getSalesOrder().getEntityId() + Constants.FRESHDESK_LOG_SPLIT_ORDER_ID + splitOrder.getEntityId());
            return null;
        }
        // Filter for tracks that belong to this specific split order
        for (SalesShipmentTrack track : tracks) {
            if (StringUtils.isNotBlank(track.getTrackNumber()) && 
                track.getSplitSalesOrderId() != null && 
                track.getSplitSalesOrderId().equals(splitOrder.getEntityId())) {
                LOGGER.debug("Found tracking for split order ID: " + splitOrder.getEntityId() + Constants.FRESHDESK_LOG_TRACK_NUMBER + track.getTrackNumber());
                return track;
            }
        }
        LOGGER.debug("No valid tracking found for split order ID: " + splitOrder.getEntityId() + ", total tracks found: " + tracks.size());
        return null;
    }
    
    /**
     * Gets tracking URL for split order
     * For global orders, creates URL with increment_id even if no AWB exists
     */
    private String getTrackingUrlForSplitOrder(SplitSalesOrder splitOrder, Integer storeId) {
        try {
            SalesShipmentTrack track = findFirstValidTrackingForSplitOrder(splitOrder);
            if (track != null && StringUtils.isNotBlank(track.getTrackNumber())) {
                // If AWB exists, use it
                String trackingUrl = buildTrackingUrlForOrder(track.getTrackNumber(), storeId);
                String incrementId = splitOrder.getIncrementId();
                // For global orders, add increment_id parameter
                if (StringUtils.isNotBlank(incrementId) && incrementId.contains("-G")) {
                    trackingUrl += "&increment_id=" + incrementId;
                }
                LOGGER.info("Built tracking URL for split order ID: " + splitOrder.getEntityId() + Constants.FRESHDESK_LOG_URL + trackingUrl);
                return trackingUrl;
            }
            
            // Fallback: For global orders without AWB, create URL with increment_id
            String incrementId = splitOrder.getIncrementId();
            if (StringUtils.isNotBlank(incrementId) && incrementId.contains("-G")) {
                String lang = getStoreLanguage(storeId);
                String trackingUrl = buildTrackingUrlWithPath(lang + "/?increment_id=" + incrementId);
                LOGGER.info("Built tracking URL for global split order (no AWB) ID: " + splitOrder.getEntityId() + Constants.FRESHDESK_LOG_INCREMENT_ID_LOWER + incrementId + Constants.FRESHDESK_LOG_URL + trackingUrl);
                return trackingUrl;
            }
            
            LOGGER.warn("No tracking found for split order ID: " + (splitOrder != null ? splitOrder.getEntityId() : "null") + Constants.FRESHDESK_LOG_INCREMENT_ID_LOWER + incrementId);
            return null;
        } catch (Exception e) {
            LOGGER.error("Error getting tracking URL for split order ID: " + (splitOrder != null ? splitOrder.getEntityId() : "null"), e);
            return null;
        }
    }
    
    /**
     * Gets tracking URL for normal order
     */
    private String getTrackingUrlForOrder(SalesOrder salesOrder, Integer storeId) {
        try {
            SalesShipmentTrack track = findFirstValidTracking(salesOrder);
            if (track == null || StringUtils.isBlank(track.getTrackNumber())) {
                LOGGER.warn("No tracking found for order ID: " + (salesOrder != null ? salesOrder.getEntityId() : "null") + Constants.FRESHDESK_LOG_INCREMENT_ID + (salesOrder != null ? salesOrder.getIncrementId() : "null"));
                return null;
            }
            String trackingUrl = buildTrackingUrlForOrder(track.getTrackNumber(), storeId);
            LOGGER.info("Built tracking URL for order ID: " + salesOrder.getEntityId() + Constants.FRESHDESK_LOG_INCREMENT_ID + salesOrder.getIncrementId() + Constants.FRESHDESK_LOG_URL + trackingUrl);
            return trackingUrl;
        } catch (Exception e) {
            LOGGER.error("Error getting tracking URL for order ID: " + (salesOrder != null ? salesOrder.getEntityId() : "null"), e);
            return null;
        }
    }
    
    /**
     * Gets store language for tracking URL
     */
    private String getStoreLanguage(Integer storeId) {
        if (storeId == null) {
            return "en";
        }
        List<Stores> stores = Constants.getStoresList();
        Optional<Stores> storeOpt = stores.stream()
                .filter(e -> Integer.valueOf(e.getStoreId()).equals(storeId))
                .findAny();
        
        if (storeOpt.isPresent()) {
            Stores storeValue = storeOpt.get();
            if (StringUtils.isNotBlank(storeValue.getStoreLanguage())) {
                return storeValue.getStoreLanguage();
            }
        }
        return "en";
    }
    
    /**
     * Builds tracking URL from tracking number and store
     */
    private String buildTrackingUrlForOrder(String trackingNumber, Integer storeId) {
        if (StringUtils.isBlank(trackingNumber)) {
            return null;
        }
        String lang = getStoreLanguage(storeId);
        String encryptedAWB = OrderEntityConverter.encryptAWB(trackingNumber);
        return buildTrackingUrlWithPath(lang + Constants.FRESHDESK_TRACKING_URL_WAYBILL_PARAM + encryptedAWB);
    }
    
    /**
     * Builds tracking URL with given path, handling base URL configuration
     * @param path The path to append (e.g., "en_US/?increment_id=123" or "en?waybill=abc")
     * @return Full URL if base URL is configured, or just the path otherwise
     */
    private String buildTrackingUrlWithPath(String path) {
        if (StringUtils.isBlank(shippingNavikBaseUrl)) {
            // If base URL is not configured, still return the path (for backward compatibility)
            LOGGER.warn("shippingNavikBaseUrl is not configured, returning path only");
            return "/" + path;
        }
        // Ensure base URL doesn't end with /
        String baseUrl = shippingNavikBaseUrl.endsWith("/") 
                ? shippingNavikBaseUrl.substring(0, shippingNavikBaseUrl.length() - 1) 
                : shippingNavikBaseUrl;
        return baseUrl + "/" + path;
    }
    
    private LinkedHashMap<String, String> getTrackingData(SalesOrder salesOrder) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        try {
            SalesShipmentTrack track = findFirstValidTracking(salesOrder);
            if (track == null) {
                return result;
            }
            
            String trackingUrl = buildTrackingUrlForOrder(track.getTrackNumber(), salesOrder.getStoreId());
            if (StringUtils.isNotBlank(trackingUrl)) {
                LOGGER.info(Constants.FRESHDESK_LOG_JOIN_TRACKING_URL + trackingUrl + ", for Order : " + salesOrder.getIncrementId());
                result.put(Constants.SHIPPING_URL, trackingUrl);
                result.put(Constants.SHIPPING_TITLE, track.getTitle());
            }
        } catch (Exception e) {
            LOGGER.error("Error getting tracking data for order: " + (salesOrder != null ? salesOrder.getIncrementId() : "null"), e);
        }
        return result;
    }


    Integer decodeStepValue(SalesOrder salesOrder) {
        Integer result = null;
        try {
            Map<String, Integer> statusStatesMap = staticComponents.getStatusStepMap();
            if (MapUtils.isNotEmpty(statusStatesMap) &&
                    statusStatesMap.get(salesOrder.getStatus()) != null) {
                result = statusStatesMap.get(salesOrder.getStatus());
            }
        } catch (Exception e) {
            LOGGER.error("error decodeStepValue(salesOrder) : "+e.getMessage());
        }
       return result;
    }

    Integer decodeColorValue(SalesOrder salesOrder) {
        Integer result = null;
        try {
            Map<String, Integer> statusColorsMap = staticComponents.getStatusColorsStepMap();
            if (MapUtils.isNotEmpty(statusColorsMap) &&
                    statusColorsMap.get(salesOrder.getStatus()) != null) {
                result = statusColorsMap.get(salesOrder.getStatus());
            }

        } catch (Exception e) {
            LOGGER.error("error decodeColorValue(salesOrder) : "+e.getMessage());
        }
        return result;
    }

	@Override
	public GenericApiResponse<MobileOrderListResponse> getMobileReturnList(MobileOrderListRequest requestBody,
			String authorizationToken, boolean unPicked) {

		GenericApiResponse<MobileOrderListResponse> finalResponse = new GenericApiResponse<>();
		final List<Integer> statusFilter = Arrays.asList(1,2,3,4,5,9,12,13,21);
		try {
			if (!configService.checkAuthorizationExternal(authorizationToken)) {
				finalResponse.setStatus(false);
				finalResponse.setStatusCode("401");
				finalResponse.setStatusMsg(Constants.MISSING_OR_WRONG_TOKEN);
				return finalResponse;
			}
			final List<Stores> stores = Constants.getStoresList();
			Stores store = getStoreFromNumber(requestBody.getPhoneNo(), stores);

			String formattedNumber = getFormatterNumber(requestBody.getPhoneNo(), store);

			List<String> rmaIds;
			if (unPicked) {
				rmaIds = salesOrderAddressRepository.findOrderReturnsByTelephoneAndStatus(formattedNumber,
						statusFilter);
			} else {
				rmaIds = salesOrderAddressRepository.findOrderReturnsByTelephone(formattedNumber);
			}
			String resultMode = Constants.RESULT_MODE_SHIPPING;

			if (CollectionUtils.isEmpty(rmaIds)) {
				CustomerEntity customerEntity = orderHelper.getCustomerDetails(null, null, formattedNumber);
				if (customerEntity != null && customerEntity.getEntityId() != null
						&& customerEntity.getEntityId() > 0) {
					if (unPicked)
						rmaIds = salesOrderAddressRepository.findOrderReturnsByCustomer(customerEntity.getEntityId());
					else
						rmaIds = salesOrderAddressRepository
								.findOrderReturnsByCustomerAndStatus(customerEntity.getEntityId(), statusFilter);
					resultMode = Constants.RESULT_MODE_CUSTOMER;
				}
			}

			MobileOrderListResponse response = processReturns(rmaIds, resultMode);
			finalResponse.setStatus(true);
			finalResponse.setStatusCode("200");
			finalResponse.setStatusMsg(Constants.SUCCESS_MSG);
			finalResponse.setReturnCount(rmaIds.size());
			finalResponse.setResponse(response);

		} catch (Exception e) {
			LOGGER.error("getMobileOrderList error: " + e.getMessage());
			finalResponse.setStatus(false);
			finalResponse.setStatusCode("201");
			finalResponse.setStatusMsg(Constants.UNKNOWN_ERROR);
			return finalResponse;
		}

		return finalResponse;

	}

	/**
	 * Process Returns list
	 * @param rmaIds
	 * @param resultMode
	 * @return
	 */
	private MobileOrderListResponse processReturns(List<String> rmaIds, String resultMode) {
		MobileOrderListResponse response = new MobileOrderListResponse();
		LinkedHashMap<String, String> idsObject = new LinkedHashMap<>();
		ArrayList<String> idsList = new ArrayList<>();
		if (CollectionUtils.isNotEmpty(rmaIds)) {
			int length = 3;
			if (Constants.getOmsBaseConfigs() != null && Constants.getOmsBaseConfigs().getWhatsappOrderConfig() != null
					&& Constants.getOmsBaseConfigs().getWhatsappOrderConfig().getRmaListCount() <= rmaIds.size()) {
				length = Constants.getOmsBaseConfigs().getWhatsappOrderConfig().getRmaListCount();
			}
			for (String rmaId : rmaIds) {
				if (rmaIds.indexOf(rmaId) + 1 > length)
					break;
				final String id = rmaId;
				idsObject.put("id" + (rmaIds.indexOf(rmaId) + 1), id);
				idsList.add(id);
			}
			response.setOrderCount(idsObject.size() + StringUtils.EMPTY);
			response.setIdObject(idsObject);
			response.setIncrementIds(idsList);
			response.setIdsString(String.join("\n", idsList));
			response.setResultMode(resultMode);
		} else {
			response.setOrderCount(String.valueOf(0));
			response.setIdObject(idsObject);
			response.setIncrementIds(idsList);
			response.setIdsString(StringUtils.EMPTY);
		}
		return response;
	}

	@Override
	public GenericApiResponse<MobileReturnDetailResponse> getMobileReturnDetails(MobileOrderDetailRequest requestBody,
			String authorizationToken) {
		GenericApiResponse<MobileReturnDetailResponse> finalResponse = new GenericApiResponse<>();
		try {
			if (!configService.checkAuthorizationExternal(authorizationToken)) {
				finalResponse.setStatus(false);
				finalResponse.setStatusCode("401");
				finalResponse.setStatusMsg(Constants.MISSING_OR_WRONG_TOKEN);
				return finalResponse;
			}
			List<AmastyRmaRequest> rmaRequests = rmaRequestRepository.findByOrderOrRmaOrAwb(requestBody.getId());
			if (Objects.isNull(rmaRequests) || rmaRequests.isEmpty()) {
				finalResponse.setStatus(false);
				finalResponse.setStatusCode("202");
				finalResponse.setStatusMsg("No returns found!");
				return finalResponse;
			}
			
			Map<String, MobileReturnDetailResponse> respnseMap = new HashMap<>();
			int id = 0;
			for (AmastyRmaRequest rmaRequest : rmaRequests) {
				MobileReturnDetailResponse returnDetail = processReturnDetails(rmaRequest);
				returnDetail.setReturnIncrementId(rmaRequest.getRmaIncId());
				respnseMap.put("RId" + (++id), returnDetail);
			}
			finalResponse.setStatus(true);
			finalResponse.setStatusCode("200");
			finalResponse.setStatusMsg(Constants.SUCCESS_MSG);
			finalResponse.setResponses(respnseMap);
			finalResponse.setReturnCount(respnseMap.size());
		} catch (Exception e) {
			LOGGER.error("getMobileReturnbDetails error: " + e);
			finalResponse.setStatus(false);
			finalResponse.setStatusCode("201");
			finalResponse.setStatusMsg(Constants.UNKNOWN_ERROR);
			return finalResponse;
		}
		return finalResponse;
	}

	private MobileReturnDetailResponse processReturnDetails(AmastyRmaRequest rmaRequest) {
		MobileReturnDetailResponse response = new MobileReturnDetailResponse();
		try {
			SalesOrder salesOrder = salesOrderRepository.findByEntityId(rmaRequest.getOrderId());
			response.setFailedAttempt(null);

			setPickupAddress(rmaRequest, salesOrder, response);
			List<SalesCreditmemo> rmaCreditMemo = creditmemoRepository
					.findByRmaNumber(rmaRequest.getRequestId().toString());
			if(!rmaCreditMemo.isEmpty()) {
				SalesCreditmemo salesCreditmemo = rmaCreditMemo.get(0);
				response.setRefundDate(UtilityConstant.ConvertTimeZone(salesCreditmemo.getCreatedAt(), salesOrder.getStoreId()));
				response.setPaymentRRN(salesCreditmemo.getReconciliationReference());
			}
			BigDecimal totalRefundAmount = rmaCreditMemo.stream()
					.map(memo -> memo.getGrandTotal().add(memo.getAmstorecreditAmount()))
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			if (Objects.nonNull(totalRefundAmount) && !BigDecimal.ZERO.equals(totalRefundAmount)) {
				response.setRefundAmount(totalRefundAmount.toString());
			} else {
				List<BigDecimal> itemTotals = new ArrayList<>();
				Set<SalesOrderItem> orderItem = salesOrder.getSalesOrderItem();
				rmaRequest.getAmastyRmaRequestItems().forEach(rmaitem -> {
					Optional<SalesOrderItem> orderItm = orderItem.stream()
							.filter(it -> it.getItemId().equals(rmaitem.getOrderItemId())).findFirst();
					if (orderItm.isPresent()) {
						SalesOrderItem salesOrderItem = orderItm.get();
						BigDecimal itemTotal = salesOrderItem.getRowTotalInclTax()
								.subtract(salesOrderItem.getDiscountAmount())
								.divide(salesOrderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP)
								.multiply(rmaitem.getQty());
						itemTotals.add(itemTotal);
					}
				});
				BigDecimal refundAmount = itemTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
				DecimalFormat df = new DecimalFormat("#.##");
				response.setRefundAmount(df.format(refundAmount.doubleValue()));
			}
			response.setRefundStatus(StringUtils.EMPTY);
			Timestamp createdAt = rmaRequest.getCreatedAt();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			String rmaCreatedAt = sdf.format(createdAt);
			LocalDate returnDate = LocalDate.parse(rmaCreatedAt);
			LocalDate today = LocalDate.now();
			long daysBetween = ChronoUnit.DAYS.between(returnDate, today);
			response.setReturnDate(rmaCreatedAt);
			response.setCountOfpendingDays(daysBetween + StringUtils.EMPTY);

			setTrackingInfo(rmaRequest, salesOrder, response);

		} catch (Exception e) {
			LOGGER.error("Error in processing return details : ", e);
		}
		return response;
	}

	private void setTrackingInfo(AmastyRmaRequest rmaRequest, SalesOrder salesOrder,
			MobileReturnDetailResponse response) {
		List<AmastyRmaTracking> rmaTracking = rmaTrackingRepository.findByRequestId(rmaRequest.getRequestId());
		Optional<AmastyRmaTracking> tracking = rmaTracking.stream().findAny();
		if (!tracking.isPresent())
			return;
		AmastyRmaTracking amastyRmaTracking = tracking.get();
		OmsBaseConfigs omsBaseConfigs = Constants.getOmsBaseConfigs();
		Optional<CarrierCode> carrier = omsBaseConfigs.getConfigs().getCarrierCodes().stream()
				.filter(code -> code.getCode().equals(amastyRmaTracking.getTrackingCode())).findFirst();
		if (carrier.isPresent()) {
			CarrierCode carrierCode = carrier.get();
			response.setShippingCompany(carrierCode.getLabel());
		}

		String rmaPaymentMethod = rmaRequest.getRmaPaymentMethod();
		if ((Objects.isNull(rmaPaymentMethod) || rmaPaymentMethod.isEmpty()) && Objects.nonNull(salesOrder)) {
			Optional<SalesOrderPayment> orderPayment = salesOrder.getSalesOrderPayment().stream().findFirst();
			if (orderPayment.isPresent()) {
				SalesOrderPayment salesOrderPayment = orderPayment.get();
				rmaPaymentMethod = salesOrderPayment.getMethod();
			}
		}
		LinkedHashMap<String, TranslationItem> paymentMethodTranslations = omsBaseConfigs.getConfigs()
				.getPaymentMethodTranslations();
		TranslationItem paymentMethodEn = paymentMethodTranslations.get(rmaPaymentMethod);
		response.setRefundPaymentMode(Objects.nonNull(paymentMethodEn) ? paymentMethodEn.getEn() : rmaPaymentMethod);

		final List<Stores> stores = Constants.getStoresList();

		Integer storeId = Objects.nonNull(salesOrder) ? salesOrder.getStoreId() : 1;
		Optional<Stores> store = stores.stream().filter(st -> st.getStoreId().equals(storeId + StringUtils.EMPTY)).findAny();
		String lang = "en";
		if (store.isPresent()) {
			Stores storeValue = store.get();
			if (StringUtils.isNotBlank(storeValue.getStoreLanguage()))
				lang = storeValue.getStoreLanguage();
		}
		String shippingUrl = shippingNavikBaseUrl + "/" + lang + Constants.FRESHDESK_TRACKING_URL_WAYBILL_PARAM + amastyRmaTracking.getTrackingNumber();
		response.setReturnLink(shippingUrl);
	}
	
	private void setPickupAddress(AmastyRmaRequest rmaRequest, SalesOrder salesOrder, MobileReturnDetailResponse response) {
		AmastyRmaStatus amastyRmaStatus = amastyRmaStatusRepository.findByStatusId(rmaRequest.getStatus());
		if (Objects.nonNull(amastyRmaStatus))
			response.setReturnStatus(amastyRmaStatus.getTitle());
		OrderAddress pickupAddress = orderRMAService.setPickupAddress(salesOrder);
		if (Objects.nonNull(pickupAddress)) {
			StringBuilder builder = new StringBuilder();
			builder.append(setAddressValues(pickupAddress.getFirstName() + " " + pickupAddress.getLastName()));
			builder.append(setAddressValues(pickupAddress.getArea()));
			builder.append(setAddressValues(pickupAddress.getBuildingNumber()));
			builder.append(setAddressValues(pickupAddress.getStreetAddress()));
			builder.append(setAddressValues(pickupAddress.getCity()));
			builder.append(setAddressValues(pickupAddress.getRegion()));
			builder.append(setAddressValues(pickupAddress.getCountry()));
			response.setPickupAddress(builder.toString());
			response.setPickupContact(pickupAddress.getMobileNumber());
		}
	}
	
	private String setAddressValues(String value) {
		if(Objects.isNull(value) || value.isEmpty()) return StringUtils.EMPTY;
		return value + ", ";
	}
	
    /**
     * Finds shipping address from sales order
     */
    private SalesOrderAddress findShippingAddress(SalesOrder salesOrder) {
        if (salesOrder == null || CollectionUtils.isEmpty(salesOrder.getSalesOrderAddress())) {
            return null;
        }
        for (SalesOrderAddress address : salesOrder.getSalesOrderAddress()) {
            if (address.getAddressType().equalsIgnoreCase(Constants.QUOTE_ADDRESS_TYPE_SHIPPING)) {
                return address;
            }
        }
        return null;
    }
    
    /**
     * Sets shipping address and phone number on response
     */
    private void setShippingAddressAndPhoneNo(SalesOrder salesOrder, MobileOrderDetailResponse response) {
        if (response == null) {
            return;
        }
        SalesOrderAddress shippingAddress = findShippingAddress(salesOrder);
        if (shippingAddress == null) {
            return;
        }
        
        if (shippingAddress.getTelephone() != null) {
            response.setShippingMobileNo(shippingAddress.getTelephone());
        }
        
        String formattedAddress = getDisplayableAddress(shippingAddress);
        if (StringUtils.isNotEmpty(formattedAddress)) {
            response.setShippingAddress(formattedAddress);
        }
    }


    private String getDisplayableAddress(SalesOrderAddress shippingAddress) {
        String result = StringUtils.EMPTY;
            if(StringUtils.isNotEmpty(shippingAddress.getStreet())) {
                result = result + ((!result.isEmpty()) ? ", " : StringUtils.EMPTY) + shippingAddress.getStreet();
            }

            if(StringUtils.isNotEmpty(shippingAddress.getNearestLandmark())) {
                result = result + ((!result.isEmpty()) ? ", " : StringUtils.EMPTY) + shippingAddress.getNearestLandmark();
            }
            if(StringUtils.isNotEmpty(shippingAddress.getArea())) {
                result = result + ((!result.isEmpty()) ? ", " : StringUtils.EMPTY) + shippingAddress.getArea();
            }
            if(StringUtils.isNotEmpty(shippingAddress.getCity())) {
                result = result + ((!result.isEmpty()) ? ", " : StringUtils.EMPTY) + shippingAddress.getCity();
            }
            if(StringUtils.isNotEmpty(shippingAddress.getRegion())) {
                result = result + ((!result.isEmpty()) ? ", " : StringUtils.EMPTY) + shippingAddress.getRegion();
            }
            if(StringUtils.isNotEmpty(shippingAddress.getCountryId())) {
                result = result + ((!result.isEmpty()) ? ", " : StringUtils.EMPTY) + shippingAddress.getCountryId();
            }
        return result;
    }

    /**
     * Gets shipment entries by phone number or customer ID
     */
    private ShipmentEntriesResult getShipmentEntries(String formattedNumber, long thresholdTimestamp) {
        List<Object[]> entries = salesOrderAddressRepository
                .findUndeliveredShipmentsByTelephone(formattedNumber, thresholdTimestamp);
        String resultMode = Constants.RESULT_MODE_SHIPPING;
        
        if (CollectionUtils.isEmpty(entries)) {
            CustomerEntity customerEntity = orderHelper.getCustomerDetails(null, null, formattedNumber);
            if (customerEntity != null && customerEntity.getEntityId() != null && customerEntity.getEntityId() > 0) {
                entries = salesOrderAddressRepository.findUndeliveredShipmentsByCustomer(
                        customerEntity.getEntityId(), thresholdTimestamp);
                resultMode = Constants.RESULT_MODE_CUSTOMER;
            }
        }
        
        return new ShipmentEntriesResult(entries, resultMode);
    }
    
    /**
     * Result holder for shipment entries lookup
     */
    private static class ShipmentEntriesResult {
        private final List<Object[]> entries;
        private final String resultMode;
        
        public ShipmentEntriesResult(List<Object[]> entries, String resultMode) {
            this.entries = entries;
            this.resultMode = resultMode;
        }
        
        public List<Object[]> getEntries() { return entries; }
        public String getResultMode() { return resultMode; }
    }
    
    /**
     * Calculates the maximum number of entries to process
     */
    private int calculateMaxEntriesCount(List<Object[]> entries) {
        int length = Math.min(entries.size(), Constants.FRESHDESK_DEFAULT_ORDER_LIST_COUNT);
        OmsBaseConfigs omsBaseConfigs = Constants.getOmsBaseConfigs();
        if (omsBaseConfigs != null && omsBaseConfigs.getWhatsappOrderConfig() != null 
                && omsBaseConfigs.getWhatsappOrderConfig().getOrderListCount() != null) {
            Integer configCount = omsBaseConfigs.getWhatsappOrderConfig().getOrderListCount();
            if (configCount > length && configCount <= entries.size()) {
                length = configCount;
            }
        }
        return length;
    }
    
    /**
     * Processes a single shipment entry and adds it to the collections if valid
     */
    private boolean processSingleShipmentEntry(Object[] row, Set<String> processedOrderIds, 
            LinkedHashMap<String, MobileShipmentListResponse.ShipmentInfo> incrementIds,
            LinkedHashMap<String, String> idObject,
            ArrayList<String> idsStringList) {
        String orderId = (String) row[0];
        
        if (processedOrderIds.contains(orderId)) {
            return false;
        }
        
        SalesOrder salesOrder = salesOrderRepository.findByIncrementId(orderId);
        if (salesOrder == null) {
            return false;
        }
        
        MobileShipmentListResponse.ShipmentInfo shipmentInfo = createShipmentInfoForList(salesOrder, orderId);
        if (shipmentInfo == null) {
            return false;
        }
        
        int index = incrementIds.size();
        incrementIds.put(String.valueOf(index), shipmentInfo);
        idObject.put("id" + (index + 1), orderId);
        idsStringList.add(orderId);
        
        processedOrderIds.add(orderId);
        return true;
    }
    
    /**
     * Processes shipment entries and builds response data
     */
    private void processShipmentEntries(List<Object[]> entries, int maxCount, String resultMode,
            MobileShipmentListResponse response) {
        Set<String> processedOrderIds = new LinkedHashSet<>();
        LinkedHashMap<String, MobileShipmentListResponse.ShipmentInfo> incrementIds = new LinkedHashMap<>();
        LinkedHashMap<String, String> idObject = new LinkedHashMap<>();
        ArrayList<String> idsStringList = new ArrayList<>();
        
        for (int i = 0; i < entries.size() && processedOrderIds.size() < maxCount; i++) {
            processSingleShipmentEntry(entries.get(i), processedOrderIds, incrementIds, 
                    idObject, idsStringList);
        }
        
        response.setOrderCount(incrementIds.size());
        response.setIdObject(idObject);
        response.setIncrementIds(incrementIds);
        response.setIdsString(String.join("\n", idsStringList));
        response.setResultMode(resultMode);
    }
    
    @Override
    public GenericApiResponse<MobileShipmentListResponse> getMobileShipmentList(
            MobileOrderListRequest requestBody, Map<String, String> requestHeader, String authorizationToken) {
        GenericApiResponse<MobileShipmentListResponse> finalResponse = new GenericApiResponse<>();
        try {
            if (!configService.checkAuthorizationExternal(authorizationToken)) {
                finalResponse.setStatus(false);
                finalResponse.setStatusCode("401");
                finalResponse.setStatusMsg(Constants.MISSING_OR_WRONG_TOKEN);
                return finalResponse;
            }
            
            final long now = Instant.now().toEpochMilli();
            final List<Stores> stores = Constants.getStoresList();
            Stores store = getStoreFromNumber(requestBody.getPhoneNo(), stores);
            String formattedNumber = getFormatterNumber(requestBody.getPhoneNo(), store);
            
            ShipmentEntriesResult entriesResult = getShipmentEntries(formattedNumber, now - orderInterval);
            List<Object[]> entries = entriesResult.getEntries();
            
            MobileShipmentListResponse response = new MobileShipmentListResponse();
            
            if (CollectionUtils.isNotEmpty(entries)) {
                int maxCount = calculateMaxEntriesCount(entries);
                processShipmentEntries(entries, maxCount, entriesResult.getResultMode(), response);
            } else {
                response.setOrderCount(0);
                response.setIdObject(new LinkedHashMap<>());
                response.setIncrementIds(new LinkedHashMap<>());
                response.setIdsString(StringUtils.EMPTY);
                response.setResultMode(entriesResult.getResultMode());
            }
            
            finalResponse.setStatus(true);
            finalResponse.setStatusCode("200");
            finalResponse.setStatusMsg(Constants.SUCCESS_MSG);
            finalResponse.setResponse(response);

        } catch (IllegalArgumentException | NullPointerException e) {
            LOGGER.error("getMobileShipmentList validation error: " + e.getMessage(), e);
            finalResponse.setStatus(false);
            finalResponse.setStatusCode("400");
            finalResponse.setStatusMsg("Invalid request: " + e.getMessage());
            return finalResponse;
        } catch (Exception e) {
            LOGGER.error("getMobileShipmentList unexpected error: " + e.getMessage(), e);
            finalResponse.setStatus(false);
            finalResponse.setStatusCode("201");
            finalResponse.setStatusMsg(Constants.UNKNOWN_ERROR);
            return finalResponse;
        }
        return finalResponse;
    }

    @Override
    public GenericApiResponse<MobileShipmentDetailResponse> getMobileShipmentDetails(
            MobileOrderDetailRequest requestBody, Map<String, String> requestHeader, String authorizationToken) {
        GenericApiResponse<MobileShipmentDetailResponse> finalResponse = new GenericApiResponse<>();
        try {
            final Instant nowInstant = Instant.now();
            final long now = nowInstant.toEpochMilli();
            if(!configService.checkAuthorizationExternal(authorizationToken)) {
                finalResponse.setStatus(false);
                finalResponse.setStatusCode("401");
                finalResponse.setStatusMsg(Constants.MISSING_OR_WRONG_TOKEN);
                return finalResponse;
            }

            String requestedId = requestBody.getId();
            if (StringUtils.isBlank(requestedId)) {
                finalResponse.setStatus(false);
                finalResponse.setStatusCode("202");
                finalResponse.setStatusMsg("Invalid request: ID is required");
                return finalResponse;
            }
            
            // Parse the requested ID to find the order and shipment
            ShipmentDetailContext context = parseShipmentId(requestedId);
            if (context == null || context.getSalesOrder() == null) {
                finalResponse.setStatus(false);
                finalResponse.setStatusCode("202");
                finalResponse.setStatusMsg("No orders found!");
                return finalResponse;
            }

            SalesOrder salesOrder = context.getSalesOrder();
            Integer storeId = salesOrder.getStoreId();

            MobileShipmentDetailResponse response = new MobileShipmentDetailResponse();
            response.setIncrementId(salesOrder.getIncrementId());
            response.setOrderId(salesOrder.getIncrementId());

            // Populate shipment details directly in response
            if (context.getSplitSalesOrder() != null) {
                populateShipmentDetailInResponse(response, context.getSplitSalesOrder(), salesOrder, storeId, now);
            } else {
                populateShipmentDetailInResponse(response, salesOrder, storeId, now);
            }

            finalResponse.setStatus(true);
            finalResponse.setStatusCode("200");
            finalResponse.setStatusMsg(Constants.SUCCESS_MSG);
            finalResponse.setResponse(response);

        } catch (IllegalArgumentException | NullPointerException e) {
            LOGGER.error("getMobileShipmentDetails validation error: " + e.getMessage(), e);
            finalResponse.setStatus(false);
            finalResponse.setStatusCode("400");
            finalResponse.setStatusMsg("Invalid request: " + e.getMessage());
            return finalResponse;
        } catch (Exception e) {
            LOGGER.error("getMobileShipmentDetails unexpected error: " + e.getMessage(), e);
            finalResponse.setStatus(false);
            finalResponse.setStatusCode("201");
            finalResponse.setStatusMsg(Constants.UNKNOWN_ERROR);
            return finalResponse;
        }
        return finalResponse;
    }


    private String getStatusLabelForSplitOrder(SplitSalesOrder splitOrder, Integer storeId) {
        String result = StringUtils.EMPTY;
        try {
            SalesOrderStatusLabelPK key = new SalesOrderStatusLabelPK();
            key.setStatus(splitOrder.getStatus());
            key.setStoreId(storeId);
            SalesOrderStatusLabel label = salesOrderStatusLabelRepository.findById(key);
            if (label != null) {
                result = label.getLabel();
            } else {
                result = splitOrder.getStatus();
            }
        } catch (Exception e) {
            LOGGER.error("Error getting status label for split order: " + e.getMessage());
        }
        return result;
    }
    
    private Integer decodeStepValueForSplitOrder(SplitSalesOrder splitOrder) {
        Integer result = null;
        try {
            Map<String, Integer> statusStatesMap = staticComponents.getStatusStepMap();
            if (MapUtils.isNotEmpty(statusStatesMap) &&
                    StringUtils.isNotBlank(splitOrder.getStatus()) &&
                    statusStatesMap.get(splitOrder.getStatus()) != null) {
                result = statusStatesMap.get(splitOrder.getStatus());
            }
        } catch (Exception e) {
            LOGGER.error("error decodeStepValueForSplitOrder: " + e.getMessage());
        }
        return result;
    }
    
    private Integer decodeColorValueForSplitOrder(SplitSalesOrder splitOrder) {
        Integer result = null;
        try {
            Map<String, Integer> statusColorsMap = staticComponents.getStatusColorsStepMap();
            if (MapUtils.isNotEmpty(statusColorsMap) &&
                    StringUtils.isNotBlank(splitOrder.getStatus()) &&
                    statusColorsMap.get(splitOrder.getStatus()) != null) {
                result = statusColorsMap.get(splitOrder.getStatus());
            }
        } catch (Exception e) {
            LOGGER.error("error decodeColorValueForSplitOrder: " + e.getMessage());
        }
        return result;
    }
    
    /**
     * Finds shipping address by address type
     */
    private SalesOrderAddress findShippingAddressByType(SalesOrder salesOrder) {
        if (salesOrder == null || CollectionUtils.isEmpty(salesOrder.getSalesOrderAddress())) {
            return null;
        }
        for (SalesOrderAddress address : salesOrder.getSalesOrderAddress()) {
            if (Constants.QUOTE_ADDRESS_TYPE_SHIPPING.equalsIgnoreCase(address.getAddressType())) {
                return address;
            }
        }
        return null;
    }
    
    /**
     * Sets shipping address and phone on shipment detail
     */
    private void setShippingAddressAndPhoneNoForShipmentDetail(SalesOrder salesOrder, 
            MobileOrderDetailResponse.ShipmentDetail detail) {
        if (detail == null) {
            return;
        }
        SalesOrderAddress address = findShippingAddressByType(salesOrder);
        if (address == null) {
            return;
        }
        
        if (StringUtils.isNotBlank(address.getTelephone())) {
            detail.setShippingMobileNo(address.getTelephone());
        }
        
        String formattedAddress = getDisplayableAddress(address);
        if (StringUtils.isNotEmpty(formattedAddress)) {
            detail.setShippingAddress(formattedAddress);
        }
    }
    
    /**
     * Populates ShipmentDetail DTO from common data
     */
    private void populateShipmentDetailFromData(MobileOrderDetailResponse.ShipmentDetail detail, 
            ShipmentDetailData data) {
        Optional.ofNullable(data.getShipmentId()).ifPresent(detail::setShipmentId);
        Optional.ofNullable(data.getStatus()).ifPresent(detail::setStatus);
        Optional.ofNullable(data.getShipmentStatus()).ifPresent(detail::setShipmentStatus);
        Optional.ofNullable(data.getStepValue()).ifPresent(detail::setStepValue);
        Optional.ofNullable(data.getColorValue()).ifPresent(detail::setColorValue);
        Optional.ofNullable(data.getDeliveryDayCount()).ifPresent(detail::setDeliveryDayCount);
        Optional.ofNullable(data.getEstimatedDeliveryTime()).ifPresent(detail::setEstimatedDeliveryTime);
        Optional.ofNullable(data.getPaymentMode()).ifPresent(detail::setPaymentMode);
        Optional.ofNullable(data.getAmount()).ifPresent(detail::setAmount);
        Optional.ofNullable(data.getShippingUrl()).ifPresent(detail::setShippingUrl);
    }
    
    /**
     * Populates MobileShipmentDetailResponse from common data
     */
    private void populateShipmentDetailResponseFromData(MobileShipmentDetailResponse response, 
            ShipmentDetailData data) {
        Optional.ofNullable(data.getShipmentId()).ifPresent(response::setShipmentId);
        Optional.ofNullable(data.getStatus()).ifPresent(response::setStatus);
        Optional.ofNullable(data.getShipmentStatus()).ifPresent(response::setShipmentStatus);
        Optional.ofNullable(data.getStepValue()).ifPresent(response::setStepValue);
        Optional.ofNullable(data.getColorValue()).ifPresent(response::setColorValue);
        Optional.ofNullable(data.getDeliveryDayCount()).ifPresent(response::setDeliveryDayCount);
        Optional.ofNullable(data.getEstimatedDeliveryTime()).ifPresent(response::setEstimatedDeliveryTime);
        Optional.ofNullable(data.getPaymentMode()).ifPresent(response::setPaymentMode);
        Optional.ofNullable(data.getAmount()).ifPresent(response::setAmount);
    }
    
    private MobileOrderDetailResponse.ShipmentDetail createShipmentDetailFromSplitOrderForOrderDetail(
            SplitSalesOrder splitOrder, SalesOrder salesOrder, Integer storeId, long now) {
        MobileOrderDetailResponse.ShipmentDetail detail = new MobileOrderDetailResponse.ShipmentDetail();
        ShipmentDetailData data = buildShipmentDetailData(splitOrder, salesOrder, storeId, now);
        populateShipmentDetailFromData(detail, data);
        setShippingAddressAndPhoneNoForShipmentDetail(salesOrder, detail);
        return detail;
    }
    
    private MobileOrderDetailResponse.ShipmentDetail createShipmentDetailFromOrderForOrderDetail(
            SalesOrder salesOrder, Integer storeId, long now) {
        MobileOrderDetailResponse.ShipmentDetail detail = new MobileOrderDetailResponse.ShipmentDetail();
        ShipmentDetailData data = buildShipmentDetailData(salesOrder, storeId, now);
        populateShipmentDetailFromData(detail, data);
        setShippingAddressAndPhoneNoForShipmentDetail(salesOrder, detail);
        return detail;
    }
    
    /**
     * Sets Arabic translations on response for split orders
     */
    private void setArabicTranslations(MobileShipmentDetailResponse response, SplitSalesOrder splitOrder, 
            String status, Integer storeId) {
        String statusLabelAr = getStatusLabelArForSplitOrder(splitOrder, storeId);
        response.setShipmentStatusAr(StringUtils.isNotBlank(statusLabelAr) ? statusLabelAr : StringUtils.EMPTY);
        String statusAr = getStatusAr(status, storeId);
        response.setStatusAr(StringUtils.isNotBlank(statusAr) ? statusAr : StringUtils.EMPTY);
    }
    
    /**
     * Sets Arabic translations on response for normal orders
     */
    private void setArabicTranslations(MobileShipmentDetailResponse response, SalesOrder salesOrder, Integer storeId) {
        String statusLabelAr = getStatusLabelAr(salesOrder);
        response.setShipmentStatusAr(StringUtils.isNotBlank(statusLabelAr) ? statusLabelAr : StringUtils.EMPTY);
        String statusAr = getStatusAr(salesOrder.getStatus(), storeId);
        response.setStatusAr(StringUtils.isNotBlank(statusAr) ? statusAr : StringUtils.EMPTY);
    }
    
    private void populateShipmentDetailInResponse(MobileShipmentDetailResponse response, 
            SplitSalesOrder splitOrder, SalesOrder salesOrder, Integer storeId, long now) {
        ShipmentDetailData data = buildShipmentDetailData(splitOrder, salesOrder, storeId, now);
        populateShipmentDetailResponseFromData(response, data);
        setArabicTranslations(response, splitOrder, data.getStatus(), storeId);
        setShippingAddressAndPhoneNoForShipmentDetailResponse(salesOrder, response);
    }
    
    private void populateShipmentDetailInResponse(MobileShipmentDetailResponse response, 
            SalesOrder salesOrder, Integer storeId, long now) {
        ShipmentDetailData data = buildShipmentDetailData(salesOrder, storeId, now);
        populateShipmentDetailResponseFromData(response, data);
        setArabicTranslations(response, salesOrder, storeId);
        setShippingAddressAndPhoneNoForShipmentDetailResponse(salesOrder, response);
    }
    
    /**
     * Context class to hold shipment detail parsing results
     */
    private static class ShipmentDetailContext {
        private SalesOrder salesOrder;
        private SplitSalesOrder splitSalesOrder;
        
        public SalesOrder getSalesOrder() {
            return salesOrder;
        }
        
        public void setSalesOrder(SalesOrder salesOrder) {
            this.salesOrder = salesOrder;
        }
        
        public SplitSalesOrder getSplitSalesOrder() {
            return splitSalesOrder;
        }
        
        public void setSplitSalesOrder(SplitSalesOrder splitSalesOrder) {
            this.splitSalesOrder = splitSalesOrder;
        }
    }
    
    /**
     * Common shipment detail data holder to eliminate duplication
     */
    private static class ShipmentDetailData {
        private String shipmentId;
        private String status;
        private String shipmentStatus;
        private Integer stepValue;
        private Integer colorValue;
        private Integer deliveryDayCount;
        private String estimatedDeliveryTime;
        private String paymentMode;
        private String amount;
        private String shippingUrl;
        
        // Getters and setters
        public String getShipmentId() { return shipmentId; }
        public void setShipmentId(String shipmentId) { this.shipmentId = shipmentId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getShipmentStatus() { return shipmentStatus; }
        public void setShipmentStatus(String shipmentStatus) { this.shipmentStatus = shipmentStatus; }
        public Integer getStepValue() { return stepValue; }
        public void setStepValue(Integer stepValue) { this.stepValue = stepValue; }
        public Integer getColorValue() { return colorValue; }
        public void setColorValue(Integer colorValue) { this.colorValue = colorValue; }
        public Integer getDeliveryDayCount() { return deliveryDayCount; }
        public void setDeliveryDayCount(Integer deliveryDayCount) { this.deliveryDayCount = deliveryDayCount; }
        public String getEstimatedDeliveryTime() { return estimatedDeliveryTime; }
        public void setEstimatedDeliveryTime(String estimatedDeliveryTime) { this.estimatedDeliveryTime = estimatedDeliveryTime; }
        public String getPaymentMode() { return paymentMode; }
        public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
        public String getAmount() { return amount; }
        public void setAmount(String amount) { this.amount = amount; }
        public String getShippingUrl() { return shippingUrl; }
        public void setShippingUrl(String shippingUrl) { this.shippingUrl = shippingUrl; }
    }
    
    /**
     * Gets shipment ID for split order
     */
    private String getShipmentIdForSplitOrder(SplitSalesOrder splitOrder, SalesOrder salesOrder) {
        String shipmentId = splitOrder.getIncrementId();
        if (StringUtils.isBlank(shipmentId)) {
            boolean isGlobal = splitOrder.getHasGlobalShipment() != null && splitOrder.getHasGlobalShipment();
            shipmentId = salesOrder.getIncrementId() + (isGlobal ? Constants.FRESHDESK_SUFFIX_GLOBAL : Constants.FRESHDESK_SUFFIX_LOCAL);
        }
        return shipmentId;
    }
    
    /**
     * Gets currency code for split order
     */
    private String getCurrencyCodeForSplitOrder(SplitSalesOrder splitOrder, SalesOrder salesOrder) {
        if (StringUtils.isNotBlank(splitOrder.getOrderCurrencyCode())) {
            return splitOrder.getOrderCurrencyCode();
        }
        return StringUtils.isNotBlank(salesOrder.getOrderCurrencyCode()) 
                ? salesOrder.getOrderCurrencyCode() : StringUtils.EMPTY;
    }
    
    /**
     * Sets basic shipment data (ID, status, label, step, color)
     */
    private void setBasicShipmentData(ShipmentDetailData data, SplitSalesOrder splitOrder, Integer storeId) {
        String status = splitOrder.getStatus();
        if (StringUtils.isNotEmpty(status)) {
            data.setStatus(status);
        }
        
        String statusLabel = getStatusLabelForSplitOrder(splitOrder, storeId);
        if (StringUtils.isNotBlank(statusLabel)) {
            data.setShipmentStatus(statusLabel);
        }
        
        Integer stepValue = decodeStepValueForSplitOrder(splitOrder);
        if (stepValue != null) {
            data.setStepValue(stepValue);
        }
        
        Integer colorValue = decodeColorValueForSplitOrder(splitOrder);
        if (colorValue != null) {
            data.setColorValue(colorValue);
        }
    }
    
    /**
     * Sets delivery and payment data
     */
    private void setDeliveryAndPaymentData(ShipmentDetailData data, SplitSalesOrder splitOrder, 
            SalesOrder salesOrder, Integer storeId, long now) {
        Timestamp estimatedDelivery = splitOrder.getEstimatedDeliveryTime() != null 
                ? splitOrder.getEstimatedDeliveryTime() 
                : splitOrder.getEstimatedDelivery();
        Object[] deliveryData = calculateDeliveryDayCountAndDate(estimatedDelivery, now);
        data.setDeliveryDayCount((Integer) deliveryData[0]);
        if (deliveryData[1] != null && StringUtils.isNotBlank((String) deliveryData[1])) {
            data.setEstimatedDeliveryTime((String) deliveryData[1]);
        }
        
        SplitSalesOrderPayment splitPayment = splitOrder.getSplitSalesOrderPayments() != null ?
            splitOrder.getSplitSalesOrderPayments().stream().findFirst().orElse(null) : null;
        if (splitPayment != null && StringUtils.isNotBlank(splitPayment.getMethod())) {
            data.setPaymentMode(translatePaymentMode(splitPayment.getMethod(), storeId));
        }
        
        if (splitOrder.getGrandTotal() != null) {
            String currency = getCurrencyCodeForSplitOrder(splitOrder, salesOrder);
            data.setAmount(formatAmount(splitOrder.getGrandTotal(), currency));
        }
    }
    
    /**
     * Builds common shipment detail data from split order
     */
    private ShipmentDetailData buildShipmentDetailData(SplitSalesOrder splitOrder, SalesOrder salesOrder, 
            Integer storeId, long now) {
        ShipmentDetailData data = new ShipmentDetailData();
        data.setShipmentId(getShipmentIdForSplitOrder(splitOrder, salesOrder));
        setBasicShipmentData(data, splitOrder, storeId);
        setDeliveryAndPaymentData(data, splitOrder, salesOrder, storeId, now);
        // Set tracking URL for split order (always set, even if null, so field appears in response)
        String trackingUrl = getTrackingUrlForSplitOrder(splitOrder, storeId);
        data.setShippingUrl(trackingUrl); // Set even if null - let JSON serializer handle it
        if (StringUtils.isNotBlank(trackingUrl)) {
            LOGGER.info("Set shippingUrl in ShipmentDetailData for split order ID: " + splitOrder.getEntityId() + Constants.FRESHDESK_LOG_URL + trackingUrl);
        } else {
            LOGGER.warn("No tracking URL set for split order ID: " + splitOrder.getEntityId() + Constants.FRESHDESK_LOG_INCREMENT_ID_LOWER + splitOrder.getIncrementId());
        }
        return data;
    }
    
    /**
     * Builds common shipment detail data from normal order
     */
    private ShipmentDetailData buildShipmentDetailData(SalesOrder salesOrder, Integer storeId, long now) {
        ShipmentDetailData data = new ShipmentDetailData();
        
        // Set shipment ID
        data.setShipmentId(salesOrder.getIncrementId());
        
        // Set status
        if (StringUtils.isNotEmpty(salesOrder.getStatus())) {
            data.setStatus(salesOrder.getStatus());
        }
        
        // Set status label
        String statusLabel = getStatusLabel(salesOrder);
        if (StringUtils.isNotBlank(statusLabel)) {
            data.setShipmentStatus(statusLabel);
        }
        
        // Set step and color values
        Integer stepValue = decodeStepValue(salesOrder);
        if (stepValue != null) {
            data.setStepValue(stepValue);
        }
        Integer colorValue = decodeColorValue(salesOrder);
        if (colorValue != null) {
            data.setColorValue(colorValue);
        }
        
        // Set estimated delivery time
        Object[] deliveryData = calculateDeliveryDayCountAndDate(
                salesOrder.getEstimatedDeliveryTime(), now);
        data.setDeliveryDayCount((Integer) deliveryData[0]);
        if (deliveryData[1] != null && StringUtils.isNotBlank((String) deliveryData[1])) {
            data.setEstimatedDeliveryTime((String) deliveryData[1]);
        }
        
        // Set payment mode
        SalesOrderPayment salesOrderPayment = salesOrder.getSalesOrderPayment() != null ?
            salesOrder.getSalesOrderPayment().stream().findFirst().orElse(null) : null;
        if (salesOrderPayment != null && StringUtils.isNotBlank(salesOrderPayment.getMethod())) {
            data.setPaymentMode(translatePaymentMode(salesOrderPayment.getMethod(), storeId));
        }
        
        // Set amount
        if (salesOrder.getGrandTotal() != null) {
            String currency = StringUtils.isNotBlank(salesOrder.getOrderCurrencyCode())
                    ? salesOrder.getOrderCurrencyCode() : StringUtils.EMPTY;
            data.setAmount(formatAmount(salesOrder.getGrandTotal(), currency));
        }
        
        // Set tracking URL for normal order (always set, even if null, so field appears in response)
        String trackingUrl = getTrackingUrlForOrder(salesOrder, storeId);
        data.setShippingUrl(trackingUrl); // Set even if null - let JSON serializer handle it
        if (StringUtils.isNotBlank(trackingUrl)) {
            LOGGER.info("Set shippingUrl in ShipmentDetailData for order ID: " + salesOrder.getEntityId() + Constants.FRESHDESK_LOG_INCREMENT_ID + salesOrder.getIncrementId() + Constants.FRESHDESK_LOG_URL + trackingUrl);
        } else {
            LOGGER.warn("No tracking URL set for order ID: " + salesOrder.getEntityId() + Constants.FRESHDESK_LOG_INCREMENT_ID + salesOrder.getIncrementId());
        }
        
        return data;
    }
    
    /**
     * Finds split order matching the requested ID suffix
     */
    private SplitSalesOrder findMatchingSplitOrder(String requestedId, SalesOrder salesOrder) {
        boolean isGlobal = requestedId.endsWith(Constants.FRESHDESK_SUFFIX_GLOBAL);
        List<SplitSalesOrder> splitOrders = splitSalesOrderRepository.findByOrderId(salesOrder.getEntityId());
        
        if (CollectionUtils.isNotEmpty(splitOrders)) {
            for (SplitSalesOrder so : splitOrders) {
                boolean hasGlobal = so.getHasGlobalShipment() != null && so.getHasGlobalShipment();
                if ((isGlobal && hasGlobal) || (!isGlobal && !hasGlobal)) {
                    return so;
                }
            }
        }
        return null;
    }
    
    /**
     * Parses split order ID to find order and split order
     */
    private ShipmentDetailContext parseSplitOrderId(String requestedId) {
        String orderId = requestedId.replace(Constants.FRESHDESK_SUFFIX_GLOBAL, StringUtils.EMPTY).replace(Constants.FRESHDESK_SUFFIX_LOCAL, StringUtils.EMPTY);
        SalesOrder salesOrder = salesOrderRepository.findByIncrementId(orderId);
        
        if (salesOrder == null) {
            return null;
        }
        
        ShipmentDetailContext context = new ShipmentDetailContext();
        context.setSalesOrder(salesOrder);
        
        SplitSalesOrder splitOrder = findMatchingSplitOrder(requestedId, salesOrder);
        if (splitOrder != null) {
            context.setSplitSalesOrder(splitOrder);
        } else {
            // Try split seller shipment as fallback
            SplitSellerShipment splitSellerShipment = splitSellerShipmentRepository.findByIncrementId(requestedId);
            if (splitSellerShipment != null) {
                return context; // Use main order
            }
        }
        
        return context;
    }
    
    /**
     * Parses normal shipment/order ID
     */
    private ShipmentDetailContext parseNormalShipmentId(String requestedId) {
        ShipmentDetailContext context = new ShipmentDetailContext();
        
        SalesShipment salesShipment = salesShipmentRepository.findByIncrementId(requestedId);
        if (salesShipment != null) {
            context.setSalesOrder(salesOrderRepository.findByEntityId(salesShipment.getOrderId()));
            return context.getSalesOrder() != null ? context : null;
        }
        
        SplitSellerShipment splitSellerShipment = splitSellerShipmentRepository.findByIncrementId(requestedId);
        if (splitSellerShipment != null) {
            context.setSalesOrder(salesOrderRepository.findByEntityId(splitSellerShipment.getOrderId()));
            return context.getSalesOrder() != null ? context : null;
        }
        
        // Try as order ID
        SalesOrder salesOrder = salesOrderRepository.findByIncrementId(requestedId);
        if (salesOrder != null) {
            context.setSalesOrder(salesOrder);
            return context;
        }
        
        return null;
    }
    
    /**
     * Parses shipment ID to find the corresponding order and split order if applicable
     * @param requestedId The requested shipment/order ID
     * @return ShipmentDetailContext with order and split order, or null if not found
     */
    private ShipmentDetailContext parseShipmentId(String requestedId) {
        if (StringUtils.isBlank(requestedId)) {
            return null;
        }
        
        boolean isSplitOrderId = requestedId.contains(Constants.FRESHDESK_SUFFIX_GLOBAL) || requestedId.contains(Constants.FRESHDESK_SUFFIX_LOCAL);
        
        if (isSplitOrderId) {
            return parseSplitOrderId(requestedId);
        } else {
            return parseNormalShipmentId(requestedId);
        }
    }

    /**
     * Sets shipping address and phone on shipment detail response
     */
    private void setShippingAddressAndPhoneNoForShipmentDetailResponse(SalesOrder salesOrder, 
            MobileShipmentDetailResponse response) {
        if (response == null) {
            return;
        }
        SalesOrderAddress address = findShippingAddressByType(salesOrder);
        if (address == null) {
            return;
        }
        
        if (StringUtils.isNotBlank(address.getTelephone())) {
            response.setShippingMobileNo(address.getTelephone());
        }
        
        String formattedAddress = getDisplayableAddress(address);
        if (StringUtils.isNotEmpty(formattedAddress)) {
            response.setShippingAddress(formattedAddress);
        }
    }

}
