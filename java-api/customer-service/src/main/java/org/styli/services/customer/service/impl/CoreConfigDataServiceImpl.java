package org.styli.services.customer.service.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.styli.services.customer.exception.NotFoundException;
import org.styli.services.customer.model.CoreConfigData;
import org.styli.services.customer.model.Store;
import org.styli.services.customer.pojo.StoreDetailsResponse;
import org.styli.services.customer.pojo.StoreDetailsResponseDTO;
import org.styli.services.customer.repository.CoreConfigDataRepository;
import org.styli.services.customer.repository.StoreRepository;
import org.styli.services.customer.service.CoreConfigDataService;
import org.styli.services.customer.utility.GenericConstants;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Component
public class CoreConfigDataServiceImpl implements CoreConfigDataService {

    private static final Log LOGGER = LogFactory.getLog(CoreConfigDataServiceImpl.class);

    @Autowired
    CoreConfigDataRepository coreConfigDataRepository;

    @Autowired
    StoreRepository storeRepository;

    @Override
    public StoreDetailsResponseDTO getStoreDetails(Integer storeId) {

        StoreDetailsResponseDTO storeDetailsResponseDTO = new StoreDetailsResponseDTO();

        Store store = storeRepository.findByStoreId(storeId);
        if (store == null) {
            storeDetailsResponseDTO.setStatus(false);
            storeDetailsResponseDTO.setStatusCode("201");
            storeDetailsResponseDTO.setStatusMsg("Store not found!");
            return storeDetailsResponseDTO;
        }

        storeDetailsResponseDTO.setStatus(true);
        storeDetailsResponseDTO.setStatusCode("200");
        storeDetailsResponseDTO.setStatusMsg("Success!");

        StoreDetailsResponse storeDetailsResponse = new StoreDetailsResponse();

        String currency = getStoreCurrency(storeId);
        storeDetailsResponse.setCurrency(currency);
        storeDetailsResponse.setCode(store.getCode());
        storeDetailsResponse.setId(storeId);

        storeDetailsResponseDTO.setResponse(storeDetailsResponse);

        return storeDetailsResponseDTO;
    }

    @Override
    public String getStoreCurrency(Integer storeId) {

        Integer websiteId = null;

        Store store = storeRepository.findByStoreId(storeId);

        if (null != store) {

            websiteId = store.getWebSiteId();
        }

        if (null == websiteId) {

            websiteId = 0;
        }

        // Get store currency
        CoreConfigData coreConfigData = coreConfigDataRepository
                .findByPathAndScopeId(GenericConstants.CONFIG_CURRENCY_OPTIONS_DEFAULT, websiteId);

        // Fallback to default store currency
        if (coreConfigData == null) {
            coreConfigData = coreConfigDataRepository.findByPathAndScopeId(
                    GenericConstants.CONFIG_CURRENCY_OPTIONS_DEFAULT, GenericConstants.ADMIN_STORE_ID);
        }

        // Fallback to base currency
        if (coreConfigData == null) {
            coreConfigData = coreConfigDataRepository.findByPathAndScopeId(
                    GenericConstants.CONFIG_CURRENCY_OPTIONS_BASE, GenericConstants.ADMIN_STORE_ID);
        }

        if (coreConfigData == null)
            return null;
        if (coreConfigData.getValue() == null)
            return null;

        return coreConfigData.getValue();
    }

    @Override
    public String getStoreLanguage(Integer storeId) {
        // Get store currency
        CoreConfigData coreConfigData = coreConfigDataRepository
                .findByPathAndScopeId(GenericConstants.CONFIG_GENERAL_LOCALE_CODE, storeId);

        // Fallback to default store currency
        if (coreConfigData == null) {
            coreConfigData = coreConfigDataRepository.findByPathAndScopeId(GenericConstants.CONFIG_GENERAL_LOCALE_CODE,
                    GenericConstants.ADMIN_STORE_ID);
        }

        if (coreConfigData == null)
            return null;
        if (coreConfigData.getValue() == null)
            return null;

        return coreConfigData.getValue();
    }

    @Override
    public int getRMAThresholdInHours(Store store) throws NotFoundException {
        int threshold;
        try {
            int websiteId = store != null ? store.getWebSiteId() : 0;
            LOGGER.info("websiteId:" + websiteId);
            CoreConfigData coreConfigData = coreConfigDataRepository
                    .findByPathAndScopeId(GenericConstants.CONFIG_SALES_RMA_PICKUP_THRESHOLD, websiteId);

            LOGGER.info("coreConfigData is null");

            if (coreConfigData == null) {
                coreConfigData = coreConfigDataRepository.findByPathAndScopeId(
                        GenericConstants.CONFIG_SALES_RMA_PICKUP_THRESHOLD, GenericConstants.DEFAULT_CONFIG_STORE_ID);
            }

            if (coreConfigData != null && null != coreConfigData.getValue()) {

                LOGGER.info("coreConfigData is not null");
                threshold = Integer.parseInt(coreConfigData.getValue());
                return threshold;
            } else {
                LOGGER.info("coreConfigData is further null");
                throw new NotFoundException("RMA applicable threshold not found", 500);
            }
        } catch (Exception e) {
            LOGGER.info("exception occiured during fetch RAM threshold");
            LOGGER.error("Could not fetch RMA applicable threshold for store: "
                    + (store != null ? store.getCode() : null) + e.getMessage());
            throw new NotFoundException("RMA applicable threshold not found", 500);
        }
    }

