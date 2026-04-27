package org.styli.services.customer.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.service.Client;
import org.springframework.dao.DataAccessException;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GetCustomerTest {

    @Mock
    private Client client;

    @InjectMocks
    private GetCustomer getCustomer;

    private CustomerEntity customerEntity;
    private Integer customerId;

    @BeforeEach
    void setUp() {
        customerId = 123;
        customerEntity = new CustomerEntity();
        customerEntity.setEntityId(customerId);
        customerEntity.setPhoneNumber("+966411055731");
        customerEntity.setIsMobileNumberRemoved(false);
    }

    @Test
    public void testRemovePhoneNumberFromShukranAccount_Success() {
        // Given
        when(client.findByEntityId(customerId)).thenReturn(customerEntity);
        when(client.saveAndFlushMongoCustomerDocument(any(CustomerEntity.class))).thenReturn(customerEntity);
        // When
        getCustomer.removePhoneNumberFromShukranAccount(customerId, client);
        // Then
        verify(client).findByEntityId(customerId);
        verify(client).saveAndFlushMongoCustomerDocument(customerEntity);
        
        // Verify that phone number is cleared and flag is set
        assert customerEntity.getPhoneNumber() == null;
        assert customerEntity.getIsMobileNumberRemoved() == true;
        assert customerEntity.getUpdatedAt() != null;
    }

    @Test
    public void testRemovePhoneNumberFromShukranAccount_CustomerNotFound() {
        // Given
        when(client.findByEntityId(customerId)).thenReturn(null);

        // When
        getCustomer.removePhoneNumberFromShukranAccount(customerId, client);

        // Then
        verify(client).findByEntityId(customerId);
        verify(client, never()).saveAndFlushCustomerEntity(any(CustomerEntity.class));
    }

    @Test
    public void testRemovePhoneNumberFromShukranAccount_NullCustomerId() {
        // Given
        Integer nullCustomerId = null;

        // When
        getCustomer.removePhoneNumberFromShukranAccount(nullCustomerId, client);

        // Then
        verify(client, never()).findByEntityId(anyInt());
        verify(client, never()).saveAndFlushCustomerEntity(any(CustomerEntity.class));
    }

    @Test
    public void testRemovePhoneNumberFromShukranAccount_DataAccessException() {
        // Given
        when(client.findByEntityId(customerId)).thenReturn(customerEntity);
        when(client.saveAndFlushCustomerEntity(any(CustomerEntity.class)))
                .thenThrow(new DataAccessException("Database error") {});

        // When
        getCustomer.removePhoneNumberFromShukranAccount(customerId, client);

        // Then
        verify(client).findByEntityId(customerId);
        verify(client).saveAndFlushCustomerEntity(customerEntity);
        
        // Verify that phone number is still cleared and flag is set even if save fails
        assert customerEntity.getPhoneNumber() == null;
        assert customerEntity.getIsMobileNumberRemoved() == true;
        assert customerEntity.getUpdatedAt() != null;
    }

    @Test
    public void testRemovePhoneNumberFromShukranAccount_VerifyPhoneNumberCleared() {
        // Given
        String originalPhoneNumber = "+966411055731";
        customerEntity.setPhoneNumber(originalPhoneNumber);
        when(client.findByEntityId(customerId)).thenReturn(customerEntity);
        when(client.saveAndFlushCustomerEntity(any(CustomerEntity.class))).thenReturn(customerEntity);

        // When
        getCustomer.removePhoneNumberFromShukranAccount(customerId, client);

        // Then
        verify(client).findByEntityId(customerId);
        verify(client).saveAndFlushCustomerEntity(customerEntity);
        
        // Verify phone number is cleared
        assert customerEntity.getPhoneNumber() == null : "Phone number should be cleared";
        assert customerEntity.getIsMobileNumberRemoved() == true : "isMobileNumberRemoved flag should be set to true";
        assert customerEntity.getUpdatedAt() != null : "UpdatedAt should be set";
    }

    @Test
    public void testRemovePhoneNumberFromShukranAccount_VerifyShukranDataPreserved() {
        // Given
        customerEntity.setPhoneNumber("+966411055731");
        customerEntity.setProfileId("test-profile-id");
        customerEntity.setCardNumber("1200000522414150L");
        customerEntity.setShukranLinkFlag(true);
        when(client.findByEntityId(customerId)).thenReturn(customerEntity);
        when(client.saveAndFlushCustomerEntity(any(CustomerEntity.class))).thenReturn(customerEntity);

        // When
        getCustomer.removePhoneNumberFromShukranAccount(customerId, client);

        // Then
        verify(client).findByEntityId(customerId);
        verify(client).saveAndFlushCustomerEntity(customerEntity);
        
        // Verify only phone number is cleared, other Shukran data is preserved
        assert customerEntity.getPhoneNumber() == null : "Phone number should be cleared";
        assert customerEntity.getIsMobileNumberRemoved() == true : "isMobileNumberRemoved flag should be set to true";
        assert customerEntity.getProfileId().equals("test-profile-id") : "Profile ID should be preserved";
        assert customerEntity.getCardNumber().equals("1200000522414150L") : "Card number should be preserved";
        assert customerEntity.getShukranLinkFlag() == true : "Shukran link flag should be preserved";
    }
}