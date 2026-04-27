package org.styli.services.customer.service.impl.Address;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.address.response.CustomerAddreesResponse;
import org.styli.services.customer.pojo.address.response.CustomerAddressBody;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntity;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.repository.StaticComponents;
import org.styli.services.customer.service.Client;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GetAddressKsaCompliantTest {

    @Mock
    private CustomerEntityRepository customerEntityRepository;

    @Mock
    private CustomerAddressEntityRepository customerAddressEntityRepository;

    @Mock
    private StaticComponents staticComponents;

    @Mock
    private Client client;

    @InjectMocks
    private GetAddress getAddress;

    private Map<String, String> requestHeader;
    private CustomerEntity customerEntity;
    private List<CustomerAddressEntity> addressList;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(getAddress, "jwtFlag", "0");
        
        requestHeader = new HashMap<>();
        requestHeader.put("x-app-source", "mobile");
        requestHeader.put("x-app-version", "5.0.0");
        
        customerEntity = new CustomerEntity();
        customerEntity.setEntityId(1);
        customerEntity.setDefaultShipping(1);
        customerEntity.setJwtToken(1);
        customerEntity.setPhoneNumber("+966 123456789");
        customerEntity.setIsMobileVerified(true);
    }

    @Test
    public void testGetAddress_AllAddressesCompliant() {
        // Arrange
        CustomerAddressEntity address1 = createAddressEntity(1, "ABCD1234");
        CustomerAddressEntity address2 = createAddressEntity(2, "EFGH5678");
        
        addressList = Arrays.asList(address1, address2);
        
        when(customerEntityRepository.existsById(anyInt())).thenReturn(true);
        when(customerAddressEntityRepository.findAllByCustomerId(anyInt())).thenReturn(addressList);
        when(customerEntityRepository.findByEntityId(anyInt())).thenReturn(customerEntity);
        when(staticComponents.getAttrMap()).thenReturn(new HashMap<>());
        
        // Act
        CustomerAddreesResponse response = getAddress.get(1, customerEntityRepository, 
                customerAddressEntityRepository, staticComponents, requestHeader, "0", client);
        
        // Assert
        assertTrue(response.isStatus());
        assertEquals(response.getStatusCode(), "200");
        assertNotNull(response.getResponse());
        assertNotNull(response.getResponse().getAddresses());
        assertEquals(response.getResponse().getAddresses().size(), 2);
        
        // Check parent level ksaAddressCompliant (in responseBody)
        assertTrue(response.getResponse().getKsaAddressCompliant(), "All addresses have shortAddress, so parent should be true");
        
        // Check individual address ksaAddressCompliant
        for (CustomerAddrees address : response.getResponse().getAddresses()) {
            assertTrue(address.getKsaAddressCompliant(), "Address with shortAddress should be compliant");
            assertNotNull(address.getShortAddress());
        }
    }

    @Test
    public void testGetAddress_SomeAddressesNonCompliant() {
        // Arrange
        CustomerAddressEntity address1 = createAddressEntity(1, "ABCD1234");
        CustomerAddressEntity address2 = createAddressEntity(2, null); // No shortAddress
        
        addressList = Arrays.asList(address1, address2);
        
        when(customerEntityRepository.existsById(anyInt())).thenReturn(true);
        when(customerAddressEntityRepository.findAllByCustomerId(anyInt())).thenReturn(addressList);
        when(customerEntityRepository.findByEntityId(anyInt())).thenReturn(customerEntity);
        when(staticComponents.getAttrMap()).thenReturn(new HashMap<>());
        
        // Act
        CustomerAddreesResponse response = getAddress.get(1, customerEntityRepository, 
                customerAddressEntityRepository, staticComponents, requestHeader, "0", client);
        
        // Assert
        assertTrue(response.isStatus());
        assertEquals(response.getStatusCode(), "200");
        assertNotNull(response.getResponse());
        assertNotNull(response.getResponse().getAddresses());
        assertEquals(response.getResponse().getAddresses().size(), 2);
        
        // Check parent level ksaAddressCompliant - should be false because not all addresses are compliant
        assertFalse(response.getResponse().getKsaAddressCompliant(), "Not all addresses have shortAddress, so parent should be false");
        
        // Check individual address ksaAddressCompliant
        CustomerAddrees compliantAddress = response.getResponse().getAddresses().stream()
                .filter(a -> a.getShortAddress() != null).findFirst().orElse(null);
        assertNotNull(compliantAddress);
        assertTrue(compliantAddress.getKsaAddressCompliant(), "Address with shortAddress should be compliant");
        
        CustomerAddrees nonCompliantAddress = response.getResponse().getAddresses().stream()
                .filter(a -> a.getShortAddress() == null).findFirst().orElse(null);
        assertNotNull(nonCompliantAddress);
        assertFalse(nonCompliantAddress.getKsaAddressCompliant(), "Address without shortAddress should not be compliant");
    }

    @Test
    public void testGetAddress_NoAddressesCompliant() {
        // Arrange
        CustomerAddressEntity address1 = createAddressEntity(1, null);
        CustomerAddressEntity address2 = createAddressEntity(2, null);
        
        addressList = Arrays.asList(address1, address2);
        
        when(customerEntityRepository.existsById(anyInt())).thenReturn(true);
        when(customerAddressEntityRepository.findAllByCustomerId(anyInt())).thenReturn(addressList);
        when(customerEntityRepository.findByEntityId(anyInt())).thenReturn(customerEntity);
        when(staticComponents.getAttrMap()).thenReturn(new HashMap<>());
        
        // Act
        CustomerAddreesResponse response = getAddress.get(1, customerEntityRepository, 
                customerAddressEntityRepository, staticComponents, requestHeader, "0", client);
        
        // Assert
        assertTrue(response.isStatus());
        assertEquals(response.getStatusCode(), "200");
        assertNotNull(response.getResponse());
        assertNotNull(response.getResponse().getAddresses());
        assertEquals(response.getResponse().getAddresses().size(), 2);
        
        // Check parent level ksaAddressCompliant - should be false
        assertFalse(response.getResponse().getKsaAddressCompliant(), "No addresses have shortAddress, so parent should be false");
        
        // Check individual address ksaAddressCompliant - all should be false
        for (CustomerAddrees address : response.getResponse().getAddresses()) {
            assertFalse(address.getKsaAddressCompliant(), "Address without shortAddress should not be compliant");
        }
    }

    @Test
    public void testGetAddress_NoAddressesFound() {
        // Arrange
        addressList = new ArrayList<>();
        
        when(customerEntityRepository.existsById(anyInt())).thenReturn(true);
        when(customerAddressEntityRepository.findAllByCustomerId(anyInt())).thenReturn(addressList);
        when(customerEntityRepository.findByEntityId(anyInt())).thenReturn(customerEntity);
        
        // Act
        CustomerAddreesResponse response = getAddress.get(1, customerEntityRepository, 
                customerAddressEntityRepository, staticComponents, requestHeader, "0", client);
        
        // Assert
        assertTrue(response.isStatus());
        assertEquals(response.getStatusCode(), "201");
        assertEquals(response.getStatusMsg(), "No Address Found");
        
        // Check parent level ksaAddressCompliant - should be false when no addresses
        // When no addresses found, responseBody might not be set, so check if it exists
        if (response.getResponse() != null) {
            assertFalse(response.getResponse().getKsaAddressCompliant(), "No addresses found, so should be false");
        }
    }

    @Test
    public void testGetAddressById_Compliant() {
        // Arrange
        CustomerAddressEntity addressEntity = createAddressEntity(1, "ABCD1234");
        
        when(customerEntityRepository.findByEntityId(anyInt())).thenReturn(customerEntity);
        when(customerAddressEntityRepository.findByEntityId(anyInt())).thenReturn(addressEntity);
        when(staticComponents.getAttrMap()).thenReturn(new HashMap<>());
        
        // Act
        CustomerAddreesResponse response = getAddress.getById(1, 1, customerEntityRepository, 
                customerAddressEntityRepository, staticComponents);
        
        // Assert
        assertTrue(response.isStatus());
        assertEquals(response.getStatusCode(), "200");
        assertNotNull(response.getResponse());
        assertNotNull(response.getResponse().getAddress());
        
        // Check parent level ksaAddressCompliant (in responseBody)
        assertTrue(response.getResponse().getKsaAddressCompliant(), "Address has shortAddress, so should be true");
        
        // Check individual address ksaAddressCompliant
        assertTrue(response.getResponse().getAddress().getKsaAddressCompliant(), 
                "Address with shortAddress should be compliant");
    }

    @Test
    public void testGetAddressById_NonCompliant() {
        // Arrange
        CustomerAddressEntity addressEntity = createAddressEntity(1, null);
        
        when(customerEntityRepository.findByEntityId(anyInt())).thenReturn(customerEntity);
        when(customerAddressEntityRepository.findByEntityId(anyInt())).thenReturn(addressEntity);
        when(staticComponents.getAttrMap()).thenReturn(new HashMap<>());
        
        // Act
        CustomerAddreesResponse response = getAddress.getById(1, 1, customerEntityRepository, 
                customerAddressEntityRepository, staticComponents);
        
        // Assert
        assertTrue(response.isStatus());
        assertEquals(response.getStatusCode(), "200");
        assertNotNull(response.getResponse());
        assertNotNull(response.getResponse().getAddress());
        
        // Check parent level ksaAddressCompliant (in responseBody)
        assertFalse(response.getResponse().getKsaAddressCompliant(), "Address has no shortAddress, so should be false");
        
        // Check individual address ksaAddressCompliant
        assertFalse(response.getResponse().getAddress().getKsaAddressCompliant(), 
                "Address without shortAddress should not be compliant");
    }

    private CustomerAddressEntity createAddressEntity(Integer entityId, String shortAddress) {
        CustomerAddressEntity address = new CustomerAddressEntity();
        address.setEntityId(entityId);
        address.setParentId(1);
        address.setIsActive(1);
        address.setFirstname("John");
        address.setLastName("Doe");
        address.setCountryId("SA");
        address.setCity("Riyadh");
        address.setRegion("Riyadh");
        address.setTelephone("+966 123456789");
        address.setShortAddress(shortAddress);
        address.setCustomerAddressEntityVarchar(new HashSet<>());
        return address;
    }
}

