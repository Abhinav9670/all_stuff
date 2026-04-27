package org.styli.services.order.service.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.helper.RedisHelper;
import org.styli.services.order.pojo.ProvinceResponse;
import org.styli.services.order.service.AddressMapperHelper;
import org.styli.services.order.utility.Constants;

import java.text.Collator;
import java.util.*;

@Service
public class AddressMapperHelperImpl implements AddressMapperHelper {

    @Autowired
    @Qualifier("restTemplateBuilder")
    RestTemplate restTemplate;

    @Autowired
    protected RedisHelper redisHelper;

    private static final String NAME_AR = "name_ar";
    private static final String NAME_EN = "name_en";

    private static final Log LOGGER = LogFactory.getLog(AddressMapperHelperImpl.class);

    @Value("${address.mapper.url}")
    private String addressMapperUrl;

    @Override
    public Map getAddressMap(String countryCode, String region) {
        // Replace with the actual API URL
        String apiUrl = addressMapperUrl + "/api/address/provinceData";

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

        try {
            // Check Redis for cached province list
            List<Map<String, String>> cachedProvinces =(List<Map<String, String>>) redisHelper.get("provinceList", countryCode, List.class);
            if (cachedProvinces != null && !cachedProvinces.isEmpty()) {
                LOGGER.info("Province list found in Redis for country: " + countryCode);
                return cachedProvinces.stream()
                        .filter(province -> (region.equals(province.get("name")) || areArabicStringsEqual(region, province.get("name_ar"))))
                        .findFirst()
                        .orElse(new HashMap<>());
            }

            LOGGER.info("Address mapper url: " + apiUrl);

            // Make the API call and parse the response
            ResponseEntity<ProvinceResponse> response = restTemplate.exchange(
                    apiUrl+"?country="+countryCode,
                    HttpMethod.GET,
                    new HttpEntity<>(requestHeaders),
                    ProvinceResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                ProvinceResponse provinceResponse = response.getBody();
                if (provinceResponse != null && provinceResponse.getResponse() != null) {
                    // Save the province list to Redis
                    redisHelper.put("provinceList", countryCode, provinceResponse.getResponse());
                    LOGGER.info("Province list saved to Redis for country: " + countryCode);

                    // Find the map where the region matches
                    return provinceResponse.getResponse().stream()
                            .filter(province -> region.equals(province.getName()))
                            .findFirst()
                            .map(province -> {
                                Map<String, Object> provinceMap = new HashMap<>();
                                provinceMap.put("id", province.getId());
                                provinceMap.put("name", province.getName());
                                provinceMap.put("name_ar", province.getName_ar());
                                return provinceMap;
                            })
                            .orElse(new HashMap<>());
                }
            } else {
                LOGGER.error("Failed to fetch region map. HTTP Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            LOGGER.error("Error while calling external API: " + e.getMessage(), e);
        }


        return new HashMap();
    }

    private static boolean areArabicStringsEqual(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return false;
        }

        // Create a Collator for Arabic locale
        Collator collator = Collator.getInstance(new Locale("ar"));
        collator.setStrength(Collator.PRIMARY); // Ignore case and diacritics

        // Compare the strings
        return collator.compare(str1, str2) == 0;
    }
}
