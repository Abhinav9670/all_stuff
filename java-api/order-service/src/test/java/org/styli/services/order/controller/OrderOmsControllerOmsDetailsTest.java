package org.styli.services.order.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import java.util.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.request.Order.OrderViewRequest;
import org.styli.services.order.pojo.request.Order.OrdersDetailsResponsedto;
import org.styli.services.order.pojo.response.Order.OmsOrderresponsedto;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.service.SalesOrderService;
import org.styli.services.order.utility.Constants;

/**
 * JUnit test cases for OrderOmsController.oms/details endpoint
 * 
 * @author Test Author
 */
public class OrderOmsControllerOmsDetailsTest extends AbstractTestNGSpringContextTests {

    @InjectMocks
    private OrderOmsController orderOmsController;

    @Mock
    private SalesOrderService salesOrderService;

    @Mock
    private ConfigService configService;

    @Mock
    private Constants constants;

    private GetOrderConsulValues orderCredentials;

    private OrderViewRequest validRequest;
    private OmsOrderresponsedto successResponse;
    private Map<String, String> requestHeaders;
    private String authorizationToken;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock Constants
        orderCredentials = new GetOrderConsulValues();
        orderCredentials.setInternalAuthEnable(false);
        orderCredentials.setExternalAuthEnable(false);
        orderCredentials.setFirebaseAuthEnable(false);
        
        ReflectionTestUtils.setField(constants, "orderCredentials", orderCredentials);
        
        // Setup valid request
        validRequest = createValidOrderViewRequest();
        
        // Setup success response
        successResponse = createSuccessResponse();
        
        // Setup request headers
        requestHeaders = new HashMap<>();
        requestHeaders.put("Content-Type", "application/json");
        requestHeaders.put("Authorization", "Bearer test-token");
        
