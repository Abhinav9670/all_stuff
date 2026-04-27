package org.styli.services.order.service;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Service
public interface ConfigService {

    public Double getWebsiteRefundByStoreId(Integer storeId);


    List<Integer> getWebsiteStoresByStoreId(Integer storeId);

    public boolean checkAuthorization(String intenalAuthorizationToken, String externalAuthorizationToken) ;

    public boolean checkAuthorizationInternal(String authorizationToken);

    public boolean checkAuthorizationExternal(String authorizationToken);

    public String getFirstInternalAuthBearerToken();

}