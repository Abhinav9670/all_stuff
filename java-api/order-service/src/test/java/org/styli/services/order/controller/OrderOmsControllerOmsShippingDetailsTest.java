package org.styli.services.order.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.styli.services.order.db.product.config.firebase.FirebaseAuthentication;
import org.styli.services.order.db.product.config.firebase.FirebaseUser;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.request.Order.OrderViewRequest;
import org.styli.services.order.pojo.request.Order.OrdersDetailsResponsedto;
import org.styli.services.order.pojo.response.Order.OmsOrderresponsedto;
import org.styli.services.order.service.SalesOrderService;
import org.styli.services.order.utility.Constants;

/**
 * JUnit test cases for OrderOmsController.oms/shipping/details endpoint
 * 
 * @author Test Author
 */
public class OrderOmsControllerOmsShippingDetailsTest extends AbstractTestNGSpringContextTests {

    @InjectMocks
    private OrderOmsController orderOmsController;

    @Mock
    private SalesOrderService salesOrderService;

    @Mock
    private FirebaseAuthentication firebaseAuthentication;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private FirebaseUser firebaseUser;

    @Mock
    private Constants constants;

    private GetOrderConsulValues orderCredentials;

    private OrderViewRequest validRequest;
    private OmsOrderresponsedto successResponse;
    private Map<String, String> requestHeaders;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock Constants
        orderCredentials = new GetOrderConsulValues();
        orderCredentials.setFirebaseAuthEnable(false);
        orderCredentials.setInternalAuthEnable(false);
        orderCredentials.setExternalAuthEnable(false);
        
        ReflectionTestUtils.setField(constants, "orderCredentials", orderCredentials);
        
        // Setup valid request
        validRequest = createValidOrderViewRequest();
        
        // Setup success response
        successResponse = createSuccessResponse();
        
