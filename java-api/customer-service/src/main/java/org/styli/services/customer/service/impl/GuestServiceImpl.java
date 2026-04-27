package org.styli.services.customer.service.impl;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.styli.services.customer.model.GuestSessions;
import org.styli.services.customer.repository.Customer.GuestSessionsRepository;
import org.styli.services.customer.service.GuestService;
import org.styli.services.customer.utility.CommonUtility;

import java.sql.Timestamp;
import java.util.Date;
import java.util.UUID;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 04/05/2022 - 12:00 PM
 */

@Component
public class GuestServiceImpl implements GuestService {

    @Autowired
    GuestSessionsRepository guestSessionsRepository;

    @Override
    public GuestSessions getGuestSession(String deviceId, String xSource, String clientVersion, Integer storeId) {

        GuestSessions guestSession = guestSessionsRepository.findByDeviceId(deviceId);

        if (ObjectUtils.isEmpty(guestSession)) {
            guestSession = new GuestSessions();
            UUID uuid = CommonUtility.getUuid();

            guestSession.setDeviceId(deviceId);
            guestSession.setClientVersion(clientVersion);
            guestSession.setPlatform(xSource);
            
            if(null != storeId)
            	guestSession.setStoreId(storeId);
            guestSession.setUuid(uuid.toString());
            guestSession.setCreatedAt(new Timestamp(new Date().getTime()));
            guestSession.setUpdatedAt(new Timestamp(new Date().getTime()));
        } else {
        	if(null != storeId) {
	            guestSession.setStoreId(storeId);
	        	guestSession.setUpdatedAt(new Timestamp(new Date().getTime()));
        	}
        }
        guestSessionsRepository.saveAndFlush(guestSession);
        return guestSession;
    }
}
