package org.styli.services.order.helper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.CoreConfigData;
import org.styli.services.order.model.Order.cancel.PayfortRefundResponse;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.SalesOrder.SalesCreditmemoComment;
import org.styli.services.order.model.SalesOrder.SalesCreditmemoGrid;
import org.styli.services.order.model.SalesOrder.SalesCreditmemoItemTax;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.rma.sequence.*;
import org.styli.services.order.model.sales.SalesCreditmemoItem;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesOrderStatusHistory;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrderItem;
import org.styli.services.order.pojo.RefundAmountObject;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.cancel.CancelOrderRequest;
import org.styli.services.order.pojo.cancel.MagentoAPIResponse;
import org.styli.services.order.pojo.cancel.MagentoRefundOrderRequest;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.request.Order.OmsProductTax;
import org.styli.services.order.repository.CoreConfigDataRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.repository.Rma.sequence.*;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoCommentRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoItemRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.styli.services.order.utility.PaymentUtility;


/**
 * @author Umesh, 17/05/2020
 * @project product-service
 */

@Component
public class RefundHelper {

    private static final Log LOGGER = LogFactory.getLog(RefundHelper.class);
    
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${magento.integration.token}")
    private String magentoIntegrationToken;

    @Value("${magento.base.url}")
    private String magentoBaseUrl;

    @Autowired
    SequenceCreditmemoOneRepository sequenceCreditmemoOneRepository;

    @Autowired
    SequenceCreditmemoThreeRepository sequenceCreditmemoThreeRepository;

    @Autowired
    SequenceCreditmemoSevenRepository sequenceCreditmemoSevenRepository;

    @Autowired
    SequenceCreditmemoElevenRepository sequenceCreditmemoElevenRepository;

    @Autowired
    SalesOrderGridRepository salesOrderGridRepository;

    @Autowired
    SalesCreditmemoRepository salesCreditmemoRepository;

    @Autowired
    SalesCreditmemoItemRepository salesCreditmemoItemRepository;

    @Autowired
    SalesCreditmemoCommentRepository salesCreditmemoCommentRepository;

    @Autowired
    SalesCreditmemoGridRepository salesCreditmemoGridRepository;

    @Autowired
    SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

    @Autowired
    AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;

    @Autowired
    AmastyStoreCreditRepository amastyStoreCreditRepository;

    @Autowired
    CoreConfigDataRepository coreConfigDataRepository;
    
    @Autowired
    SequenceCreditmemoTwentyOneRepository sequenceCreditmemoTwentyOneRepository;
    
    @Autowired
    SequenceCreditmemoNineteenRepository sequenceCreditmemoNineteenRepository;
    
    @Autowired
    SequenceCreditmemoSeventeenRepository sequenceCreditmemoSeventeenRepository;
    
    @Autowired
    SequenceCreditmemoFifteenRepository sequenceCreditmemoFifteenRepository;
    
    @Autowired
    SequenceCreditmemoFiftyOneRepository squenceCreditmemoFiftyOneRepository;

	@Autowired
	SequenceCreditmemoTwentyThreeRepository sequenceCreditmemoTwentyThreeRepository;

	@Autowired
	SequenceCreditmemoTwentyFiveRepository sequenceCreditmemoTwentyFiveRepository;

    @Autowired
    SalesOrderRepository salesOrderRepository;
    
    @Autowired
    PaymentDtfHelper paymentDtfHelper;
    
	@Autowired
	private AmastyRmaRequestRepository amastyRmaRequestRepository;
	
	@Autowired
	private OmsorderentityConverter omsorderentityConverter;
	
	@Value("${region.value}")
	private String regionValue;

    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;

	@Autowired
	PaymentUtility paymentUtility;

    private String generateIncrementId(Integer newSequenceValue, Integer storeId) {

        Integer incrementStartValue = 1;
        int incrementStepValue = 1;

        String storeIdStr = storeId == 1 ? "" : String.valueOf(storeId);

        return storeIdStr + String.format(OrderConstants.INCREMENT_PADDING,
                ((newSequenceValue - incrementStartValue) * incrementStepValue + incrementStartValue));
    }


    public String getIncrementId(Integer storeId) {

        String incrementId = null;

        try {

            switch (storeId) {
                default: {
                    SequenceCreditmemoOne sequenceOrderNew = new SequenceCreditmemoOne();
                    sequenceCreditmemoOneRepository.save(sequenceOrderNew);

                    Integer newSequenceValue = sequenceOrderNew.getSequenceValue();
                    incrementId = generateIncrementId(newSequenceValue, storeId);
                    break;
                }
                // Store id - 3
                case OrderConstants.STORE_ID_SA_AR: {
                    SequenceCreditmemoThree sequenceOrderNew = new SequenceCreditmemoThree();
                    sequenceCreditmemoThreeRepository.save(sequenceOrderNew);
                    Integer newSequenceValue = sequenceOrderNew.getSequenceValue();
                    incrementId = generateIncrementId(newSequenceValue, storeId);

                    break;
                }
                // Store id - 7
                case OrderConstants.STORE_ID_AE_EN: {
                    SequenceCreditmemoSeven sequenceOrderNew = new SequenceCreditmemoSeven();
                    sequenceCreditmemoSevenRepository.save(sequenceOrderNew);
                    Integer newSequenceValue = sequenceOrderNew.getSequenceValue();
                    incrementId = generateIncrementId(newSequenceValue, storeId);

                    break;
                }
                // Store id - 11
                case OrderConstants.STORE_ID_AE_AR: {
                    SequenceCreditmemoEleven sequenceOrderNew = new SequenceCreditmemoEleven();
                    sequenceCreditmemoElevenRepository.save(sequenceOrderNew);
                    Integer newSequenceValue = sequenceOrderNew.getSequenceValue();
                    incrementId = generateIncrementId(newSequenceValue, storeId);

                    break;
                }

                // Store id - 15
			case OrderConstants.STORE_ID_QA_EN: {

				SequenceCreditmemoFifteen sequenceOrderNew = new SequenceCreditmemoFifteen();
				sequenceCreditmemoFifteenRepository.save(sequenceOrderNew);
				Integer newSequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(newSequenceValue, storeId);

				break;
			}

             // Store id - 17
			case OrderConstants.STORE_ID_QA_AR: {

				SequenceCreditmemoSeventeen sequenceOrderNew = new SequenceCreditmemoSeventeen();
				sequenceCreditmemoSeventeenRepository.save(sequenceOrderNew);
				Integer newSequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(newSequenceValue, storeId);

				break;
			}

             // Store id - 19
			case OrderConstants.STORE_ID_BH_EN: {

				SequenceCreditmemoNineteen sequenceOrderNew = new SequenceCreditmemoNineteen();
				sequenceCreditmemoNineteenRepository.save(sequenceOrderNew);
				Integer newSequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(newSequenceValue, storeId);

				break;
			}

             // Store id - 21
			case OrderConstants.STORE_ID_BH_AR: {

				SequenceCreditmemoTwentyOne sequenceOrderNew = new SequenceCreditmemoTwentyOne();
				sequenceCreditmemoTwentyOneRepository.save(sequenceOrderNew);
				Integer newSequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(newSequenceValue, storeId);

				break;
			}
			// Store id - 23
				case OrderConstants.STORE_ID_OM_EN: {

					SequenceCreditmemoTwentyThree sequenceOrderNew = new SequenceCreditmemoTwentyThree();
					sequenceCreditmemoTwentyThreeRepository.save(sequenceOrderNew);
					Integer newSequenceValue = sequenceOrderNew.getSequenceValue();
					incrementId = generateIncrementId(newSequenceValue, storeId);

					break;
				}
				// Store id - 25
				case OrderConstants.STORE_ID_OM_AR: {

					SequenceCreditmemoTwentyFive sequenceOrderNew = new SequenceCreditmemoTwentyFive();
					sequenceCreditmemoTwentyFiveRepository.save(sequenceOrderNew);
					Integer newSequenceValue = sequenceOrderNew.getSequenceValue();
					incrementId = generateIncrementId(newSequenceValue, storeId);

					break;
				}

				// Store id - 51
			case OrderConstants.STORE_ID_IN_EN: {

				SequenceCreditmemoFiftyOne sequenceOrderNew = new SequenceCreditmemoFiftyOne();
				squenceCreditmemoFiftyOneRepository.save(sequenceOrderNew);
				Integer newSequenceValue = sequenceOrderNew.getSequenceValue();
				incrementId = generateIncrementId(newSequenceValue, storeId);

				break;
			}
            }

        } catch (DataAccessException e) {
            LOGGER.error("Could not create increment ID for credit memo. storeId: " + storeId);
        }

        return incrementId;
    }

    public SalesOrder cancelOrderObject(CancelOrderRequest request, SalesOrder order, boolean typeRefund,
            String paymentMethod) {

    	BigDecimal storeCreditAmount = null;
		if (typeRefund && paymentMethod != null && OrderConstants.checkPaymentMethod(paymentMethod) ) {

			storeCreditAmount = order.getAmstorecreditAmount();
			
			order.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
			
			order.setState(OrderConstants.CLOSED_ORDER_STATUS);
			order.setWmsStatus(2);

			order.setBaseDiscountRefunded(order.getBaseDiscountInvoiced());
			order.setDiscountRefunded(order.getDiscountInvoiced());
			order.setBaseShippingRefunded(order.getBaseShippingInvoiced());
			order.setShippingRefunded(order.getShippingInvoiced());
			order.setBaseSubtotalRefunded(order.getBaseSubtotalInvoiced());
			order.setSubtotalRefunded(order.getSubtotalInvoiced());
			order.setBaseTaxRefunded(order.getBaseTaxInvoiced());
			order.setTaxRefunded(order.getTaxInvoiced());
			order.setBaseTotalOnlineRefunded(order.getBaseTotalPaid());
			order.setTotalRefunded(order.getBaseTotalPaid());
			order.setTotalOnlineRefunded(order.getBaseTotalPaid());
			order.setBaseTotalRefunded(order.getBaseTotalPaid());
			if (storeCreditAmount != null) {
				order.setToRefund(storeCreditAmount);
				order.setAmstorecreditRefundedAmount(storeCreditAmount);
				order.setAmstorecreditRefundedBaseAmount(storeCreditAmount);
			} else {
				order.setToRefund(order.getBaseTotalPaid());
			}
			order.setBaseDiscountTaxCompensationRefunded(order.getBaseDiscountTaxCompensationInvoiced());
			order.setDiscountTaxCompensationRefunded(order.getDiscountTaxCompensationInvoiced());

		} else {

			storeCreditAmount = order.getAmstorecreditAmount();

			if (order.getStatus().equals(OrderConstants.PROCESSING_ORDER_STATUS)) {
				order.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
			} 
			order.setState(OrderConstants.CLOSED_ORDER_STATUS);
			order.setWmsStatus(2);
			order.setBaseDiscountCanceled(order.getBaseDiscountAmount());
			order.setBaseShippingCanceled(order.getBaseShippingAmount());
			order.setBaseSubtotalCanceled(order.getBaseSubtotal());
			order.setBaseTaxCanceled(order.getBaseTaxAmount());
			order.setBaseTotalCanceled(order.getBaseGrandTotal());
			order.setDiscountCanceled(order.getDiscountAmount());
			order.setShippingCanceled(order.getShippingAmount());
			order.setSubtotalCanceled(order.getSubtotal());
			order.setTaxCanceled(order.getTaxAmount());
			order.setTotalCanceled(order.getGrandTotal());
			if (storeCreditAmount != null) {
				order.setToRefund(storeCreditAmount);
				order.setAmstorecreditRefundedAmount(storeCreditAmount);
				order.setAmstorecreditRefundedBaseAmount(storeCreditAmount);
			}
		}

		if (null != request.getReason()) {
			String reason = request.getReason();
			String emotionlessReason = reason.replaceAll(Constants.CHARACTERFILETR, "");
			order.setCancellationReason(emotionlessReason);
		}
        order.setCancellationReasonId(request.getReasonId());
        order.setUpdatedAt(new Timestamp(new Date().getTime()));

        return order;

    }

