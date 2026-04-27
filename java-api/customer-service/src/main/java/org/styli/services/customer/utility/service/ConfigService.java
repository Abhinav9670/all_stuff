package org.styli.services.customer.utility.service;

import org.springframework.stereotype.Service;
import org.styli.services.customer.utility.exception.NotFoundException;
import org.styli.services.customer.utility.pojo.config.Environments;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponseDTO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Service
public interface ConfigService {

    StoreConfigResponseDTO getStoreV1Configs(HttpServletRequest httpServletRequest, boolean toPush);

    public List<Environments> getV1Environments() throws NotFoundException;

    public void sendToConsul(String responseAsString);

    public void saveDataToGCP(StoreConfigResponseDTO resp, String objectName);

    public void saveOldDataToGCP(StoreConfigResponseDTO resp);
    
    public String getAddressDbVersion();
    
    public void saveAllDataToGCP(String objectName);

}
