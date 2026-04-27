package org.styli.services.order.service.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.styli.services.order.model.sales.SellerConfig;
import org.styli.services.order.pojo.InventoryMapping;
import org.styli.services.order.pojo.SellerInfo;
import org.styli.services.order.pojo.SellerInventoryMapping;
import org.styli.services.order.pojo.UnicommereceInventoryMapping;
import org.styli.services.order.repository.SalesOrder.SellerConfigRepository;
import org.styli.services.order.service.SellerConfigService;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.SellerConfigUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of SellerConfigService.
 * Centralizes seller configuration lookup logic that was previously duplicated
 * in OrderpushHelper and SalesOrderServiceV3Impl.
 * 
 * @author Order Service Team
 */
@Service
public class SellerConfigServiceImpl implements SellerConfigService {

    private static final Log LOGGER = LogFactory.getLog(SellerConfigServiceImpl.class);
    
    private static final String DEFAULT_SELLER_ID = "0001";
    private static final String DEFAULT_SELLER_NAME = "styli";

    @Autowired
    private SellerConfigRepository sellerConfigRepository;

    @Override
    public ConfigSource getConfigSource() {
        Boolean useDbConfig = Constants.orderCredentials.getUseSellerConfigFromDb();
        return Boolean.TRUE.equals(useDbConfig) ? ConfigSource.DATABASE : ConfigSource.CONSUL;
    }

    @Override
    public SellerConfig getSellerConfigForWarehouse(String warehouseId) {
        return getSellerConfigForWarehouse(warehouseId, null);
    }

    @Override
    public SellerConfig getSellerConfigForWarehouse(String warehouseId, String filterFlag) {
        if (warehouseId == null) {
            return null;
        }
        
        switch (getConfigSource()) {
            case DATABASE:
                List<SellerConfig> sellerConfigs = sellerConfigRepository.findByStyliWarehouseId(warehouseId);
                return SellerConfigUtil.getSafeSellerConfig(sellerConfigs, warehouseId, filterFlag);
            case CONSUL:
                SellerInventoryMapping mapping = getSellerInventoryMappingFromConsul(warehouseId, filterFlag);
                return mapping != null ? SellerConfigUtil.convertSellerInventoryMappingToSellerConfig(mapping) : null;
            default:
                return null;
        }
    }

    @Override
    public SellerConfig getSellerConfigBySellerIdAndWarehouse(String sellerId, String warehouseId) {
        switch (getConfigSource()) {
            case DATABASE:
                List<SellerConfig> sellerConfigs = sellerConfigRepository.findBySellerIdAndStyliWarehouseId(sellerId, warehouseId);
                if (CollectionUtils.isNotEmpty(sellerConfigs)) {
                    if (sellerConfigs.size() > 1) {
                        LOGGER.warn("[SellerConfigService] Multiple SellerConfig records found for sellerId: " + sellerId + 
                                    ", styliWarehouseId: " + warehouseId + ". Count: " + sellerConfigs.size());
                    }
                    return sellerConfigs.get(0);
                }
                return null;
            case CONSUL:
                SellerInventoryMapping mapping = Constants.orderCredentials.getSellerInventoryMapping() != null
                    ? Constants.orderCredentials.getSellerInventoryMapping().stream()
                        .filter(m -> {
                            boolean sellerMatch = sellerId == null || sellerId.equals(m.getSellerId());
                            boolean warehouseMatch = warehouseId == null || warehouseId.equals(m.getWareHouseId());
                            return sellerMatch && warehouseMatch;
                        })
                        .findFirst()
                        .orElse(null)
                    : null;
                return mapping != null ? SellerConfigUtil.convertSellerInventoryMappingToSellerConfig(mapping) : null;
            default:
                return null;
        }
    }

    @Override
    public List<SellerConfig> getAllSellerConfigs() {
        switch (getConfigSource()) {
            case DATABASE:
                return sellerConfigRepository.findAll();
            case CONSUL:
                List<SellerInventoryMapping> consulMappings = Constants.orderCredentials.getSellerInventoryMapping();
                if (CollectionUtils.isNotEmpty(consulMappings)) {
                    return consulMappings.stream()
                        .map(SellerConfigUtil::convertSellerInventoryMappingToSellerConfig)
                        .collect(Collectors.toList());
                }
                return new ArrayList<>();
            default:
                return new ArrayList<>();
        }
    }

    @Override
    public SellerInventoryMapping getSellerInventoryMappingFromConsul(String warehouseId, String filterFlag) {
        if (Constants.orderCredentials.getSellerInventoryMapping() == null) {
            return null;
        }
        return Constants.orderCredentials.getSellerInventoryMapping().stream()
            .filter(m -> m.getWareHouseId().equalsIgnoreCase(warehouseId))
            .filter(m -> {
                if (filterFlag == null) return true;
                switch (filterFlag) {
                    case "pushToWms":
                        return Boolean.TRUE.equals(m.getPushToWms());
                    case "pushToSellerCentral":
                        return Boolean.TRUE.equals(m.getPushToSellerCentral());
                    case "pushOrderForSku":
                        return Boolean.TRUE.equals(m.getPushOrderForSku());
                    default:
                        return true;
                }
            })
            .findFirst()
            .orElse(null);
    }

