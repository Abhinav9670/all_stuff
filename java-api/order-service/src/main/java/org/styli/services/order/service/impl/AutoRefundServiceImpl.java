package org.styli.services.order.service.impl;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.common.protocol.types.Field.Str;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.model.sales.SalesOrder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.styli.services.order.converter.OrderEntityConverter;
import org.springframework.transaction.annotation.Transactional;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.helper.PrepaidRefundHelper;
import org.styli.services.order.model.sales.RtoAutoRefund;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrderPayment;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.autoRefund.AutoRefundDTO;
import org.styli.services.order.pojo.autoRefund.RefundResponseDTO;
import org.styli.services.order.pojo.autoRefund.RtoOrderResponse;
import org.styli.services.order.repository.SalesOrder.RtoAutoRefundRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.service.AutoRefundService;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;

@Service
public class AutoRefundServiceImpl implements AutoRefundService {
	
	private static final Log LOGGER = LogFactory.getLog(AutoRefundServiceImpl.class);

	@Autowired
	private SalesOrderRepository salesOrderRepository;

	@Autowired
	private SplitSalesOrderRepository splitSalesOrderRepository;

	@Autowired
	@Lazy
	private PrepaidRefundHelper prepaidRefundHelper;

	@Autowired
	private RtoAutoRefundRepository rtoAutoRefundRepository;

	@Autowired
	private OrderHelper orderHelper;

	/**
	 * Returns RTO orders or search RTO orders
	 */
	@Override
	public RefundResponseDTO rtoOrders(AutoRefundDTO autoRefundDTO) {
		RefundResponseDTO resp = new RefundResponseDTO();
		normalizePaginationParams(autoRefundDTO);

		List<String> incrementIdsList = sanitizeList(autoRefundDTO.getIncrementIds());
		List<String> statusList = sanitizeList(autoRefundDTO.getStatus());
		updateStatusList(statusList);

		PageRequest pageable = PageRequest.of(autoRefundDTO.getOffset(), autoRefundDTO.getPageSize(), Sort.by("created_at").descending());
		Date rtoFromDate = Constants.orderCredentials.getTabby().getRtoFromDate();

		Page<SalesOrder> refundOrders = null;
		Page<RtoAutoRefund> refundOrdersStatus = null;

		if (!statusList.isEmpty()) {
			if (statusList.contains("rto")) {
				refundOrders = salesOrderRepository.getRtoOrdersByStatus(pageable, rtoFromDate);
			} else {
				pageable = PageRequest.of(autoRefundDTO.getOffset(), autoRefundDTO.getPageSize(), Sort.by("createdAt").descending());
				refundOrdersStatus = rtoAutoRefundRepository.findByStatusIn(statusList, pageable);
			}
		} else if (incrementIdsList.isEmpty()) {
			refundOrders = salesOrderRepository.getRtoOrders(pageable, rtoFromDate);
		} else {
			refundOrders = salesOrderRepository.findByIncrementIdInAndCreatedAfter(incrementIdsList, rtoFromDate, pageable);
		}

		return buildResponse(resp, refundOrders, refundOrdersStatus);
	}

	private void normalizePaginationParams(AutoRefundDTO autoRefundDTO) {
		autoRefundDTO.setOffset(Math.max(autoRefundDTO.getOffset(), 0));
		autoRefundDTO.setPageSize(autoRefundDTO.getPageSize() > 0 ? autoRefundDTO.getPageSize() : 10);
	}

	private List<String> sanitizeList(List<String> list) {
		return list == null ? new ArrayList<>() : list.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
	}

	private void updateStatusList(List<String> statusList) {
		int index = statusList.indexOf("pending");
		if (index != -1) {
			statusList.set(index, "rto");
		}
	}

	private RefundResponseDTO buildResponse(RefundResponseDTO resp, Page<SalesOrder> refundOrders, Page<RtoAutoRefund> refundOrdersStatus) {
		List<RtoOrderResponse> orders = new ArrayList<>();

		if (refundOrders != null) {
			refundOrders.getContent().forEach(order -> orders.add(mapSalesOrderToResponse(order)));
			setResponseDetails(resp, refundOrders, orders);
		} else if (refundOrdersStatus != null) {
			refundOrdersStatus.getContent().forEach(order -> orders.add(mapRtoAutoRefundToResponse(order)));
			setResponseDetails(resp, refundOrdersStatus, orders);
		}
		return resp;
	}

