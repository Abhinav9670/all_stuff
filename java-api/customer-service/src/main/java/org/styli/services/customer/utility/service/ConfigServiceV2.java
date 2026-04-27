package org.styli.services.customer.utility.service;

import org.springframework.stereotype.Service;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponseDTO;

import javax.servlet.http.HttpServletRequest;

@Service
public interface ConfigServiceV2 {

    StoreConfigResponseDTO getStoreV2Configs(HttpServletRequest httpServletRequest, boolean pushToConsul, boolean pushToGCP);

}
