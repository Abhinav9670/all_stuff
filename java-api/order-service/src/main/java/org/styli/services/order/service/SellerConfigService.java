package org.styli.services.order.service;

import org.styli.services.order.model.sales.SellerConfig;
import org.styli.services.order.pojo.SellerInfo;
import org.styli.services.order.pojo.SellerInventoryMapping;

import java.util.List;

/**
 * Service interface for seller configuration operations.
 * Centralizes the logic for fetching seller configurations from either
 * database (SellerConfig) or Consul (SellerInventoryMapping) based on
 * the useSellerConfigFromDb flag.
 * 
 * @author Order Service Team
 */
public interface SellerConfigService {

    /**
     * Enum to represent the configuration source for seller/warehouse mappings
     */
    enum ConfigSource {
        DATABASE,
        CONSUL
    }

    /**
     * Determines the configuration source based on the useSellerConfigFromDb flag
     * @return ConfigSource.DATABASE if useSellerConfigFromDb is true, ConfigSource.CONSUL otherwise
     */
    ConfigSource getConfigSource();

    /**
     * Gets SellerConfig for a given warehouse ID from the appropriate source (DB or Consul)
     * @param warehouseId The warehouse ID to look up
     * @return SellerConfig if found, null otherwise
     */
    SellerConfig getSellerConfigForWarehouse(String warehouseId);

    /**
     * Gets SellerConfig for a given warehouse ID with optional filter flag
     * @param warehouseId The warehouse ID to look up
     * @param filterFlag Optional filter: "pushToWms", "pushToSellerCentral", "pushOrderForSku", or null for no filter
     * @return SellerConfig if found, null otherwise
     */
    SellerConfig getSellerConfigForWarehouse(String warehouseId, String filterFlag);

    /**
     * Gets SellerConfig by seller ID and warehouse ID from the appropriate source (DB or Consul)
     * @param sellerId The seller ID to look up
     * @param warehouseId The warehouse ID to look up
     * @return SellerConfig if found, null otherwise
     */
    SellerConfig getSellerConfigBySellerIdAndWarehouse(String sellerId, String warehouseId);

    /**
     * Gets all SellerConfigs from the appropriate source
     * @return List of all SellerConfig entries
     */
    List<SellerConfig> getAllSellerConfigs();

    /**
     * Gets SellerInventoryMapping from Consul with optional filter
     * @param warehouseId The warehouse ID to look up
     * @param filterFlag Optional filter: "pushToWms", "pushToSellerCentral", "pushOrderForSku", or null for no filter
     * @return SellerInventoryMapping if found, null otherwise
     */
    SellerInventoryMapping getSellerInventoryMappingFromConsul(String warehouseId, String filterFlag);

    /**
     * Gets seller ID and name for a warehouse from the appropriate source
     * @param warehouseId The warehouse ID to look up
     * @return SellerInfo containing sellerId and sellerName, with defaults if not found
     */
    SellerInfo getSellerIdAndNameForWarehouse(String warehouseId);

    /**
     * Gets list of warehouse IDs that have pushToWms enabled from the appropriate source
     * @return List of warehouse IDs with pushToWms enabled
     */
    List<String> getWmsEnabledWarehouseIds();

    /**
     * Gets list of warehouse IDs that have pushToSellerCentral enabled from the appropriate source
     * @return List of warehouse IDs with pushToSellerCentral enabled
     */
    List<String> getSellerCentralEnabledWarehouseIds();
}
