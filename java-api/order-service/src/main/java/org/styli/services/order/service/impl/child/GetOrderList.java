package org.styli.services.order.service.impl.child;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.request.Order.OmsOrderListRequest;
import org.styli.services.order.pojo.request.Order.OrderListRequest;
import org.styli.services.order.pojo.request.Order.OrderListRequestV2;
import org.styli.services.order.pojo.request.Order.SourceType;
import org.styli.services.order.pojo.response.V3.CustomerOrderListResponse;
import org.styli.services.order.pojo.response.V3.CustomerOrdersResponseV2DTO;
import org.styli.services.order.pojo.response.Order.CustomerOrdersResponseDTO;
import org.styli.services.order.pojo.response.Order.OrderResponse;
import org.styli.services.order.pojo.response.V3.OrderResponseV3;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.utility.Constants;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class GetOrderList {

	private static final Log LOGGER = LogFactory.getLog(GetOrderList.class);
	@PersistenceContext
	EntityManager manager;

	@Autowired
	OrderHelper orderHelper;

	@Autowired
	AmastyRmaRequestRepository amastyRmaRequestRepository;


	public CustomerOrdersResponseDTO get(OrderListRequest request, StaticComponents staticComponents,
										 SalesOrderRepository salesOrderRepository,
										 OrderEntityConverter orderEntityConverter,
										 CustomerEntityRepository customerEntityRepository,
										 ConfigService configService, MulinHelper mulinHelper,
										 RestTemplate restTemplate) {
		CustomerOrdersResponseDTO resp = new CustomerOrdersResponseDTO();

		CustomerEntity customerEntity = orderHelper.getCustomerDetails(request.getCustomerId(), null);

		if (null != customerEntity && null == customerEntity.getEntityId()) {

			resp.setStatus(false);
			resp.setStatusCode("203");
			resp.setStatusMsg("Invalid customer ID!");
			return resp;
		}

		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
				.findAny().orElse(null);

		if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			resp.setStatus(false);
			resp.setStatusCode("202");
			resp.setStatusMsg("Store not found!");
			return resp;
		}

		List<Integer> storeIds = configService.getWebsiteStoresByStoreId(request.getStoreId());

		if (request.getOffSet() < 0)
			request.setOffSet(0);

		Pageable pageableSizeSortedByCreatedAtDesc = PageRequest.of(request.getOffSet(), request.getPageSize(),
				Sort.by("created_at").descending());
		List<SalesOrder> customerOrders = salesOrderRepository.findByCustomerIdAndStoreIdIn(request.getCustomerId(),
				storeIds, pageableSizeSortedByCreatedAtDesc);

		Map<String, ProductResponseBody> productsFromMulin = mulinHelper
				.getMulinProductsFromOrder(customerOrders, restTemplate);

		ObjectMapper mapper = new ObjectMapper();
		List<OrderResponse> orders = new ArrayList<>();
		for (SalesOrder order : customerOrders) {
			OrderResponse orderResponseBody = orderEntityConverter.convertOrder(order, true, mapper,
					request.getStoreId(), productsFromMulin, "", false);
			orders.add(orderResponseBody);
		}

		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg("Customer orders fetched successfully!");
		resp.setResponse(orders);

		return resp;

	}

	public CustomerOrdersResponseV2DTO getV3(OrderListRequest request, StaticComponents staticComponents,
												 SalesOrderRepository salesOrderRepository,
												 OrderEntityConverter orderEntityConverter,
												 CustomerEntityRepository customerEntityRepository,
												 ConfigService configService, MulinHelper mulinHelper,
												 RestTemplate restTemplate) {
		CustomerOrdersResponseV2DTO resp = new CustomerOrdersResponseV2DTO();

		CustomerEntity customerEntity = orderHelper.getCustomerDetails(request.getCustomerId(), null);

		if (null != customerEntity && null == customerEntity.getEntityId()) {

			resp.setStatus(false);
			resp.setStatusCode("203");
			resp.setStatusMsg("Invalid customer ID!");
			return resp;
		}

		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
				.findAny().orElse(null);

		if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			resp.setStatus(false);
			resp.setStatusCode("202");
			resp.setStatusMsg("Store not found!");
			return resp;
		}

		List<Integer> storeIds = configService.getWebsiteStoresByStoreId(request.getStoreId());
		int pageNumber = request.getOffSet() < 0 ? 0 : request.getOffSet();
		int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 5;
		Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("created_at").descending());
		// Fetch paginated data
		Page<SalesOrder> customerOrdersPage = salesOrderRepository.findByCustomerIdAndStoreIdInWithPagination(
				request.getCustomerId(), storeIds, pageable);
		List<SalesOrder> customerOrderList = customerOrdersPage.getContent(); // Extract paginated results

		Map<String, ProductResponseBody> productsFromMulin = mulinHelper
				.getMulinProductsFromOrder(customerOrderList, restTemplate);

		ObjectMapper mapper = new ObjectMapper();
		CustomerOrderListResponse customerOrderListResponse = new CustomerOrderListResponse();
		List<OrderResponseV3> orderResponseV3List = new ArrayList<>();
		for (SalesOrder order : customerOrderList) {
			OrderResponse orderResponse = orderEntityConverter.mapOrderInfoForOrderList(order, true,
					request.getStoreId(), productsFromMulin, false);
			OrderResponseV3 orderResponseBody = mapper.convertValue(orderResponse, OrderResponseV3.class);
			orderResponseBody.setGlobalShippingAmount(null!=order.getGlobalShippingAmount()?order.getGlobalShippingAmount().toString():"0");
			orderResponseBody = orderEntityConverter.convertOrderV3(orderResponse,orderResponseBody,order, true, mapper,
						request.getStoreId(), productsFromMulin, "", false);
				orderResponseBody.setSplitOrder(true);
			orderResponseV3List.add(orderResponseBody);
		}
		customerOrderListResponse.setOrders(orderResponseV3List);
		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg("Customer orders fetched successfully!");
		resp.setResponse(customerOrderListResponse);
		resp.setTotalCount(customerOrdersPage.getTotalElements());
		resp.setTotalPageSize(customerOrdersPage.getTotalPages());

		return resp;

	}



	public CustomerOrdersResponseDTO getOmsOrderList(OmsOrderListRequest request, StaticComponents staticComponents,
													 SalesOrderGridRepository salesOrderGridRepository, OrderEntityConverter orderEntityConverter,
													 CustomerEntityRepository customerEntityRepository, ConfigService configService) {
		CustomerOrdersResponseDTO resp = new CustomerOrdersResponseDTO();

		String source = "";
		if (request.getOffset() < 0) {
			request.setOffset(0);
		}if(request.getPageSize() < 0) {

			request.setPageSize(20);
		}

		PageRequest pageableSizeSortedByCreatedAtDesc = PageRequest.of(request.getOffset(), request.getPageSize(),
				Sort.by("created_at").descending());

		if (null != request.getFilters().getSource() && request.getFilters().getSource().equals(SourceType.APP.value)) {
			source = "1";
		} else if (null != request.getFilters().getSource()
				&& request.getFilters().getSource().equals(SourceType.MSITE.value)) {
			source = "2";
		} else if (null != request.getFilters().getSource()
				&& request.getFilters().getSource().equals(SourceType.WEB.value)) {
			source = "0";
		}
		Page<SalesOrderGrid> customerOrders = null;
		List<SalesOrderGrid> salesOrderGridList = null;
		long count =0;
		long totalPages=0;

		List<Integer> storeIds = new ArrayList<>();
		List<Integer> sourcelst = new ArrayList<>();
		List<String> status = new ArrayList<>();
		List<String> appVersion = new ArrayList<>();
		String customerId = null;

		if ((request.getFilters().getStoreId() != null)) {
			storeIds = request.getFilters().getStoreId();
		}
		if ((request.getFilters().getSource() != null)) {
			sourcelst = request.getFilters().getSource();
		}
		if ((request.getFilters().getStatus() != null)) {
			status = request.getFilters().getStatus();
		}
		if ((request.getFilters().getAppVersion() != null)) {
			appVersion = request.getFilters().getAppVersion();
		}

		// Create a safe list for isSplitOrder that handles null values properly
		final List<Integer> isSplitOrder = createSplitOrderFilterList(request.getFilters().getIsSplitOrder());
		final boolean includeNullSplitOrder = shouldIncludeNullSplitOrder(request.getFilters().getIsSplitOrder());

		List<String> emailList = new ArrayList<>();
		if ((null != request.getFilters().getCustomerEmail()) && !(request.getFilters().getCustomerEmail().isEmpty())) {
			emailList = Stream.of(request.getFilters().getCustomerEmail().split(",", -1)).map(String::trim)
					.collect(Collectors.toList());

		}

		if ((null != request.getFilters().getCustomerId()) && !(request.getFilters().getCustomerId().isEmpty())) {
			customerId = request.getFilters().getCustomerId();

		}

		List<String> incrementIdList = new ArrayList<>();
		if ((null != request.getFilters().getCustomerEmail()) && !(request.getFilters().getIncrementId().isEmpty())) {
			incrementIdList = Stream.of(request.getFilters().getIncrementId().split(",", -1)).map(String::trim)
					.collect(Collectors.toList());

		}

		if (StringUtils.isNoneBlank(request.getQuery())) {

			List<String> queryList = new ArrayList<>();
			String customerName = "";
			queryList = Stream.of(request.getQuery().split(",", -1)).map(String::trim)
					.collect(Collectors.toList());

			customerOrders = salesOrderGridRepository.findOmsOrderQueryDetails(
					queryList,customerName,request.getFilters().getPaymentMethod(),
					isSplitOrder, includeNullSplitOrder,
					pageableSizeSortedByCreatedAtDesc );


			if( null != customerOrders
					&& CollectionUtils.isEmpty(customerOrders.getContent())) {
				customerName = request.getQuery();

				if (customerName.matches("[a-zA-Z]+") || "".equals(customerName)) {

					customerOrders = salesOrderGridRepository.findOmsOrderDetails(customerName,
							request.getFilters().getPaymentMethod(), isSplitOrder, includeNullSplitOrder, pageableSizeSortedByCreatedAtDesc);
				} else {
					// For Arabic
					PageRequest pageableSizeSortCreatedAtDesc = PageRequest.of(request.getOffset(),
							request.getPageSize(), Sort.by("created_at").descending());
					customerOrders = salesOrderGridRepository.findOmsOrderQueryDetailsForArabic(customerName, isSplitOrder, includeNullSplitOrder,
							pageableSizeSortCreatedAtDesc);
				}
			}

		}else if( StringUtils.isNotBlank(request.getFilters().getFromDate())
				&& StringUtils.isNotBlank(request.getFilters().getToDate())) {
			if ((storeIds.isEmpty()) && (status.isEmpty()) && (sourcelst.isEmpty()) && (appVersion.isEmpty())
					&& (emailList.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDates(
						request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			} else if (!(storeIds.isEmpty()) && !(sourcelst.isEmpty()) && !(status.isEmpty()) && !(appVersion.isEmpty())
					&& (emailList.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForAll(storeIds, sourcelst, status,
						appVersion, request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			} else if (!(status.isEmpty()) && (storeIds.isEmpty()) && (sourcelst.isEmpty())
					&& (appVersion.isEmpty() && (emailList.isEmpty()) && (incrementIdList.isEmpty()))) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForStatus(status,
						request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			} else if (!(storeIds.isEmpty()) && (sourcelst.isEmpty()) && (status.isEmpty()) && (appVersion.isEmpty())
					&& (emailList.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForStoreId(storeIds,
						request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			} else if (!(emailList.isEmpty()) && (storeIds.isEmpty()) && (sourcelst.isEmpty()) && (status.isEmpty())
					&& (appVersion.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForMailId(emailList,
						request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			}else if(!(sourcelst.isEmpty()) && (storeIds.isEmpty()) && (status.isEmpty()) && (appVersion.isEmpty())
					&& (emailList.isEmpty()) && (incrementIdList.isEmpty())){
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForSource(sourcelst,
						request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			}else if((storeIds.isEmpty()) && (status.isEmpty()) && (sourcelst.isEmpty()) && (appVersion.isEmpty())
					&& (emailList.isEmpty()) && !(incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForIncrementId(incrementIdList,
						request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			}else if(!(storeIds.isEmpty()) && !(status.isEmpty()) && !(sourcelst.isEmpty()) && (appVersion.isEmpty())
					&& !(emailList.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForStoreStatusSourceAndMail(storeIds,
						status, sourcelst, emailList, request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			}else if(!(storeIds.isEmpty()) && (status.isEmpty()) && (sourcelst.isEmpty()) && (appVersion.isEmpty())
					&& !(emailList.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForStoreAndMail(storeIds,
						emailList, request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			}else if(!(sourcelst.isEmpty()) && !(storeIds.isEmpty()) && !(status.isEmpty()) && (appVersion.isEmpty())
					&& (emailList.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForSourceStoreIdAndStatus(sourcelst,storeIds,
						status,request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			} else if (!(status.isEmpty()) && !(emailList.isEmpty()) && (sourcelst.isEmpty()) && (storeIds.isEmpty())
					&& (appVersion.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForStatusAndMail(status, emailList,
						request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			} else if (!(status.isEmpty()) && !(sourcelst.isEmpty()) && !(emailList.isEmpty()) && (storeIds.isEmpty())
					&& (appVersion.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForStatusSourceAndMail(status,
						sourcelst, emailList, request.getFilters().getCustomerName(),
						request.getFilters().getFromDate(), request.getFilters().getToDate(),
						request.getFilters().getPaymentMethod(), isSplitOrder, includeNullSplitOrder, pageableSizeSortedByCreatedAtDesc);
			} else if (!(status.isEmpty()) && !(sourcelst.isEmpty()) && (emailList.isEmpty()) && (storeIds.isEmpty())
					&& (appVersion.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForStatusAndSource(status,sourcelst,
						request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			}else if(!(storeIds.isEmpty()) && !(sourcelst.isEmpty()) && (emailList.isEmpty()) && (status.isEmpty())
					&& (appVersion.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForStoreAndSource(storeIds,sourcelst,
						request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			}else if(!(storeIds.isEmpty()) && !(status.isEmpty()) && (sourcelst.isEmpty()) && (emailList.isEmpty()) &&
					(appVersion.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForStoreAndStatus(storeIds,status,
						request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			}else if((storeIds.isEmpty()) && (status.isEmpty()) && !(sourcelst.isEmpty()) && (appVersion.isEmpty())
					&& !(emailList.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForSourceAndMail(sourcelst, emailList,
						request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			}else if(!(storeIds.isEmpty()) && !(status.isEmpty()) && (sourcelst.isEmpty()) && (appVersion.isEmpty())
					&& !(emailList.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForStoreStatusAndMail(storeIds, status,
						emailList, request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			}else if(!(storeIds.isEmpty()) && (status.isEmpty()) && !(sourcelst.isEmpty()) && (appVersion.isEmpty())
					&& !(emailList.isEmpty()) && (incrementIdList.isEmpty())) {
				customerOrders = salesOrderGridRepository.findOmsOrderDetailsByDatesForStoreSourceAndMail(storeIds, sourcelst,
						emailList, request.getFilters().getCustomerName(), request.getFilters().getFromDate(),
						request.getFilters().getToDate(), request.getFilters().getPaymentMethod(),
						isSplitOrder, includeNullSplitOrder,
						pageableSizeSortedByCreatedAtDesc);
			}
		} else {
			if (null != request.getFilters() && null != request.getFilters().getCustomerName()) {

				if (request.getFilters().getCustomerName().matches("[a-zA-Z]+")
						|| "".equals(request.getFilters().getCustomerName())) {

					if ((storeIds.isEmpty()) && (status.isEmpty()) && (sourcelst.isEmpty()) && (appVersion.isEmpty())
							&& (emailList.isEmpty()) && (incrementIdList.isEmpty())) {

						customerOrders = salesOrderGridRepository.findOmsOrderDetails(
								request.getFilters().getCustomerName(), request.getFilters().getPaymentMethod(),
								isSplitOrder, includeNullSplitOrder,
								pageableSizeSortedByCreatedAtDesc);

					} else if (!(storeIds.isEmpty()) && !(sourcelst.isEmpty()) && !(status.isEmpty())
							&& !(appVersion.isEmpty()) && (emailList.isEmpty()) && (incrementIdList.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForAll(storeIds, sourcelst, status,
								appVersion, request.getFilters().getCustomerName(),
								request.getFilters().getPaymentMethod(), isSplitOrder, includeNullSplitOrder, pageableSizeSortedByCreatedAtDesc);
					} else if (!(storeIds.isEmpty()) && (sourcelst.isEmpty()) && (status.isEmpty())
							&& (appVersion.isEmpty()) && (emailList.isEmpty()) && (incrementIdList.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForStoreId(storeIds,
								request.getFilters().getCustomerName(), request.getFilters().getPaymentMethod(),
								isSplitOrder, includeNullSplitOrder,
								pageableSizeSortedByCreatedAtDesc);
					} else if (!(sourcelst.isEmpty()) && (storeIds.isEmpty()) && (status.isEmpty())
							&& (appVersion.isEmpty()) && (emailList.isEmpty()) && (incrementIdList.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForSource(sourcelst,
								request.getFilters().getCustomerName(), request.getFilters().getPaymentMethod(),
								isSplitOrder, includeNullSplitOrder,
								pageableSizeSortedByCreatedAtDesc);
					} else if (!(status.isEmpty()) && (storeIds.isEmpty()) && (sourcelst.isEmpty())
							&& (appVersion.isEmpty()) && (emailList.isEmpty()) && (incrementIdList.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForStatus(status,
								request.getFilters().getCustomerName(), request.getFilters().getPaymentMethod(),
								isSplitOrder, includeNullSplitOrder,
								pageableSizeSortedByCreatedAtDesc);
					} else if (!(appVersion.isEmpty()) && (storeIds.isEmpty()) && (sourcelst.isEmpty())
							&& (status.isEmpty()) && (emailList.isEmpty()) && (incrementIdList.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForAppVersion(appVersion,
								request.getFilters().getCustomerName(), request.getFilters().getPaymentMethod(),
								isSplitOrder, includeNullSplitOrder,
								pageableSizeSortedByCreatedAtDesc);
					} else if (!(emailList.isEmpty()) && (appVersion.isEmpty()) && (storeIds.isEmpty())
							&& (sourcelst.isEmpty()) && (status.isEmpty()) && (incrementIdList.isEmpty())) {
						if(null != customerId) {
													customerOrders = salesOrderGridRepository.findOmsOrderDetailsForMailListCustomerId(emailList,
								customerId, request.getFilters().getCustomerName(), request.getFilters().getPaymentMethod(),
								isSplitOrder, includeNullSplitOrder,
								pageableSizeSortedByCreatedAtDesc);
						}else {
													customerOrders = salesOrderGridRepository.findOmsOrderDetailsForMailList(emailList,
								request.getFilters().getCustomerName(), request.getFilters().getPaymentMethod(),
								isSplitOrder, includeNullSplitOrder,
								pageableSizeSortedByCreatedAtDesc);
						}

					} else if (!(incrementIdList.isEmpty()) && (emailList.isEmpty()) && (appVersion.isEmpty())
							&& (storeIds.isEmpty())	&& (sourcelst.isEmpty()) && (status.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForIncrementIdList(incrementIdList,
								request.getFilters().getCustomerName(), request.getFilters().getPaymentMethod(),
								isSplitOrder, includeNullSplitOrder,
								pageableSizeSortedByCreatedAtDesc);
					} else if (!(storeIds.isEmpty()) && !(status.isEmpty()) && !(sourcelst.isEmpty()) && (incrementIdList.isEmpty())
							&& (emailList.isEmpty()) && (appVersion.isEmpty())){
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForStoreIdAndStatusAndSource(
								storeIds, status, sourcelst, request.getFilters().getCustomerName(),
								request.getFilters().getPaymentMethod(), isSplitOrder, includeNullSplitOrder, pageableSizeSortedByCreatedAtDesc);
					}else if(!(storeIds.isEmpty()) && !(status.isEmpty()) && !(sourcelst.isEmpty()) &&
							!(emailList.isEmpty()) && (appVersion.isEmpty()) && (incrementIdList.isEmpty()) ){
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForStoreIdAndStatusAndSourceAndMail(
								storeIds, status, sourcelst, emailList, request.getFilters().getCustomerName(),
								request.getFilters().getPaymentMethod(), isSplitOrder, includeNullSplitOrder, pageableSizeSortedByCreatedAtDesc);
					} else if (!(storeIds.isEmpty()) && !(status.isEmpty()) && (sourcelst.isEmpty()) && (incrementIdList.isEmpty())
							&& (emailList.isEmpty()) && (appVersion.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForStoreIdAndStatus(storeIds,
								status, request.getFilters().getCustomerName(), request.getFilters().getPaymentMethod(),
								isSplitOrder, includeNullSplitOrder,
								pageableSizeSortedByCreatedAtDesc);
					} else if (!(status.isEmpty()) && !(sourcelst.isEmpty()) && (storeIds.isEmpty()) && (incrementIdList.isEmpty())
							&& (emailList.isEmpty()) && (appVersion.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForStatusAndSource(status,
								sourcelst, request.getFilters().getCustomerName(),
								request.getFilters().getPaymentMethod(), isSplitOrder, includeNullSplitOrder, pageableSizeSortedByCreatedAtDesc);
					} else if (!(storeIds.isEmpty()) && !(sourcelst.isEmpty()) && (status.isEmpty()) && (incrementIdList.isEmpty())
							&& (emailList.isEmpty()) && (appVersion.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForStoreAndSource(storeIds,
								sourcelst, request.getFilters().getCustomerName(),
								request.getFilters().getPaymentMethod(), isSplitOrder, includeNullSplitOrder, pageableSizeSortedByCreatedAtDesc);
					}else if(!(storeIds.isEmpty()) && (sourcelst.isEmpty()) && (status.isEmpty()) && (incrementIdList.isEmpty())
							&& !(emailList.isEmpty()) && (appVersion.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForStoreIdAndMail(storeIds,
								emailList, request.getFilters().getCustomerName(),
								request.getFilters().getPaymentMethod(), isSplitOrder, includeNullSplitOrder, pageableSizeSortedByCreatedAtDesc);
					}else if((storeIds.isEmpty()) && !(sourcelst.isEmpty()) && (status.isEmpty()) && (incrementIdList.isEmpty())
							&& !(emailList.isEmpty()) && (appVersion.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForSourceAndMail(sourcelst,
								emailList, request.getFilters().getCustomerName(),
								request.getFilters().getPaymentMethod(), isSplitOrder, includeNullSplitOrder, pageableSizeSortedByCreatedAtDesc);
					}else if((storeIds.isEmpty()) && (sourcelst.isEmpty()) && !(status.isEmpty()) && (incrementIdList.isEmpty())
							&& !(emailList.isEmpty()) && (appVersion.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForStatusAndMail(status,
								emailList, request.getFilters().getCustomerName(),
								request.getFilters().getPaymentMethod(), isSplitOrder, includeNullSplitOrder, pageableSizeSortedByCreatedAtDesc);
					}else if(!(storeIds.isEmpty()) && (sourcelst.isEmpty()) && !(status.isEmpty()) && (incrementIdList.isEmpty())
							&& !(emailList.isEmpty()) && (appVersion.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForStoreIdAndStatusAndMail(storeIds,status,
								emailList, request.getFilters().getCustomerName(),
								request.getFilters().getPaymentMethod(), isSplitOrder, includeNullSplitOrder, pageableSizeSortedByCreatedAtDesc);
					}else if(!(storeIds.isEmpty()) && !(sourcelst.isEmpty()) && (status.isEmpty()) && (incrementIdList.isEmpty())
							&& !(emailList.isEmpty()) && (appVersion.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForStoreIdAndSourceAndMail(storeIds,sourcelst,
								emailList, request.getFilters().getCustomerName(),
								request.getFilters().getPaymentMethod(), isSplitOrder, includeNullSplitOrder, pageableSizeSortedByCreatedAtDesc);
					}else if((storeIds.isEmpty()) && !(sourcelst.isEmpty()) && !(status.isEmpty()) && (incrementIdList.isEmpty())
							&& !(emailList.isEmpty()) && (appVersion.isEmpty())) {
						customerOrders = salesOrderGridRepository.findOmsOrderDetailsForStatusAndSourceAndMail(status,sourcelst,
								emailList, request.getFilters().getCustomerName(),
								request.getFilters().getPaymentMethod(), isSplitOrder, includeNullSplitOrder, pageableSizeSortedByCreatedAtDesc);
					}

				}else {
					// For Arabic
					PageRequest pageableSizeSortCreatedAtDesc = PageRequest.of(request.getOffset(),
							request.getPageSize(), Sort.by("createdAt").descending());
					customerOrders = salesOrderGridRepository.findOmsOrderQueryDetailsForArabic(
							request.getFilters().getCustomerName(), isSplitOrder, includeNullSplitOrder, pageableSizeSortCreatedAtDesc);
				}
			}


		}


		if( null != customerOrders
				&& CollectionUtils.isNotEmpty(customerOrders.getContent())) {

			ObjectMapper mapper = new ObjectMapper();
			List<OrderResponse> orders = new ArrayList<>();
			for (SalesOrderGrid order : customerOrders.getContent()) {
				OrderResponse orderResponseBody = orderEntityConverter.convertOmsOrderObject(order, false, mapper,
						order.getStoreId(), new HashMap<String, String>(), new HashMap<>());
				orderResponseBody.setArchived(request.isUseArchive() ? 1 : 0);
				orders.add(orderResponseBody);
			}

			resp.setTotalCount(customerOrders.getTotalElements());
			resp.setTotalPageSize(customerOrders.getTotalPages());

			resp.setStatus(true);
			resp.setStatusCode("200");
			resp.setStatusMsg("Customer orders fetched successfully!");
			resp.setResponse(orders);
		} else if(CollectionUtils.isNotEmpty(salesOrderGridList)){
			ObjectMapper mapper = new ObjectMapper();
			List<OrderResponse> orders = new ArrayList<>();
			for (SalesOrderGrid order : salesOrderGridList) {
				OrderResponse orderResponseBody = orderEntityConverter.convertOmsOrderObject(order, false, mapper,
						order.getStoreId(), new HashMap<String, String>(), new HashMap<>());
				orderResponseBody.setArchived(request.isUseArchive() ? 1 : 0);
				orders.add(orderResponseBody);
			}
			resp.setTotalCount(count);
			resp.setTotalPageSize((int)totalPages);

			resp.setStatus(true);
			resp.setStatusCode("200");
			resp.setStatusMsg("Customer orders fetched successfully!");
			resp.setResponse(orders);
		}else{
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("No Result Found!");
		}
		return resp;

	}


	public CustomerOrdersResponseDTO getOrdersV2(OrderListRequestV2 request,
												 SalesOrderRepository salesOrderRepository,
												 OrderEntityConverter orderEntityConverter,
												 ConfigService configService, MulinHelper mulinHelper,
												 RestTemplate restTemplate) {
		CustomerOrdersResponseDTO responseDTO = new CustomerOrdersResponseDTO();
		CustomerEntity customer= orderHelper.getCustomerDetails(request.getCustomerId(), null);
		if (null != customer && null == customer.getEntityId()) {
			responseDTO.setStatus(false);
			responseDTO.setStatusCode("203");
			responseDTO.setStatusMsg("Invalid customer ID!");
			return responseDTO;
		}
		List<Stores> storeList = Constants.getStoresList();
		Stores storeInfo = storeList.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
				.findAny().orElse(null);
		if (storeInfo == null || storeInfo.getStoreCode() == null || storeInfo.getStoreCurrency() == null) {
			responseDTO.setStatus(false);
			responseDTO.setStatusCode("202");
			responseDTO.setStatusMsg("Store not found!");
			return responseDTO;
		}
		List<Integer> storeIdList = configService.getWebsiteStoresByStoreId(request.getStoreId());
		int pageNumber = request.getOffSet() < 0 ? 0 : request.getOffSet();
		int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 5;
		Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("created_at").descending());
		// Fetch paginated data
		Page<SalesOrder> customerOrdersPage = salesOrderRepository.findByCustomerIdAndStoreIdInWithPagination(
				request.getCustomerId(), storeIdList, pageable);
		List<SalesOrder> customerOrderList = customerOrdersPage.getContent(); // Extract paginated results
		Map<String, ProductResponseBody> productsFromMulin = mulinHelper
				.getMulinProductsFromOrder(customerOrderList, restTemplate);
		List<OrderResponse> orders = new ArrayList<>();
		for (SalesOrder order : customerOrderList) {
			OrderResponse orderResponse = orderEntityConverter.mapOrderInfoForOrderList(order, true,
					request.getStoreId(), productsFromMulin, false);
			orders.add(orderResponse);
		}
		responseDTO.setStatus(true);
		responseDTO.setStatusCode("200");
		responseDTO.setStatusMsg("Customer orders fetched successfully!");
		responseDTO.setResponse(orders);
		responseDTO.setTotalCount(customerOrdersPage.getTotalElements());
		responseDTO.setTotalPageSize(customerOrdersPage.getTotalPages());

		return responseDTO;
	}

	/**
	 * Creates a safe list for isSplitOrder filtering that excludes null values
	 * @param isSplitOrderFilter the filter value from request
	 * @return List of Integer values for IN clause (never contains null)
	 */
	private List<Integer> createSplitOrderFilterList(Boolean isSplitOrderFilter) {
		if (isSplitOrderFilter == null) {
			// When no filter specified, include both split and non-split orders
			return Arrays.asList(0, 1);
		} else if (isSplitOrderFilter) {
			// When filtering for split orders only
			return List.of(1);
		} else {
			// When filtering for non-split orders only
			return List.of(0);
		}
	}

	/**
	 * Determines whether to include null values in the split order filter
	 * @param isSplitOrderFilter the filter value from request
	 * @return true if null values should be included in results
	 */
	private boolean shouldIncludeNullSplitOrder(Boolean isSplitOrderFilter) {
		if (isSplitOrderFilter == null) {
			// When no filter specified, include unknown (null) values
			return true;
		} else if (isSplitOrderFilter) {
			// When filtering for split orders only, exclude null values
			return false;
		} else {
			// When filtering for non-split orders, include null values (treat as non-split)
			return true;
		}
	}
}