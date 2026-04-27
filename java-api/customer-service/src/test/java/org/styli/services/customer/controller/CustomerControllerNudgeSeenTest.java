package org.styli.services.customer.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.styli.services.customer.pojo.CustomerRequestBody;
import org.styli.services.customer.pojo.GenericApiResponse;
import org.styli.services.customer.service.CustomerV4Service;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class CustomerControllerNudgeSeenTest {

    @Mock
    private CustomerV4Service customerV4Service;

    @InjectMocks
    private CustomerController customerController;

    private Map<String, String> requestHeader;
    private CustomerRequestBody request;
    private GenericApiResponse<String> mockResponse;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
        
        requestHeader = new HashMap<>();
        requestHeader.put("Token", "test-token");
        
        request = new CustomerRequestBody();
        request.setCustomerId(123);
        request.setStoreId(1);
        
        mockResponse = new GenericApiResponse<>();
        mockResponse.setStatus(true);
        mockResponse.setStatusCode("200");
        mockResponse.setStatusMsg("Nudge seen timestamp recorded successfully");
        mockResponse.setResponse("Success");
    }

    @Test
    public void testRecordNudgeSeen_Success() {
        // Given
        when(customerV4Service.recordNudgeSeen(any(CustomerRequestBody.class), any(Map.class)))
            .thenReturn(mockResponse);

        // When
        GenericApiResponse<String> response = customerController.recordNudgeSeen(requestHeader, request);

        // Then
        assertNotNull(response);
        assertTrue(response.getStatus());
        assertEquals("200", response.getStatusCode());
        assertEquals("Nudge seen timestamp recorded successfully", response.getStatusMsg());
        assertEquals("Success", response.getResponse());
        
        verify(customerV4Service).authenticateCheck(requestHeader, request.getCustomerId());
        verify(customerV4Service).recordNudgeSeen(eq(request), any(Map.class));
    }

    @Test
    public void testRecordNudgeSeen_NullRequest() {
        // Given
        CustomerRequestBody nullRequest = null;

        // When
        GenericApiResponse<String> response = customerController.recordNudgeSeen(requestHeader, nullRequest);

        // Then
        assertNotNull(response);
        assertFalse(response.getStatus());
        assertEquals("400", response.getStatusCode());
        assertEquals("Customer ID is required", response.getStatusMsg());
        
        verify(customerV4Service, never()).recordNudgeSeen(any(), any());
    }

    @Test
    public void testRecordNudgeSeen_NullCustomerId() {
        // Given
        CustomerRequestBody requestWithoutId = new CustomerRequestBody();
        requestWithoutId.setCustomerId(null);

        // When
        GenericApiResponse<String> response = customerController.recordNudgeSeen(requestHeader, requestWithoutId);

        // Then
        assertNotNull(response);
        assertFalse(response.getStatus());
        assertEquals("400", response.getStatusCode());
        assertEquals("Customer ID is required", response.getStatusMsg());
        
        verify(customerV4Service, never()).recordNudgeSeen(any(), any());
    }

    @Test
    public void testRecordNudgeSeen_CustomerNotFound() {
        // Given
        GenericApiResponse<String> errorResponse = new GenericApiResponse<>();
        errorResponse.setStatus(false);
        errorResponse.setStatusCode("404");
        errorResponse.setStatusMsg("Customer not found");
        
        when(customerV4Service.recordNudgeSeen(any(CustomerRequestBody.class), any(Map.class)))
            .thenReturn(errorResponse);

        // When
        GenericApiResponse<String> response = customerController.recordNudgeSeen(requestHeader, request);

        // Then
        assertNotNull(response);
        assertFalse(response.getStatus());
        assertEquals("404", response.getStatusCode());
        assertEquals("Customer not found", response.getStatusMsg());
        
        verify(customerV4Service).authenticateCheck(eq(requestHeader), eq(request.getCustomerId()));
        verify(customerV4Service).recordNudgeSeen(eq(request), any(Map.class));
    }

    @Test
    public void testRecordNudgeSeen_InternalServerError() {
        // Given
        GenericApiResponse<String> errorResponse = new GenericApiResponse<>();
        errorResponse.setStatus(false);
        errorResponse.setStatusCode("500");
        errorResponse.setStatusMsg("Internal server error");
        
        when(customerV4Service.recordNudgeSeen(any(CustomerRequestBody.class), any(Map.class)))
            .thenReturn(errorResponse);

        // When
        GenericApiResponse<String> response = customerController.recordNudgeSeen(requestHeader, request);

        // Then
        assertNotNull(response);
        assertFalse(response.getStatus());
        assertEquals("500", response.getStatusCode());
        assertEquals("Internal server error", response.getStatusMsg());
        
        verify(customerV4Service).authenticateCheck(eq(requestHeader), eq(request.getCustomerId()));
        verify(customerV4Service).recordNudgeSeen(eq(request), any(Map.class));
    }

    @Test
    public void testRecordNudgeSeen_JwtFlagDisabled() {
        // Given
        ReflectionTestUtils.setField(customerController, "jwtFlag", "0");
        when(customerV4Service.recordNudgeSeen(any(CustomerRequestBody.class), any(Map.class)))
            .thenReturn(mockResponse);

        // When
        GenericApiResponse<String> response = customerController.recordNudgeSeen(requestHeader, request);

        // Then
        assertNotNull(response);
        assertTrue(response.getStatus());
        
        verify(customerV4Service, never()).authenticateCheck(any(), any());
        verify(customerV4Service).recordNudgeSeen(eq(request), any(Map.class));
    }
}

