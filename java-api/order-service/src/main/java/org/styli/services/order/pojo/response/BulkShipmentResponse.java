package org.styli.services.order.pojo.response;

import java.util.List;
import lombok.Data;

/**
 * Response structure for bulk shipment operations.
 * Avoids redundant status fields by having one overall status for the entire bulk operation.
 */
@Data
public class BulkShipmentResponse {
	
	// Overall operation status
	private Boolean status;
	private String statusCode;
	private String statusMsg;
	
	// Individual shipment results
	private List<ShipmentResult> shipments;
	
	// Summary statistics
	private Integer totalRequested;
	private Integer successCount;
	private Integer failureCount;
	
	/**
	 * Individual shipment result within the bulk operation
	 */
	@Data
	public static class ShipmentResult {
		private String orderCode;
		private Boolean success;
		private String shipmentCode;
		private Integer shipmentId;
		private String shipmentIncrementId;
		private List<shipmentItem> shipmentItems;
		private String errorMessage; // Only populated if success = false
	}
}

