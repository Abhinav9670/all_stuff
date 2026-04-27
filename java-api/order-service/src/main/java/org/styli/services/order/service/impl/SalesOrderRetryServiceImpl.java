package org.styli.services.order.service.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.ExternalQuoteHelper;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrderItem;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.quote.response.GetQuoteResponse;
import org.styli.services.order.pojo.quote.response.QuoteUpdateDTOV2;
import org.styli.services.order.pojo.request.AddToQuoteProductsRequest;
import org.styli.services.order.pojo.request.AddToQuoteV4Request;
import org.styli.services.order.pojo.request.Order.ReOrderRequest;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderItemRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.service.SalesOrderRetryService;
import org.styli.services.order.service.SalesOrderServiceV2;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.GenericConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import java.util.stream.Collectors;

/**
 * @author Kushal, 22/09/2020
 * @project order-service
 */

@Component
public class SalesOrderRetryServiceImpl implements SalesOrderRetryService {

	private static final Log LOGGER = LogFactory.getLog(SalesOrderRetryServiceImpl.class);

	private static final String SHIPMENT_TYPE_LOCAL = "Local";
	private static final String SHIPMENT_TYPE_GLOBAL = "Global";
	private static final String PRODUCT_TYPE_SIMPLE = "simple";
	private static final String LOG_PREFIX_REORDER = "ReOrderV2 - SplitSalesOrder ";

  @Autowired
  StaticComponents staticComponents;

  @Autowired
  SalesOrderRepository salesOrderRepository;

  @Autowired
  ExternalQuoteHelper externalQuoteHelper;

  @Autowired
  SalesOrderServiceV2 salesOrderServiceV2;

  @Autowired
  MulinHelper mulinHelper;

  @Autowired
  SplitSalesOrderRepository splitSalesOrderRepository;

  @Autowired
  SplitSalesOrderItemRepository splitSalesOrderItemRepository;


  @Override
  @Transactional
  public OrderResponseDTO reOrderV2(Map<String, String> requestHeader, ReOrderRequest request, String mode,
                                    String tokenHeader, String xHeaderToken, String xSource,
			String xClientVersion, RestTemplate restTemplate) throws NotFoundException {

		OrderResponseDTO resp = new OrderResponseDTO();

		if (request.getOrderId() == null || request.getCustomerId() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Parameters missing!");
			return resp;
		}

		SalesOrder order = salesOrderRepository.findByEntityIdAndCustomerId(request.getOrderId(),
				request.getCustomerId());
		if (order == null) {
			resp.setStatus(false);
			resp.setStatusCode("202");
			resp.setStatusMsg("Order not found!");
			return resp;
		}

		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
				.orElse(null);

		if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Store not found!");
			return resp;
		}

		if (order.getCustomerId() == null) {
			resp.setStatus(false);
			resp.setStatusCode("203");
			resp.setStatusMsg("Customer Id not found in order!");
			return resp;
		}

		if (mode.equalsIgnoreCase(GenericConstants.REORDER_MODE_IS_RETRY_PAYMEWNT)) {

			GetQuoteResponse quoteResponse = externalQuoteHelper.fetchQuote(null, order.getCustomerId(),
					request.getStoreId(), tokenHeader, true, xHeaderToken, xSource, xClientVersion,false, requestHeader.get(Constants.deviceId));

			if (quoteResponse == null
					|| !Boolean.TRUE.equals(quoteResponse.getStatus())
					|| !"200".equals(quoteResponse.getStatusCode())) {

				resp.setStatus(false);
				resp.setStatusCode("202");
				resp.setStatusMsg("Error: Quote was not found for given parameters!");
				return resp;
			} else if (quoteResponse.getResponse() != null && "200".equals(quoteResponse.getStatusCode())
					&& StringUtils.isNotBlank(quoteResponse.getResponse().getCustomerId())) {
				salesOrderServiceV2.authenticateCheck(requestHeader,
						Integer.valueOf(quoteResponse.getResponse().getCustomerId()));
			}
		}
		// Skipping promo removal from quote as quote is not available here

		AddToQuoteV4Request addToQuoteV4Request = new AddToQuoteV4Request();
		List<AddToQuoteProductsRequest> productsRequests = new ArrayList<>();
		
		// Get the list of SalesOrderItem to process
		List<SalesOrderItem> itemsToProcess = getItemsToReorder(request.getSplitOrderId(), order);
		
