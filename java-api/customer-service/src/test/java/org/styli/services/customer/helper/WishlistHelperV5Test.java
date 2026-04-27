package org.styli.services.customer.helper;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.pojo.StoreDetailsResponse;
import org.styli.services.customer.pojo.elastic.ChildProductElastic;
import org.styli.services.customer.pojo.elastic.FlashSaleElastic;
import org.styli.services.customer.pojo.elastic.HitsDetailResponseElastic;
import org.styli.services.customer.pojo.elastic.PriceType;
import org.styli.services.customer.pojo.elastic.ResponseDetailElastic;
import org.styli.services.customer.pojo.registration.request.CustomerWishListRequest;
import org.styli.services.customer.pojo.registration.request.WishProduct;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.ProductsHitsResponseV4;
import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4DTO;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@SpringBootTest(classes = { WishlistHelperV5Test.class })
public class WishlistHelperV5Test extends AbstractTestNGSpringContextTests {

	@InjectMocks
	WishlistHelperV5 wishlistHelperV5;

	private CustomerEntity customerEntity;
	Map<String, String> requestHeader;

	@Mock
	RestTemplate restTemplate;

	@BeforeMethod
	public void beforeMethod() {
		customerEntity = new CustomerEntity();
		customerEntity.setEntityId(1);
		customerEntity.setFirstName("First Name");
		customerEntity.setLastName("Last Name");
		customerEntity.setEmail("test.100@mailinator.com");
		customerEntity.setGroupId(1);
		customerEntity.setStoreId(1);
		customerEntity.setCreatedAt(new Date());
		customerEntity.setUpdatedAt(new Date());
		customerEntity.setCreatedIn("nowhere");
		customerEntity.setIsActive(1);
		customerEntity.setPhoneNumber("889898797");
		customerEntity.setJwtToken(1);
	}

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

	@Test
	public void convertResponseFromAlgoliaTest() {
		ProductsHitsResponseV4 productsHitsResponseV4 = new ProductsHitsResponseV4();
		productsHitsResponseV4.setSku(Arrays.asList("10"));
		productsHitsResponseV4.setTypeId("1");
		productsHitsResponseV4.setImageUrl("imgurl");
		productsHitsResponseV4.setVisibilityCatalog(1);
		productsHitsResponseV4.setUrl("url");
		StoreDetailsResponse storeDetailsResponse = new StoreDetailsResponse();
		storeDetailsResponse.setCurrency("SAR");

		ProductDetailsResponseV4DTO response = wishlistHelperV5.convertResponseFromAlgolia(productsHitsResponseV4,
				storeDetailsResponse, "top", 1);
		assertEquals(response.getSku(), "10");

	}

	@Test
	public void retrieveProductDetailsFrmCurofyTest() {
		ElasticProductHelperV5 elasticProductHelperV5 = new ElasticProductHelperV5();
		WishProduct wishProduct = new WishProduct();
		wishProduct.setSku("001");
		wishProduct.setParentProductId("1");
		CustomerWishListRequest customerWishListRequest = new CustomerWishListRequest();

		customerWishListRequest.setCustomerId(1);
		customerWishListRequest.setStoreId(1);
		customerWishListRequest.setWishList(Arrays.asList(wishProduct));
		ChildProductElastic childProductElastic = new ChildProductElastic();
		childProductElastic.setId("1");
		childProductElastic.setSku("sku1");
		childProductElastic.setSize("11");
		Map<String, PriceType> price = new HashMap<>();
		PriceType type = new PriceType();
		type.setDefaultPrice(10);
		type.setDefaultOriginalFormatted("10");
		price.put("sar", type);
		HitsDetailResponseElastic hits = new HitsDetailResponseElastic();
		hits.setCategoryIds(Arrays.asList(1, 2, 3));
		hits.setConfigProducts(Arrays.asList(childProductElastic));
		hits.setPrice(price);
		FlashSaleElastic flashSaleElastic = new FlashSaleElastic();
		flashSaleElastic.setActive(true);
		flashSaleElastic.setColor("red");
		flashSaleElastic.setEnd("");
		flashSaleElastic.setStart("");
		ResponseDetailElastic res = new ResponseDetailElastic();
		res.setFlashSale(flashSaleElastic);
		res.setHits(Arrays.asList(hits));
		ResponseEntity<ResponseDetailElastic> response = new ResponseEntity<>(HttpStatus.OK).ok(res);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(ResponseDetailElastic.class))).thenReturn(response);
		List<ProductDetailsResponseV4DTO> listRespo = wishlistHelperV5.retrieveProductDetailsFrmCurofy("dev", "url",
				elasticProductHelperV5, restTemplate, customerWishListRequest);
		assertEquals(listRespo.isEmpty(), false);

	}
}
