package org.styli.services.customer.service;

import org.springframework.stereotype.Service;
import org.styli.services.customer.model.GuestSessions;

import java.util.Map;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 04/05/2022 - 11:59 AM
 */

@Service
public interface GuestService {

    GuestSessions getGuestSession(String deviceId, String xSource, String clientVersion, Integer storeId);
}
