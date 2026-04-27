package org.styli.services.customer.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.styli.services.customer.helper.ElasticProductHelperV5;
import org.styli.services.customer.helper.LoginCapchaHelper;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.helper.WishlistHelperV5;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.customer.model.Store;
import org.styli.services.customer.model.Wishlist.WishlistEntity;
import org.styli.services.customer.model.Wishlist.WishlistItem;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntity;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntityVarchar;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.request.CustomerV4Registration;
import org.styli.services.customer.pojo.registration.request.CustomerWishListRequest;
import org.styli.services.customer.pojo.registration.request.WishProduct;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerV4RegistrationResponse;
import org.styli.services.customer.pojo.registration.response.CustomerWishlistResponse;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.repository.SequenceCustomerEntityRepository;
import org.styli.services.customer.repository.StaticComponents;
import org.styli.services.customer.repository.StoreRepository;
import org.styli.services.customer.repository.Address.CustomerAddressEntityVarcharRepository;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerGridFlatRepository;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.service.WhatsappService;
import org.styli.services.customer.service.impl.Address.SaveAddress;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.helper.AddressMapperHelperV2;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponse;
import org.styli.services.customer.utility.pojo.request.AdrsmprResponse;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { CustomerV4ServiceImplWhishlistTest.class })
public class CustomerV4ServiceImplWhishlistTest extends AbstractTestNGSpringContextTests {

	CustomerAddressEntity newCustAddressObject;
	CustomerAddrees customerAddrees;
	CustomerAddressEntityVarchar customerAddressEntityVarchar;
	AdrsmprResponse ad;
	@Autowired
	private WebApplicationContext webApplicationContext;

	private CustomerV4Registration customerInfoRequest;

	private CustomerV4RegistrationResponse customerV4RegistrationResponse;

	@InjectMocks
	private CustomerV4ServiceImpl customerV4ServiceImpl;

	@Mock
	private JwtValidator validator;
	@Mock
	StaticComponents staticComponents;
	@Mock
	CustomerGridFlatRepository customerGridFlatRepository;
	@Mock
	CustomerAddressEntityVarcharRepository customerAddressEntityVarcharRepository;

	@Mock
	AsyncService asyncService;
	@Mock
	CustomerEntityRepository customerEntityRepository;
	@Mock
	CustomerAddressEntityRepository customerAddressEntityRepository;
	@Mock
	StoreRepository storeRepository;

	@InjectMocks
	ValidateUser validateUser;

	@InjectMocks
	UpdateUser updateUser;
	@InjectMocks
	Constants constants;
	@InjectMocks
	SaveAddress saveAddress;

	@Mock
	PasswordHelper passwordHelper;

	@Mock
	RedisHelper redisHelper;

	@Mock
	IosSigninHelper iosSigninHelper;

	@Mock
	GoogleSigninHelper googleSigninHelper;

	@Mock
	StoreConfigResponse storeConfigResponse;

	@Mock
	RestTemplate restTemplate;

	@Mock
	Client client;
	@Mock
	WishlistHelperV5 wishlistHelper;

	@Mock
	SequenceCustomerEntityRepository sequenceCustomerEntityRepository;

	@InjectMocks
	LoginUser loginUser;

	@InjectMocks
	AddWishlist addWishlist;

	@InjectMocks
	LoginCapchaHelper loginCapchaHelper;

	@Mock
	WhatsappService whatsappService;

	@InjectMocks
	private SaveCustomer saveCustomer;

	Map<String, String> requestHeader;

	private CustomerEntity customerEntity;

	private CustomerLoginV4Request customerLoginV4Request;