	public SplitSalesOrder cancelOrderObjectForSplitOrder(CancelOrderRequest request, SplitSalesOrder order, boolean typeRefund,
	String paymentMethod) {

		BigDecimal storeCreditAmount = null;
		if (typeRefund && paymentMethod != null && OrderConstants.checkPaymentMethod(paymentMethod) ) {

			storeCreditAmount = order.getAmstorecreditAmount();
			
			order.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
			
			order.setState(OrderConstants.CLOSED_ORDER_STATUS);

			order.setWmsStatus(2);

			order.setBaseDiscountRefunded(order.getBaseDiscountInvoiced());
			order.setDiscountRefunded(order.getDiscountInvoiced());
			order.setBaseShippingRefunded(order.getBaseShippingInvoiced());
			order.setShippingRefunded(order.getShippingInvoiced());
			order.setBaseSubtotalRefunded(order.getBaseSubtotalInvoiced());
			order.setSubtotalRefunded(order.getSubtotalInvoiced());
			order.setBaseTaxRefunded(order.getBaseTaxInvoiced());
			order.setTaxRefunded(order.getTaxInvoiced());
			order.setBaseTotalOnlineRefunded(order.getBaseTotalPaid());
			order.setTotalRefunded(order.getBaseTotalPaid());
			order.setTotalOnlineRefunded(order.getBaseTotalPaid());
			order.setBaseTotalRefunded(order.getBaseTotalPaid());
			if (storeCreditAmount != null) {
				order.setToRefund(storeCreditAmount);
				order.setAmstorecreditRefundedAmount(storeCreditAmount);
				order.setAmstorecreditRefundedBaseAmount(storeCreditAmount);
			} else {
				order.setToRefund(order.getBaseTotalPaid());
			}
			order.setBaseDiscountTaxCompensationRefunded(order.getBaseDiscountTaxCompensationInvoiced());
			order.setDiscountTaxCompensationRefunded(order.getDiscountTaxCompensationInvoiced());

		} else {

			storeCreditAmount = order.getAmstorecreditAmount();

			order.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
			
			order.setState(OrderConstants.CLOSED_ORDER_STATUS);

			order.setWmsStatus(2);

			order.setBaseDiscountCanceled(order.getBaseDiscountAmount());
			order.setBaseShippingCanceled(order.getBaseShippingAmount());
			order.setBaseSubtotalCanceled(order.getBaseSubtotal());
			order.setBaseTaxCanceled(order.getBaseTaxAmount());
			order.setBaseTotalCanceled(order.getBaseGrandTotal());
			order.setDiscountCanceled(order.getDiscountAmount());
			order.setShippingCanceled(order.getShippingAmount());
			order.setSubtotalCanceled(order.getSubtotal());
			order.setTaxCanceled(order.getTaxAmount());
			order.setTotalCanceled(order.getGrandTotal());
			if (storeCreditAmount != null) {
				order.setToRefund(storeCreditAmount);
				order.setAmstorecreditRefundedAmount(storeCreditAmount);
				order.setAmstorecreditRefundedBaseAmount(storeCreditAmount);
			}
		}

		if (null != request.getReason()) {
			String reason = request.getReason();
			String emotionlessReason = reason.replaceAll(Constants.CHARACTERFILETR, "");
			order.setCancellationReason(emotionlessReason);
		}
		order.setCancellationReasonId(request.getReasonId());
		order.setUpdatedAt(new Timestamp(new Date().getTime()));

		return order;
}

	public SalesOrder cancelOrderObjectV2(CancelOrderRequest request, SalesOrder order, SplitSalesOrder splitSalesOrder, boolean typeRefund,
	String paymentMethod) {

		// Add null checks to prevent NPE
		if (order == null) {
			throw new IllegalArgumentException("SalesOrder cannot be null");
		}
		if (splitSalesOrder == null) {
			throw new IllegalArgumentException("SplitSalesOrder cannot be null");
		}

		BigDecimal storeCreditAmount = null;
		
		// Helper method to safely add BigDecimals with null checking
		java.util.function.BiFunction<BigDecimal, BigDecimal, BigDecimal> safeAdd = (a, b) -> {
			BigDecimal valueA = (a != null) ? a : BigDecimal.ZERO;
			BigDecimal valueB = (b != null) ? b : BigDecimal.ZERO;
			return valueA.add(valueB);
		};

		
		if (typeRefund && paymentMethod != null && OrderConstants.checkPaymentMethod(paymentMethod) ) {

			storeCreditAmount = splitSalesOrder.getAmstorecreditAmount();

			order.setBaseDiscountRefunded(safeAdd.apply(order.getBaseDiscountRefunded(), splitSalesOrder.getBaseDiscountInvoiced()));
			order.setDiscountRefunded(safeAdd.apply(order.getDiscountRefunded(), splitSalesOrder.getDiscountInvoiced()));
			order.setBaseShippingRefunded(safeAdd.apply(order.getBaseShippingRefunded(), splitSalesOrder.getBaseShippingInvoiced()));
			order.setShippingRefunded(safeAdd.apply(order.getShippingRefunded(), splitSalesOrder.getShippingInvoiced()));
			order.setBaseSubtotalRefunded(safeAdd.apply(order.getBaseSubtotalRefunded(), splitSalesOrder.getBaseSubtotalInvoiced()));
			order.setSubtotalRefunded(safeAdd.apply(order.getSubtotalRefunded(), splitSalesOrder.getSubtotalInvoiced()));
			order.setBaseTaxRefunded(safeAdd.apply(order.getBaseTaxRefunded(), splitSalesOrder.getBaseTaxInvoiced()));
			order.setTaxRefunded(safeAdd.apply(order.getTaxRefunded(), splitSalesOrder.getTaxInvoiced()));
			order.setBaseTotalOnlineRefunded(safeAdd.apply(order.getBaseTotalOnlineRefunded(), splitSalesOrder.getBaseTotalPaid()));
			order.setTotalRefunded(safeAdd.apply(order.getTotalRefunded(), splitSalesOrder.getBaseTotalPaid()));
			order.setTotalOnlineRefunded(safeAdd.apply(order.getTotalOnlineRefunded(), splitSalesOrder.getBaseTotalPaid()));
			order.setBaseTotalRefunded(safeAdd.apply(order.getBaseTotalRefunded(), splitSalesOrder.getBaseTotalPaid()));
			if (storeCreditAmount != null) {
				order.setToRefund(safeAdd.apply(order.getToRefund(), storeCreditAmount));
				order.setAmstorecreditRefundedAmount(safeAdd.apply(order.getAmstorecreditRefundedAmount(), storeCreditAmount));
				order.setAmstorecreditRefundedBaseAmount(safeAdd.apply(order.getAmstorecreditRefundedBaseAmount(), storeCreditAmount));
			} else {
				order.setToRefund(safeAdd.apply(order.getToRefund(), splitSalesOrder.getBaseTotalPaid()));
			}
			order.setBaseDiscountTaxCompensationRefunded(safeAdd.apply(order.getBaseDiscountTaxCompensationRefunded(), splitSalesOrder.getBaseDiscountTaxCompensationInvoiced()));
			order.setDiscountTaxCompensationRefunded(safeAdd.apply(order.getDiscountTaxCompensationRefunded(), splitSalesOrder.getDiscountTaxCompensationInvoiced()));

		} else {

			storeCreditAmount = splitSalesOrder.getAmstorecreditAmount();

			order.setBaseDiscountCanceled(safeAdd.apply(order.getBaseDiscountCanceled(), splitSalesOrder.getBaseDiscountAmount()));
			order.setBaseShippingCanceled(safeAdd.apply(order.getBaseShippingCanceled(), splitSalesOrder.getBaseShippingAmount()));
			order.setBaseSubtotalCanceled(safeAdd.apply(order.getBaseSubtotalCanceled(), splitSalesOrder.getBaseSubtotal()));
			order.setBaseTaxCanceled(safeAdd.apply(order.getBaseTaxCanceled(), splitSalesOrder.getBaseTaxAmount()));
			order.setBaseTotalCanceled(safeAdd.apply(order.getBaseTotalCanceled(), splitSalesOrder.getBaseGrandTotal()));
			order.setDiscountCanceled(safeAdd.apply(order.getDiscountCanceled(), splitSalesOrder.getDiscountAmount()));
			order.setShippingCanceled(safeAdd.apply(order.getShippingCanceled(), splitSalesOrder.getShippingAmount()));
			order.setSubtotalCanceled(safeAdd.apply(order.getSubtotalCanceled(), splitSalesOrder.getSubtotal()));
			order.setTaxCanceled(safeAdd.apply(order.getTaxCanceled(), splitSalesOrder.getTaxAmount()));
			order.setTotalCanceled(safeAdd.apply(order.getTotalCanceled(), splitSalesOrder.getGrandTotal()));
			if (storeCreditAmount != null) {
				order.setToRefund(safeAdd.apply(order.getToRefund(), storeCreditAmount));
				order.setAmstorecreditRefundedAmount(safeAdd.apply(order.getAmstorecreditRefundedAmount(), storeCreditAmount));
				order.setAmstorecreditRefundedBaseAmount(safeAdd.apply(order.getAmstorecreditRefundedBaseAmount(), storeCreditAmount));
			}
		}
		order.setUpdatedAt(new Timestamp(new Date().getTime()));
		return order;
	}

    public SalesOrder cancelOrderItems(SalesOrder order, boolean typeRefund) {
        if (CollectionUtils.isEmpty(order.getSalesOrderItem()))
            return order;

        for (SalesOrderItem item : order.getSalesOrderItem()) {
                if (typeRefund) {
                    item.setAmountRefunded(item.getRowTotal());
                    item.setBaseAmountRefunded(item.getBaseRowTotal());
                    item.setDiscountTaxCompensationRefunded(item.getDiscountTaxCompensationInvoiced());
                    item.setBaseDiscountTaxCompensationRefunded(item.getBaseDiscountTaxCompensationInvoiced());
                    item.setTaxRefunded(item.getTaxInvoiced());
                    item.setBaseTaxRefunded(item.getBaseTaxInvoiced());
                    item.setDiscountRefunded(item.getDiscountInvoiced());
                    item.setBaseDiscountRefunded(item.getBaseDiscountInvoiced());
                    item.setQtyRefunded(item.getQtyOrdered());
                } else {
                    item.setDiscountTaxCompensationCanceled(item.getDiscountTaxCompensationAmount());
                    item.setTaxCanceled(item.getTaxAmount());
                    item.setQtyCanceled(item.getQtyOrdered());
                }
        }
        return order;
    }

