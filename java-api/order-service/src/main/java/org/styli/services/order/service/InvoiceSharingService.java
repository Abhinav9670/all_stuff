package org.styli.services.order.service;

import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesShipmentTrack;

public interface InvoiceSharingService {
	void sendInvoiceToLogistiqAsync(String awb, SalesOrder order, SalesShipmentTrack track, String authorizationToken);
	boolean sendInvoiceToLogistiq(String awb, SalesOrder order, SalesShipmentTrack track, String authorizationToken);
	void retryFailedInvoiceUploads();
	RetryResult retryInvoiceUploadForTrack(SalesShipmentTrack track);
	
	/**
	 * Result of retry operation
	 */
	class RetryResult {
		private final boolean success;

		public RetryResult(boolean success) {
			this.success = success;
		}

		public boolean isSuccess() {
			return success;
		}
	}
}