        // Setup authorization token
        authorizationToken = "valid-auth-token";
    }

    @Test(testName = "testOmsDetails_InternalAuthEnabled_ValidToken_Success")
    public void testOmsDetails_InternalAuthEnabled_ValidToken_Success() throws Exception {
        // Arrange
        orderCredentials.setInternalAuthEnable(true);
        when(configService.checkAuthorization(authorizationToken, null)).thenReturn(true);
        when(salesOrderService.getOmsOrderDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(requestHeaders, validRequest, authorizationToken);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        assertEquals(result.getStatusCode(), "200");
        assertEquals(result.getStatusMsg(), "Success");
        verify(configService).checkAuthorization(authorizationToken, null);
        verify(salesOrderService).getOmsOrderDetails(validRequest);
    }

    @Test(testName = "testOmsDetails_InternalAuthEnabled_InvalidToken_Unauthorized")
    public void testOmsDetails_InternalAuthEnabled_InvalidToken_Unauthorized() throws Exception {
        // Arrange
        orderCredentials.setInternalAuthEnable(true);
        when(configService.checkAuthorization(authorizationToken, null)).thenReturn(false);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(requestHeaders, validRequest, authorizationToken);

        // Assert
        assertNotNull(result);
        assertFalse(result.getStatus());
        assertEquals(result.getStatusCode(), HttpStatus.UNAUTHORIZED.toString());
        verify(configService).checkAuthorization(authorizationToken, null);
        verify(salesOrderService, never()).getOmsOrderDetails(any(OrderViewRequest.class));
    }

    @Test(testName = "testOmsDetails_InternalAuthEnabled_NullToken_Unauthorized")
    public void testOmsDetails_InternalAuthEnabled_NullToken_Unauthorized() throws Exception {
        // Arrange
        orderCredentials.setInternalAuthEnable(true);
        when(configService.checkAuthorization(null, null)).thenReturn(false);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(requestHeaders, validRequest, null);

        // Assert
        assertNotNull(result);
        assertFalse(result.getStatus());
        assertEquals(result.getStatusCode(), HttpStatus.UNAUTHORIZED.toString());
        verify(configService).checkAuthorization(null, null);
        verify(salesOrderService, never()).getOmsOrderDetails(any(OrderViewRequest.class));
    }

    @Test(testName = "testOmsDetails_InternalAuthDisabled_Success")
    public void testOmsDetails_InternalAuthDisabled_Success() throws Exception {
        // Arrange
        orderCredentials.setInternalAuthEnable(false);
        when(salesOrderService.getOmsOrderDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(requestHeaders, validRequest, authorizationToken);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        assertEquals(result.getStatusCode(), "200");
        assertEquals(result.getStatusMsg(), "Success");
        verify(configService, never()).checkAuthorization(anyString(), any());
        verify(salesOrderService).getOmsOrderDetails(validRequest);
    }

    @Test(testName = "testOmsDetails_WithOrderId_Success")
    public void testOmsDetails_WithOrderId_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithOrderId = createRequestWithOrderId();
        orderCredentials.setInternalAuthEnable(false);
        when(salesOrderService.getOmsOrderDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(requestHeaders, requestWithOrderId, authorizationToken);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderDetails(requestWithOrderId);
    }

    @Test(testName = "testOmsDetails_WithCustomerIdAndStoreId_Success")
    public void testOmsDetails_WithCustomerIdAndStoreId_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithCustomerAndStore = createRequestWithCustomerAndStore();
        orderCredentials.setInternalAuthEnable(false);
        when(salesOrderService.getOmsOrderDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(requestHeaders, requestWithCustomerAndStore, authorizationToken);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderDetails(requestWithCustomerAndStore);
    }

    @Test(testName = "testOmsDetails_WithOrderCode_Success")
    public void testOmsDetails_WithOrderCode_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithOrderCode = createRequestWithOrderCode();
        orderCredentials.setInternalAuthEnable(false);
        when(salesOrderService.getOmsOrderDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(requestHeaders, requestWithOrderCode, authorizationToken);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderDetails(requestWithOrderCode);
    }

    @Test(testName = "testOmsDetails_WithEmailSendFlag_Success")
    public void testOmsDetails_WithEmailSendFlag_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithEmailFlag = createRequestWithEmailFlag();
        orderCredentials.setInternalAuthEnable(false);
        when(salesOrderService.getOmsOrderDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(requestHeaders, requestWithEmailFlag, authorizationToken);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderDetails(requestWithEmailFlag);
    }

    @Test(testName = "testOmsDetails_WithStoreCreditBalanceFlag_Success")
    public void testOmsDetails_WithStoreCreditBalanceFlag_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithStoreCreditFlag = createRequestWithStoreCreditFlag();
        orderCredentials.setInternalAuthEnable(false);
        when(salesOrderService.getOmsOrderDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(requestHeaders, requestWithStoreCreditFlag, authorizationToken);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderDetails(requestWithStoreCreditFlag);
    }

    @Test(testName = "testOmsDetails_WithUseArchiveFlag_Success")
    public void testOmsDetails_WithUseArchiveFlag_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithArchiveFlag = createRequestWithArchiveFlag();
        orderCredentials.setInternalAuthEnable(false);
        when(salesOrderService.getOmsOrderDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(requestHeaders, requestWithArchiveFlag, authorizationToken);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderDetails(requestWithArchiveFlag);
    }

    @Test(testName = "testOmsDetails_ServiceThrowsException")
    public void testOmsDetails_ServiceThrowsException() throws Exception {
        // Arrange
        orderCredentials.setInternalAuthEnable(false);
        when(salesOrderService.getOmsOrderDetails(any(OrderViewRequest.class)))
                .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        try {
            orderOmsController.orderDetails(requestHeaders, validRequest, authorizationToken);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Service error");
            verify(salesOrderService).getOmsOrderDetails(validRequest);
        }
    }

    @Test(testName = "testOmsDetails_ConfigServiceThrowsException")
    public void testOmsDetails_ConfigServiceThrowsException() throws Exception {
        // Arrange
        orderCredentials.setInternalAuthEnable(true);
        when(configService.checkAuthorization(authorizationToken, null))
                .thenThrow(new RuntimeException("Config service error"));

        // Act & Assert
        try {
            orderOmsController.orderDetails(requestHeaders, validRequest, authorizationToken);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Config service error");
            verify(configService).checkAuthorization(authorizationToken, null);
            verify(salesOrderService, never()).getOmsOrderDetails(any(OrderViewRequest.class));
        }
    }

    @Test(testName = "testOmsDetails_EmptyRequest_Success")
    public void testOmsDetails_EmptyRequest_Success() throws Exception {
        // Arrange
        OrderViewRequest emptyRequest = new OrderViewRequest();
        orderCredentials.setInternalAuthEnable(false);
        when(salesOrderService.getOmsOrderDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(requestHeaders, emptyRequest, authorizationToken);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderDetails(emptyRequest);
    }

    @Test(testName = "testOmsDetails_NullRequestHeaders_Success")
    public void testOmsDetails_NullRequestHeaders_Success() throws Exception {
        // Arrange
        orderCredentials.setInternalAuthEnable(false);
        when(salesOrderService.getOmsOrderDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(null, validRequest, authorizationToken);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderDetails(validRequest);
    }

    @Test(testName = "testOmsDetails_WithCustomerEmail_Success")
    public void testOmsDetails_WithCustomerEmail_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithEmail = createRequestWithCustomerEmail();
        orderCredentials.setInternalAuthEnable(false);
        when(salesOrderService.getOmsOrderDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(requestHeaders, requestWithEmail, authorizationToken);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderDetails(requestWithEmail);
    }

    @Test(testName = "testOmsDetails_WithCodeParameter_Success")
    public void testOmsDetails_WithCodeParameter_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithCode = createRequestWithCode();
        orderCredentials.setInternalAuthEnable(false);
        when(salesOrderService.getOmsOrderDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(requestHeaders, requestWithCode, authorizationToken);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderDetails(requestWithCode);
    }

    @Test(testName = "testOmsDetails_WithShowSellerCancelledFlag_Success")
    public void testOmsDetails_WithShowSellerCancelledFlag_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithSellerCancelledFlag = createRequestWithSellerCancelledFlag();
        orderCredentials.setInternalAuthEnable(false);
        when(salesOrderService.getOmsOrderDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderDetails(requestHeaders, requestWithSellerCancelledFlag, authorizationToken);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderDetails(requestWithSellerCancelledFlag);
    }

    /**
     * Helper method to create a valid OrderViewRequest
     */
    private OrderViewRequest createValidOrderViewRequest() {
        OrderViewRequest request = new OrderViewRequest();
        request.setOrderId(12345);
        request.setCustomerId(67890);
        request.setStoreId(1);
        request.setEmailSend(false);
        request.setFetchStoreCreditBalance(false);
        request.setRmaItemQtyProcessed(true);
        request.setUseArchive(false);
        return request;
    }

    /**
     * Helper method to create a request with order ID
     */
    private OrderViewRequest createRequestWithOrderId() {
        OrderViewRequest request = new OrderViewRequest();
        request.setOrderId(12345);
        return request;
    }

    /**
     * Helper method to create a request with customer ID and store ID
     */
    private OrderViewRequest createRequestWithCustomerAndStore() {
        OrderViewRequest request = new OrderViewRequest();
        request.setCustomerId(67890);
        request.setStoreId(1);
        return request;
    }

    /**
     * Helper method to create a request with order code
     */
    private OrderViewRequest createRequestWithOrderCode() {
        OrderViewRequest request = new OrderViewRequest();
        request.setOrderCode("ORD-12345");
        return request;
    }

    /**
     * Helper method to create a request with email send flag
     */
    private OrderViewRequest createRequestWithEmailFlag() {
        OrderViewRequest request = new OrderViewRequest();
        request.setOrderId(12345);
        request.setEmailSend(true);
        return request;
    }

    /**
     * Helper method to create a request with store credit balance flag
     */
    private OrderViewRequest createRequestWithStoreCreditFlag() {
        OrderViewRequest request = new OrderViewRequest();
        request.setOrderId(12345);
        request.setFetchStoreCreditBalance(true);
        return request;
    }

    /**
     * Helper method to create a request with archive flag
     */
    private OrderViewRequest createRequestWithArchiveFlag() {
        OrderViewRequest request = new OrderViewRequest();
        request.setOrderId(12345);
        request.setUseArchive(true);
        return request;
    }

    /**
     * Helper method to create a request with customer email
     */
    private OrderViewRequest createRequestWithCustomerEmail() {
        OrderViewRequest request = new OrderViewRequest();
        request.setOrderId(12345);
        request.setCustomerEmail("test@example.com");
        return request;
    }

    /**
     * Helper method to create a request with code parameter
     */
    private OrderViewRequest createRequestWithCode() {
        OrderViewRequest request = new OrderViewRequest();
        request.setCode("encoded-order-id");
        return request;
    }

    /**
     * Helper method to create a request with seller cancelled flag
     */
    private OrderViewRequest createRequestWithSellerCancelledFlag() {
        OrderViewRequest request = new OrderViewRequest();
        request.setOrderId(12345);
        request.setShowSellerCancelled(true);
        return request;
    }

    /**
     * Helper method to create a success response
     */
    private OmsOrderresponsedto createSuccessResponse() {
        OmsOrderresponsedto response = new OmsOrderresponsedto();
        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg("Success");
        
        OrdersDetailsResponsedto orderDetails = new OrdersDetailsResponsedto();
        orderDetails.setOrderId(12345);
        orderDetails.setIncrementId("ORD-12345");
        orderDetails.setStatus("pending");
        orderDetails.setEmail("test@example.com");
        orderDetails.setCustomerId(67890);
        orderDetails.setStoreId("1");
        
        response.setResponse(orderDetails);
        return response;
    }
}
