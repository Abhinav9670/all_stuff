package org.styli.services.order.helper;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.pojo.request.Order.OmsOrderListRequest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { TamaraHelperTest.class })
public class TamaraHelperTest extends AbstractTestNGSpringContextTests {
	org.styli.services.order.model.Customer.CustomerEntity customerEntity;
	Map<String, String> requestHeader;
	private List<Stores> storeList;
	SalesOrder salesOrder;
	OmsOrderListRequest request;

	@BeforeClass
	public void beforeClass() {

		requestHeader = new HashMap<>();
		requestHeader.put("x-source", "android");
		requestHeader.put("x-client-version", "11");
		requestHeader.put("token", "token");
		requestHeader.put("X-Header-Token", "test@mail.com");
		try {
			// Prepare mock Data
			Gson g = new Gson();
			String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/store_list.json")));
			Type listType = new TypeToken<ArrayList<Stores>>() {
			}.getType();
			storeList = g.fromJson(storeData, listType);
		} catch (Exception e) {
		}

	}

	@BeforeTest
	public void beforeTest() {
		System.out.println("Initialise @BeforeTest ");
		MockitoAnnotations.initMocks(this);

		customerEntity = new org.styli.services.order.model.Customer.CustomerEntity();
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
		customerEntity.setPhoneNumber("8989891111");
//		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "token,dwd,1,2");
//		ReflectionTestUtils.setField(configService, "externalAuthBearerToken", "token,ds,1,1");
	}

	@Test
	public void retrievePaymentTTest() {
	}
}
