package org.styli.services.order.helper;

import org.apache.commons.lang.StringUtils;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.utility.Constants;

/**
 * Utility class for invoice-related operations
 */
public class InvoiceHelper {

	/**
	 * Private constructor to prevent instantiation
	 */
	private InvoiceHelper() {
		throw new UnsupportedOperationException("Utility class cannot be instantiated");
	}

	/**
	 * Build encode value for invoice URL for normal orders
	 * 
	 * @param order SalesOrder entity
	 * @return Encoded value string (entityId or entityId#customerEmail)
	 */
	public static String buildNormalOrderEncodeValue(SalesOrder order) {
		if (isNewInvoiceEncodeEnabled()) {
			// Use defaultString to prevent NPE if customerEmail is null
			String customerEmail = StringUtils.defaultString(order.getCustomerEmail(), "");
			return order.getEntityId().toString().concat("#").concat(customerEmail);
		} else {
			return order.getEntityId().toString();
		}
	}

	/**
	 * Build encode value for invoice URL for split orders
	 * 
	 * @param splitSalesOrder SplitSalesOrder entity
	 * @return Encoded value string (mainOrderId#splitOrderId or mainOrderId#splitOrderId#email)
	 */
	public static String buildSplitOrderEncodeValue(SplitSalesOrder splitSalesOrder) {
		Integer entityId = splitSalesOrder.getSalesOrder().getEntityId();
		String customerEmail = splitSalesOrder.getCustomerEmail() != null ? 
				splitSalesOrder.getCustomerEmail() : splitSalesOrder.getSalesOrder().getCustomerEmail();
		
		// For split orders: mainOrderId#splitOrderId or mainOrderId#splitOrderId#email
		String encodeValue = entityId.toString().concat("#").concat(splitSalesOrder.getEntityId().toString());
		
		if (isNewInvoiceEncodeEnabled()) {
			// Use defaultString to prevent NPE if customerEmail is null
			String safeEmail = StringUtils.defaultString(customerEmail, "");
			encodeValue = encodeValue.concat("#").concat(safeEmail);
		}
		
		return encodeValue;
	}

	/**
	 * Build encode value for invoice URL based on order type
	 * 
	 * @param order SalesOrder entity (main order)
	 * @param splitSalesOrder SplitSalesOrder entity (null for normal orders)
	 * @return Encoded value string
	 */
	public static String buildInvoiceEncodeValue(SalesOrder order, SplitSalesOrder splitSalesOrder) {
		if (splitSalesOrder != null) {
			return buildSplitOrderEncodeValue(splitSalesOrder);
		} else {
			return buildNormalOrderEncodeValue(order);
		}
	}

	/**
	 * Check if new invoice encode is enabled
	 * 
	 * @return true if new invoice encode is enabled
	 */
	private static boolean isNewInvoiceEncodeEnabled() {
		return Constants.orderCredentials != null && 
				Constants.orderCredentials.getWms().isNewInVoiceEncode();
	}
}
