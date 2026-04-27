package org.styli.services.customer.service.impl;

import com.google.gson.Gson;
import org.json.JSONObject;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.epsilon.request.ShukranEnrollmentRequest;
import org.styli.services.customer.pojo.registration.request.CustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.redis.GlobalRedisHelper;
import org.styli.services.customer.redis.RedisHelper;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeTest;

@SpringBootTest(classes = { org.styli.services.customer.service.impl.ExternalServiceAdapterImplTest.class })
public class ExternalServiceAdapterImplTest extends AbstractTestNGSpringContextTests {
    @InjectMocks
    private ExternalServiceAdapterImpl externalServiceAdapterImpl;

    @Mock
    GlobalRedisHelper globalRedisHelper;

    @Mock
    private RestTemplate restTemplate;

    @BeforeTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getEpsilonProfile() throws Exception{
        when(restTemplate.exchange(
                any(),
                eq(String.class)))
                .thenReturn(mockGetEpsilonProfile());
        ResponseEntity<String> getProfileEntity = externalServiceAdapterImpl.getEpsilonProfile("8972167898",1);
        assertTrue(getProfileEntity.getStatusCode().is2xxSuccessful());
        assertNotNull(new JSONObject(getProfileEntity.getBody()));
    }

    private ResponseEntity<String> mockGetEpsilonProfile() throws IOException {
        String responseData = new String(
                Files.readAllBytes(Paths.get("src/test/resources/epsilon_get_response.json")));
        return new ResponseEntity<String>(responseData, HttpStatus.OK);
    }

    @Test
    public void updateEpsilonProfile() throws Exception {
        when(restTemplate.exchange(
                any(),
                eq(String.class)))
                .thenReturn(mockUpdateEpsilonProfile());
        ResponseEntity<String> updateProfileEntity = externalServiceAdapterImpl.updateEpsilonProfile(mockCustomerUpdateProfileRequest(),"e11a629b-eec1-400f-ad08-6026c69a5d2d");
        assertTrue(updateProfileEntity.getStatusCode().is2xxSuccessful());
        assertNotNull(new JSONObject(updateProfileEntity.getBody()));

    }
    @Test
    public void createShukranAccountTest() throws Exception {
        ShukranEnrollmentRequest mockShukranEnrollmentRequest = ShukranEnrollmentRequest.builder()
                .customerId(522212).storeId(1)
                .customerEmail("test.100@mailinator.com").build();
        when(restTemplate.exchange(
                any(),
                eq(String.class)))
                .thenReturn(mockCreateShukranAccount());
       ResponseEntity<String> createShukranResponseEntity = externalServiceAdapterImpl.createShukranAccount(mockShukranEnrollmentRequest,mockCustomer());
        assertTrue(createShukranResponseEntity.getStatusCode().is2xxSuccessful());
    }

    private Customer mockCustomer() throws IOException {
        Customer customer = new Customer();
        customer.setCustomerId(522212);
        customer.setMobileNumber("+965 2636363666");
        customer.setEmail("test@gmail.com");
        customer.setStoreId(1);
        customer.setDob(new Date());
        return customer;
    }

    private ResponseEntity<String> mockCreateShukranAccount() throws IOException {
        String response =new String(
                Files.readAllBytes(Paths.get("src/test/resources/enrollment_api_request.json")));
        return new ResponseEntity<>(response,HttpStatus.OK);
    }

    @Test
    public void linkShukranAccount() throws Exception {
        when(restTemplate.exchange(
                any(),
                eq(String.class)))
                .thenReturn(mockUpdateEpsilonProfile());
        ResponseEntity<String> linkShukranResponseEntity = externalServiceAdapterImpl.linkShukranAccount("e7114135-4c55-4af6-bd91-fddf768a4df8");
        assertTrue(linkShukranResponseEntity.getStatusCode().is2xxSuccessful());
        assertNotNull(new JSONObject(linkShukranResponseEntity.getBody()));

    }

    private CustomerUpdateProfileRequest mockCustomerUpdateProfileRequest() {
        CustomerUpdateProfileRequest customerInfoRequestMock = new CustomerUpdateProfileRequest();
        customerInfoRequestMock.setFirstName("karthik");
        customerInfoRequestMock.setLastName("v");
        customerInfoRequestMock.setMobileNumber("8972167898");
        customerInfoRequestMock.setGender(1);
        return customerInfoRequestMock;
    }

    private ResponseEntity<String> mockUpdateEpsilonProfile() throws IOException {
        String responseData = new String(
                Files.readAllBytes(Paths.get("src/test/resources/epsilon_update_response.json")));
        return new ResponseEntity<String>(responseData, HttpStatus.OK);
    }
}