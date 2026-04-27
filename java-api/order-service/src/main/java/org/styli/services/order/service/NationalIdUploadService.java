package org.styli.services.order.service;

import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesShipmentTrack;

/**
 * Service for National ID fetch, base64 conversion, and upload to Logistiq.
 * All logic lives in separate new files; does not modify existing invoice/National ID code.
 */
public interface NationalIdUploadService {

	/**
	 * Schedules async National ID fetch + base64 + upload to Logistiq for the given AWB/order/track.
	 * Invoice upload is not performed here; CRON handles invoice upload.
	 */
	void scheduleNationalIdUploadAsync(String awbNumber, SalesOrder order, SalesShipmentTrack track);

	/**
	 * CRON: process tracks that have AWB but National ID not yet uploaded (status null or PENDING).
	 * Does not alter any existing code paths; called only from scheduler or manual endpoint.
	 */
	void processPendingNationalIdUploads();
}
