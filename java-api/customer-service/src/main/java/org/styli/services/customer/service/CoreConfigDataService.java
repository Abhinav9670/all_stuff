package org.styli.services.customer.service;

import org.springframework.stereotype.Service;
import org.styli.services.customer.exception.NotFoundException;
import org.styli.services.customer.model.Store;
import org.styli.services.customer.pojo.StoreDetailsResponseDTO;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Service
public interface CoreConfigDataService {

    StoreDetailsResponseDTO getStoreDetails(Integer storeId);

    String getStoreCurrency(Integer storeId);

    String getStoreLanguage(Integer storeId);

    int getStoreShipmentChargesThreshold(Store store) throws NotFoundException;

    int getStoreShipmentCharges(Store store) throws NotFoundException;

    int getCodCharges(Store store) throws NotFoundException;

    int getTaxPercentage(Store store) throws NotFoundException;

    int getRMAThresholdInHours(Store store) throws NotFoundException;
}
