package org.styli.services.order.service.impl;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.helper.RedisHelper;
import org.styli.services.order.pojo.Province;
import org.styli.services.order.pojo.ProvinceResponse;

import java.util.*;

class AddressMapperHelperImplTest {

    @InjectMocks
    private AddressMapperHelperImpl addressMapperHelper;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RedisHelper redisHelper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAddressMapFromRedis() {
        // Mock Redis data
        String countryCode = "SA";
        String region = "Riyadh";
        List<Map<String, String>> cachedProvinces = new ArrayList<>();
        Map<String, String> province = new HashMap<>();
        province.put("name", "Riyadh");
        province.put("name_ar", "الرياض");
        cachedProvinces.add(province);

        when(redisHelper.get("provinceList", countryCode, List.class)).thenReturn(cachedProvinces);

        // Call the method
        Map result = addressMapperHelper.getAddressMap(countryCode, region);

        // Verify and assert
        assertNotNull(result);
        assertEquals("Riyadh", result.get("name"));
        verify(redisHelper, times(1)).get("provinceList", countryCode, List.class);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void testGetAddressMapFromApi() {
        // Mock API response
        String countryCode = "SA";
        String region = "Riyadh";
        ProvinceResponse provinceResponse = new ProvinceResponse();
        List<Province> provinces = new ArrayList<>();
        Province province = new Province();
        province.setName("Riyadh");
        province.setName_ar("الرياض");
        provinces.add(province);
        provinceResponse.setResponse(provinces);

        when(redisHelper.get("provinceList", countryCode, List.class)).thenReturn(null);
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(ProvinceResponse.class)
        )).thenReturn(new ResponseEntity<>(provinceResponse, HttpStatus.OK));

        // Call the method
        Map result = addressMapperHelper.getAddressMap(countryCode, region);

        // Verify and assert
        assertNotNull(result);
        assertEquals("Riyadh", result.get("name"));
        verify(redisHelper, times(1)).put("provinceList", countryCode, provinces);
    }

    @Test
    void testGetAddressMapApiFailure() {
        // Mock API failure
        String countryCode = "SA";
        String region = "Riyadh";

        when(redisHelper.get("provinceList", countryCode, List.class)).thenReturn(null);
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(ProvinceResponse.class)
        )).thenThrow(new RuntimeException("API error"));

        // Call the method
        Map result = addressMapperHelper.getAddressMap(countryCode, region);

        // Verify and assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(redisHelper, never()).put(anyString(), anyString(), any());
    }
}