    @Override
    public int getStoreShipmentChargesThreshold(Store store) throws NotFoundException {
        int threshold;
        try {
            int websiteId = store != null ? store.getWebSiteId() : 0;
            CoreConfigData coreConfigData = coreConfigDataRepository
                    .findByPathAndScopeId(GenericConstants.CONFIG_SALES_SHIPPING_CHARGES_THRESHOLD, websiteId);

            if (coreConfigData == null) {
                coreConfigData = coreConfigDataRepository.findByPathAndScopeId(
                        GenericConstants.CONFIG_SALES_SHIPPING_CHARGES_THRESHOLD,
                        GenericConstants.DEFAULT_CONFIG_STORE_ID);
            }

            if (coreConfigData != null) {
                threshold = Integer.parseInt(coreConfigData.getValue());
                return threshold;
            } else {
                throw new NotFoundException("shipping charges threshold not found", 500);
            }
        } catch (Exception e) {
            LOGGER.error("Could not fetch shipping charges threshold for store: "
                    + (store != null ? store.getCode() : null));
            throw new NotFoundException("shipping charges threshold not found", 500);
        }
    }

    @Override
    public int getStoreShipmentCharges(Store store) throws NotFoundException {
        int charges;
		int websiteId = 0;

		try {

			if (store != null && null != store.getWebSiteId()) {

				websiteId = store.getWebSiteId();
			}
			CoreConfigData coreConfigData = coreConfigDataRepository
					.findByPathAndScopeId(GenericConstants.CONFIG_SALES_SHIPPING_CHARGES, websiteId);

			if (coreConfigData == null) {
				coreConfigData = coreConfigDataRepository.findByPathAndScopeId(
						GenericConstants.CONFIG_SALES_SHIPPING_CHARGES, GenericConstants.DEFAULT_CONFIG_STORE_ID);
			}

			if (coreConfigData != null && null != coreConfigData.getValue()) {

				charges = Integer.parseInt(coreConfigData.getValue());
				return charges;
			} else {
				throw new NotFoundException("shipping charges not found", 500);
			}
		} catch (Exception e) {
            LOGGER.error("Could not fetch shipping charges for webiste: " + websiteId);
            throw new NotFoundException("shipping charges not found", 500);
        }
    }

    @Override
    public int getCodCharges(Store store) throws NotFoundException {
        int charges;
        try {
            int websiteId = store != null ? store.getWebSiteId() : 0;
            CoreConfigData coreConfigData = coreConfigDataRepository
                    .findByPathAndScopeId(GenericConstants.CONFIG_SALES_COD_CHARGES, websiteId);

            if (coreConfigData == null) {
                coreConfigData = coreConfigDataRepository.findByPathAndScopeId(
                        GenericConstants.CONFIG_SALES_COD_CHARGES, GenericConstants.DEFAULT_CONFIG_STORE_ID);
            }

            if (coreConfigData != null) {
                charges = Integer.parseInt(coreConfigData.getValue());
                return charges;
            } else {
                throw new NotFoundException("cod charges not found", 500);
            }
        } catch (Exception e) {
            LOGGER.error("Could not fetch cod charges for store: " + (store != null ? store.getStoreId() : null));
            throw new NotFoundException("cod charges not found", 500);
        }
    }

    @Override
    public int getTaxPercentage(Store store) throws NotFoundException {
        int taxPercentage;
        try {
            int websiteId = store != null ? store.getWebSiteId() : 0;
            CoreConfigData coreConfigData = coreConfigDataRepository
                    .findByPathAndScopeId(GenericConstants.CONFIG_SALES_TAX_PERCENTAGE, websiteId);

            if (coreConfigData == null) {
                coreConfigDataRepository.findByPathAndScopeId(GenericConstants.CONFIG_SALES_TAX_PERCENTAGE,
                        GenericConstants.DEFAULT_CONFIG_STORE_ID);
            }

            if (coreConfigData != null) {
                taxPercentage = Integer.parseInt(coreConfigData.getValue());
                return taxPercentage;
            } else {
                throw new NotFoundException("tax percentage not found", 500);
            }
        } catch (Exception e) {
            LOGGER.error("Could not fetch tax percentage for store: " + (store != null ? store.getCode() : null));
            throw new NotFoundException("tax percentage not found", 500);
        }
    }
}
