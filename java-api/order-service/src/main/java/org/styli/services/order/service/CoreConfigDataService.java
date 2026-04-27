package org.styli.services.order.service;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.StoreDetailsResponseDTO;
import org.styli.services.order.model.Store;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Service
public interface CoreConfigDataService {

    StoreDetailsResponseDTO getStoreDetails(Integer storeId);

    String getStoreCurrency(Integer storeId);

    String getStoreLanguage(Integer storeId);

    BigDecimal getStoreShipmentChargesThreshold(Store store) throws NotFoundException;

    BigDecimal getStoreShipmentChargesThreshold(Integer websiteId) throws NotFoundException;

    BigDecimal getStoreShipmentCharges(Store store) throws NotFoundException;

    BigDecimal getStoreShipmentCharges(Integer websiteId) throws NotFoundException;

    BigDecimal getCodCharges(Store store) throws NotFoundException;

    BigDecimal getCodCharges(Integer websiteId) throws NotFoundException;

    BigDecimal getTaxPercentage(Store store) throws NotFoundException;

    BigDecimal getTaxPercentage(Integer websiteId) throws NotFoundException;

    BigDecimal getRMAThresholdInHours(Store store) throws NotFoundException;

    BigDecimal getRMAThresholdInHours(Integer websiteId, String code) throws NotFoundException;
    
    BigDecimal getCurrencyConversionRate(Integer websiteId)throws NotFoundException;
    
    BigDecimal getCustomDutiesPercentage(Store store) throws NotFoundException;

    BigDecimal getImportFeePercentage(Store store) throws NotFoundException;

    BigDecimal getMinimumDutiesAmount(Store store) throws NotFoundException;
    
    BigDecimal getCustomDutiesPercentage(Integer websiteId) throws NotFoundException;

    BigDecimal getImportFeePercentage(Integer websiteId) throws NotFoundException;

    BigDecimal getMinimumDutiesAmount(Integer websiteId) throws NotFoundException;
    
    Integer getQuoteProductMaxQtyNumber(Integer websiteId) throws NotFoundException;

	BigDecimal getImportMaxFeePercentage(Integer websiteId)throws NotFoundException;

	Map<String, String> getCurrentAddressDbVersion() throws NotFoundException;
	Map<String, Object> saveCurrentAddressDbVersion(String addressversion)throws NotFoundException;
	
	BigDecimal getCatalogCurrencyConversionRate(Integer websiteId)throws NotFoundException;

}
