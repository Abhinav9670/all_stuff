package org.styli.services.customer.utility;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.styli.services.customer.pojo.GetOrderConsulValues;
import org.styli.services.customer.pojo.LoginCredentials;
import org.styli.services.customer.pojo.consul.FreeShipping;
import org.styli.services.customer.pojo.consul.QuoteFreeShippingConsul;
import org.styli.services.customer.utility.pojo.config.Stores;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

public class ConstantsTest {
	@InjectMocks
	Constants constants;

	@BeforeTest
	public void beforeTest() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	void setOrderCredentials() {
		GetOrderConsulValues val = new GetOrderConsulValues();
		val.setFirebaseAuthEnable(true);
		val.setExternalAuthEnable(false);
		val.setInternalAuthEnable(false);
		Constants.setOrderCredentials(new Gson().toJson(val).toString());
		LoginCredentials logincred = new LoginCredentials();
		logincred.setIosCleintId("1");
		Constants.setLoginCredentials(new Gson().toJson(logincred).toString());
		ConsulValues value = new ConsulValues();
		value.setInventoryBaseUrl("url");
		Map<String, String> mp = new HashMap<>();
		mp.put("catalogConsulKeys", "val1");
		ReflectionTestUtils.setField(constants, "catalogConsulValues", mp);
		Constants.getInventoryBaseUrl();
		Constants.setCatalogConsulValues("test");
		QuoteFreeShippingConsul quoteFreeShippingConsul = new QuoteFreeShippingConsul();
		FreeShipping freeshiping = new FreeShipping();
		quoteFreeShippingConsul.setFreeShipping(freeshiping);
		Constants.setFreeShipping(new Gson().toJson(quoteFreeShippingConsul).toString());
		Constants.getAddressvalidation();
		Constants.getAddressMapper();
		Stores store = new Stores();
		List<Stores> lst = new ArrayList<>();
		lst.add(store);
		Constants.setStoresList(lst);
		assertEquals(Constants.getStoresList().isEmpty(), false);

	}

	@Test
	void setJwtTokenTest() {
		LinkedTreeMap<String, String> linkedMap = new LinkedTreeMap<>();
		linkedMap.put("area_validation_en", "area_validation_en");
		linkedMap.put("area_validation_ar", "area_validation_ar");
		linkedMap.put("city-validation_en", "city-validation_en");
		linkedMap.put("city-validation_ar", "city-validation_ar");
		linkedMap.put("region-validation_en", "region-validation_en");
		linkedMap.put("region-validation_ar", "region-validation_ar");

		Map<String, Object> map = new HashMap<>();
		map.put("java_create_order_jwt_token_enable", true);
		map.put("addressValidationMessage", linkedMap);
		Constants.setJwtToken(map);
		assertNotNull(Constants.addressValidatetionMessage);

	}

}
