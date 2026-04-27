package org.styli.services.order.service;

import java.util.Map;

public interface AddressMapperHelper {
    Map<String, String> getAddressMap(String countryCode, String region);
}