	private RtoOrderResponse mapSalesOrderToResponse(SalesOrder order) {
		SalesOrderPayment orderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
		RtoOrderResponse response = new RtoOrderResponse();
		response.setIncrementId(order.getIncrementId());
		response.setCustomerEmail(order.getCustomerEmail());
		if (orderPayment != null) {
			response.setMethod(orderPayment.getMethod());
			response.setGrandTotal(determineGrandTotal(order, orderPayment));
		}
		List<RtoAutoRefund> rtoAutoRefund = orderHelper.getRtoAutoRefundList(order);
		response.setStatus(!rtoAutoRefund.isEmpty() ? rtoAutoRefund.get(0).getStatus() : PaymentConstants.REFUND_STATUS_PENDING);
		return response;
	}

	private BigDecimal determineGrandTotal(SalesOrder order, SalesOrderPayment orderPayment) {
		if (orderPayment.getMethod().equalsIgnoreCase(OrderConstants.PAYMENT_METHOD_TYPE_FREE)) {
			return order.getAmstorecreditAmount();
		} else if (orderPayment.getMethod().equalsIgnoreCase(OrderConstants.SHUKRAN_PAYMENT)
				&& order.getSubSalesOrder() != null
				&& order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency() != null
				&& order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency().compareTo(BigDecimal.ZERO) > 0) {
			return order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency();
		}
		return order.getGrandTotal();
	}

	private RtoOrderResponse mapRtoAutoRefundToResponse(RtoAutoRefund order) {
		RtoOrderResponse response = new RtoOrderResponse();
		response.setIncrementId(order.getIncrementId());
		response.setCustomerEmail(order.getCustomerEmail());
		response.setMethod(order.getPaymentMethod());
		response.setGrandTotal(order.getRefundAmount());
		response.setStatus(order.getStatus());
		return response;
	}

	private void setResponseDetails(RefundResponseDTO resp, Page<?> page, List<RtoOrderResponse> orders) {
		resp.setTotalCount(page.getTotalElements());
		resp.setTotalPageSize(page.getTotalPages());
		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg("Refund orders fetched successfully!");
		resp.setResponse(orders);
	}

	List<String>[] getParentAndSplitIncrementIds(List<String> incrementIds) {
		List<String> parentIncrementIds = new ArrayList<>();
		List<String> splitIncrementIds = new ArrayList<>();
		for (String incrementId : incrementIds) {
			if (incrementId.contains("-")) {
				splitIncrementIds.add(incrementId);
			} else {
				parentIncrementIds.add(incrementId);
			}
		}
		return new List[] { parentIncrementIds, splitIncrementIds };
	}

	@Override
	@Async("asyncExecutor")
	@Transactional
	public void intiateBulkRefund(List<String> incrementIds) {

		LOGGER.info("Orders to Initiate Refund : " + incrementIds);
		List<String>[] incrementIdsList = getParentAndSplitIncrementIds(incrementIds);
		incrementIds = incrementIdsList[0];
		List<String> splitIncrementIds = incrementIdsList[1];

		List<SalesOrder> salesOrder = salesOrderRepository.findByIncrementIdIn(incrementIds);
		List<SplitSalesOrder> splitSalesOrders = splitSalesOrderRepository.findByIncrementIdIn(splitIncrementIds);
        System.out.println("intiateBulkRefund ->" + splitSalesOrders.size());
		for (SalesOrder salesOrder2 : salesOrder) {
			try {
				prepaidRefundHelper.prepaidRefundCall(salesOrder2);
			} catch (Exception e) {
				LOGGER.error("Error In RTO Order: " + salesOrder2.getIncrementId() + " auto refund. Error : " + e);
			}
		}

		for (SplitSalesOrder splitSalesOrder : splitSalesOrders) {
			try {
				prepaidRefundHelper.prepaidRefundCallForSplitOrder(splitSalesOrder);
			} catch (Exception e) {
				LOGGER.error("Error In Split Order: " + splitSalesOrder.getIncrementId() + " auto refund. Error : " + e);
			}
		}
	}

