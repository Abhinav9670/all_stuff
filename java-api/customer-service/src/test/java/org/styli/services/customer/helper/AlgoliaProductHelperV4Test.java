package org.styli.services.customer.helper;

import static org.testng.Assert.assertNotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.styli.services.customer.pojo.CatalogProductEntityDTO;
import org.styli.services.customer.pojo.PriceDetails;
import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4DTO;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@SpringBootTest(classes = { WishlistHelperV5Test.class })
public class AlgoliaProductHelperV4Test extends AbstractTestNGSpringContextTests {

	@InjectMocks
	AlgoliaProductHelperV4 algoliaProductHelperV4;

	@BeforeClass
	public void beforeClass() {

		requestHeader = new HashMap<>();
		requestHeader.put("x-source", "android");
		requestHeader.put("x-client-version", "11");
		requestHeader.put("token", "token");
		requestHeader.put("X-Header-Token", "test@mail.com");

	}

	@BeforeTest
	public void beforeTest() {
		MockitoAnnotations.initMocks(this);
	}

	Map<String, String> requestHeader;

	@Test
	public void convertOldToV4ResponseTest() {
		PriceDetails prices = new PriceDetails();
		prices.setPrice("10");
		prices.setSpecialPrice("10");
		CatalogProductEntityDTO catalogProductEntityDTO = new CatalogProductEntityDTO();
		catalogProductEntityDTO.setId("1");
		catalogProductEntityDTO.setSku("sku1");
		catalogProductEntityDTO.setSize("11");
		catalogProductEntityDTO.setPrices(prices);
		CatalogProductEntityDTO req = new CatalogProductEntityDTO();
		req.setId("1");
		req.setSku("sku01");
		req.setName("name");
		req.setPrices(prices);
		req.setCurrency("SAR");
		req.setBrand("brand");
		req.setProductType("type");
		req.setConfigProducts(Arrays.asList(catalogProductEntityDTO));

		ProductDetailsResponseV4DTO respo = algoliaProductHelperV4.convertOldToV4Response(req);
		assertNotNull(respo);

	}
}
