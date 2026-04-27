package org.styli.services.customer.utility.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.client.OrderClient;
import org.styli.services.customer.utility.pojo.config.Environments;
import org.styli.services.customer.utility.pojo.config.Store;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponse;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponseDTO;
import org.styli.services.customer.utility.pojo.config.Stores;
import org.styli.services.customer.utility.service.ConfigService;
import org.styli.services.customer.utility.service.ConfigServiceV2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.cache.KVCache;

@Component
public class ConfigServiceV2Impl implements ConfigServiceV2 {

    private static final Log LOGGER = LogFactory.getLog(ConfigServiceV2Impl.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${consul.ip.address}")
    String consulIpAddress;

    @Autowired
    ConfigService configService;

    @Value("${env}")
    String env;

    @Value("${gcp.object.name}")
    String gcpObjectName;

    @Autowired
    OrderClient orderClient;
    
    @Value("${db.version}")
    private String dbVersion;
    
    @Value("${consul.port}")
    private String consulPort;
    
    @Value("${consul.token}")
    private String consulToken;
    
    
    
  
    @Override
    public StoreConfigResponseDTO getStoreV2Configs(HttpServletRequest httpServletRequest,
                                                    boolean pushToConsul,
                                                    boolean pushToGCP) {
        StoreConfigResponseDTO resp = new StoreConfigResponseDTO();

        try {
           
   
            List<Stores> storesFromConsul = Constants.getStoresList();
            
            StoreConfigResponse storeConfigResponseFromConsul = Constants.getConsulConfigResponse();
            if(null != storeConfigResponseFromConsul) {
            	
           // List<Stores> storesFromConsul = storeConfigResponseFromConsul.getEnvironments().get(0).getStores();
            	
            List<Store> storesFromDB = orderClient.findAllStores();
            String newDbVersion = configService.getAddressDbVersion();
            if (StringUtils.isEmpty(newDbVersion)) {
              newDbVersion = dbVersion;
            }
            storeConfigResponseFromConsul.setDbVersion(newDbVersion);
          

            List<Stores> newStores= new ArrayList<>();
            storesFromDB.stream()
                    .filter(e -> e.getStoreId() != 0)
                    .filter(e -> ObjectUtils.isEmpty(e.getIsExternal()) || e.getIsExternal() != 1)
					.forEach((e -> {

						Stores storeInConsul = storesFromConsul.stream()
								.filter(ex -> ex.getStoreId().equals(e.getStoreId().toString())).findAny().orElse(null);
						if (storeInConsul == null) {
							LOGGER.info("store not found " + e.toString());
							Stores newStore = new Stores();
							newStore.setStoreId(parseNullStr(e.getStoreId()));
							newStore.setStoreCode(parseNullStr(e.getCode()));
							newStore.setWebsiteId(e.getWebSiteId());
							newStore.setStoreName(e.getName());
							if (e.getWarehouseLocationCode() != null)
								newStore.setWarehouseId(Integer.parseInt(e.getWarehouseLocationCode()));
							newStore.setMapperTable(e.getWarehouseInventoryTable());
							newStore.setStoreCurrency(e.getCurrency());
							newStore.setCurrencyConversionRate(e.getCurrencyConversionRate());
							newStores.add(newStore);

						} else {

							newStores.add(storeInConsul);
						}
					}));

            storeConfigResponseFromConsul.getEnvironments().get(0).setStores(newStores);

            if (pushToConsul) {
                String consulString = new ObjectMapper().writeValueAsString(storeConfigResponseFromConsul);
                configService.sendToConsul(consulString);
                LOGGER.info("consul pushed successfully");
            }

            resp.setStatus(true);
            resp.setStatusCode("200");
            resp.setStatusMsg("Success!");

            if (pushToGCP) {
                // Only include stores where isEnable is true; fallback to true when missing
                List<Stores> storesForGcp = newStores.stream()
                        .filter(s -> s.getIsEnable() == null || Boolean.TRUE.equals(s.getIsEnable()))
                        .collect(Collectors.toList());

                Environments sourceEnv = storeConfigResponseFromConsul.getEnvironments().get(0);
                Environments gcpEnv = new Environments(sourceEnv.getType(), sourceEnv.getBaseurl(),
                        sourceEnv.getApiurl(), new ArrayList<>(storesForGcp));

                StoreConfigResponse gcpResponse = new StoreConfigResponse();
                BeanUtils.copyProperties(storeConfigResponseFromConsul, gcpResponse);
                gcpResponse.setEnvironments(new ArrayList<>());
                gcpResponse.getEnvironments().add(gcpEnv);

                // API response shows only enabled stores (same as what is pushed to GCP)
                resp.setResponse(gcpResponse);

                StoreConfigResponseDTO gcpResp = new StoreConfigResponseDTO();
                BeanUtils.copyProperties(resp, gcpResp);
                gcpResp.setResponse(gcpResponse);

                String objectNameNew = "new_"+gcpObjectName;
                configService.saveDataToGCP(gcpResp, objectNameNew);
                configService.saveOldDataToGCP(gcpResp);

                String objectNameAll = "all-" + gcpObjectName;
                configService.saveAllDataToGCP(objectNameAll);
                LOGGER.info("GCP pushed successfully");
            } else {
                resp.setResponse(storeConfigResponseFromConsul);
            }
            
        }else {
        	
        	 resp.setStatus(false);
             resp.setStatusCode("201");
             resp.setStatusMsg("Exception occoured durinf consul fetch !!");
        }

        } catch (Exception e) {
            LOGGER.error("CONSUL_LOG_EXCEPTION", e);
        }
        return resp;

    }

    private String parseNullStr(Object val) {
        return (val == null) ? null : String.valueOf(val);
    }
    
    
}