	public SalesOrderGrid cancelSplitOrderGrid(SplitSalesOrder splitSalesOrder, boolean typeRefund, String paymentMethod) {
		// pass the sales order entity id from split order to fetch the grid
		SalesOrderGrid grid = salesOrderGridRepository.findByEntityId(splitSalesOrder.getSalesOrder().getEntityId());
		if (grid == null)
			return null;

		BigDecimal storeCreditAmount = splitSalesOrder.getAmstorecreditAmount();

		if (typeRefund && paymentMethod != null && (paymentMethod.equalsIgnoreCase(PaymentCodeENUM.PAYFORT_FORT_CC.getValue())
				|| paymentMethod.equalsIgnoreCase(PaymentCodeENUM.MD_PAYFORT.getValue())
				|| paymentMethod.equalsIgnoreCase(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
				|| paymentMethod.equalsIgnoreCase(PaymentCodeENUM.APPLE_PAY.getValue()))) {
			grid.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
		} else {
			grid.setStatus(OrderConstants.CLOSED_ORDER_STATUS);

		}
		grid.setTotalRefunded(grid.getTotalPaid());

		if (storeCreditAmount != null) {
			splitSalesOrder.setToRefund(storeCreditAmount);
			splitSalesOrder.setAmstorecreditRefundedAmount(storeCreditAmount);
			splitSalesOrder.setAmstorecreditRefundedBaseAmount(storeCreditAmount);
		} else {
			splitSalesOrder.setToRefund(splitSalesOrder.getBaseTotalPaid());
		}
		return grid;
	}

	public SalesOrder cancelOrderItemsV2(SalesOrder order, SplitSalesOrder splitSalesOrder, boolean typeRefund) {
        if (CollectionUtils.isEmpty(order.getSalesOrderItem()))
            return order;

		List<String> skus = splitSalesOrder.getSplitSalesOrderItems().stream().map(item -> item.getSku()).toList();

        for (SalesOrderItem item : order.getSalesOrderItem()) {
			if(!skus.contains(item.getSku())) continue;

            	if (typeRefund) {
                    item.setAmountRefunded(item.getRowTotal());
                    item.setBaseAmountRefunded(item.getBaseRowTotal());
                    item.setDiscountTaxCompensationRefunded(item.getDiscountTaxCompensationInvoiced());
                    item.setBaseDiscountTaxCompensationRefunded(item.getBaseDiscountTaxCompensationInvoiced());
                    item.setTaxRefunded(item.getTaxInvoiced());
                    item.setBaseTaxRefunded(item.getBaseTaxInvoiced());
                    item.setDiscountRefunded(item.getDiscountInvoiced());
                    item.setBaseDiscountRefunded(item.getBaseDiscountInvoiced());
                    item.setQtyRefunded(item.getQtyOrdered());
                } else {
                    item.setDiscountTaxCompensationCanceled(item.getDiscountTaxCompensationAmount());
                    item.setTaxCanceled(item.getTaxAmount());
                    item.setQtyCanceled(item.getQtyOrdered());
                }
        }
        return order;
    }

	public SplitSalesOrder cancelOrderItemsForSplitOrder(SplitSalesOrder order, boolean typeRefund) {
        if (CollectionUtils.isEmpty(order.getSplitSalesOrderItems()))
            return order;

        for (SplitSalesOrderItem item : order.getSplitSalesOrderItems()) {
                if (typeRefund) {
                    item.setAmountRefunded(item.getRowTotal());
                    item.setBaseAmountRefunded(item.getBaseRowTotal());
                    item.setDiscountTaxCompensationRefunded(item.getDiscountTaxCompensationInvoiced());
                    item.setBaseDiscountTaxCompensationRefunded(item.getBaseDiscountTaxCompensationInvoiced());
                    item.setTaxRefunded(item.getTaxInvoiced());
                    item.setBaseTaxRefunded(item.getBaseTaxInvoiced());
                    item.setDiscountRefunded(item.getDiscountInvoiced());
                    item.setBaseDiscountRefunded(item.getBaseDiscountInvoiced());
                    item.setQtyRefunded(item.getQtyOrdered());
                } else {
                    item.setDiscountTaxCompensationCanceled(item.getDiscountTaxCompensationAmount());
                    item.setTaxCanceled(item.getTaxAmount());
                    item.setQtyCanceled(item.getQtyOrdered());
                }
        }
        return order;
    }

    public SalesOrder cancelOrderPayment(SalesOrder order) {
        if (CollectionUtils.isEmpty(order.getSalesOrderPayment()))
            return order;

        for (SalesOrderPayment payment : order.getSalesOrderPayment()) {
            payment.setAmountRefunded(payment.getAmountPaid());
            payment.setBaseAmountRefunded(payment.getBaseAmountPaid());
            payment.setShippingRefunded(payment.getShippingAmount());
            payment.setBaseShippingRefunded(payment.getBaseShippingAmount());
        }

        return order;
    }

    public SalesOrderGrid cancelOrderGrid(SalesOrder order, boolean typeRefund, String paymentMethod) {
    	
        SalesOrderGrid grid = salesOrderGridRepository.findByEntityId(order.getEntityId());
        if (grid == null)
            return null;

        BigDecimal storeCreditAmount = order.getAmstorecreditAmount();
        
            if (typeRefund && paymentMethod != null && (paymentMethod.equalsIgnoreCase(PaymentCodeENUM.PAYFORT_FORT_CC.getValue())
                    || paymentMethod.equalsIgnoreCase(PaymentCodeENUM.MD_PAYFORT.getValue())
                    || paymentMethod.equalsIgnoreCase(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())
                    || paymentMethod.equalsIgnoreCase(PaymentCodeENUM.APPLE_PAY.getValue()))) {
                grid.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
            } else {
                grid.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
                
            }
            grid.setTotalRefunded(grid.getTotalPaid());
        
        if (storeCreditAmount != null) {
            order.setToRefund(storeCreditAmount);
            order.setAmstorecreditRefundedAmount(storeCreditAmount);
            order.setAmstorecreditRefundedBaseAmount(storeCreditAmount);
        } else {
            order.setToRefund(order.getBaseTotalPaid());
        }
        return grid;
    }
    
	public SalesOrder cancelStatusHistory(SalesOrder order, boolean typeRefund,BigDecimal totalPaid
			, String message) {

			SalesOrderStatusHistory history = new SalesOrderStatusHistory();
			history.setParentId(order.getEntityId());
			history.setVisibleOnFront(0);
			history.setCreatedAt(new Timestamp(new Date().getTime()));
			if (typeRefund) {
				// Refunded status to OTS
				paymentUtility.publishToSplitPubSubOTSForSalesOrder(order,"10.0","Refunded");
				history.setComment(
						"We refunded " + order.getOrderCurrencyCode() + " " + totalPaid + " offline.");
				history.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
				history.setEntityName("creditmemo");
			} else {
				history.setComment(message);
				history.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
				history.setEntityName("order");
			}

			salesOrderStatusHistoryRepository.saveAndFlush(history);
		

		return order;
	}

	public SplitSalesOrder cancelStatusHistoryForSplitOrder(SplitSalesOrder order, boolean typeRefund,BigDecimal totalPaid
			, String message) {

			SalesOrderStatusHistory history = new SalesOrderStatusHistory();
			history.setParentId(order.getSalesOrder().getEntityId());
			history.setSplitOrderId(order.getEntityId());
			history.setVisibleOnFront(0);
			history.setCreatedAt(new Timestamp(new Date().getTime()));
			if (typeRefund) {
				history.setComment(
						"We refunded " + order.getOrderCurrencyCode() + " " + totalPaid + " offline.");
				history.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
				history.setEntityName("creditmemo");
			} else {
				history.setComment(message);
				history.setStatus(OrderConstants.CLOSED_ORDER_STATUS);
				history.setEntityName("order");
			}

			salesOrderStatusHistoryRepository.saveAndFlush(history);
		

		return order;
	}

    public SalesCreditmemo createCreditMemo(SalesOrder order, String memoIncrementId, BigDecimal totalAmount,
    		BigDecimal cancelStoreCreditAmount,
			String paymentMethod, Map<String, BigDecimal> skumapList, boolean isFullCancelled
			,Map<String, BigDecimal> actualskumApList, RefundPaymentRespone response, BigDecimal totalShukranBurnedValueInCurrency, BigDecimal totalShukranBurnedValueInBaseCurrency, BigDecimal totalShukranCoinsBurned) {

		BigDecimal grandTotal = BigDecimal.ZERO;
		BigDecimal baseGrandTotal = BigDecimal.ZERO;

		BigDecimal adjustment = BigDecimal.ZERO;
		BigDecimal baseAdjustMent = BigDecimal.ZERO;

		BigDecimal adjustmentNegative = BigDecimal.ZERO;
		BigDecimal baseAdjustMentNegative = BigDecimal.ZERO;

		BigDecimal amastyStoreCreditAmount = BigDecimal.ZERO;
		BigDecimal baseAmastyStoreCreditAmount = BigDecimal.ZERO;

		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getDonationAmount()
				&& isFullCancelled) {
			adjustment = order.getSubSalesOrder().getDonationAmount().negate();
			baseAdjustMent = order.getSubSalesOrder().getBaseDonationAmount().negate();
			adjustmentNegative = order.getSubSalesOrder().getDonationAmount();
			baseAdjustMentNegative = order.getSubSalesOrder().getBaseDonationAmount();
		}

		if (null != cancelStoreCreditAmount) {
			amastyStoreCreditAmount = cancelStoreCreditAmount;
			baseAmastyStoreCreditAmount = amastyStoreCreditAmount.multiply(order.getStoreToBaseRate())
					.setScale(4, RoundingMode.HALF_UP);

		}

		if (null != totalAmount) {

			grandTotal = totalAmount;

			baseGrandTotal = totalAmount.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);
		}

		SalesCreditmemo memo = new SalesCreditmemo();
		memo.setStoreId(order.getStoreId());
		memo.setBaseShippingTaxAmount(order.getBaseShippingTaxAmount());
		memo.setStoreToOrderRate(order.getStoreToOrderRate());
		
