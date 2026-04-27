package org.styli.services.order.helper;

import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.utility.OrderConstants;

/**
 * Logistiq document payload flags derived from order context.
 */
public final class LogistiqShipmentHelper {

	private LogistiqShipmentHelper() {
	}

	/**
	 * International shipment for Logistiq when warehouse is the international hub and store is not SA EN/AR.
	 */
	public static boolean isInternationalShipment(SalesOrder order) {
		if (order == null || order.getStoreId() == null || order.getSubSalesOrder() == null) {
			return false;
		}
		if (Integer.valueOf(OrderConstants.STORE_ID_SA_EN).equals(order.getStoreId())
				|| Integer.valueOf(OrderConstants.STORE_ID_SA_AR).equals(order.getStoreId())) {
			return false;
		}
		return OrderConstants.ORDER_PUSH_OMS_LOCATION_CODE.equals(order.getSubSalesOrder().getWarehouseLocationId());
	}
}