		if (CollectionUtils.isNotEmpty(itemsToProcess)) {

			Map<String, ProductResponseBody> productsFromMulin = mulinHelper
					.getMulinProductsFromOrder(Collections.singletonList(order), restTemplate);

			itemsToProcess.stream()
					.forEach(el -> {
						for (Map.Entry<String, ProductResponseBody> entry : productsFromMulin.entrySet()) {
							ProductResponseBody productDetailsFromMulin = entry.getValue();
							productDetailsFromMulin.getVariants().stream().filter(e -> e.getSku().equals(el.getSku()))
									.findAny().ifPresent(e -> {
										AddToQuoteProductsRequest productsRequest = new AddToQuoteProductsRequest();
										productsRequest.setParentSku(productDetailsFromMulin.getSku());
										productsRequest.setSku(el.getSku());
										productsRequest.setProductId(el.getProductId());
										
										Integer parentProductId = determineParentProductId(el);
										if (parentProductId != null) {
											productsRequest.setParentProductId(parentProductId);
										}
										
										productsRequest.setQuantity(el.getQtyOrdered().intValue());
										productsRequest.setOverrideQuantity(true);
										productsRequests.add(productsRequest);
									});

						}
					});
		}

		if (CollectionUtils.isNotEmpty(productsRequests)) {

			addToQuoteV4Request.setAddToQuoteProductsRequests(productsRequests);
			addToQuoteV4Request.setCustomerId(order.getCustomerId());
			addToQuoteV4Request.setStoreId(order.getStoreId());
			addToQuoteV4Request.setSource(request.getSource());
			addToQuoteV4Request.setIpAddress(request.getIpAddress());

			QuoteUpdateDTOV2 response = externalQuoteHelper.addToQuote(addToQuoteV4Request, tokenHeader, xHeaderToken, requestHeader.get(Constants.deviceId));

			if (response == null || !response.getStatus() || !response.getStatusCode().equals("200")) {

				resp.setStatus(false);
				resp.setStatusCode("202");
				resp.setStatusMsg("Error: add to quote operation failed!");
				return resp;
			}

			resp.setStatus(true);
			resp.setStatusCode("200");
			resp.setStatusMsg("Success!");

		} else {
			resp.setStatus(false);
			resp.setStatusCode("205");
			resp.setStatusMsg("No items available in order!");
		}
		return resp;

	}

	/**
	 * Gets the list of SalesOrderItem to reorder based on splitOrderId.
	 * If splitOrderId is provided, returns only items from that specific SplitSalesOrder.
	 * Otherwise, returns all items from the order (with optional shipmentType filtering for split orders).
	 * 
	 * @param splitOrderId The split order ID from the request (optional)
	 * @param order The main sales order
	 * @return List of SalesOrderItem to reorder
	 */
	private List<SalesOrderItem> getItemsToReorder(String splitOrderId, SalesOrder order) {
		if (splitOrderId == null || StringUtils.isBlank(splitOrderId)) {
			return getItemsForOrderWithoutSplitId(order);
		}
		
		return getItemsForSplitOrder(splitOrderId, order);
	}
	
	/**
	 * Gets items to reorder when no splitOrderId is provided.
	 * For split orders, defaults to Local items; for normal orders, returns all simple items.
	 * 
	 * @param order The main sales order
	 * @return List of SalesOrderItem to reorder
	 */
	private List<SalesOrderItem> getItemsForOrderWithoutSplitId(SalesOrder order) {
		List<SalesOrderItem> simpleItems = order.getSalesOrderItem().stream()
				.filter(e -> PRODUCT_TYPE_SIMPLE.equalsIgnoreCase(e.getProductType()))
				.toList();
		
		if (!checkIfSplitOrder(order)) {
			return simpleItems;
		}
		
		return simpleItems;
	}
	
	/**
	 * Gets items to reorder for a specific split order.
	 * 
	 * @param splitOrderId The split order ID (as String)
	 * @param order The main sales order
	 * @return List of SalesOrderItem to reorder
	 */
	private List<SalesOrderItem> getItemsForSplitOrder(String splitOrderId, SalesOrder order) {
		try {
			Integer splitOrderIdInt = parseSplitOrderId(splitOrderId);
			if (splitOrderIdInt == null) {
				return new ArrayList<>();
			}
			
			SplitSalesOrder splitSalesOrder = splitSalesOrderRepository.findByEntityId(splitOrderIdInt);
			if (splitSalesOrder == null) {
				LOGGER.warn(LOG_PREFIX_REORDER + "not found for splitOrderId: " + splitOrderId);
				return new ArrayList<>();
			}
			
			if (!validateSplitOrderBelongsToMainOrder(splitSalesOrder, splitOrderId, order)) {
				return new ArrayList<>();
			}
			
			String expectedShipmentType = determineExpectedShipmentType(splitSalesOrder);
			logSplitOrderDetails(splitOrderId, splitSalesOrder, expectedShipmentType);
			
			List<SplitSalesOrderItem> splitOrderItems = splitSalesOrderItemRepository
					.findBySplitSalesOrderEntityId(splitOrderIdInt);
			
			if (CollectionUtils.isEmpty(splitOrderItems)) {
				LOGGER.warn(LOG_PREFIX_REORDER + "No items found for splitOrderId: " + splitOrderId);
				return new ArrayList<>();
			}
			
			List<SalesOrderItem> salesOrderItems = filterSplitOrderItems(splitOrderItems, expectedShipmentType);
			logFilteringResults(splitOrderId, expectedShipmentType, splitOrderItems.size(), salesOrderItems.size(), splitSalesOrder);
			
			return salesOrderItems;
			
		} catch (DataAccessException ex) {
			LOGGER.error("ReOrderV2 - Database error fetching items for splitOrderId: " + splitOrderId, ex);
			return new ArrayList<>();
		} catch (IllegalArgumentException ex) {
			LOGGER.error("ReOrderV2 - Invalid argument error for splitOrderId: " + splitOrderId, ex);
			return new ArrayList<>();
		}
	}
	
	/**
	 * Parses the splitOrderId String to Integer.
	 * 
	 * @param splitOrderId The split order ID as String
	 * @return Parsed Integer, or null if parsing fails
	 */
	private Integer parseSplitOrderId(String splitOrderId) {
		try {
			return Integer.parseInt(splitOrderId.trim());
		} catch (NumberFormatException ex) {
			LOGGER.warn("ReOrderV2 - Invalid splitOrderId format: " + splitOrderId);
			return null;
		}
	}
	
	/**
	 * Validates that the split order belongs to the main order.
	 * 
	 * @param splitSalesOrder The split sales order
	 * @param splitOrderId The split order ID (for logging)
	 * @param order The main sales order
	 * @return true if valid, false otherwise
	 */
	private boolean validateSplitOrderBelongsToMainOrder(SplitSalesOrder splitSalesOrder, String splitOrderId, SalesOrder order) {
		if (splitSalesOrder.getSalesOrder() == null || 
				!splitSalesOrder.getSalesOrder().getEntityId().equals(order.getEntityId())) {
			LOGGER.warn(LOG_PREFIX_REORDER + splitOrderId + " does not belong to order " + order.getEntityId());
			return false;
		}
		return true;
	}
	
	/**
	 * Filters split order items by shipment type and product type.
	 * 
	 * @param splitOrderItems The list of split order items
	 * @param expectedShipmentType The expected shipment type
	 * @return Filtered list of SalesOrderItem
	 */
	private List<SalesOrderItem> filterSplitOrderItems(List<SplitSalesOrderItem> splitOrderItems, 
			String expectedShipmentType) {
		return splitOrderItems.stream()
				.filter(splitItem -> matchesSplitItemShipmentType(splitItem, expectedShipmentType))
				.map(SplitSalesOrderItem::getSalesOrderItem)
				.filter(item -> item != null && PRODUCT_TYPE_SIMPLE.equalsIgnoreCase(item.getProductType()))
				.toList();
	}
	
	/**
	 * Checks if a split order item matches the expected shipment type.
	 * 
	 * @param splitItem The split order item
	 * @param expectedShipmentType The expected shipment type
	 * @return true if matches, false otherwise
	 */
	private boolean matchesSplitItemShipmentType(SplitSalesOrderItem splitItem, String expectedShipmentType) {
		boolean matches = matchesShipmentTypeFilterFromString(splitItem.getShipmentType(), expectedShipmentType);
		if (!matches) {
			LOGGER.warn("ReOrderV2 - SplitSalesOrderItem " + splitItem.getItemId() 
					+ " has shipmentType: " + splitItem.getShipmentType() 
					+ " but expected: " + expectedShipmentType);
		}
		return matches;
	}
	
	/**
	 * Logs split order details for debugging.
	 * 
	 * @param splitOrderId The split order ID
	 * @param splitSalesOrder The split sales order
	 * @param expectedShipmentType The expected shipment type
	 */
	private void logSplitOrderDetails(String splitOrderId, SplitSalesOrder splitSalesOrder, String expectedShipmentType) {
		LOGGER.info(LOG_PREFIX_REORDER + splitOrderId + " has shipmentMode: " + splitSalesOrder.getShipmentMode() 
				+ ", hasGlobalShipment: " + splitSalesOrder.getHasGlobalShipment() + ", expectedShipmentType: " + expectedShipmentType);
	}
	
	/**
	 * Logs filtering results and warnings.
	 * 
	 * @param splitOrderId The split order ID
	 * @param expectedShipmentType The expected shipment type
	 * @param totalItems Total number of split items
	 * @param filteredItems Number of filtered items
	 * @param splitSalesOrder The split sales order
	 */
	private void logFilteringResults(String splitOrderId, String expectedShipmentType, 
			int totalItems, int filteredItems, SplitSalesOrder splitSalesOrder) {
		LOGGER.info(LOG_PREFIX_REORDER + "Found " + filteredItems + " items for splitOrderId: " + splitOrderId 
				+ " (expected " + expectedShipmentType + ", filtered from " + totalItems + " split items)");
		
		if (totalItems > 0) {
			LOGGER.info(LOG_PREFIX_REORDER + splitOrderId + " details: "
					+ "hasGlobalShipment=" + splitSalesOrder.getHasGlobalShipment()
					+ ", shipmentMode=" + splitSalesOrder.getShipmentMode()
					+ ", expectedShipmentType=" + expectedShipmentType
					+ ", totalSplitItems=" + totalItems
					+ ", filteredItems=" + filteredItems);
		}
		
		if (expectedShipmentType != null && filteredItems < totalItems) {
			LOGGER.warn(LOG_PREFIX_REORDER + "Shipment type mismatch detected! splitOrderId: " + splitOrderId 
					+ " expected " + expectedShipmentType + " but some items have different shipmentType. "
					+ "Filtered " + totalItems + " items down to " + filteredItems);
		}
	}

	/**
	 * Determines the expected shipment type from a SplitSalesOrder.
	 * 
	 * @param splitSalesOrder The split sales order
	 * @return "Local" if local/express, "Global" if global, null if cannot determine
	 */
	private String determineExpectedShipmentType(SplitSalesOrder splitSalesOrder) {
		// Check hasGlobalShipment flag first (most reliable)
		Boolean hasGlobalShipment = splitSalesOrder.getHasGlobalShipment();
		if (hasGlobalShipment != null) {
			return determineFromGlobalShipmentFlag(splitSalesOrder, hasGlobalShipment);
		}
		
		// If hasGlobalShipment is null, check shipmentMode
		String shipmentMode = splitSalesOrder.getShipmentMode();
		if (StringUtils.isNotBlank(shipmentMode)) {
			String result = determineFromShipmentMode(splitSalesOrder, shipmentMode);
			if (result != null) {
				return result;
			}
		}
		
		// Default: if hasGlobalShipment is null and shipmentMode doesn't match, assume Local
		LOGGER.warn(LOG_PREFIX_REORDER + splitSalesOrder.getEntityId() 
				+ " could not determine shipment type (hasGlobalShipment=null, shipmentMode=" + shipmentMode 
				+ "), defaulting to Local");
		return SHIPMENT_TYPE_LOCAL;
	}
	
	/**
	 * Determines shipment type from hasGlobalShipment flag.
	 * 
	 * @param splitSalesOrder The split sales order
	 * @param hasGlobalShipment The global shipment flag
	 * @return "Local" or "Global" based on the flag
	 */
	private String determineFromGlobalShipmentFlag(SplitSalesOrder splitSalesOrder, Boolean hasGlobalShipment) {
		if (hasGlobalShipment) {
			LOGGER.info(LOG_PREFIX_REORDER + splitSalesOrder.getEntityId() + " determined as Global from hasGlobalShipment=true");
			return SHIPMENT_TYPE_GLOBAL;
		}
		LOGGER.info(LOG_PREFIX_REORDER + splitSalesOrder.getEntityId() + " determined as Local from hasGlobalShipment=false");
		return SHIPMENT_TYPE_LOCAL;
	}
	
	/**
	 * Determines shipment type from shipmentMode string.
	 * 
	 * @param splitSalesOrder The split sales order
	 * @param shipmentMode The shipment mode string
	 * @return "Local" or "Global" if determined, null otherwise
	 */
	private String determineFromShipmentMode(SplitSalesOrder splitSalesOrder, String shipmentMode) {
		String modeLower = shipmentMode.toLowerCase().trim();
		
		if (Constants.GLOBAL_SHIPMENT.equalsIgnoreCase(modeLower) || "global".equals(modeLower)) {
			LOGGER.info(LOG_PREFIX_REORDER + splitSalesOrder.getEntityId() + " determined as Global from shipmentMode=" + shipmentMode);
			return SHIPMENT_TYPE_GLOBAL;
		}
		
		if (Constants.LOCAL_SHIPMENT.equalsIgnoreCase(modeLower) || "local".equals(modeLower) 
				|| Constants.EXPRESS_SHIPMENT.equalsIgnoreCase(modeLower) || "express".equals(modeLower)) {
			LOGGER.info(LOG_PREFIX_REORDER + splitSalesOrder.getEntityId() + " determined as Local from shipmentMode=" + shipmentMode);
			return SHIPMENT_TYPE_LOCAL;
		}
		
		return null;
	}

	/**
	 * Checks if an order is a split order (has both local and global products).
	 * 
	 * @param order The sales order to check
	 * @return true if order has both local and global products, false otherwise
	 */
	private boolean checkIfSplitOrder(SalesOrder order) {
		// Check explicit flag first
		if (order.getIsSplitOrder() != null && order.getIsSplitOrder() == 1) {
			return true;
		}
		
		// Check if order has both local and global products by checking shipmentType
		Map<Boolean, Long> shipmentTypeCounts = order.getSalesOrderItem().stream()
				.map(SalesOrderItem::getShipmentType)
				.filter(st -> StringUtils.isNotBlank(st))
				.collect(Collectors.partitioningBy(
						SHIPMENT_TYPE_LOCAL::equalsIgnoreCase,
						Collectors.counting()
				));
		return shipmentTypeCounts.getOrDefault(true, 0L) > 0 
				&& shipmentTypeCounts.getOrDefault(false, 0L) > 0;
	}
	
	/**
	 * Determines if a sales order item matches the shipment type filter.
	 * 
	 * @param item The sales order item to check
	 * @param filterShipmentType The shipment type to filter by ("Local" or "Global")
	 * @return true if item matches the filter, false otherwise
	 */
	private boolean matchesShipmentTypeFilter(SalesOrderItem item, String filterShipmentType) {
		if (filterShipmentType == null) {
			return true; // No filter, include all
		}
		
		return matchesShipmentTypeFilterFromString(item.getShipmentType(), filterShipmentType);
	}
	
	/**
	 * Determines if a shipment type string matches the filter.
	 * 
	 * @param itemShipmentType The shipment type from the item (can be null)
	 * @param filterShipmentType The shipment type to filter by ("Local" or "Global")
	 * @return true if item matches the filter, false otherwise
	 */
	private boolean matchesShipmentTypeFilterFromString(String itemShipmentType, String filterShipmentType) {
		if (filterShipmentType == null) {
			return true; // No filter, include all
		}
		
		if (SHIPMENT_TYPE_LOCAL.equalsIgnoreCase(filterShipmentType)) {
			// For Local: must be exactly "Local"
			return itemShipmentType != null 
					&& SHIPMENT_TYPE_LOCAL.equalsIgnoreCase(itemShipmentType);
		} else if (SHIPMENT_TYPE_GLOBAL.equalsIgnoreCase(filterShipmentType)) {
			// For Global: must NOT be "Local" (and not null/empty)
			return StringUtils.isNotBlank(itemShipmentType)
					&& !SHIPMENT_TYPE_LOCAL.equalsIgnoreCase(itemShipmentType);
		}
		
		return true; // Unknown filter type, include all
	}

	/**
	 * Determines the parentProductId for a sales order item.
	 * If item has a parent, uses parent's productId; otherwise uses item's own productId.
	 * 
	 * @param item The sales order item
	 * @return The parsed parentProductId, or null if parsing fails
	 */
	private Integer determineParentProductId(SalesOrderItem item) {
		Integer parentProductId = null;
		String parentIdStr = (item.getParentOrderItem() != null) 
				? item.getParentOrderItem().getProductId() 
				: null;
		
		// Try to parse parent ID first if it exists and is not blank
		if (StringUtils.isNotBlank(parentIdStr)) {
			try {
				parentProductId = Integer.parseInt(parentIdStr);
			} catch (NumberFormatException ex) {
				LOGGER.warn("ReOrderV2 - Failed to parse parentProductId from parent: '" + parentIdStr + "'. Falling back to item's productId.");
			}
		}
		
		// If parentProductId is still null (no parent, blank parentId, or parsing failed), use item's ID
		if (parentProductId == null) {
			String itemIdStr = item.getProductId();
			if (StringUtils.isNotBlank(itemIdStr)) {
				try {
					parentProductId = Integer.parseInt(itemIdStr);
				} catch (NumberFormatException ex) {
					LOGGER.warn("ReOrderV2 - Failed to parse parentProductId from item productId: '" + itemIdStr + "'");
				}
			}
		}
		
		if (parentProductId == null) {
			LOGGER.error("ReOrderV2 - parentProductId could not be determined for SKU: " + item.getSku() + ", productId: " + item.getProductId());
		}
		
		return parentProductId;
	}

}