		memo.setBaseToOrderRate(order.getBaseToOrderRate());
		if (null != paymentMethod && (paymentMethod.equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())
				|| paymentMethod.equalsIgnoreCase(PaymentCodeENUM.FREE.getValue()))) {
			memo.setGrandTotal(new BigDecimal(0));
			memo.setBaseGrandTotal(new BigDecimal(0));
		} else {
			memo.setGrandTotal(grandTotal);
			memo.setBaseGrandTotal(baseGrandTotal);
		}
		if (MapUtils.isNotEmpty(skumapList)) {
			BigDecimal subTotal = getCanceledItemSubTotal(order, skumapList, null);
			BigDecimal subTotalIncludeTax = getCanceledItemQty(order, skumapList, null);
			BigDecimal baseSubTotalIncludingTax = subTotalIncludeTax.multiply(order.getStoreToBaseRate()).setScale(4,
					RoundingMode.HALF_UP);
			BigDecimal taxCompcompAmount = getCanceledTaxCompItemQty(order, skumapList);
			BigDecimal taxAmount = getCanceledTaxItemQty(order, skumapList);
			
			BigDecimal discount = getDiscountAmount(order, skumapList);
			BigDecimal baseDiscount = taxCompcompAmount.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);
			memo.setBaseSubtotalInclTax(baseSubTotalIncludingTax);
			memo.setSubtotalInclTax(subTotalIncludeTax);
			memo.setTaxAmount(taxAmount);
			memo.setBaseTaxAmount(
					taxCompcompAmount.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP));
			memo.setDiscountTaxCompensationAmount(taxCompcompAmount);
			memo.setBaseDiscountTaxCompensationAmount(
					taxCompcompAmount.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP));
			memo.setSubtotal(subTotal);
			memo.setBaseSubtotal(
					subTotal.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP));
			
			memo.setBaseDiscountAmount(baseDiscount);
			memo.setDiscountAmount(discount);

		} else {

			memo.setBaseSubtotalInclTax(order.getBaseSubtotalInclTax());
			memo.setSubtotalInclTax(order.getSubtotalInclTax());
			memo.setTaxAmount(order.getTaxAmount());
			memo.setDiscountTaxCompensationAmount(order.getDiscountTaxCompensationInvoiced());
			memo.setBaseDiscountTaxCompensationAmount(order.getBaseDiscountTaxCompensationInvoiced());
			memo.setSubtotal(order.getSubtotalInvoiced());
			memo.setBaseTaxAmount(order.getBaseTaxAmount());
			memo.setBaseDiscountAmount(order.getBaseDiscountAmount());
			memo.setDiscountAmount(order.getDiscountAmount());
		}
		if(isFullCancelled) {
			memo.setShippingAmount(order.getShippingAmount());
			memo.setBaseShippingAmount(order.getBaseShippingAmount());
			memo.setShippingTaxAmount(order.getShippingTaxAmount());
			memo.setShippingInclTax(order.getShippingInclTax());

		}
		memo.setStoreToBaseRate(order.getStoreToBaseRate());
		memo.setBaseToGlobalRate(order.getBaseToGlobalRate());
		memo.setBaseAdjustment(baseAdjustMent);

		

		memo.setAdjustment(adjustment);


		memo.setOrderId(order.getEntityId());
		memo.setState(2);
		memo.setShippingAddressId(order.getShippingAddressId());
		memo.setBillingAddressId(order.getBillingAddressId());
		memo.setBaseCurrencyCode(order.getBaseCurrencyCode());
		memo.setGlobalCurrencyCode(order.getGlobalCurrencyCode());
		memo.setOrderCurrencyCode(order.getOrderCurrencyCode());
		memo.setStoreCurrencyCode(order.getStoreCurrencyCode());
		memo.setIncrementId(memoIncrementId);
		memo.setCreatedAt(new Timestamp(new Date().getTime()));
		memo.setUpdatedAt(new Timestamp(new Date().getTime()));

		memo.setBaseShippingInclTax(order.getBaseShippingInclTax());
		memo.setDiscountDescription(order.getCouponCode());
		memo.setCashOnDeliveryFee(order.getCashOnDeliveryFee());
		memo.setBaseCashOnDeliveryFee(order.getBaseCashOnDeliveryFee());
		memo.setAmstorecreditAmount(amastyStoreCreditAmount);
		memo.setAmstorecreditBaseAmount(baseAmastyStoreCreditAmount);
		memo.setRmaNumber(order.getEntityId().toString());

		// EAS value to credit memo
		if(order.getSubSalesOrder() != null){
			if(order.getSubSalesOrder().getEasValueInCurrency() != null && order.getSubSalesOrder().getEasValueInCurrency().compareTo(BigDecimal.ZERO)>0){
				memo.setEasCoins(order.getSubSalesOrder().getEasCoins());
				memo.setEasValueInCurrency(order.getSubSalesOrder().getEasValueInCurrency());
				memo.setEasValueInBaseCurrency(order.getSubSalesOrder().getEasValueInBaseCurrency());
			}
		}
		// EAS value to credit memo

		memo.setAdjustmentNegative(adjustmentNegative);
		memo.setBaseAdjustmentNegative(baseAdjustMentNegative);

		String returnValue;
 		try {
 			if(MapUtils.isNotEmpty(actualskumApList)) {
 	 			returnValue = mapper.writeValueAsString(actualskumApList);

 			}else {
 				returnValue = mapper.writeValueAsString(skumapList);
 			}
 	         memo.setCustomerNote(returnValue);      

 		} catch (JsonProcessingException e) {
 			LOGGER.error("error during customer note credit memo:"+e.getMessage());
 			
 		}
		if (Objects.nonNull(response)) {
			memo.setReconciliationReference(response.getPaymentRRN());
		}
		SalesCreditmemo memo1= setShukranValues(memo, totalShukranBurnedValueInCurrency, totalShukranBurnedValueInBaseCurrency, totalShukranCoinsBurned);
		salesCreditmemoRepository.saveAndFlush(memo1);

		return memo1;
	}

	public SalesCreditmemo setShukranValues(SalesCreditmemo memo, BigDecimal totalShukranBurnedValueInCurrency, BigDecimal totalShukranBurnedValueInBaseCurrency, BigDecimal totalShukranCoinsBurned){
		if(totalShukranCoinsBurned != null && totalShukranCoinsBurned.compareTo(BigDecimal.ZERO)>0){
			memo.setShukranPointsRefunded(totalShukranCoinsBurned);
			memo.setShukranPointsRefundedValueInCurrency(totalShukranBurnedValueInCurrency);
			memo.setShukranPointsRefundedValueInBaseCurrency(totalShukranBurnedValueInBaseCurrency);
		}
		return memo;
	}
    public void createCreditmemoItems(SalesOrder order, SalesCreditmemo memo , Map<String , BigDecimal> skuMapList) {

        if (CollectionUtils.isEmpty(order.getSalesOrderItem()))
            return;

        for (SalesOrderItem orderItem : order.getSalesOrderItem()) {
        	
            SalesCreditmemoItem memoItem = new SalesCreditmemoItem();
            memoItem.setParentId(memo.getEntityId());
            memoItem.setBasePrice(orderItem.getBasePrice());
            if (orderItem.getProductType() != null && orderItem.getProductType().equalsIgnoreCase("configurable")) {
                memoItem.setTaxAmount(orderItem.getTaxAmount());
                memoItem.setBaseRowTotal(orderItem.getBaseRowTotal());
                memoItem.setDiscountAmount(orderItem.getDiscountAmount());
                memoItem.setRowTotal(orderItem.getRowTotal());
                memoItem.setBaseDiscountAmount(orderItem.getBaseDiscountAmount());
                memoItem.setBaseTaxAmount(orderItem.getBaseTaxAmount());
                memoItem.setBaseRowTotalInclTax(orderItem.getBaseRowTotalInclTax());
                memoItem.setRowTotalInclTax(orderItem.getRowTotalInclTax());
                memoItem.setDiscountTaxCompensationAmount(orderItem.getDiscountTaxCompensationAmount());
                memoItem.setBaseDiscountTaxCompensationAmount(orderItem.getBaseDiscountTaxCompensationAmount());
                memoItem.setWeeeTaxAppliedRowAmount(orderItem.getWeeeTaxAppliedRowAmount());
                memoItem.setWeeeTaxRowDisposition(orderItem.getWeeeTaxRowDisposition());
                memoItem.setBaseWeeeTaxRowDisposition(orderItem.getBaseWeeeTaxRowDisposition());
            }
            memoItem.setPriceInclTax(orderItem.getPriceInclTax());
            memoItem.setBasePriceInclTax(orderItem.getBasePriceInclTax());
            if(MapUtils.isNotEmpty(skuMapList)) {
            
            	 memoItem.setQty(skuMapList.get(orderItem.getSku()));
            }else {
            	 memoItem.setQty(orderItem.getQtyOrdered());
            }
           
            memoItem.setBaseCost(orderItem.getBaseCost());
            memoItem.setPrice(orderItem.getPrice());
            memoItem.setProductId(orderItem.getProductId());
            memoItem.setOrderItemId(orderItem.getItemId());
            memoItem.setSku(orderItem.getSku());
            memoItem.setName(orderItem.getName());
           
            salesCreditmemoItemRepository.saveAndFlush(memoItem);
        }

    }
    
    public void createCancelCreditmemoItems(SalesOrder order, SalesCreditmemo memo , Map<String , BigDecimal> skuMapList
    		, List<SalesOrderItem> items) {

        if (CollectionUtils.isEmpty(order.getSalesOrderItem()))
            return;
        
        BigDecimal totalDiscount = BigDecimal.ZERO;
		BigDecimal totalbaseDiscount = BigDecimal.ZERO;

		BigDecimal totalTaxcompAmount = BigDecimal.ZERO;
		BigDecimal totalBaseTaxCompAmount = BigDecimal.ZERO;

		BigDecimal totalTaxAmount = BigDecimal.ZERO;
		BigDecimal totalBaseTaxAmount = BigDecimal.ZERO;

		BigDecimal totalrowTotal = BigDecimal.ZERO;
		BigDecimal totalBaseRowTotal = BigDecimal.ZERO;

		BigDecimal totalPriceIncludingTax = BigDecimal.ZERO;
		BigDecimal totalBaseTotalPriceIncludingTax = BigDecimal.ZERO;

		BigDecimal totalrowRotalIncludingTax = BigDecimal.ZERO;
		BigDecimal totalBaseRotalIncludingTax = BigDecimal.ZERO;

        for (SalesOrderItem orderItem : order.getSalesOrderItem()) {
        	
        	if(skuMapList.containsKey(orderItem.getSku())) {
        		
            SalesCreditmemoItem memoItem = new SalesCreditmemoItem();
            memoItem.setParentId(memo.getEntityId());
            memoItem.setBasePrice(orderItem.getBasePrice());
            if (orderItem.getProductType() != null && orderItem.getProductType().equalsIgnoreCase("configurable")) {
                
                
				if (MapUtils.isNotEmpty(skuMapList)) {
					
					BigDecimal subTotal = getCanceledItemQty(order, skuMapList, orderItem.getSku());
					memoItem.setBaseRowTotal(subTotal.multiply(order.getStoreToOrderRate()).setScale(4,  RoundingMode.HALF_UP));
					memoItem.setBaseRowTotalInclTax(subTotal.multiply(order.getStoreToOrderRate()).setScale(4,  RoundingMode.HALF_UP));
	                memoItem.setRowTotalInclTax(subTotal);
	                
	                

				}
				BigDecimal orderedQty = skuMapList.get(orderItem.getSku());
				
				BigDecimal discount = BigDecimal.ZERO;
				BigDecimal baseDiscount = BigDecimal.ZERO;

				BigDecimal taxcompAmount = BigDecimal.ZERO;
				BigDecimal baseTaxCompAmount = BigDecimal.ZERO;

				BigDecimal indivisualTax = orderItem.getTaxAmount().divide(orderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(4,
						RoundingMode.HALF_UP);
				BigDecimal taxAmount = indivisualTax.multiply(orderedQty).setScale(4,
						RoundingMode.HALF_UP);
				BigDecimal baseTaxAmount = taxAmount.multiply(order.getStoreToBaseRate()).setScale(4,
						RoundingMode.HALF_UP);
				
				BigDecimal indivisualRow = orderItem.getRowTotal().divide(orderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(4,
						RoundingMode.HALF_UP);
				
				BigDecimal rowTotal =indivisualRow.multiply(orderedQty).setScale(4,
						RoundingMode.HALF_UP);
				BigDecimal baseRowTotal = rowTotal.multiply(order.getStoreToBaseRate()).setScale(4,
						RoundingMode.HALF_UP);

				BigDecimal priceIncludingTax = orderItem.getPriceInclTax().divide(orderedQty, 4, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);
				BigDecimal basepriceIncludingTax = priceIncludingTax.multiply(order.getStoreToBaseRate()).setScale(4,
						RoundingMode.HALF_UP);

				BigDecimal rowRotalIncludingTax = orderItem.getRowTotalInclTax().divide(orderedQty, 4, RoundingMode.HALF_UP)
						.setScale(4, RoundingMode.HALF_UP);
				BigDecimal baseRowpriceIncludingTax = rowRotalIncludingTax.multiply(order.getStoreToBaseRate())
						.setScale(4, RoundingMode.HALF_UP);

				if (null != orderItem.getDiscountAmount()) {
					
					BigDecimal qtyCancelled = skuMapList.get(orderItem.getSku());
					BigDecimal qtyOrdered = orderItem.getQtyOrdered();
					BigDecimal Indivisualdiscount = orderItem.getDiscountAmount().divide(qtyOrdered, 4, RoundingMode.HALF_UP).setScale(4,
							RoundingMode.HALF_UP);
					if(null != qtyCancelled) {
						discount = Indivisualdiscount.multiply(qtyCancelled).setScale(4, RoundingMode.HALF_UP);
					}
					baseDiscount = discount.multiply(order.getStoreToBaseRate()).setScale(4,
							RoundingMode.HALF_UP);

					
					rowTotal = rowTotal.subtract(discount);
					baseRowTotal = baseRowTotal.subtract(baseDiscount);
				}
				if (null != orderItem.getDiscountTaxCompensationAmount()) {

					taxcompAmount = orderItem.getDiscountTaxCompensationAmount()
							.divide(orderedQty, 4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
					baseTaxCompAmount = taxcompAmount.multiply(order.getStoreToBaseRate()).setScale(4,
							RoundingMode.HALF_UP);
				}
				
				if(null != orderItem.getProductType() 
						&& !orderItem.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)) {
					
					totalDiscount = totalDiscount.add(discount);
					totalbaseDiscount = totalbaseDiscount.add(baseDiscount);

					totalTaxcompAmount = totalTaxcompAmount.add(taxcompAmount);
					totalBaseTaxCompAmount = totalBaseTaxCompAmount.add(baseTaxCompAmount);

					totalTaxAmount = totalTaxAmount.add(taxAmount);
					totalBaseTaxAmount = totalBaseTaxAmount.add(baseTaxAmount);

					totalrowTotal = totalrowTotal.add(rowTotal);
					totalBaseRowTotal = totalBaseRowTotal.add(baseRowTotal);

					totalPriceIncludingTax = totalPriceIncludingTax.add(priceIncludingTax);
					totalBaseTotalPriceIncludingTax = totalBaseTotalPriceIncludingTax.add(basepriceIncludingTax);

					totalrowRotalIncludingTax = totalrowRotalIncludingTax.add(rowRotalIncludingTax);
					totalBaseRotalIncludingTax = totalBaseRotalIncludingTax.add(baseRowpriceIncludingTax);
				}
				
               
                memoItem.setDiscountAmount(discount);
                memoItem.setRowTotal(rowTotal);
                memoItem.setBaseDiscountAmount(baseDiscount);
                memoItem.setBaseTaxAmount(baseTaxAmount);
                memoItem.setDiscountTaxCompensationAmount(taxcompAmount);
                memoItem.setBaseDiscountTaxCompensationAmount(baseTaxCompAmount);
                memoItem.setWeeeTaxAppliedRowAmount(orderItem.getWeeeTaxAppliedRowAmount());
                memoItem.setWeeeTaxRowDisposition(orderItem.getWeeeTaxRowDisposition());
                memoItem.setBaseWeeeTaxRowDisposition(orderItem.getBaseWeeeTaxRowDisposition());
                memoItem.setPriceInclTax(priceIncludingTax);
                memoItem.setBasePriceInclTax(basepriceIncludingTax);
                memoItem.setTaxAmount(taxAmount);
                memoItem.setBaseTaxAmount(totalBaseTaxAmount);
            }
            
            if(MapUtils.isNotEmpty(skuMapList)) {
            
            	 memoItem.setQty(skuMapList.get(orderItem.getSku()));
            }else {
            	 memoItem.setQty(orderItem.getQtyOrdered());
            }
            
            
           
            memoItem.setBaseCost(orderItem.getBaseCost());
            memoItem.setPrice(orderItem.getPrice());
            memoItem.setProductId(orderItem.getProductId());
            memoItem.setOrderItemId(orderItem.getItemId());
            memoItem.setSku(orderItem.getSku());
            memoItem.setName(orderItem.getName());
            salesCreditmemoItemRepository.saveAndFlush(memoItem);
        }
        }
        
        //memo.setSubtotal(totalrowTotal);

//		memo.setDiscountAmount(totalDiscount);
//		memo.setBaseDiscountAmount(totalbaseDiscount);
//
//		memo.setTaxAmount(totalTaxAmount);
//		memo.setBaseTaxAmount(totalBaseTaxAmount);
//
//		memo.setDiscountTaxCompensationAmount(totalTaxcompAmount);
//		memo.setBaseDiscountTaxCompensationAmount(totalBaseTaxCompAmount);
//		memo.setBaseSubtotalInclTax(totalBaseTotalPriceIncludingTax);
		//memo.setSubtotalInclTax(totalPriceIncludingTax);
		
		 salesCreditmemoRepository.saveAndFlush(memo);

    }


	public void createRtoCreditmemoItems(SalesCreditmemo memo, List<SalesCreditmemoItem> memoItems) {

		for(SalesCreditmemoItem memoItem: memoItems) {
			memoItem.setParentId(memo.getEntityId());
			salesCreditmemoItemRepository.saveAndFlush(memoItem);
		}

	}
    
    public void createRefundCreditmemoItems(SalesOrder order, SalesCreditmemo memo, AmastyRmaRequest amastyRequest
    		, Map<String,BigDecimal> mapSkuList) {

		try {
			if (CollectionUtils.isEmpty(order.getSalesOrderItem())) {

				return;
			}

			BigDecimal totalDiscount = BigDecimal.ZERO;
			BigDecimal totalbaseDiscount = BigDecimal.ZERO;

			BigDecimal totalTaxcompAmount = BigDecimal.ZERO;
			BigDecimal totalBaseTaxCompAmount = BigDecimal.ZERO;

			BigDecimal totalTaxAmount = BigDecimal.ZERO;
			BigDecimal totalBaseTaxAmount = BigDecimal.ZERO;

			BigDecimal totalrowTotal = BigDecimal.ZERO;
			BigDecimal totalBaseRowTotal = BigDecimal.ZERO;

			BigDecimal totalPriceIncludingTax = BigDecimal.ZERO;
			BigDecimal totalBaseTotalPriceIncludingTax = BigDecimal.ZERO;

			BigDecimal totalrowRotalIncludingTax = BigDecimal.ZERO;
			BigDecimal totalBaseRotalIncludingTax = BigDecimal.ZERO;

			BigDecimal totalVoucherRefunded = BigDecimal.ZERO;


			CopyOnWriteArrayList<SalesOrderItem> orderListItems = new CopyOnWriteArrayList<>(order.getSalesOrderItem());

			for (SalesOrderItem orderItem : orderListItems) {

				AmastyRmaRequestItem returnItem = amastyRequest
						.getAmastyRmaRequestItems().stream().filter(e -> e.getOrderItemId().equals(orderItem.getItemId())
								&& null != e.getItemStatus() && (e.getItemStatus() != 12) || e.getItemStatus() != 13)
						.findFirst().orElse(null);
				SalesCreditmemoItem memoItem = new SalesCreditmemoItem();

				if (null != returnItem && mapSkuList.containsKey(orderItem.getSku())) {


					SalesOrderItem childOrderItem = order.getSalesOrderItem().stream()
							.filter(e -> e.getItemId().equals(returnItem.getOrderItemId())).findFirst().orElse(null);

					BigDecimal orderedQty = BigDecimal.ZERO;
					if (null != childOrderItem && null != returnItem.getActualQuantyReturned()
							&& !(returnItem.getActualQuantyReturned().equals(0))) {

						memoItem.setQty(new BigDecimal(returnItem.getActualQuantyReturned()));
						orderedQty = new BigDecimal(returnItem.getActualQuantyReturned());
					} else if (null != childOrderItem) {

						memoItem.setQty(returnItem.getQty());

						orderedQty = mapSkuList.get(orderItem.getSku());
					}
					order.getSalesOrderItem().add(childOrderItem);


					BigDecimal discount = BigDecimal.ZERO;
					BigDecimal baseDiscount = BigDecimal.ZERO;

					BigDecimal taxcompAmount = BigDecimal.ZERO;
					BigDecimal baseTaxCompAmount = BigDecimal.ZERO;

					BigDecimal indivisualtaxAmount = orderItem.getTaxAmount().divide(orderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(4,
							RoundingMode.HALF_UP);
					BigDecimal taxAmount = indivisualtaxAmount.multiply(orderedQty).setScale(4,
							RoundingMode.HALF_UP);
					BigDecimal baseTaxAmount = taxAmount.multiply(order.getStoreToBaseRate()).setScale(4,
							RoundingMode.HALF_UP);

					List<OmsProductTax> productTaxObj = null;
					if ("IN".equalsIgnoreCase(regionValue)) {
						productTaxObj = omsorderentityConverter.getTaxObjects(orderItem, true);
						indivisualtaxAmount = productTaxObj.stream().map(x -> new BigDecimal(x.getTaxAmount())).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4,
								RoundingMode.HALF_UP);
						taxAmount = indivisualtaxAmount.multiply(orderedQty).setScale(4,
								RoundingMode.HALF_UP);
						baseTaxAmount = taxAmount.multiply(order.getStoreToBaseRate()).setScale(4,
								RoundingMode.HALF_UP);
					}


					BigDecimal indivisualRowTotal = orderItem.getRowTotal().divide(orderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(4,
							RoundingMode.HALF_UP);

					BigDecimal rowTotal = indivisualRowTotal.multiply(orderedQty).setScale(4,
							RoundingMode.HALF_UP);
					BigDecimal baseRowTotal = rowTotal.multiply(order.getStoreToBaseRate()).setScale(4,
							RoundingMode.HALF_UP);

					BigDecimal priceIncludingTax = orderItem.getPriceInclTax();
					BigDecimal basepriceIncludingTax = priceIncludingTax.multiply(order.getStoreToBaseRate()).setScale(4,
							RoundingMode.HALF_UP);

					BigDecimal rowRotalIncludingTax = orderItem.getRowTotalInclTax().divide(orderedQty, 4, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);
					BigDecimal baseRowpriceIncludingTax = rowRotalIncludingTax.multiply(order.getStoreToBaseRate())
							.setScale(4, RoundingMode.HALF_UP);

					if (null != orderItem.getDiscountAmount()) {

						BigDecimal qtyCancelled = mapSkuList.get(orderItem.getSku());
						BigDecimal qtyOrdered = orderItem.getQtyOrdered();
						BigDecimal indivisualdiscount = orderItem.getDiscountAmount().divide(qtyOrdered, 4, RoundingMode.HALF_UP).setScale(4,
								RoundingMode.HALF_UP);
						if (null != qtyCancelled) {
							discount = indivisualdiscount.multiply(qtyCancelled).setScale(4, RoundingMode.HALF_UP);
						}
						baseDiscount = discount.multiply(order.getStoreToBaseRate()).setScale(4,
								RoundingMode.HALF_UP);


						rowTotal = rowTotal.subtract(discount);
						baseRowTotal = baseRowTotal.subtract(baseDiscount);
					}
					if (null != orderItem.getDiscountTaxCompensationAmount()) {

						BigDecimal qtyCancelled = mapSkuList.get(orderItem.getSku());
						BigDecimal indivisualTaxComp = orderItem.getDiscountTaxCompensationAmount().divide(orderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(4,
								RoundingMode.HALF_UP);

						taxcompAmount = indivisualTaxComp.multiply(qtyCancelled).setScale(4,
								RoundingMode.HALF_UP);
						baseTaxCompAmount = taxcompAmount.multiply(order.getStoreToBaseRate()).setScale(4,
								RoundingMode.HALF_UP);
					}

					if (null != orderItem.getProductType()
							&& !orderItem.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)) {

						totalDiscount = totalDiscount.add(discount);
						totalbaseDiscount = totalbaseDiscount.add(baseDiscount);

						totalTaxcompAmount = totalTaxcompAmount.add(taxcompAmount);
						totalBaseTaxCompAmount = totalBaseTaxCompAmount.add(baseTaxCompAmount);

						totalTaxAmount = totalTaxAmount.add(taxAmount);
						totalBaseTaxAmount = totalBaseTaxAmount.add(baseTaxAmount);

						totalrowTotal = totalrowTotal.add(rowTotal);
						totalBaseRowTotal = totalBaseRowTotal.add(baseRowTotal);

						totalPriceIncludingTax = totalPriceIncludingTax.add(priceIncludingTax);
						totalBaseTotalPriceIncludingTax = totalBaseTotalPriceIncludingTax.add(basepriceIncludingTax);

						totalrowRotalIncludingTax = totalrowRotalIncludingTax.add(rowRotalIncludingTax);
						totalBaseRotalIncludingTax = totalBaseRotalIncludingTax.add(baseRowpriceIncludingTax);
					}


					if (MapUtils.isNotEmpty(mapSkuList)) {

						BigDecimal subTotal = getCanceledItemQty(order, mapSkuList, orderItem.getSku());
						memoItem.setBaseRowTotal(
								subTotal.multiply(order.getStoreToOrderRate()).setScale(4, RoundingMode.HALF_UP));
						memoItem.setBaseRowTotalInclTax(
								subTotal.multiply(order.getStoreToOrderRate()).setScale(4, RoundingMode.HALF_UP));
						memoItem.setRowTotalInclTax(subTotal);

					}


					//memoItem.setBaseRowTotal(baseRowTotal);
					memoItem.setRowTotal(rowTotal);
					//memoItem.setRowTotalInclTax(rowTotal);
					//memoItem.setBaseRowTotalInclTax(baseRowpriceIncludingTax);
					memoItem.setParentId(memo.getEntityId());
					memoItem.setBasePrice(orderItem.getBasePrice());
					memoItem.setTaxAmount(taxAmount);
					memoItem.setDiscountAmount(discount);

					memoItem.setBaseDiscountAmount(baseDiscount);
					memoItem.setBaseTaxAmount(baseTaxAmount);
					memoItem.setDiscountTaxCompensationAmount(taxcompAmount);
					memoItem.setBaseDiscountTaxCompensationAmount(baseTaxCompAmount);
					memoItem.setWeeeTaxAppliedRowAmount(orderItem.getWeeeTaxAppliedRowAmount());
					memoItem.setWeeeTaxRowDisposition(orderItem.getWeeeTaxRowDisposition());
					memoItem.setBaseWeeeTaxRowDisposition(orderItem.getBaseWeeeTaxRowDisposition());

					memoItem.setPriceInclTax(priceIncludingTax);
					memoItem.setBasePriceInclTax(basepriceIncludingTax);
					memoItem.setBaseCost(orderItem.getBaseCost());
					memoItem.setPrice(orderItem.getPrice());
					memoItem.setProductId(orderItem.getProductId());
					memoItem.setOrderItemId(orderItem.getItemId());
					memoItem.setSku(orderItem.getSku());
					memoItem.setName(orderItem.getName());
					memoItem.setQty(orderItem.getQtyInvoiced());
					memoItem.setHsnCode(orderItem.getHsnCode());
					memoItem.setVoucherAmount(BigDecimal.ZERO);

					if (null != returnItem.getGiftVoucherRefundedAmount()) {
						if (returnItem.getOrderItemId().equals(memoItem.getOrderItemId())) {
							memoItem.setVoucherAmount(returnItem.getGiftVoucherRefundedAmount());
							totalVoucherRefunded = totalVoucherRefunded.add(returnItem.getGiftVoucherRefundedAmount());
						}
					}

					AmastyRmaRequestItem returnItemGV = amastyRequest
							.getAmastyRmaRequestItems().stream().filter(e -> e.getOrderItemId().equals(orderItem.getItemId()))
							.findFirst().orElse(null);

					List<SalesOrderItem> orderItemList = orderListItems.stream().filter(e -> e.getSku().equals(orderItem.getSku())).collect(Collectors.toList());
					BigDecimal refundedVoucherAmount = getGiftVoucherAmountByRequestRMA(orderItemList, amastyRequest);
					memoItem.setVoucherAmount(refundedVoucherAmount);

					if (null != returnItemGV) {
						if (null != returnItemGV.getGiftVoucherRefundedAmount() && returnItemGV.getOrderItemId().equals(orderItem.getItemId())) {
							memoItem.setVoucherAmount(returnItemGV.getGiftVoucherRefundedAmount());
							totalVoucherRefunded = totalVoucherRefunded.add(returnItemGV.getGiftVoucherRefundedAmount());
						}
					}


					if (MapUtils.isNotEmpty(mapSkuList)) {

						memoItem.setQty(mapSkuList.get(orderItem.getSku()));
					} else {
						memoItem.setQty(orderItem.getQtyOrdered());
					}
					if ("IN".equalsIgnoreCase(regionValue) && null != productTaxObj) {
						for (OmsProductTax productTax : productTaxObj) {
							SalesCreditmemoItemTax memoItemTax = new SalesCreditmemoItemTax();

							memoItemTax.setTaxType(productTax.getTaxType());
							memoItemTax.setTaxCountry(regionValue);
							memoItemTax.setTaxAmount(new BigDecimal(productTax.getTaxAmount()).multiply(orderedQty).setScale(4, RoundingMode.HALF_UP));
							memoItemTax.setTaxPercentage(new BigDecimal(productTax.getTaxPercentage()).multiply(orderedQty).setScale(4, RoundingMode.HALF_UP));
							memoItem.addSalesCreditmemoItemTax(memoItemTax);
						}
					}
					salesCreditmemoItemRepository.saveAndFlush(memoItem);

				}


				//memo.setSubtotal(totalrowTotal);

				memo.setDiscountAmount(totalDiscount);
				memo.setBaseDiscountAmount(totalbaseDiscount);

				memo.setTaxAmount(totalTaxAmount);
				memo.setBaseTaxAmount(totalBaseTaxAmount);

				memo.setDiscountTaxCompensationAmount(totalTaxcompAmount);
				memo.setBaseDiscountTaxCompensationAmount(totalBaseTaxCompAmount);
				memo.setVoucherAmount(totalVoucherRefunded);
				//memo.setBaseSubtotalInclTax(totalBaseTotalPriceIncludingTax);
				//memo.setSubtotalInclTax(totalPriceIncludingTax);

				salesCreditmemoRepository.saveAndFlush(memo);

			}
		} catch (Exception e) {
			LOGGER.info("exception save items"+ e.getMessage());
			throw new RuntimeException(e);
		}

    }
    
    private BigDecimal getGiftVoucherAmountByRequestRMA(List<SalesOrderItem> orderItemList, AmastyRmaRequest amastyRequest) {
    	Set<AmastyRmaRequestItem> amastyRmaRequestItems = amastyRequest.getAmastyRmaRequestItems();
    	for (AmastyRmaRequestItem amastyRmaRequestItem : amastyRmaRequestItems) {
    		List<SalesOrderItem> filterOrderItems = orderItemList.stream().filter(e -> e.getItemId().equals(amastyRmaRequestItem.getOrderItemId())).collect(Collectors.toList());
    		if(filterOrderItems.size() > 0) {
    			return amastyRmaRequestItem.getGiftVoucherRefundedAmount();
    		}
		}
    	return BigDecimal.ZERO;
    }
    
    
/**
 * @param order
 * @param memoIncrementId
 * @param store
 * @param totalRefundAmount
 * @param paymentMethod
 * @param rmaRequestId
 * @param refundAmountDetails
 * @return
 */
public SalesCreditmemo createReturnCreditMemo(SalesOrder order, String memoIncrementId,
		String totalRefundAmount, String paymentMethod, 
		String rmaRequestId,RefundAmountObject refundAmountDetails,Map<String,BigDecimal> mapSkuList,String msgString, RefundPaymentRespone refundResponse, String totalRefundOnlineAmount, String totalAmastyCreditAmount, double returnFee, double returnInvoiceAmount, BigDecimal totalAmountToShowInSMS) {
    	LOGGER.info("total refund online amount: "+ totalRefundOnlineAmount);
    	BigDecimal grandTotal = BigDecimal.ZERO;
    	BigDecimal baseGrandTotal = BigDecimal.ZERO;
    	
    	BigDecimal adjustment = BigDecimal.ZERO;
    	BigDecimal baseAdjustMent = BigDecimal.ZERO;
    	
    	BigDecimal adjustmentNegative = BigDecimal.ZERO;
    	BigDecimal baseAdjustMentNegative = BigDecimal.ZERO;
    	
    	BigDecimal amastyStoreCreditAmount = BigDecimal.ZERO;
    	BigDecimal baseAmastyStoreCreditAmount = BigDecimal.ZERO;
  
    	
		if (null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getDonationAmount()) {
			adjustment = order.getSubSalesOrder().getDonationAmount().negate();
			baseAdjustMent = order.getSubSalesOrder().getBaseDonationAmount().negate();
			adjustmentNegative = order.getSubSalesOrder().getDonationAmount();
			baseAdjustMentNegative = order.getSubSalesOrder().getBaseDonationAmount();
		}if(null != order.getShippingAmount()) {
			
			adjustment = adjustment.add(order.getShippingAmount()).negate();
			baseAdjustMent = baseAdjustMent.add(order.getBaseShippingAmount()).negate();
			adjustmentNegative = adjustmentNegative.add(order.getShippingAmount()) ;
			baseAdjustMentNegative =  baseAdjustMentNegative.add(order.getBaseShippingAmount()) ;
		}if(null != order.getImportFee() && null != order.getBaseImportFee()) {
			
			adjustment = adjustment.add(order.getImportFee()).negate();
			baseAdjustMent = baseAdjustMent.add(order.getBaseImportFee()).negate();
			adjustmentNegative = adjustmentNegative.add(order.getImportFee());
			baseAdjustMentNegative = baseAdjustMentNegative.add(order.getBaseImportFee()) ;
		}
    	
    	amastyStoreCreditAmount = refundAmountDetails.getRefundStorecreditAmount();
    	SalesCreditmemo memo = new SalesCreditmemo();
    	if(null != amastyStoreCreditAmount && amastyStoreCreditAmount.compareTo(BigDecimal.ZERO) == 0) {
    		LOGGER.info("order.getStoreToBaseRate() "+ order.getStoreToBaseRate());
    		baseAmastyStoreCreditAmount= amastyStoreCreditAmount.multiply(order.getStoreToBaseRate())
    				.setScale(4, RoundingMode.HALF_UP);
    	}
		if (null != totalRefundAmount) {

			grandTotal = new BigDecimal(totalRefundAmount);
			
			baseGrandTotal = new BigDecimal(totalRefundAmount).multiply(order.getStoreToBaseRate()).setScale(4,
					RoundingMode.HALF_UP);
		}
		
		if (MapUtils.isNotEmpty(mapSkuList)) {
			
			BigDecimal subTotal = getCanceledItemSubTotal(order, mapSkuList, null);
			BigDecimal subTotalIncludingTax = getCanceledItemQty(order, mapSkuList, null);
			BigDecimal baseSubTotalIncludingTax = subTotalIncludingTax.multiply(order.getStoreToBaseRate()).setScale(4,
					RoundingMode.HALF_UP);
			BigDecimal taxCompcompAmount = getCanceledTaxCompItemQty(order, mapSkuList);
			BigDecimal taxAmount = getCanceledTaxItemQty(order, mapSkuList);
			
			BigDecimal discount = getDiscountAmount(order, mapSkuList);
			BigDecimal baseDiscount = discount.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP);
			memo.setBaseSubtotalInclTax(baseSubTotalIncludingTax);
			memo.setSubtotalInclTax(subTotalIncludingTax);
			memo.setTaxAmount(taxAmount);
			memo.setBaseTaxAmount(
					taxCompcompAmount.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP));
			memo.setDiscountTaxCompensationAmount(taxCompcompAmount);
			memo.setBaseDiscountTaxCompensationAmount(
					taxCompcompAmount.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP));
			memo.setSubtotal(subTotal);
			memo.setBaseSubtotal(
					subTotal.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP));
			memo.setBaseDiscountAmount(order.getBaseDiscountAmount());
            memo.setDiscountAmount(order.getDiscountAmount());
            memo.setBaseDiscountAmount(baseDiscount);
            memo.setBaseSubtotal(subTotal.multiply(order.getStoreToBaseRate()).setScale(4, RoundingMode.HALF_UP));

		}else {
        	
        	 memo.setBaseSubtotalInclTax(order.getBaseSubtotalInclTax());
             memo.setSubtotalInclTax(order.getSubtotalInclTax());
             memo.setTaxAmount(order.getTaxAmount());
             memo.setDiscountTaxCompensationAmount(order.getDiscountTaxCompensationInvoiced());
             memo.setBaseDiscountTaxCompensationAmount(order.getBaseDiscountTaxCompensationInvoiced());
             memo.setSubtotal(order.getSubtotalInvoiced());
             memo.setBaseTaxAmount(order.getBaseTaxAmount());
             memo.setBaseDiscountAmount(order.getBaseDiscountAmount());
             memo.setDiscountAmount(order.getDiscountAmount());
        }
				    	 

        
        memo.setStoreId(order.getStoreId());
        memo.setStoreToOrderRate(order.getStoreToOrderRate());
       
        memo.setBaseToOrderRate(order.getBaseToOrderRate());
        if (OrderConstants.checkPaymentMethod(paymentMethod)) {
        memo.setGrandTotal(grandTotal);
        }else if(OrderConstants.checkBNPLPaymentMethods(paymentMethod) || paymentMethod.equalsIgnoreCase(PaymentConstants.CASHFREE)){
        	memo.setGrandTotal(grandTotal);
        }else {
        	 memo.setGrandTotal(BigDecimal.ZERO);
        }
        memo.setBaseShippingAmount(order.getBaseShippingAmount());
        memo.setStoreToBaseRate(order.getStoreToBaseRate());
        memo.setBaseToGlobalRate(order.getBaseToGlobalRate());
        memo.setBaseAdjustment(baseAdjustMent);
        memo.setAdjustment(adjustment);
        memo.setBaseGrandTotal(baseGrandTotal);
        memo.setOrderId(order.getEntityId());
        memo.setState(2);
        memo.setShippingAddressId(order.getShippingAddressId());
        memo.setBillingAddressId(order.getBillingAddressId());
        memo.setBaseCurrencyCode(order.getBaseCurrencyCode());
        memo.setGlobalCurrencyCode(order.getGlobalCurrencyCode());
        memo.setOrderCurrencyCode(order.getOrderCurrencyCode());
        memo.setStoreCurrencyCode(order.getStoreCurrencyCode());
        memo.setIncrementId(memoIncrementId);
        memo.setCreatedAt(new Timestamp(new Date().getTime()));
        memo.setUpdatedAt(new Timestamp(new Date().getTime()));
        memo.setBaseDiscountTaxCompensationAmount(order.getBaseDiscountTaxCompensationInvoiced());
        memo.setDiscountDescription(order.getCouponCode());
        memo.setCashOnDeliveryFee(order.getCashOnDeliveryFee());
        memo.setBaseCashOnDeliveryFee(order.getBaseCashOnDeliveryFee());
		if (OrderConstants.checkPaymentMethod(paymentMethod)) {
			memo.setAmstorecreditAmount(amastyStoreCreditAmount);
			memo.setAmstorecreditBaseAmount(baseAmastyStoreCreditAmount);
		} else if (OrderConstants.checkBNPLPaymentMethods(paymentMethod)
				|| paymentMethod.equalsIgnoreCase(PaymentConstants.CASHFREE)) {
			memo.setAmstorecreditAmount(amastyStoreCreditAmount);
			memo.setAmstorecreditBaseAmount(baseAmastyStoreCreditAmount);
		}else{
			BigDecimal baseTotalToBeRefundAmount = refundAmountDetails.getRefundStorecreditAmount().multiply(order.getStoreToBaseRate()).setScale(4,RoundingMode.HALF_UP);
        	if(refundAmountDetails.getRefundStorecreditAmount().compareTo(BigDecimal.ZERO)>0) {

				memo.setAmstorecreditAmount(refundAmountDetails.getRefundStorecreditAmount());
				memo.setAmstorecreditBaseAmount(baseTotalToBeRefundAmount);
			}
        }
        memo.setRmaNumber(rmaRequestId);
        
        // EAS value to credit memo
        AmastyRmaRequest rmaRequest = amastyRmaRequestRepository.findByRequestId(Integer.parseInt(rmaRequestId));
        memo.setEasCoins(rmaRequest.getEasCoins());
        memo.setEasValueInCurrency(rmaRequest.getEasValueInCurrency());
        memo.setEasValueInBaseCurrency(rmaRequest.getEasValueInBaseCurrency());
        // EAS value to credit memo

	// shukran value in credit memo
        memo.setShukranPointsRefunded(rmaRequest.getShukranPointsRefunded());
		memo.setShukranPointsRefundedValueInBaseCurrency(rmaRequest.getShukranPointsRefundedValueInBaseCurrency());
		memo.setShukranPointsRefundedValueInCurrency(rmaRequest.getShukranPointsRefundedValueInCurrency());
        // shukran end value in credit memo
        memo.setAdjustmentNegative(adjustmentNegative);
        memo.setBaseAdjustmentNegative(baseAdjustMentNegative);
        String returnValue;
		try {
			returnValue = mapper.writeValueAsString(mapSkuList);
	         memo.setCustomerNote(returnValue);      

		} catch (JsonProcessingException e) {
			LOGGER.error("error during customer note credit memo:"+e.getMessage());
			
		}

       if(returnFee>0){
		   String refundMessage= "We deducted "+ order.getStoreCurrencyCode() + returnFee+" as return fee";
		   if(returnInvoiceAmount>0){
			   refundMessage= refundMessage + "and taken" + order.getStoreCurrencyCode() + returnInvoiceAmount+ "as return invoice amount";
		   }
		   paymentDtfHelper.updateOrderStatusHistory(order, refundMessage, "return", order.getStatus());
	   }

	   if(returnInvoiceAmount<=0) {
		   String message = null;
		   String messageOnline = " online";
		   if (OrderConstants.checkPaymentMethod(paymentMethod)) {

			   if (null == msgString && StringUtils.isNotEmpty(totalRefundOnlineAmount) && StringUtils.isNotBlank(totalRefundOnlineAmount) && new BigDecimal(totalRefundOnlineAmount).compareTo(new BigDecimal(0)) > 0) {
				   message = "We refunded " + order.getStoreCurrencyCode() + totalRefundOnlineAmount + messageOnline;
			   } else {
				   message = msgString;
			   }

			   if (null != order.getAmstorecreditAmount()) {
				   message = message + "  styli credit " + order.getStoreCurrencyCode() + totalAmastyCreditAmount + " to your account";
			   }

		   } else if (OrderConstants.checkBNPLPaymentMethods(paymentMethod)) {
			   if (StringUtils.isNotBlank(totalRefundOnlineAmount) && new BigDecimal(totalRefundOnlineAmount).compareTo(new BigDecimal(0)) > 0) {
				   message = "We refunded " + order.getStoreCurrencyCode() + totalRefundOnlineAmount + messageOnline;
			   }
			   if (StringUtils.isNotBlank(totalAmastyCreditAmount) && StringUtils.isNotEmpty(totalAmastyCreditAmount) && new BigDecimal(totalAmastyCreditAmount).compareTo(new BigDecimal(0)) > 0) {
				   message = message + " & tabby " + order.getStoreCurrencyCode() + totalAmastyCreditAmount
						   + "to your account";
			   }
		   } else if (PaymentConstants.CASHFREE.equalsIgnoreCase(paymentMethod)) {
			   if (StringUtils.isNotBlank(totalRefundOnlineAmount) && new BigDecimal(totalRefundOnlineAmount).compareTo(new BigDecimal(0)) > 0) {
				   message = "We refunded " + order.getStoreCurrencyCode() + totalRefundOnlineAmount + messageOnline;
			   }

			   if (StringUtils.isNotBlank(totalAmastyCreditAmount) && StringUtils.isNotEmpty(totalAmastyCreditAmount) && new BigDecimal(totalAmastyCreditAmount).compareTo(new BigDecimal(0)) > 0) {
				   message = message + " & cashfree " + order.getStoreCurrencyCode() + totalAmastyCreditAmount
						   + "to your account";
			   }
		   } else if(paymentMethod.equalsIgnoreCase(PaymentCodeENUM.SHUKRAN_PAYMENT.toString())) {
			   message = "We refunded " + order.getStoreCurrencyCode() + rmaRequest.getShukranPointsRefundedValueInCurrency() + " to user Shukran amount";
		   } else if (StringUtils.isNotBlank(totalAmastyCreditAmount) && StringUtils.isNotEmpty(totalAmastyCreditAmount) && new BigDecimal(totalAmastyCreditAmount).compareTo(new BigDecimal(0)) > 0) {

			   message = OrderConstants.WE_REFUND_STYLI_CREDIT + order.getStoreCurrencyCode() + totalAmastyCreditAmount + " to user account";
		   }
		   paymentDtfHelper.updateOrderStatusHistory(order, message, "return", order.getStatus());
	   }
		if (Objects.nonNull(refundResponse)) {
			memo.setReconciliationReference(refundResponse.getPaymentRRN());
		}
		memo.setSmsMoney(totalAmountToShowInSMS.setScale(2, RoundingMode.HALF_UP));
        salesCreditmemoRepository.saveAndFlush(memo);
        
        return memo;
    }

    public void createCreditmemoComment(SalesCreditmemo memo, BigDecimal storeCreditAmount) {

        SalesCreditmemoComment memoComment = new SalesCreditmemoComment();
        memoComment.setParentId(memo.getEntityId());
        memoComment.setIsCustomerNotified(0);
        memoComment.setIsVisibleOnFront(0);
        String grandTotalMsg  = OrderConstants.AND_ONLINE_REFUND+memo.getStoreCurrencyCode()+" " + memo.getGrandTotal();
        if (storeCreditAmount != null && !(storeCreditAmount.compareTo(BigDecimal.ZERO) ==0)) {
            memoComment.setComment(OrderConstants.AMOUNT_REFUND_TO_STYLI_CREDIT_MSG+memo.getStoreCurrencyCode()+" " + storeCreditAmount);
            
		}
		if (null != memo.getGrandTotal() && !(memo.getGrandTotal().compareTo(BigDecimal.ZERO) == 0)) {
			
			String msg = "";
			if(storeCreditAmount != null && !(storeCreditAmount.compareTo(BigDecimal.ZERO) ==0)) {
				 msg = OrderConstants.AMOUNT_REFUND_TO_STYLI_CREDIT_MSG+memo.getStoreCurrencyCode()+" " + storeCreditAmount;
			}
			memoComment.setComment(msg.concat(grandTotalMsg));
		}
        memoComment.setCreatedAt(new Timestamp(new Date().getTime()));
        salesCreditmemoCommentRepository.saveAndFlush(memoComment);

    }
    
	/**
	 * @param memo
	 * @param storeCreditAmount
	 * @param msgString
	 */
	public void createCreditmemoFailComment(SalesCreditmemo memo, BigDecimal storeCreditAmount, String msgString) {

        SalesCreditmemoComment memoComment = new SalesCreditmemoComment();
        memoComment.setParentId(memo.getEntityId());
        memoComment.setIsCustomerNotified(0);
        memoComment.setIsVisibleOnFront(0);
        String grandTotalMsg  = OrderConstants.AND_PAYFORT_ERROR+ " "+msgString;
        
        if (storeCreditAmount != null && !(storeCreditAmount.compareTo(BigDecimal.ZERO) ==0)) {
            memoComment.setComment(OrderConstants.AMOUNT_REFUND_TO_STYLI_CREDIT_MSG+memo.getStoreCurrencyCode()+" " + storeCreditAmount);
            
		}
		if (null != memo.getGrandTotal() && !(memo.getGrandTotal().compareTo(BigDecimal.ZERO) == 0)) {
			
			String msg = "";
			if(storeCreditAmount != null && !(storeCreditAmount.compareTo(BigDecimal.ZERO) ==0)) {
				 msg = OrderConstants.AMOUNT_REFUND_TO_STYLI_CREDIT_MSG+memo.getStoreCurrencyCode()+" " + storeCreditAmount;
			}
			memoComment.setComment(msg.concat(grandTotalMsg));
		}
        memoComment.setCreatedAt(new Timestamp(new Date().getTime()));
        salesCreditmemoCommentRepository.saveAndFlush(memoComment);

    }

    public void createCreditmemoGrid(SalesOrder order, SalesCreditmemo memo, String memoIncrementId,
            SalesOrderGrid orderGrid, BigDecimal baseGrandTotal) {

        SalesCreditmemoGrid memoGrid = new SalesCreditmemoGrid();
        memoGrid.setEntityId(memo.getEntityId());
        memoGrid.setIncrementId(memoIncrementId);
        memoGrid.setCreatedAt(new Timestamp(new Date().getTime()));
        memoGrid.setUpdatedAt(new Timestamp(new Date().getTime()));
        memoGrid.setOrderId(order.getEntityId());
        memoGrid.setOrderIncrementId(order.getIncrementId());
        memoGrid.setOrderCreatedAt(order.getCreatedAt());
        memoGrid.setState(2);
        memoGrid.setBaseGrandTotal(baseGrandTotal);
        memoGrid.setOrderStatus(OrderConstants.CLOSED_ORDER_STATUS);
        memoGrid.setStoreId(order.getStoreId());
        memoGrid.setCustomerGroupId(order.getCustomerGroupId());
        memoGrid.setSubtotal(order.getSubtotalInvoiced());
        memoGrid.setOrderBaseGrandTotal(order.getBaseGrandTotal());
        if (orderGrid != null) {
            memoGrid.setBillingName(orderGrid.getBillingName());
            memoGrid.setBillingAddress(orderGrid.getBillingAddress());
            memoGrid.setShippingAddress(orderGrid.getShippingAddress());
            memoGrid.setCustomerName(orderGrid.getCustomerName());
            memoGrid.setCustomerEmail(orderGrid.getCustomerEmail());
            memoGrid.setPaymentMethod(orderGrid.getPaymentMethod());
            memoGrid.setShippingInformation(orderGrid.getShippingInformation());
            memoGrid.setShippingAndHandling(orderGrid.getShippingAndHandling());
        }
        salesCreditmemoGridRepository.saveAndFlush(memoGrid);

    }

    public void releaseStoreCredit(SalesOrder order, BigDecimal storeCreditAmount) {

        List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
                .findByCustomerId(order.getCustomerId());
        AmastyStoreCredit amastyStoreCredit = amastyStoreCredits.size() > 0 ? amastyStoreCredits.get(0) : null;
        BigDecimal customerStoreCreditBalance = BigDecimal.ZERO;
		if (null == amastyStoreCredit) {

			amastyStoreCredit = new AmastyStoreCredit();
			customerStoreCreditBalance = storeCreditAmount;
			amastyStoreCredit.setCustomerId(order.getCustomerId());
		} else {
			customerStoreCreditBalance = amastyStoreCredit.getStoreCredit();
			if (null != customerStoreCreditBalance) {
				customerStoreCreditBalance = customerStoreCreditBalance.add(storeCreditAmount);

			}

		}
		if(null != amastyStoreCredit.getReturnableAmount()) {
			
			BigDecimal existingStoreCreditAmount = amastyStoreCredit.getReturnableAmount();
			
			existingStoreCreditAmount = existingStoreCreditAmount.add(storeCreditAmount);
			amastyStoreCredit.setReturnableAmount(existingStoreCreditAmount);
		}else {
			
			amastyStoreCredit.setReturnableAmount(storeCreditAmount);
		}
        
        if (amastyStoreCredit != null) {
        	
            amastyStoreCredit.setStoreCredit(customerStoreCreditBalance);
            amastyStoreCreditRepository.saveAndFlush(amastyStoreCredit);

            List<AmastyStoreCreditHistory> histories = amastyStoreCreditHistoryRepository
                    .findByCustomerId(order.getCustomerId());
            int newCustomerHistoryId = 1;
            if (CollectionUtils.isNotEmpty(histories)) {
                AmastyStoreCreditHistory lastHistory = histories.get(histories.size() - 1);
                newCustomerHistoryId = lastHistory.getCustomerHistoryId() + 1;
            }
            AmastyStoreCreditHistory history = new AmastyStoreCreditHistory();
            history.setCustomerHistoryId(newCustomerHistoryId);
            history.setCustomerId(order.getCustomerId());
            history.setDeduct(0);
            history.setDifference(storeCreditAmount);
            history.setStoreCreditBalance(amastyStoreCredit.getStoreCredit());
            history.setAction(5);
            history.setActionData("[\"" + order.getIncrementId() + "\"]");
            history.setMessage(null);
            history.setCreatedAt(new Timestamp(new Date().getTime()));
            history.setStoreId(order.getStoreId());
            amastyStoreCreditHistoryRepository.saveAndFlush(history);
        }

    }


	public void releaseStoreCreditForSplitOrder(SplitSalesOrder order, BigDecimal storeCreditAmount) {
		List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
				.findByCustomerId(order.getCustomerId());
		AmastyStoreCredit amastyStoreCredit = amastyStoreCredits.size() > 0 ? amastyStoreCredits.get(0) : null;
		BigDecimal customerStoreCreditBalance = BigDecimal.ZERO;
		if (null == amastyStoreCredit) {
			amastyStoreCredit = new AmastyStoreCredit();
			customerStoreCreditBalance = storeCreditAmount;
			amastyStoreCredit.setCustomerId(order.getCustomerId());
		} else {
			customerStoreCreditBalance = amastyStoreCredit.getStoreCredit();
			if (null != customerStoreCreditBalance) {
				customerStoreCreditBalance = customerStoreCreditBalance.add(storeCreditAmount);

			}

		}
		if(null != amastyStoreCredit.getReturnableAmount()) {
			
			BigDecimal existingStoreCreditAmount = amastyStoreCredit.getReturnableAmount();
			
			existingStoreCreditAmount = existingStoreCreditAmount.add(storeCreditAmount);
			amastyStoreCredit.setReturnableAmount(existingStoreCreditAmount);
		}else {
			
			amastyStoreCredit.setReturnableAmount(storeCreditAmount);
		}

		if (amastyStoreCredit != null) {
			
			amastyStoreCredit.setStoreCredit(customerStoreCreditBalance);
			amastyStoreCreditRepository.saveAndFlush(amastyStoreCredit);

			List<AmastyStoreCreditHistory> histories = amastyStoreCreditHistoryRepository
					.findByCustomerId(order.getCustomerId());
			int newCustomerHistoryId = 1;
			if (CollectionUtils.isNotEmpty(histories)) {
				AmastyStoreCreditHistory lastHistory = histories.get(histories.size() - 1);
				newCustomerHistoryId = lastHistory.getCustomerHistoryId() + 1;
			}
			AmastyStoreCreditHistory history = new AmastyStoreCreditHistory();
			history.setCustomerHistoryId(newCustomerHistoryId);
			history.setCustomerId(order.getCustomerId());
			history.setDeduct(0);
			history.setDifference(storeCreditAmount);
			history.setStoreCreditBalance(amastyStoreCredit.getStoreCredit());
			history.setAction(5);
			history.setActionData("[\"" + order.getIncrementId() + "\"]");
			history.setMessage(null);
			history.setCreatedAt(new Timestamp(new Date().getTime()));
			history.setStoreId(order.getStoreId());
			amastyStoreCreditHistoryRepository.saveAndFlush(history);
		}
	}

    public boolean payfortRefundInitiation(SalesOrder order, String fortId, String paymentMethod, Stores store) {

        String accessCode = getPayfortAccessCode(paymentMethod, store);
        String merchantIdentifier = getPayfortMerchantIdentifier(paymentMethod, store);
        String inPassPhrase = getPayfortInPassPhrase(paymentMethod, store);

        if (order.getBaseGrandTotal() == null || accessCode == null || merchantIdentifier == null
                || inPassPhrase == null) {
            LOGGER.error("Could not fetch inPassPhrase for payfort!");
            return false;
        }
        Integer orderTotal = order.getBaseGrandTotal().multiply(new BigDecimal(100)).intValue();

        // Values for payfort request
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("command", "REFUND");
        requestMap.put("access_code", accessCode);
        requestMap.put("merchant_identifier", merchantIdentifier);
        requestMap.put("currency", order.getStoreCurrencyCode());
        requestMap.put("language", "en");
        requestMap.put("merchant_reference", order.getMerchantReferance());
        requestMap.put("amount", orderTotal);
        requestMap.put("fort_id", fortId);
        requestMap.put("order_description", order.getIncrementId());

        // https://stackoverflow.com/a/22663788/3963153
        // Remove null values from map to prevent exception
        requestMap.values().removeAll(Collections.singleton(null));

        // Signature creation starts
        requestMap = requestMap.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(Collectors
                .toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        StringBuilder requestString = new StringBuilder(inPassPhrase);
        for (Map.Entry<String, Object> entry : requestMap.entrySet())
            requestString.append(entry.getKey()).append("=").append(entry.getValue());
        requestString.append(inPassPhrase);

        String signature;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(requestString.toString().getBytes(StandardCharsets.UTF_8));
            signature = DatatypeConverter.printHexBinary(hashed);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Could not create signature for payfort!");
            return false;
        }
        requestMap.put("signature", signature);
        // Signature creation ends

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

        HttpEntity<Map<String, Object>> requestBody = new HttpEntity<>(requestMap, requestHeaders);
        String url = "https://sbpaymentservices.payfort.com/FortAPI/paymentApi";

        try {
            ResponseEntity<PayfortRefundResponse> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
                    PayfortRefundResponse.class);

            // return false;
            if (response.getStatusCode() == HttpStatus.OK) {
                if (response.getBody() != null && response.getBody().getResponseCode().equals("06000")) {
                    LOGGER.error("payfort refund success. order: " + order.getIncrementId() + ", "
                            + response.getBody().getResponseCode() + ", " + response.getBody().getResponseMessage());
                    return true;
                } else {
                    LOGGER.error("payfort refund failed. order: " + order.getIncrementId() + ", "
                            + response.getBody().getResponseCode() + ", " + response.getBody().getResponseMessage());
                    return false;
                }
            } else {
                LOGGER.error("Could not refund for order: " + order.getIncrementId());
                return false;
            }
        } catch (RestClientException e) {
            LOGGER.error("Exception while creating  refund for order: " + order.getIncrementId());
            return false;
        }

    }

    private String getPayfortInPassPhrase(String paymentMethod, Stores store) {
        String inPassPhrase = null;
        String configPath;
        if (paymentMethod.equalsIgnoreCase(PaymentCodeENUM.PAYFORT_FORT_CC.getValue())
                || paymentMethod.equalsIgnoreCase(PaymentCodeENUM.MD_PAYFORT.getValue())
                || paymentMethod.equalsIgnoreCase(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())) {
            configPath = "payment/payfort_fort/sha_in_pass_phrase";
        } else if (paymentMethod.equalsIgnoreCase(PaymentCodeENUM.APPLE_PAY.getValue())) {
            configPath = "payment/apple_pay/sha_in_pass_phrase";
        } else {
            return null;
        }
        try {
            CoreConfigData coreConfigData = coreConfigDataRepository.findByPathAndScopeId(configPath,
                    store.getWebsiteId());
            if (coreConfigData == null) {
                coreConfigData = coreConfigDataRepository.findByPathAndScopeId(configPath, Constants.ADMIN_STORE_ID);
            }
            if (coreConfigData != null)
                inPassPhrase = coreConfigData.getValue();
        } catch (Exception ignored) {
        }
        return inPassPhrase;
    }

    private String getPayfortMerchantIdentifier(String paymentMethod, Stores store) {

        String merchantIdentifier = null;
        String configPath;
        if (paymentMethod.equalsIgnoreCase(PaymentCodeENUM.PAYFORT_FORT_CC.getValue())
                || paymentMethod.equalsIgnoreCase(PaymentCodeENUM.MD_PAYFORT.getValue())
                || paymentMethod.equalsIgnoreCase(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())) {
            configPath = "payment/payfort_fort/merchant_identifier";
        } else if (paymentMethod.equalsIgnoreCase(PaymentCodeENUM.APPLE_PAY.getValue())) {
            configPath = "payment/apple_pay/merchant_identifier";
        } else {
            return null;
        }
        try {
            CoreConfigData coreConfigData = coreConfigDataRepository.findByPathAndScopeId(configPath,
                    store.getWebsiteId());
            if (coreConfigData == null) {
                coreConfigData = coreConfigDataRepository.findByPathAndScopeId(configPath, Constants.ADMIN_STORE_ID);
            }
            if (coreConfigData != null)
                merchantIdentifier = coreConfigData.getValue();
        } catch (Exception ignored) {
        }

        return merchantIdentifier;
    }

    private String getPayfortAccessCode(String paymentMethod, Stores store) {

        String accessCode = null;
        String configPath;
        if (paymentMethod.equalsIgnoreCase(PaymentCodeENUM.PAYFORT_FORT_CC.getValue())
                || paymentMethod.equalsIgnoreCase(PaymentCodeENUM.MD_PAYFORT.getValue())
                || paymentMethod.equalsIgnoreCase(PaymentCodeENUM.MD_PAYFORT_CC_VAULT.getValue())) {
            configPath = "payment/payfort_fort/access_code";
        } else if (paymentMethod.equalsIgnoreCase(PaymentCodeENUM.APPLE_PAY.getValue())) {
            configPath = "payment/apple_pay/access_code";
        } else {
            return null;
        }
        try {
            CoreConfigData coreConfigData = coreConfigDataRepository.findByPathAndScopeId(configPath,
                    store.getWebsiteId());
            if (coreConfigData == null) {
                coreConfigData = coreConfigDataRepository.findByPathAndScopeId(configPath, Constants.ADMIN_STORE_ID);
            }
            if (coreConfigData != null)
                accessCode = coreConfigData.getValue();
        } catch (Exception ignored) {
        }

        return accessCode;
    }

    public MagentoAPIResponse payfortRefundInitiationFromMagento(SalesOrder order) {

        MagentoAPIResponse resp = new MagentoAPIResponse();

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
        requestHeaders.set("Authorization", "Bearer " + magentoIntegrationToken);
		BigDecimal grandTotal = order.getGrandTotal();
        
        if(null != order.getSubSalesOrder() && null != order.getSubSalesOrder().getDonationAmount()) {
    		LOGGER.info("this order has donation amount");
    		
    		BigDecimal donationAmount = order.getSubSalesOrder().getDonationAmount();
    		if(null != order.getAmstorecreditAmount()) {
    			
    			BigDecimal amastyStoreCreditAmount = order.getAmstorecreditAmount();
    			 			BigDecimal divideVal = new BigDecimal(100);
    			
    			 grandTotal = order.getGrandTotal();
    			
    			BigDecimal totalAmount = grandTotal.add(amastyStoreCreditAmount);
    			
    			BigDecimal donationPercenatgeShare = donationAmount.
    					divide(totalAmount, 4, RoundingMode.HALF_UP).multiply(divideVal).setScale(4, RoundingMode.HALF_UP);
    			
    			 BigDecimal refundGrandTotal = grandTotal.divide(divideVal,4, RoundingMode.HALF_UP)
    					.multiply(donationPercenatgeShare).setScale(4, RoundingMode.HALF_UP);
    			 
    			 grandTotal = grandTotal.subtract(refundGrandTotal);
     			
    			
    		}else {
    			
    			grandTotal = order.getGrandTotal().subtract(donationAmount);
    			
    		}
    	}
        

        MagentoRefundOrderRequest magentoRefundOrderRequest = new MagentoRefundOrderRequest();
        magentoRefundOrderRequest.setOrderId(order.getEntityId());
        magentoRefundOrderRequest.setRefundedAmount(grandTotal);

        HttpEntity<MagentoRefundOrderRequest> requestBody = new HttpEntity<>(magentoRefundOrderRequest, requestHeaders);
        String url = magentoBaseUrl + "/rest/V1/payfort-refund";
        // String url = "https://dev.stylifashion.com" + "/rest/V1/payfort-refund";

        LOGGER.info("magento url for refund: " + url);
        LOGGER.info("Request body: " + magentoRefundOrderRequest.toString());

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
    
    
	public BigDecimal getCanceledItemQty(SalesOrder order, Map<String, BigDecimal> mapSkuList , String sku) {

		BigDecimal totalCancelVal = new BigDecimal(0);
		
		if(null != sku) {
			
			for (Map.Entry<String, BigDecimal> entrMap : mapSkuList.entrySet()) {

				if (null != entrMap.getValue() && entrMap.getValue().intValue() !=0
						&& sku.equalsIgnoreCase(entrMap.getKey())){
					SalesOrderItem salesOrderItem = order.getSalesOrderItem().stream()
							.filter(e -> e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)
									&& entrMap.getKey().equals(e.getSku()))
							.findFirst().orElse(null);

					if (null != salesOrderItem && null != salesOrderItem.getPriceInclTax()) {

						BigDecimal subTotalPrice = salesOrderItem.getPriceInclTax().multiply(entrMap.getValue()).setScale(4,
								RoundingMode.HALF_UP);
						;

						totalCancelVal = totalCancelVal.add(subTotalPrice);
					}
				}
				
			}
			
		}else {
			
			for (Map.Entry<String, BigDecimal> entrMap : mapSkuList.entrySet()) {

				if (null != entrMap.getValue() && entrMap.getValue().intValue() !=0) {
					SalesOrderItem salesOrderItem = order.getSalesOrderItem().stream()
							.filter(e -> e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)
									&& entrMap.getKey().equals(e.getSku()))
							.findFirst().orElse(null);

					if (null != salesOrderItem && null != salesOrderItem.getPriceInclTax()) {

						BigDecimal subTotalPrice = salesOrderItem.getPriceInclTax().multiply(entrMap.getValue()).setScale(4,
								RoundingMode.HALF_UP);
						;

						totalCancelVal = totalCancelVal.add(subTotalPrice);
					}
				}
				
			}
			
		}

	

		return totalCancelVal;
	}
	
	public BigDecimal getCanceledItemSubTotal(SalesOrder order, Map<String, BigDecimal> mapSkuList , String sku) {

		BigDecimal totalCancelVal = new BigDecimal(0);
		
		if(null != sku) {
			
			for (Map.Entry<String, BigDecimal> entrMap : mapSkuList.entrySet()) {

				if (null != entrMap.getValue() && entrMap.getValue().intValue() !=0
						&& sku.equalsIgnoreCase(entrMap.getKey())){
					SalesOrderItem salesOrderItem = order.getSalesOrderItem().stream()
							.filter(e -> e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)
									&& entrMap.getKey().equals(e.getSku()))
							.findFirst().orElse(null);

					if (null != salesOrderItem && null != salesOrderItem.getPrice()) {

						BigDecimal subTotalPrice = salesOrderItem.getPrice().multiply(entrMap.getValue()).setScale(4,
								RoundingMode.HALF_UP);
						;

						totalCancelVal = totalCancelVal.add(subTotalPrice);
					}
				}
				
			}
			
		}else {
			
			for (Map.Entry<String, BigDecimal> entrMap : mapSkuList.entrySet()) {

				if (null != entrMap.getValue() && entrMap.getValue().intValue() !=0) {
					SalesOrderItem salesOrderItem = order.getSalesOrderItem().stream()
							.filter(e -> e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)
									&& entrMap.getKey().equals(e.getSku()))
							.findFirst().orElse(null);

					if (null != salesOrderItem && null != salesOrderItem.getPrice()) {

						BigDecimal subTotalPrice = salesOrderItem.getPrice().multiply(entrMap.getValue()).setScale(4,
								RoundingMode.HALF_UP);
						;

						totalCancelVal = totalCancelVal.add(subTotalPrice);
					}
				}
				
			}
			
		}

	

		return totalCancelVal;
	}
	
	public BigDecimal getCanceledTaxCompItemQty(SalesOrder order, Map<String, BigDecimal> mapSkuList) {

		BigDecimal totalCancelVal = new BigDecimal(0);

		for (Map.Entry<String, BigDecimal> entrMap : mapSkuList.entrySet()) {

			if (null != entrMap.getValue() && entrMap.getValue().intValue() != 0) {
				SalesOrderItem salesOrderItem = order.getSalesOrderItem().stream()
						.filter(e -> e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)
								&& entrMap.getKey().equals(e.getSku()))
						.findFirst().orElse(null);

				if (null != salesOrderItem && null != salesOrderItem.getDiscountTaxCompensationAmount()) {

					BigDecimal subTotalPrice = salesOrderItem.getDiscountTaxCompensationAmount()
							.multiply(entrMap.getValue()).setScale(4, RoundingMode.HALF_UP);
					;

					totalCancelVal = totalCancelVal.add(subTotalPrice);
				}
			}

		}

		return totalCancelVal;
	}
	
	public BigDecimal getCanceledTaxItemQty(SalesOrder order, Map<String, BigDecimal> mapSkuList) {

		BigDecimal totalCancelVal = new BigDecimal(0);
		
		
			
			for (Map.Entry<String, BigDecimal> entrMap : mapSkuList.entrySet()) {

				if (null != entrMap.getValue() && entrMap.getValue().intValue() !=0) {
					SalesOrderItem salesOrderItem = order.getSalesOrderItem().stream()
							.filter(e -> e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)
									&& entrMap.getKey().equals(e.getSku()))
							.findFirst().orElse(null);

					if (null != salesOrderItem && null != salesOrderItem.getTaxAmount()) {
						
						
						BigDecimal indivisualTaxAmount = salesOrderItem.getTaxAmount()
								.divide(salesOrderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP).setScale(4,
										RoundingMode.HALF_UP).setScale(4,
												RoundingMode.HALF_UP);

						BigDecimal taxAmount = indivisualTaxAmount.multiply(entrMap.getValue()).setScale(4,
								RoundingMode.HALF_UP);
						;

						totalCancelVal = totalCancelVal.add(taxAmount);
					}
				}
				
			}
			
	

	

		return totalCancelVal;
	}
	
	public BigDecimal getDiscountAmount(SalesOrder order, Map<String, BigDecimal> mapSkuList) {

		BigDecimal totalCancelVal = new BigDecimal(0);
		
		
			
			for (Map.Entry<String, BigDecimal> entrMap : mapSkuList.entrySet()) {

				if (null != entrMap.getValue() && entrMap.getValue().intValue() != 0) {
					SalesOrderItem salesOrderItem = order.getSalesOrderItem().stream()
							.filter(e -> e.getProductType().equalsIgnoreCase(
									OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE) && entrMap.getKey().equals(e.getSku()))
							.findFirst().orElse(null);

					if (null != salesOrderItem && null != salesOrderItem.getDiscountAmount()) {

						BigDecimal qtyCancelled = mapSkuList.get(salesOrderItem.getSku());
						BigDecimal qtyOrdered = salesOrderItem.getQtyOrdered();
						BigDecimal Indivisualdiscount = salesOrderItem.getDiscountAmount()
								.divide(qtyOrdered, 4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
						BigDecimal cancelDiscountVal = BigDecimal.ZERO;
						if (null != qtyCancelled) {
							cancelDiscountVal = Indivisualdiscount.multiply(qtyCancelled).setScale(4,
									RoundingMode.HALF_UP);
						}

						totalCancelVal = totalCancelVal.add(cancelDiscountVal);
					}
				}
				
			}
			
	

	

		return totalCancelVal;
	}
	
	/**
	 * @param request
	 * @param order
	 */
	public void updateOrderStatusHistory( SalesOrder order, String message
			,String entity, String status) {
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
	
	
	
	public BigDecimal getGrandTotalAmount(SalesOrder order, Map<String, BigDecimal> mapSkuList ) {

		BigDecimal totalCancelVal = new BigDecimal(0);
		
			for (Map.Entry<String, BigDecimal> entrMap : mapSkuList.entrySet()) {

				if (null != entrMap.getValue() && entrMap.getValue().intValue() !=0) {
					SalesOrderItem item = order.getSalesOrderItem().stream()
							.filter(e -> e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE)
									&& entrMap.getKey().equals(e.getSku()))
							.findFirst().orElse(null);

					BigDecimal priceIncludeTax = item.getPriceInclTax();
					BigDecimal qtyCancelled = mapSkuList.get(item.getSku());
					if (null != item.getDiscountAmount() && !(item.getDiscountAmount().compareTo(BigDecimal.ZERO) == 0)) {

						
						BigDecimal qtyOrdered = item.getQtyOrdered();
						BigDecimal Indivisualdiscount = item.getDiscountAmount().divide(qtyOrdered, 4, RoundingMode.HALF_UP).setScale(4,
								RoundingMode.HALF_UP);
						BigDecimal cancelDiscountVal = BigDecimal.ZERO;
						if (null != qtyCancelled) {
							cancelDiscountVal = Indivisualdiscount.multiply(qtyCancelled).setScale(4, RoundingMode.HALF_UP);

						}
						priceIncludeTax = priceIncludeTax.multiply(qtyCancelled).setScale(4, RoundingMode.HALF_UP).subtract(cancelDiscountVal);
						totalCancelVal = totalCancelVal.add(priceIncludeTax);
						}else {
						
							priceIncludeTax = priceIncludeTax.multiply(qtyCancelled).setScale(4, RoundingMode.HALF_UP);
							totalCancelVal = totalCancelVal.add(priceIncludeTax);
								
					}
				}
				
			}
			
		

	

		return totalCancelVal;
	}
	

}
