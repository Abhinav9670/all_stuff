package org.styli.services.customer.service.impl;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.styli.services.customer.exception.NotFoundException;
import org.styli.services.customer.model.CoreConfigData;
import org.styli.services.customer.model.EavAttribute;
import org.styli.services.customer.model.Store;
import org.styli.services.customer.pojo.StoreDetailsResponseDTO;
import org.styli.services.customer.repository.CoreConfigDataRepository;
import org.styli.services.customer.repository.StoreRepository;
import org.styli.services.customer.repository.Customer.EavAttributeRepository;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class CoreConfigDataServiceImplTest extends AbstractTestNGSpringContextTests {

	@InjectMocks
	CoreConfigDataServiceImpl coreConfigDataServiceImpl;

	@Mock
	StoreRepository storeRepository;
	@Mock
	CoreConfigDataRepository coreConfigDataRepository;
	@Mock
	EavAttributeRepository eavAttributeRepository;

	Store store;
	CoreConfigData config;

	@BeforeTest
	public void beforeTest() {
		MockitoAnnotations.initMocks(this);
		store = new Store();
		store.setCode("1");
		store.setStoreId(1);
		store.setGroupId("1");
		store.setWebSiteId(1);
		store.setName("storename");

		config = new CoreConfigData();
		config.setConfigId(1);
		config.setValue("1");
		config.setScope("scope");
	}

	@Test
	public void testGetStoreDetails() {
		when(storeRepository.findByStoreId(anyInt())).thenReturn(store);
		
		when(coreConfigDataRepository.findByPathAndScopeId(anyString(),anyInt())).thenReturn(config);
		StoreDetailsResponseDTO response = coreConfigDataServiceImpl.getStoreDetails(1);
		assertEquals(response.getStatus(), true);
		assertNotNull(response.getResponse());
	}

	@Test
	void testGetStoreLanguage() {
		List<EavAttribute> eavAttributeList = new ArrayList<>();
		EavAttribute eavAttribute = new EavAttribute();
		eavAttribute.setAttributeId(1);
		eavAttribute.setAttributeCode("1");
		eavAttributeList.add(eavAttribute);
		when(coreConfigDataRepository.findByPathAndScopeId(anyString(), anyInt())).thenReturn(config);
		String response = coreConfigDataServiceImpl.getStoreLanguage(1);
		assertEquals(response, config.getValue());
	}

	@Test
	void testGetRMAThresholdInHours() throws NotFoundException {
		List<EavAttribute> eavAttributeList = new ArrayList<>();
		EavAttribute eavAttribute = new EavAttribute();
		eavAttribute.setAttributeId(1);
		eavAttribute.setAttributeCode("1");
		eavAttributeList.add(eavAttribute);
		when(coreConfigDataRepository.findByPathAndScopeId(anyString(), anyInt())).thenReturn(config);
		int response = coreConfigDataServiceImpl.getRMAThresholdInHours(store);
		int responseship = coreConfigDataServiceImpl.getStoreShipmentCharges(store);
		int responsecod = coreConfigDataServiceImpl.getCodCharges(store);
		int responsethreshold = coreConfigDataServiceImpl.getStoreShipmentChargesThreshold(store);
		int responsetax = coreConfigDataServiceImpl.getTaxPercentage(store);
		assertEquals(responseship, Integer.valueOf(config.getValue()));
		assertEquals(responsecod, Integer.valueOf(config.getValue()));
		assertEquals(response, Integer.valueOf(config.getValue()));
		assertEquals(responsethreshold, Integer.valueOf(config.getValue()));
		assertEquals(responsetax, Integer.valueOf(config.getValue()));
	}
//	@Test
//	void testGetStoreShipmentCharges() throws NotFoundException {
//		List<EavAttribute> eavAttributeList = new ArrayList<>();
//		EavAttribute eavAttribute = new EavAttribute();
//		eavAttribute.setAttributeId(1);
//		eavAttribute.setAttributeCode("1");
//		eavAttributeList.add(eavAttribute);
//		when(coreConfigDataRepository.findByPathAndScopeId(anyString(),anyInt())).thenReturn(config);
//		int responseship = coreConfigDataServiceImpl.getStoreShipmentCharges(store);
//		int responsecod = coreConfigDataServiceImpl.getCodCharges(store);
//		assertEquals(responseship, Integer.valueOf(config.getValue()));
//		assertEquals(responsecod, Integer.valueOf(config.getValue()));
//	}
}
