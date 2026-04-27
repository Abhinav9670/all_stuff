package org.styli.services.customer.utility.helper;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.mockito.MockedStatic;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class AddressNudgeUtilityTest {

    private CustomerEntity ksaUser;
    private CustomerEntity nonKsaUser;
    private CustomerAddrees addressWithShortAddress;
    private CustomerAddrees addressWithoutShortAddress;

    @BeforeMethod
    public void setUp() {
        // Setup KSA user (storeId 1)
        ksaUser = new CustomerEntity();
        ksaUser.setEntityId(1);
        ksaUser.setStoreId(1);

        // Setup non-KSA user (storeId 7 - UAE)
        nonKsaUser = new CustomerEntity();
        nonKsaUser.setEntityId(2);
        nonKsaUser.setStoreId(7);

        // Setup address with shortAddress
        addressWithShortAddress = new CustomerAddrees();
        addressWithShortAddress.setShortAddress("ABCD1234");
        addressWithShortAddress.setAddressId(1);

        // Setup address without shortAddress
        addressWithoutShortAddress = new CustomerAddrees();
        addressWithoutShortAddress.setShortAddress(null);
        addressWithoutShortAddress.setAddressId(2);
    }

    private void setupKSAStoreIdsMock(MockedStatic<ServiceConfigs> mockedServiceConfigs, 
                                       MockedStatic<Constants> mockedConstants) {
        mockedServiceConfigs.when(ServiceConfigs::isAddressNudgeEnabled).thenReturn(true);
        List<Integer> ksaStoreIds = new ArrayList<>();
        ksaStoreIds.add(1);
        ksaStoreIds.add(3);
        mockedConstants.when(() -> Constants.getStoreIdsByCountryCode("966")).thenReturn(ksaStoreIds);
    }

    @Test
    public void testShouldShowNudge_WhenNudgeDisabled_ReturnsFalse() {
        try (MockedStatic<ServiceConfigs> mockedServiceConfigs = mockStatic(ServiceConfigs.class)) {
            mockedServiceConfigs.when(ServiceConfigs::isAddressNudgeEnabled).thenReturn(false);

            boolean result = AddressNudgeUtility.shouldShowNudge(ksaUser, addressWithoutShortAddress, 5);

            assertFalse(result);
        }
    }

    @Test
    public void testShouldShowNudge_WhenUserIsNull_ReturnsFalse() {
        try (MockedStatic<ServiceConfigs> mockedServiceConfigs = mockStatic(ServiceConfigs.class)) {
            mockedServiceConfigs.when(ServiceConfigs::isAddressNudgeEnabled).thenReturn(true);

            boolean result = AddressNudgeUtility.shouldShowNudge(null, addressWithoutShortAddress, 5);

            assertFalse(result);
        }
    }

    @Test
    public void testShouldShowNudge_WhenUserIsNotKSA_ReturnsFalse() {
        try (MockedStatic<ServiceConfigs> mockedServiceConfigs = mockStatic(ServiceConfigs.class);
             MockedStatic<Constants> mockedConstants = mockStatic(Constants.class)) {
            mockedServiceConfigs.when(ServiceConfigs::isAddressNudgeEnabled).thenReturn(true);
            mockedConstants.when(() -> Constants.getStoreIdsByCountryCode("966")).thenReturn(null);

            boolean result = AddressNudgeUtility.shouldShowNudge(nonKsaUser, addressWithoutShortAddress, 5);

            assertFalse(result);
        }
    }

    @Test
    public void testShouldShowNudge_WhenAddressIsNull_ReturnsFalse() {
        try (MockedStatic<ServiceConfigs> mockedServiceConfigs = mockStatic(ServiceConfigs.class);
             MockedStatic<Constants> mockedConstants = mockStatic(Constants.class)) {
            setupKSAStoreIdsMock(mockedServiceConfigs, mockedConstants);

            boolean result = AddressNudgeUtility.shouldShowNudge(ksaUser, null, 5);

            assertFalse(result);
        }
    }

    @Test
    public void testShouldShowNudge_WhenShortAddressExists_ReturnsFalse() {
        try (MockedStatic<ServiceConfigs> mockedServiceConfigs = mockStatic(ServiceConfigs.class);
             MockedStatic<Constants> mockedConstants = mockStatic(Constants.class)) {
            setupKSAStoreIdsMock(mockedServiceConfigs, mockedConstants);

            boolean result = AddressNudgeUtility.shouldShowNudge(ksaUser, addressWithShortAddress, 5);

            assertFalse(result);
        }
    }

    @Test
    public void testShouldShowNudge_WhenLastSeenDateIsNull_ReturnsTrue() {
        try (MockedStatic<ServiceConfigs> mockedServiceConfigs = mockStatic(ServiceConfigs.class);
             MockedStatic<Constants> mockedConstants = mockStatic(Constants.class)) {
            setupKSAStoreIdsMock(mockedServiceConfigs, mockedConstants);

            boolean result = AddressNudgeUtility.shouldShowNudge(ksaUser, addressWithoutShortAddress, 5);

            assertTrue(result);
        }
    }

    @Test
    public void testShouldShowNudge_WhenFrequencyDaysPassed_ReturnsTrue() {
        try (MockedStatic<ServiceConfigs> mockedServiceConfigs = mockStatic(ServiceConfigs.class);
             MockedStatic<Constants> mockedConstants = mockStatic(Constants.class)) {
            setupKSAStoreIdsMock(mockedServiceConfigs, mockedConstants);

            // Last seen 6 days ago, frequency is 5 days
            boolean result = AddressNudgeUtility.shouldShowNudge(ksaUser, addressWithoutShortAddress, 5);

            assertTrue(result);
        }
    }

    @Test
    public void testShouldShowNudge_WhenFrequencyDaysNotPassed_ReturnsFalse() {
        try (MockedStatic<ServiceConfigs> mockedServiceConfigs = mockStatic(ServiceConfigs.class);
             MockedStatic<Constants> mockedConstants = mockStatic(Constants.class)) {
            setupKSAStoreIdsMock(mockedServiceConfigs, mockedConstants);

            // Last seen 3 days ago, frequency is 5 days
            boolean result = AddressNudgeUtility.shouldShowNudge(ksaUser, addressWithoutShortAddress, 5);

            assertFalse(result);
        }
    }

    @Test
    public void testUpdateAddressComplianceStatus_WhenCustomerIsNull_ReturnsFalse() {
        org.styli.services.customer.service.Client mockClient = mock(org.styli.services.customer.service.Client.class);
        List<org.styli.services.customer.pojo.address.response.CustomerAddressEntity> addresses = new ArrayList<>();
        
        boolean result = AddressNudgeUtility.updateAddressComplianceStatus(null, addresses, mockClient);
        
        assertFalse(result);
        verify(mockClient, never()).saveAndFlushCustomerEntity(any());
    }

    @Test
    public void testUpdateAddressComplianceStatus_WhenNoAddresses_SetsComplianceToFalse() {
        CustomerEntity customer = new CustomerEntity();
        customer.setEntityId(1);
        customer.setIsAddressCompliance(true); // Initially compliant
        
        org.styli.services.customer.service.Client mockClient = mock(org.styli.services.customer.service.Client.class);
        List<org.styli.services.customer.pojo.address.response.CustomerAddressEntity> addresses = new ArrayList<>();
        
        boolean result = AddressNudgeUtility.updateAddressComplianceStatus(customer, addresses, mockClient);
        
        assertFalse(result);
        assertFalse(customer.getIsAddressCompliance());
        verify(mockClient).saveAndFlushCustomerEntity(customer);
    }

    @Test
    public void testUpdateAddressComplianceStatus_WhenAllAddressesCompliant_SetsComplianceToTrue() {
        CustomerEntity customer = new CustomerEntity();
        customer.setEntityId(1);
        customer.setIsAddressCompliance(false); // Initially non-compliant
        
        org.styli.services.customer.service.Client mockClient = mock(org.styli.services.customer.service.Client.class);
        
        org.styli.services.customer.pojo.address.response.CustomerAddressEntity address1 = 
                mock(org.styli.services.customer.pojo.address.response.CustomerAddressEntity.class);
        when(address1.getIsActive()).thenReturn(1);
        when(address1.getShortAddress()).thenReturn("ABCD1234");
        
        org.styli.services.customer.pojo.address.response.CustomerAddressEntity address2 = 
                mock(org.styli.services.customer.pojo.address.response.CustomerAddressEntity.class);
        when(address2.getIsActive()).thenReturn(1);
        when(address2.getShortAddress()).thenReturn("EFGH5678");
        
        List<org.styli.services.customer.pojo.address.response.CustomerAddressEntity> addresses = new ArrayList<>();
        addresses.add(address1);
        addresses.add(address2);
        
        boolean result = AddressNudgeUtility.updateAddressComplianceStatus(customer, addresses, mockClient);
        
        assertTrue(result);
        assertTrue(customer.getIsAddressCompliance());
        verify(mockClient).saveAndFlushCustomerEntity(customer);
    }

    @Test
    public void testUpdateAddressComplianceStatus_WhenOneAddressNonCompliant_SetsComplianceToFalse() {
        CustomerEntity customer = new CustomerEntity();
        customer.setEntityId(1);
        customer.setIsAddressCompliance(true); // Initially compliant
        
        org.styli.services.customer.service.Client mockClient = mock(org.styli.services.customer.service.Client.class);
        
        org.styli.services.customer.pojo.address.response.CustomerAddressEntity address1 = 
                mock(org.styli.services.customer.pojo.address.response.CustomerAddressEntity.class);
        when(address1.getIsActive()).thenReturn(1);
        when(address1.getShortAddress()).thenReturn("ABCD1234");
        
        org.styli.services.customer.pojo.address.response.CustomerAddressEntity address2 = 
                mock(org.styli.services.customer.pojo.address.response.CustomerAddressEntity.class);
        when(address2.getIsActive()).thenReturn(1);
        when(address2.getShortAddress()).thenReturn(null); // Non-compliant
        
        List<org.styli.services.customer.pojo.address.response.CustomerAddressEntity> addresses = new ArrayList<>();
        addresses.add(address1);
        addresses.add(address2);
        
        boolean result = AddressNudgeUtility.updateAddressComplianceStatus(customer, addresses, mockClient);
        
        assertFalse(result);
        assertFalse(customer.getIsAddressCompliance());
        verify(mockClient).saveAndFlushCustomerEntity(customer);
    }

    @Test
    public void testUpdateAddressComplianceStatus_WhenInactiveAddressesIgnored() {
        CustomerEntity customer = new CustomerEntity();
        customer.setEntityId(1);
        customer.setIsAddressCompliance(false);
        
        org.styli.services.customer.service.Client mockClient = mock(org.styli.services.customer.service.Client.class);
        
        org.styli.services.customer.pojo.address.response.CustomerAddressEntity activeAddress = 
                mock(org.styli.services.customer.pojo.address.response.CustomerAddressEntity.class);
        when(activeAddress.getIsActive()).thenReturn(1);
        when(activeAddress.getShortAddress()).thenReturn("ABCD1234");
        
        org.styli.services.customer.pojo.address.response.CustomerAddressEntity inactiveAddress = 
                mock(org.styli.services.customer.pojo.address.response.CustomerAddressEntity.class);
        when(inactiveAddress.getIsActive()).thenReturn(0); // Inactive
        when(inactiveAddress.getShortAddress()).thenReturn(null); // Missing shortAddress but inactive
        
        List<org.styli.services.customer.pojo.address.response.CustomerAddressEntity> addresses = new ArrayList<>();
        addresses.add(activeAddress);
        addresses.add(inactiveAddress);
        
        boolean result = AddressNudgeUtility.updateAddressComplianceStatus(customer, addresses, mockClient);
        
        // Should be compliant because only active address has shortAddress
        assertTrue(result);
        assertTrue(customer.getIsAddressCompliance());
        verify(mockClient).saveAndFlushCustomerEntity(customer);
    }

    @Test
    public void testUpdateAddressComplianceStatus_WhenClientIsNull_StillUpdatesFlag() {
        CustomerEntity customer = new CustomerEntity();
        customer.setEntityId(1);
        customer.setIsAddressCompliance(false);
        
        org.styli.services.customer.pojo.address.response.CustomerAddressEntity address = 
                mock(org.styli.services.customer.pojo.address.response.CustomerAddressEntity.class);
        when(address.getIsActive()).thenReturn(1);
        when(address.getShortAddress()).thenReturn("ABCD1234");
        
        List<org.styli.services.customer.pojo.address.response.CustomerAddressEntity> addresses = new ArrayList<>();
        addresses.add(address);
        
        boolean result = AddressNudgeUtility.updateAddressComplianceStatus(customer, addresses, null);
        
        assertTrue(result);
        assertTrue(customer.getIsAddressCompliance());
        // Should not throw exception even when client is null
    }
}