    @Override
    public SellerInfo getSellerIdAndNameForWarehouse(String warehouseId) {
        switch (getConfigSource()) {
            case DATABASE:
                SellerConfig sellerConfig = getSellerConfigForWarehouse(warehouseId, null);
                if (sellerConfig != null) {
                    String sellerId = sellerConfig.getSellerId();
                    String sellerName = sellerConfig.getBasicSettings() != null ? 
                        sellerConfig.getBasicSettings().getSellerName() : DEFAULT_SELLER_NAME;
                    return new SellerInfo(sellerId, sellerName);
                }
                break;
            case CONSUL:
                SellerInventoryMapping mapping = getSellerInventoryMappingFromConsul(warehouseId, null);
                if (mapping != null) {
                    return new SellerInfo(mapping.getSellerId(), mapping.getSellerName());
                }
                break;
        }
        // Fallback to UnicommerceInventoryMapping or InventoryMapping
        return getFallbackSellerInfo(warehouseId);
    }

    @Override
    public List<String> getWmsEnabledWarehouseIds() {
        switch (getConfigSource()) {
            case DATABASE:
                List<SellerConfig> sellerConfigs = sellerConfigRepository.findAll();
                if (CollectionUtils.isNotEmpty(sellerConfigs)) {
                    return sellerConfigs.stream()
                        .filter(s -> s.getBasicSettings() != null && Boolean.TRUE.equals(s.getBasicSettings().getPushToWms()))
                        .map(SellerConfig::getStyliWarehouseId)
                        .distinct()
                        .collect(Collectors.toList());
                }
                return new ArrayList<>();
            case CONSUL:
                List<SellerInventoryMapping> mappings = Constants.orderCredentials.getSellerInventoryMapping();
                if (CollectionUtils.isNotEmpty(mappings)) {
                    return mappings.stream()
                        .filter(m -> Boolean.TRUE.equals(m.getPushToWms()))
                        .map(SellerInventoryMapping::getWareHouseId)
                        .distinct()
                        .collect(Collectors.toList());
                }
                return new ArrayList<>();
            default:
                return new ArrayList<>();
        }
    }

    @Override
    public List<String> getSellerCentralEnabledWarehouseIds() {
        switch (getConfigSource()) {
            case DATABASE:
                List<SellerConfig> sellerConfigs = sellerConfigRepository.findAll();
                if (CollectionUtils.isNotEmpty(sellerConfigs)) {
                    return sellerConfigs.stream()
                        .filter(s -> s.getBasicSettings() != null && Boolean.TRUE.equals(s.getBasicSettings().getPushToSellerCentral()))
                        .map(SellerConfig::getStyliWarehouseId)
                        .distinct()
                        .collect(Collectors.toList());
                }
                return new ArrayList<>();
            case CONSUL:
                List<SellerInventoryMapping> mappings = Constants.orderCredentials.getSellerInventoryMapping();
                if (CollectionUtils.isNotEmpty(mappings)) {
                    return mappings.stream()
                        .filter(m -> Boolean.TRUE.equals(m.getPushToSellerCentral()))
                        .map(SellerInventoryMapping::getWareHouseId)
                        .distinct()
                        .collect(Collectors.toList());
                }
                return new ArrayList<>();
            default:
                return new ArrayList<>();
        }
    }

    /**
     * Fallback method to get seller info from UnicommerceInventoryMapping or InventoryMapping
     * @param warehouseId The warehouse ID to look up
     * @return SellerInfo with defaults if no mapping found
     */
    private SellerInfo getFallbackSellerInfo(String warehouseId) {
        if (Constants.orderCredentials.getUnicommerceInventoryMapping() != null) {
            UnicommereceInventoryMapping unicommerceMapping = Constants.orderCredentials.getUnicommerceInventoryMapping().stream()
                .filter(m -> m.getWareHouseId().equalsIgnoreCase(warehouseId))
                .findFirst()
                .orElse(null);
            if (unicommerceMapping != null) {
                return new SellerInfo(unicommerceMapping.getSellerId(), unicommerceMapping.getSellerName());
            }
        }
        if (Constants.orderCredentials.getInventoryMapping() != null) {
            InventoryMapping invMapping = Constants.orderCredentials.getInventoryMapping().stream()
                .filter(m -> m.getWareHouseId().equalsIgnoreCase(warehouseId))
                .findFirst()
                .orElse(null);
            if (invMapping != null) {
                return new SellerInfo(invMapping.getSellerId(), invMapping.getSellerName());
            }
        }
        return new SellerInfo(DEFAULT_SELLER_ID, DEFAULT_SELLER_NAME);
    }
}
