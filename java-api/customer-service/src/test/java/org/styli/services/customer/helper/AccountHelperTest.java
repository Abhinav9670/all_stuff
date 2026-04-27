package org.styli.services.customer.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.styli.services.customer.model.CustomerGridFlat;
import org.styli.services.customer.model.DeleteCustomersEventsEntity;
import org.styli.services.customer.pojo.DeleteCustomerEntity;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.consul.DeleteCustomer;
import org.styli.services.customer.pojo.consul.Email;
import org.styli.services.customer.pojo.consul.Message;
import org.styli.services.customer.pojo.otp.OtpBucketObject;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerGridFlatRepository;
import org.styli.services.customer.repository.Customer.DeleteCustomerEntityRepository;
import org.styli.services.customer.repository.Customer.DeleteCustomersEventsRepository;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

public class AccountHelperTest {

	@Mock
	RedisHelper redisHelperMock;

	@Mock
	DeleteCustomerEntityRepository deleteCustomerEntityRepositoryMock;

	@Mock
	CustomerGridFlatRepository customerGridFlatRepositoryMock;

	@Mock
	CustomerAddressEntityRepository customerAddressEntityRepositoryMock;

	@Mock
	CustomerEntityRepository customerEntityRepositoryMock;

	@Mock
	DeleteCustomersEventsRepository deleteCustomersEventsRepositoryMock;

	@Mock
	private KafkaTemplate<String, Object> kafkaTemplateMock;

	@Mock
	private SendResult<String, Object> sendResultMock;

	@Mock
	private OtpBucketObject otpBucketObjectMock;

	@Mock
	private Stores storesMock;

	@Value("${customer.kafka.customer_delete_topic}")
	private String customerDeleteTopic;

	@Value("${env}")
	private String env;

	@InjectMocks
	ServiceConfigs config;

	@Spy
	private PhoneNumberUtil phoneNumberUtilSpy = PhoneNumberUtil.getInstance();

	@InjectMocks
	AccountHelper accountHelper;

	private DeleteCustomerEntity customerEntity;

	private DeleteCustomerEntity customerEntity1;

	private List<DeleteCustomerEntity> list;
	@Mock
	private DeleteCustomerEntityRepository deleteCustomerEntityRepository;
	@Mock
	private CustomerGridFlatRepository customerGridFlatRepository;
	@Mock
	private CustomerEntityRepository customerEntityRepository;
	@Mock
	private DeleteCustomersEventsRepository deleteCustomersEventsRepository;

	@BeforeClass
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	@BeforeMethod
	public void beforeMethod() {
		customerEntity = new DeleteCustomerEntity();
		customerEntity.setEntityId(1);
		customerEntity.setCustomerId(1);
		customerEntity1 = new DeleteCustomerEntity();
		customerEntity1.setEntityId(2);
		customerEntity.setCustomerId(2);
		list = new ArrayList<>();
		list.add(customerEntity);
		list.add(customerEntity1);

	}

	@Test
	public void testGetBucketObject() {
		String deleteCustomerOtpCacheName = "testCache";
		String customerId = "123";
		when(redisHelperMock.get(ArgumentMatchers.any(), any(), ArgumentMatchers.any(Class.class)))
				.thenReturn(otpBucketObjectMock);
		OtpBucketObject otpBucketObject = accountHelper.getBucketObject(deleteCustomerOtpCacheName, customerId);
		Assert.assertEquals(otpBucketObjectMock, otpBucketObject);
	}

	@Test
	public void testGetBucketObjectWithNullCustomerId() {
		String deleteCustomerOtpCacheName = "testCache";
		String customerId = null;
		OtpBucketObject otpBucketObject = accountHelper.getBucketObject(deleteCustomerOtpCacheName, customerId);
		Assert.assertNull(otpBucketObject);
	}