        // Setup request headers
        requestHeaders = new HashMap<>();
        requestHeaders.put("Content-Type", "application/json");
        requestHeaders.put("Authorization", "Bearer test-token");
    }

    @Test(testName = "testOmsShippingDetails_FirebaseAuthEnabled_Success")
    public void testOmsShippingDetails_FirebaseAuthEnabled_Success() throws Exception {
        // Arrange
        orderCredentials.setFirebaseAuthEnable(true);
        doNothing().when(firebaseAuthentication).verifyToken(httpServletRequest);
        when(authentication.getPrincipal()).thenReturn(firebaseUser);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, validRequest, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        assertEquals(result.getStatusCode(), "200");
        assertEquals(result.getStatusMsg(), "Success");
        verify(firebaseAuthentication).verifyToken(httpServletRequest);
        verify(salesOrderService).getOmsOrderShippingDetails(validRequest);
    }

    @Test(testName = "testOmsShippingDetails_FirebaseAuthEnabled_Unauthorized")
    public void testOmsShippingDetails_FirebaseAuthEnabled_Unauthorized() throws Exception {
        // Arrange
        orderCredentials.setFirebaseAuthEnable(true);
        doNothing().when(firebaseAuthentication).verifyToken(httpServletRequest);
        when(authentication.getPrincipal()).thenReturn(new Object()); // Non-FirebaseUser
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, validRequest, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertFalse(result.getStatus());
        assertEquals(result.getStatusCode(), HttpStatus.UNAUTHORIZED.toString());
        verify(firebaseAuthentication).verifyToken(httpServletRequest);
        verify(salesOrderService, never()).getOmsOrderShippingDetails(any(OrderViewRequest.class));
    }

    @Test(testName = "testOmsShippingDetails_FirebaseAuthDisabled_Success")
    public void testOmsShippingDetails_FirebaseAuthDisabled_Success() throws Exception {
        // Arrange
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, validRequest, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        assertEquals(result.getStatusCode(), "200");
        assertEquals(result.getStatusMsg(), "Success");
        verify(firebaseAuthentication, never()).verifyToken(any(HttpServletRequest.class));
        verify(salesOrderService).getOmsOrderShippingDetails(validRequest);
    }

    @Test(testName = "testOmsShippingDetails_WithOrderId_Success")
    public void testOmsShippingDetails_WithOrderId_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithOrderId = createRequestWithOrderId();
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, requestWithOrderId, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderShippingDetails(requestWithOrderId);
    }

    @Test(testName = "testOmsShippingDetails_WithCustomerIdAndStoreId_Success")
    public void testOmsShippingDetails_WithCustomerIdAndStoreId_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithCustomerAndStore = createRequestWithCustomerAndStore();
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, requestWithCustomerAndStore, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderShippingDetails(requestWithCustomerAndStore);
    }

    @Test(testName = "testOmsShippingDetails_WithOrderCode_Success")
    public void testOmsShippingDetails_WithOrderCode_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithOrderCode = createRequestWithOrderCode();
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, requestWithOrderCode, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderShippingDetails(requestWithOrderCode);
    }

    @Test(testName = "testOmsShippingDetails_WithEmailSendFlag_Success")
    public void testOmsShippingDetails_WithEmailSendFlag_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithEmailFlag = createRequestWithEmailFlag();
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, requestWithEmailFlag, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderShippingDetails(requestWithEmailFlag);
    }

    @Test(testName = "testOmsShippingDetails_WithStoreCreditBalanceFlag_Success")
    public void testOmsShippingDetails_WithStoreCreditBalanceFlag_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithStoreCreditFlag = createRequestWithStoreCreditFlag();
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, requestWithStoreCreditFlag, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderShippingDetails(requestWithStoreCreditFlag);
    }

    @Test(testName = "testOmsShippingDetails_WithUseArchiveFlag_Success")
    public void testOmsShippingDetails_WithUseArchiveFlag_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithArchiveFlag = createRequestWithArchiveFlag();
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, requestWithArchiveFlag, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderShippingDetails(requestWithArchiveFlag);
    }

    @Test(testName = "testOmsShippingDetails_WithCustomerEmail_Success")
    public void testOmsShippingDetails_WithCustomerEmail_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithEmail = createRequestWithCustomerEmail();
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, requestWithEmail, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderShippingDetails(requestWithEmail);
    }

    @Test(testName = "testOmsShippingDetails_WithCodeParameter_Success")
    public void testOmsShippingDetails_WithCodeParameter_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithCode = createRequestWithCode();
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, requestWithCode, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderShippingDetails(requestWithCode);
    }

    @Test(testName = "testOmsShippingDetails_WithShowSellerCancelledFlag_Success")
    public void testOmsShippingDetails_WithShowSellerCancelledFlag_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithSellerCancelledFlag = createRequestWithSellerCancelledFlag();
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, requestWithSellerCancelledFlag, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderShippingDetails(requestWithSellerCancelledFlag);
    }

    @Test(testName = "testOmsShippingDetails_ServiceThrowsException")
    public void testOmsShippingDetails_ServiceThrowsException() throws Exception {
        // Arrange
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        try {
            orderOmsController.orderShippingDetails(requestHeaders, validRequest, httpServletRequest);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Service error");
            verify(salesOrderService).getOmsOrderShippingDetails(validRequest);
        }
    }

    @Test(testName = "testOmsShippingDetails_FirebaseAuthThrowsException")
    public void testOmsShippingDetails_FirebaseAuthThrowsException() throws Exception {
        // Arrange
        orderCredentials.setFirebaseAuthEnable(true);
        doThrow(new RuntimeException("Firebase auth error"))
                .when(firebaseAuthentication).verifyToken(httpServletRequest);

        // Act & Assert
        try {
            orderOmsController.orderShippingDetails(requestHeaders, validRequest, httpServletRequest);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Firebase auth error");
            verify(firebaseAuthentication).verifyToken(httpServletRequest);
            verify(salesOrderService, never()).getOmsOrderShippingDetails(any(OrderViewRequest.class));
        }
    }

    @Test(testName = "testOmsShippingDetails_EmptyRequest_Success")
    public void testOmsShippingDetails_EmptyRequest_Success() throws Exception {
        // Arrange
        OrderViewRequest emptyRequest = new OrderViewRequest();
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, emptyRequest, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderShippingDetails(emptyRequest);
    }

    @Test(testName = "testOmsShippingDetails_NullRequestHeaders_Success")
    public void testOmsShippingDetails_NullRequestHeaders_Success() throws Exception {
        // Arrange
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(null, validRequest, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderShippingDetails(validRequest);
    }

    @Test(testName = "testOmsShippingDetails_NullHttpServletRequest_Success")
    public void testOmsShippingDetails_NullHttpServletRequest_Success() throws Exception {
        // Arrange
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, validRequest, null);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderShippingDetails(validRequest);
    }

    @Test(testName = "testOmsShippingDetails_WithRmaItemQtyProcessedFlag_Success")
    public void testOmsShippingDetails_WithRmaItemQtyProcessedFlag_Success() throws Exception {
        // Arrange
        OrderViewRequest requestWithRmaFlag = createRequestWithRmaItemQtyProcessedFlag();
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(successResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, requestWithRmaFlag, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getOmsOrderShippingDetails(requestWithRmaFlag);
    }

    @Test(testName = "testOmsShippingDetails_ShippingDetailsResponse_Success")
    public void testOmsShippingDetails_ShippingDetailsResponse_Success() throws Exception {
        // Arrange
        OmsOrderresponsedto shippingResponse = createShippingDetailsResponse();
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getOmsOrderShippingDetails(any(OrderViewRequest.class)))
                .thenReturn(shippingResponse);

        // Act
        OmsOrderresponsedto result = orderOmsController.orderShippingDetails(requestHeaders, validRequest, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        assertEquals(result.getStatusCode(), "200");
        assertEquals(result.getStatusMsg(), "Shipping details retrieved successfully");
        
        OrdersDetailsResponsedto orderDetails = result.getResponse();
        assertNotNull(orderDetails);
        assertEquals(orderDetails.getShippingMethod(), "standard_shipping");
        assertEquals(orderDetails.getShippingDescription(), "Standard Shipping");
        assertEquals(orderDetails.getEstimatedDeliveryTime(), "3-5 business days");
        assertNotNull(orderDetails.getShippingAddress());
        
        verify(salesOrderService).getOmsOrderShippingDetails(validRequest);
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
     * Helper method to create a request with RMA item quantity processed flag
     */
    private OrderViewRequest createRequestWithRmaItemQtyProcessedFlag() {
        OrderViewRequest request = new OrderViewRequest();
        request.setOrderId(12345);
        request.setRmaItemQtyProcessed(false);
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

    /**
     * Helper method to create a shipping details response
     */
    private OmsOrderresponsedto createShippingDetailsResponse() {
        OmsOrderresponsedto response = new OmsOrderresponsedto();
        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg("Shipping details retrieved successfully");
        
        OrdersDetailsResponsedto orderDetails = new OrdersDetailsResponsedto();
        orderDetails.setOrderId(12345);
        orderDetails.setIncrementId("ORD-12345");
        orderDetails.setStatus("shipped");
        orderDetails.setEmail("test@example.com");
        orderDetails.setCustomerId(67890);
        orderDetails.setStoreId("1");
        orderDetails.setShippingMethod("standard_shipping");
        orderDetails.setShippingDescription("Standard Shipping");
        orderDetails.setEstimatedDeliveryTime("3-5 business days");
        
        // Mock shipping address
        org.styli.services.order.pojo.response.CustomerAddrees shippingAddress = 
            new org.styli.services.order.pojo.response.CustomerAddrees();
        shippingAddress.setFirstName("John");
        shippingAddress.setLastName("Doe");
        shippingAddress.setStreetAddress("123 Main St");
        shippingAddress.setCity("New York");
        shippingAddress.setPostCode("10001");
        shippingAddress.setCountry("US");
        orderDetails.setShippingAddress(shippingAddress);
        
        response.setResponse(orderDetails);
        return response;
    }
}
