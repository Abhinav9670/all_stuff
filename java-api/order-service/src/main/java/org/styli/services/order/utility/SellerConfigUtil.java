package org.styli.services.order.utility;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.styli.services.order.model.sales.SellerConfig;
import org.styli.services.order.pojo.SellerBasicSettings;
import org.styli.services.order.pojo.SellerConfiguration;
import org.styli.services.order.pojo.SellerInventoryMapping;

import java.util.List;

/**
 * Utility class for SellerConfig related operations
 * 
 * @author Order Service Team
 */
public class SellerConfigUtil {

	private static final Log LOGGER = LogFactory.getLog(SellerConfigUtil.class);

	/**
	 * Private constructor to prevent instantiation of utility class
	 */
	private SellerConfigUtil() {
		throw new IllegalStateException("Utility class");
	}

	/**
	 * Safely retrieves a SellerConfig from a list, handling multiple results gracefully.
	 * This method ensures that:
	 * 1. Empty lists return null with appropriate logging
	 * 2. Multiple results are logged as warnings since the query should be more specific
	 * 3. Optional filtering is applied based on criteria (pushToWms, pushToSellerCentral)
	 * 
	 * @param sellerConfigs List of SellerConfig from repository query
	 * @param warehouseId Warehouse ID for logging purposes
	 * @param filterCriteria Optional filter: "pushToWms", "pushToSellerCentral", or null for no filter
	 * @return First matching SellerConfig, or null if list is empty
	 */
	public static SellerConfig getSafeSellerConfig(List<SellerConfig> sellerConfigs, String warehouseId, String filterCriteria) {
		if (CollectionUtils.isEmpty(sellerConfigs)) {
			LOGGER.warn("[SellerConfigUtil] No SellerConfig found for styliWarehouseId: " + warehouseId);
			return null;
		}

		// Log warning if multiple results found
		if (sellerConfigs.size() > 1) {
			LOGGER.warn("[SellerConfigUtil] Multiple SellerConfig records found for styliWarehouseId: " + warehouseId + 
						". Count: " + sellerConfigs.size() + ". This query should be more specific.");
		}

		// Apply filter criteria if specified
		if (filterCriteria != null && !filterCriteria.isEmpty()) {
			switch (filterCriteria) {
				case "pushToWms":
					return sellerConfigs.stream()
						.filter(sc -> sc.getBasicSettings() != null && Boolean.TRUE.equals(sc.getBasicSettings().getPushToWms()))
						.findFirst()
						.orElse(sellerConfigs.get(0)); // Fallback to first if no match
				case "pushToSellerCentral":
					return sellerConfigs.stream()
						.filter(sc -> sc.getBasicSettings() != null && Boolean.TRUE.equals(sc.getBasicSettings().getPushToSellerCentral()))
						.findFirst()
						.orElse(sellerConfigs.get(0)); // Fallback to first if no match
				default:
					LOGGER.warn("[SellerConfigUtil] Unknown filter criteria: " + filterCriteria);
					return sellerConfigs.get(0);
			}
		}

		// No filter specified, return first result with warning already logged above
		return sellerConfigs.get(0);
	}

	/**
	 * Converts a SellerInventoryMapping (from Consul) to a SellerConfig entity
	 * for unified processing when useSellerConfigFromDb flag is false.
	 * 
	 * @param sim SellerInventoryMapping from Consul configuration
	 * @return SellerConfig with populated basic settings and configuration
	 */
	public static SellerConfig convertSellerInventoryMappingToSellerConfig(SellerInventoryMapping sim) {
		if (sim == null) {
			return null;
		}

		SellerConfig config = new SellerConfig();
		config.setSellerId(sim.getSellerId());
		config.setStyliWarehouseId(sim.getWareHouseId());
		config.setSellerWarehouseId(sim.getSellerWareHouseId());
		config.setSellerType("DEFAULT"); // Placeholder as SellerInventoryMapping doesn't have sellerType

		// Convert BasicSettings
		SellerBasicSettings basicSettings = new SellerBasicSettings();
		basicSettings.setPushToWms(sim.getPushToWms());
		basicSettings.setSellerName(sim.getSellerName());
		basicSettings.setWarehouseName(sim.getWareHouseName());
		basicSettings.setDefaultShipTo(sim.getDefaultShipTo());
		basicSettings.setPushOrderForSku(sim.getPushOrderForSku());
		basicSettings.setHasGlobalShipment(sim.getHasGlobalShipment());
		basicSettings.setPushToSellerCentral(sim.getPushToSellerCentral());
		basicSettings.setDefaultFullfilmentBy(sim.getDefaultFullfilmentBy());
		basicSettings.setDefaultShipToWarehouseId(sim.getDefaultShipToWarehouseId());
		config.setBasicSettings(basicSettings);

		// Convert Configuration
		SellerConfiguration configuration = new SellerConfiguration();
		configuration.setPackedSlaHrs(sim.getPackedSlaHrs());
		configuration.setShippedSlaHrs(sim.getShippedSlaHrs());
		configuration.setPickupInfoName(sim.getPickupInfoName());
		configuration.setWmsWarehouseBaseUrl(sim.getWmsWareHouseBaseUrl());
		configuration.setWmsWareHouseOutwardOrder(sim.getWmsWareHouseOutwardOrder());
		configuration.setWmsWareHouseOrderCancel(sim.getWmsWareHouseOrderCancel());
		configuration.setAcknowledgementSlaHrs(sim.getAcknowledgementSlaHrs());
		configuration.setWmsWarehouseHeaderPassword(sim.getWmsHeaderUsrPassword());
		configuration.setWmsWarehouseHeaderUserName(sim.getWmsHeaderUsrName());
		configuration.setOrderStatusGovernance(sim.getOrderStatusGoverance());
		configuration.setMaxAcknowledgementBuffer(sim.getMaxAcknowledgementBuffer());
		configuration.setMaxPackedBuffer(sim.getMaxPackedBuffer());
		configuration.setMaxShippedBuffer(sim.getMaxShippedBuffer());
		config.setConfiguration(configuration);

		// SellerAddress is not available in SellerInventoryMapping
		config.setAddress(null);

		return config;
	}
}