	@Test
	public void testGenerateSafeOtp() {
		String otp = "123456";
		long now = 123456;
		Map<String, Object> newConfigs = new LinkedHashMap<>();
		Map<String, Object> map = new LinkedHashMap<>();
		Map<String, String> lang = new LinkedHashMap<>();
		lang.put("en", "default message");
		map.put("DEFAULTMSG", lang);
		newConfigs.put("forceResetOtp", map);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);
//        when(ServiceConfigs.forceResetOtp()).thenReturn(false);
		when(otpBucketObjectMock.getOtp()).thenReturn(otp);
		when(otpBucketObjectMock.getExpiresAt()).thenReturn(null);
		String generatedOtp = accountHelper.generateSafeOtp(otpBucketObjectMock, now);
		Assert.assertEquals(otp, generatedOtp);
	}

	@Test
	public void testGenerateSafeOtpWithNullOtpBucketObject() {
		long now = 123456;
		String generatedOtp = accountHelper.generateSafeOtp(null, now);
		Assert.assertNotNull(generatedOtp);
		Assert.assertEquals(generatedOtp.length(), ServiceConfigs.getOtpLength());
	}

	@Test
	public void testGetOtpMessage() {
		String otp = "123456";
		long now = 123456;
		Stores stores = new Stores();
		stores.setStoreId("1");
		Message msg = new Message();
		msg.setEn("default message");
		msg.setAr("default message");
		DeleteCustomer dm = new DeleteCustomer();
		dm.setOtpMessage(msg);
		Map<String, Object> newConfigs = new LinkedHashMap<>();

		newConfigs.put("deleteCustomer", dm);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);
		when(otpBucketObjectMock.getOtp()).thenReturn(otp);
		when(otpBucketObjectMock.getExpiresAt()).thenReturn(null);
		String otpMsg = accountHelper.getOtpMessage("en");
		String arOtpMsg = accountHelper.getOtpMessage("ar");
		Assert.assertEquals(otpMsg, msg.getEn());
	}

	@Test
	public void testGetEmailSubject() {
		String otp = "123456";
		long now = 123456;
		Stores stores = new Stores();
		stores.setStoreId("1");
		Message msg = new Message();
		msg.setEn("default message");
		Email mail = new Email();
		mail.setContent(msg);
		mail.setSubject(msg);
		DeleteCustomer dm = new DeleteCustomer();
		dm.setOtpMessage(msg);
		dm.setEmail(mail);
		Map<String, Object> newConfigs = new LinkedHashMap<>();

		newConfigs.put("deleteCustomer", dm);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);
		when(otpBucketObjectMock.getOtp()).thenReturn(otp);
		when(otpBucketObjectMock.getExpiresAt()).thenReturn(null);
		String otpMsg = accountHelper.getEmailSubject("en");
		String subMsg = accountHelper.getEmailMessage("en");
		Assert.assertEquals(otpMsg, msg.getEn());
		Assert.assertEquals(subMsg, msg.getEn());
	}

	@Test
	public void testArabicGetEmailSubject() {
		String otp = "123456";
		long now = 123456;
		Stores stores = new Stores();
		stores.setStoreId("1");
		Message msg = new Message();
		msg.setAr("default message");
		Email mail = new Email();
		mail.setContent(msg);
		mail.setSubject(msg);
		DeleteCustomer dm = new DeleteCustomer();
		dm.setOtpMessage(msg);
		dm.setEmail(mail);
		Map<String, Object> newConfigs = new LinkedHashMap<>();

		newConfigs.put("deleteCustomer", dm);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);
		when(otpBucketObjectMock.getOtp()).thenReturn(otp);
		when(otpBucketObjectMock.getExpiresAt()).thenReturn(null);
		String otpMsg = accountHelper.getEmailSubject("ar");
		String subMsg = accountHelper.getEmailMessage("ar");
		Assert.assertEquals(otpMsg, msg.getAr());
		Assert.assertEquals(subMsg, msg.getAr());
	}

	@Test
    void getDeleteRequestsForCleanupTest() {
    	when(deleteCustomerEntityRepositoryMock.findAllByCronProcessedAndCompletedAt(anyInt(),any())).thenReturn(list);
    	 List<DeleteCustomerEntity> subMsg = accountHelper.getDeleteRequestsForCleanup();
         Assert.assertNotNull(subMsg);
    }

	@Test
    void getDeleteRequestsForCleanupexpTest() {
    	when(deleteCustomerEntityRepositoryMock.findAllByCronProcessedAndCompletedAt(anyInt(),any())).thenThrow(new RuntimeException("Error saving address"));
    	 List<DeleteCustomerEntity> subMsg = accountHelper.getDeleteRequestsForCleanup();
         Assert.assertEquals(subMsg.isEmpty(),true);
    }

	@Test
    void getDeleteRequestsTest() {
    	when(deleteCustomerEntityRepository.findAllByTtlTimeLessThanAndMarkedForDeleteAndCronProcessedNot(any(),anyInt(),anyInt())).thenReturn(list);
    	 List<DeleteCustomerEntity> subMsg = accountHelper.getDeleteRequests();
         Assert.assertNotNull(subMsg);
         Assert.assertEquals(subMsg.isEmpty(),false);
    }

	@Test
    void getDeleteRequestsexpTest() {
    	when(deleteCustomerEntityRepository.findAllByTtlTimeLessThanAndMarkedForDeleteAndCronProcessedNot(any(),anyInt(),anyInt())).thenThrow(new RuntimeException("Error saving address"));
    	 List<DeleteCustomerEntity> subMsg = accountHelper.getDeleteRequests();
         Assert.assertEquals(subMsg.isEmpty(),true);
    }

	@Test
	void handleCustomerGridFlatTest() {
		CustomerGridFlat customerGridFlat = new CustomerGridFlat();
		CustomerEntity customer = new CustomerEntity();
		customer.setEntityId(1);
		customerGridFlat.setId(1);
		when(customerGridFlatRepository.findByEntityId(anyInt())).thenReturn(customerGridFlat);
		accountHelper.handleCustomerGridFlat("test@mailinator.com", customer);
		verify(customerGridFlatRepository, times(1)).findByEntityId(anyInt());
	}

	@Test
	void handleCustomerAddressEntityTest() {
		CustomerEntity customer = new CustomerEntity();
		customer.setEntityId(1);
		accountHelper.handleCustomerAddressEntity(customer);
		verify(customerAddressEntityRepositoryMock, times(1)).deleteByParentId(anyInt());
	}

	@Test
	void handleCustomerEntityTest() {
		CustomerEntity customer = new CustomerEntity();
		customer.setEntityId(1);
		accountHelper.handleCustomerEntity("test@mailinator.com", customer);
		verify(customerEntityRepository, times(1)).save(any());
	}

	@Test
	void handleKafkaPushTest() {
		CustomerEntity customer = new CustomerEntity();
		customer.setEntityId(1);
		accountHelper.handleKafkaPush(customer);
		verify(kafkaTemplateMock, times(1)).send(any(), any());
	}

	@Test
	void handleDeleteCustomerEventsRowsTest() {
		CustomerEntity customer = new CustomerEntity();
		customer.setEntityId(1);
		Message msg = new Message();
		msg.setEn("default message");
		DeleteCustomer dm = new DeleteCustomer();
		dm.setOtpMessage(msg);
		List<String> liststr = new ArrayList<>();
		liststr.add("task1");
		liststr.add("task2");
		dm.setTasks(liststr);
		Map<String, Object> newConfigs = new LinkedHashMap<>();

		newConfigs.put("deleteCustomer", dm);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);
		when(deleteCustomersEventsRepository.saveAndFlush(any())).thenReturn(new DeleteCustomersEventsEntity());
		accountHelper.handleDeleteCustomerEventsRows(customer);
		verify(deleteCustomersEventsRepository, times(2)).saveAndFlush(any());
	}
//    @Test
//    void handleDeleteCustomerEntityTest() {
//    	CustomerEntity customer = new CustomerEntity();
//    	customer.setEntityId(1);
//    	
//    	accountHelper.handleDeleteCustomerEntity(list,customer);
//    	verify(deleteCustomerEntityRepository, times(1)).saveAndFlush(any());
//    }

}