	@Override
	@Transactional
	public void updateRtoAutoRefund(SalesOrder salesOrder, SplitSalesOrder splitSalesOrder, String status) {
		String incrementId = salesOrder != null ? salesOrder.getIncrementId() : splitSalesOrder.getIncrementId();

		LOGGER.info("RTO refund order " + incrementId + "status to be updated. Status : " + status);
		SalesOrderPayment orderPayment = salesOrder != null ? salesOrder.getSalesOrderPayment().stream().findFirst().orElse(null) : null;
		SplitSalesOrderPayment orderPaymentSplit = splitSalesOrder != null ? splitSalesOrder.getSplitSalesOrderPayments().stream().findFirst().orElse(null) : null;
		
		try {
			// Try to find existing record first
			RtoAutoRefund rtoAutoRefund = rtoAutoRefundRepository.findByIncrementId(incrementId);
			if (Objects.nonNull(rtoAutoRefund)) {
				rtoAutoRefund.setStatus(status);
				rtoAutoRefund.setRefundAt(new Timestamp(new Date().getTime()));
				rtoAutoRefundRepository.saveAndFlush(rtoAutoRefund);
			} else {
				// Create new record with proper exception handling for duplicates
				RtoAutoRefund rtoAutoRefunds = new RtoAutoRefund();
				if(salesOrder != null){
					rtoAutoRefunds.setSalesOrder(salesOrder);
					rtoAutoRefunds.setCustomerEmail(salesOrder.getCustomerEmail());
					rtoAutoRefunds.setIncrementId(salesOrder.getIncrementId());
					rtoAutoRefunds.setPaymentId(salesOrder.getSubSalesOrder().getPaymentId());
					if (null != orderPayment) {
						rtoAutoRefunds.setPaymentMethod(orderPayment.getMethod());
					}
					rtoAutoRefunds.setOrderAmount(salesOrder.getGrandTotal());
					rtoAutoRefunds.setRefundAmount(salesOrder.getGrandTotal());
				} else if (splitSalesOrder != null) {
					rtoAutoRefunds.setSplitSalesOrder(splitSalesOrder);
					rtoAutoRefunds.setSalesOrder(splitSalesOrder.getSalesOrder());
					rtoAutoRefunds.setCustomerEmail(splitSalesOrder.getSalesOrder().getCustomerEmail());
					rtoAutoRefunds.setIncrementId(splitSalesOrder.getIncrementId());
					rtoAutoRefunds.setPaymentId(splitSalesOrder.getSplitSubSalesOrder().getPaymentId());
					if (null != orderPaymentSplit) {
						rtoAutoRefunds.setPaymentMethod(orderPaymentSplit.getMethod());
					}
					rtoAutoRefunds.setOrderAmount(splitSalesOrder.getGrandTotal());
					rtoAutoRefunds.setRefundAmount(splitSalesOrder.getGrandTotal());
				} else {
					LOGGER.error("Both salesOrder and splitSalesOrder are null for incrementId: " + incrementId);
					return; // Cannot proceed without either order
				}
				rtoAutoRefunds.setStatus(status);
				rtoAutoRefunds.setRefundAt(new Timestamp(new Date().getTime()));
				rtoAutoRefundRepository.saveAndFlush(rtoAutoRefunds);
			}
			LOGGER.info("RTO refund order " + incrementId + "status updated successfully!. Status : " + status);
		} catch (Exception e) {
			// Handle race condition - another thread may have already created the record
			LOGGER.warn("Concurrent creation detected for incrementId: " + incrementId + ". Retrying update...", e);
			
			// Retry logic: try to find and update the record that was created by another thread
			try {
				RtoAutoRefund existingRecord = rtoAutoRefundRepository.findByIncrementId(incrementId);
				if (existingRecord != null) {
					existingRecord.setStatus(status);
					existingRecord.setRefundAt(new Timestamp(new Date().getTime()));
					rtoAutoRefundRepository.saveAndFlush(existingRecord);
					LOGGER.info("RTO refund order " + incrementId + " updated successfully after retry!. Status : " + status);
				} else {
					LOGGER.error("Failed to create or update RTO refund record for incrementId: " + incrementId, e);
					throw e;
				}
			} catch (Exception retryException) {
				LOGGER.error("Failed to retry update for RTO refund order " + incrementId, retryException);
				throw retryException;
			}
		}
	}

	@Override
	public RefundPaymentRespone updateStatusToInitiateRefund(List<String> incrementIds) {
		List<String>[] incrementIdsList = getParentAndSplitIncrementIds(incrementIds);
		incrementIds = incrementIdsList[0];
		List<String> splitIncrementIds = incrementIdsList[1];
		
		List<SalesOrder> salesOrder = salesOrderRepository.findByIncrementIdIn(incrementIds);
		List<SplitSalesOrder> splitSalesOrders = splitSalesOrderRepository.findByIncrementIdIn(splitIncrementIds);

		for (SalesOrder salesOrder2 : salesOrder) {
			updateRtoAutoRefund(salesOrder2, null, PaymentConstants.REFUND_STATUS_INITIATED);
		}
		for (SplitSalesOrder splitSalesOrder : splitSalesOrders) {
			updateRtoAutoRefund(null, splitSalesOrder, PaymentConstants.REFUND_STATUS_INITIATED);
		}

		RefundPaymentRespone res = new RefundPaymentRespone();
		res.setStatus(true);
		res.setStatusCode("200");
		res.setStatusMsg("Refunded Initiated!");
		return res;

	}

}