	@InjectMocks
	private AddressMapperHelperV2 addressMapperHelperV2;
	@InjectMocks
	private ElasticProductHelperV5 elasticProductHelperV5;
	private List<Stores> storeList;

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
		try {
			String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/store_list.json")));
			Type listType = new TypeToken<ArrayList<Stores>>() {
			}.getType();
			storeList = new Gson().fromJson(storeData, listType);

		} catch (Exception e) {
		}

	}

	@BeforeTest
	public void beforeTest() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	void getWishListTest() throws Exception {
		setStaticfields();
		setWishlistData();

		CustomerWishlistResponse reponse = customerV4ServiceImpl.getWishList(1, 1, true);
		assertEquals(reponse.isStatus(), true);
		assertEquals(reponse.getStatusCode(), "200");
	}

	private void setWishlistData() {
		WishlistItem wishlistItem = new WishlistItem();
		wishlistItem.setSku("001");
		wishlistItem.setStoreId(1);
		WishlistEntity wishlistEntity = new WishlistEntity();
		wishlistEntity.setId(1);
		wishlistEntity.setWishListItems(Arrays.asList(wishlistItem));

		when(client.findByCustomerId(anyInt())).thenReturn(wishlistEntity);
		when(client.exitsById(anyInt())).thenReturn(true);
		Store store = new Store();
		store.setName("ind");
		store.setStoreId(1);
		store.setWebSiteId(1);
		when(client.findByStoreId(anyInt())).thenReturn(store);
		when(client.findByWebSiteId(anyInt())).thenReturn(Arrays.asList(1));
		when(client.findByCustomerId(anyInt())).thenReturn(wishlistEntity);
		when(client.saveandFlushWishlistEntity(any())).thenReturn(wishlistEntity);
	}

	@Test
	void getWishListFailTest() throws Exception {
		setStaticfields();
		WishlistItem wishlistItem = new WishlistItem();
		wishlistItem.setSku("001");
		wishlistItem.setStoreId(1);
		WishlistEntity wishlistEntity = new WishlistEntity();
		wishlistEntity.setId(1);
		wishlistEntity.setWishListItems(Arrays.asList(wishlistItem));

		when(client.findByCustomerId(anyInt())).thenReturn(null);
		when(client.exitsById(anyInt())).thenReturn(true);
		Store store = new Store();
		store.setName("ind");
		store.setStoreId(1);
		store.setWebSiteId(1);
		when(client.findByStoreId(anyInt())).thenReturn(store);
		when(client.findByWebSiteId(anyInt())).thenReturn(Arrays.asList(1));

		CustomerWishlistResponse reponse = customerV4ServiceImpl.getWishList(1, 1, true);
		assertEquals(reponse.isStatus(), true);
		assertEquals(reponse.getStatusCode(), "201");
	}

	@Test
	void removeWishListTest() throws Exception {
		setStaticfields();
		setWishlistData();
		WishProduct wishProduct = new WishProduct();
		wishProduct.setSku("001");
		CustomerWishListRequest CustomerWishListRequest = new CustomerWishListRequest();
		CustomerWishListRequest.setStoreId(1);
		CustomerWishListRequest.setCustomerId(1);
		CustomerWishListRequest.setWishList(Arrays.asList(wishProduct));

		CustomerWishlistResponse reponse = customerV4ServiceImpl.removeWishList(CustomerWishListRequest);
		assertEquals(reponse.isStatus(), true);
		assertEquals(reponse.getStatusCode(), "200");
	}

	@Test
	void saveUpdateV4OneWishListAlreadyExistTest() throws Exception {
		setStaticfields();
		setWishlistData();
		WishProduct wishProduct = new WishProduct();
		wishProduct.setSku("001");
		wishProduct.setSpecialPrice("speprice");
		CustomerWishListRequest CustomerWishListRequest = new CustomerWishListRequest();
		CustomerWishListRequest.setStoreId(1);
		CustomerWishListRequest.setCustomerId(1);
		CustomerWishListRequest.setWishList(Arrays.asList(wishProduct));
		when(client.getStoresArray()).thenReturn(storeList);
		CustomerWishlistResponse reponse = customerV4ServiceImpl.saveUpdateV4OneWishList(CustomerWishListRequest,
				requestHeader, true);
		assertEquals(reponse.isStatus(), true);
		assertEquals(reponse.getStatusCode(), "200");
	}

//	@Test
//	void saveUpdateV4OneWishListTest() throws Exception {
//		setStaticfields();
//		setWishlistData();
//		WishProduct wishProduct = new WishProduct();
////		wishProduct.setSku("001");
//		wishProduct.setParentProductId("1");
//		wishProduct.setSpecialPrice("speprice");
//		CustomerWishListRequest CustomerWishListRequest = new CustomerWishListRequest();
//		CustomerWishListRequest.setStoreId(1);
//		CustomerWishListRequest.setCustomerId(1);
//		CustomerWishListRequest.setWishList(Arrays.asList(wishProduct));
//		ProductDetailsResponseV4DTO productDetailsResponseV4DTO = new ProductDetailsResponseV4DTO();
//		productDetailsResponseV4DTO.setSku("1");
//		productDetailsResponseV4DTO.setPrices(new PriceDetails());
//		when(wishlistHelper.retrieveProductDetailsFrmCurofy(any(), any(), any(), any(), any())).thenReturn(Arrays.asList(productDetailsResponseV4DTO));
//		when(client.getStoresArray()).thenReturn(storeList);
//		CustomerWishlistResponse reponse = customerV4ServiceImpl.saveUpdateV4OneWishList(CustomerWishListRequest,requestHeader,true);
//	}

	private void setStaticfields() {
		ReflectionTestUtils.setField(customerV4ServiceImpl, "jwtFlag", "1");
		ReflectionTestUtils.setField(customerV4ServiceImpl, "env", "dev");
		ReflectionTestUtils.setField(customerV4ServiceImpl, "consulIpAddress", "10.0.0.1");
		ReflectionTestUtils.setField(customerV4ServiceImpl, "saveCustomer", saveCustomer);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "elasticProductHelperV5", elasticProductHelperV5);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "addWishlist", addWishlist);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "saveAddress", saveAddress);
		ReflectionTestUtils.setField(saveAddress, "addressMapperHelperV2", addressMapperHelperV2);
		ReflectionTestUtils.setField(addressMapperHelperV2, "adrsmprBaseUrl", "someurl");

	}
}
