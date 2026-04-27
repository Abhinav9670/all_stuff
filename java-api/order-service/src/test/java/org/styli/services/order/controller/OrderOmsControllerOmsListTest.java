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
import org.styli.services.order.pojo.request.Order.OmsOrderListRequest;
import org.styli.services.order.pojo.request.Order.OmsRequestFilters;
import org.styli.services.order.pojo.response.Order.CustomerOrdersResponseDTO;
import org.styli.services.order.pojo.response.Order.OrderResponse;
import org.styli.services.order.service.SalesOrderService;
import org.styli.services.order.utility.Constants;

/**
 * JUnit test cases for OrderOmsController.oms/list endpoint
 * 
 * @author Test Author
 */
public class OrderOmsControllerOmsListTest extends AbstractTestNGSpringContextTests {

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

    private OmsOrderListRequest validRequest;
    private CustomerOrdersResponseDTO successResponse;
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
        validRequest = createValidOmsOrderListRequest();
        
        // Setup success response
        successResponse = createSuccessResponse();
        
        // Setup request headers
        requestHeaders = new HashMap<>();
        requestHeaders.put("Content-Type", "application/json");
        requestHeaders.put("Authorization", "Bearer test-token");
    }

    @Test(testName = "testOmsList_FirebaseAuthEnabled_Success")
    public void testOmsList_FirebaseAuthEnabled_Success() throws Exception {
        // Arrange
        orderCredentials.setFirebaseAuthEnable(true);
        doNothing().when(firebaseAuthentication).verifyToken(httpServletRequest);
        when(authentication.getPrincipal()).thenReturn(firebaseUser);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        when(salesOrderService.getCustomerOmsOrders(any(OmsOrderListRequest.class)))
                .thenReturn(successResponse);

        // Act
        CustomerOrdersResponseDTO result = orderOmsController.orderListAll(requestHeaders, validRequest, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        assertEquals(result.getStatusCode(), "200");
        assertEquals(result.getStatusMsg(), "Success");
        verify(firebaseAuthentication).verifyToken(httpServletRequest);
        verify(salesOrderService).getCustomerOmsOrders(validRequest);
    }

    @Test(testName = "testOmsList_FirebaseAuthEnabled_Unauthorized")
    public void testOmsList_FirebaseAuthEnabled_Unauthorized() throws Exception {
        // Arrange
        orderCredentials.setFirebaseAuthEnable(true);
        doNothing().when(firebaseAuthentication).verifyToken(httpServletRequest);
        when(authentication.getPrincipal()).thenReturn(new Object()); // Non-FirebaseUser
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // Act
        CustomerOrdersResponseDTO result = orderOmsController.orderListAll(requestHeaders, validRequest, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertFalse(result.getStatus());
        assertEquals(result.getStatusCode(), HttpStatus.UNAUTHORIZED.toString());
        verify(firebaseAuthentication).verifyToken(httpServletRequest);
        verify(salesOrderService, never()).getCustomerOmsOrders(any(OmsOrderListRequest.class));
    }

    @Test(testName = "testOmsList_FirebaseAuthDisabled_Success")
    public void testOmsList_FirebaseAuthDisabled_Success() throws Exception {
        // Arrange
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getCustomerOmsOrders(any(OmsOrderListRequest.class)))
                .thenReturn(successResponse);

        // Act
        CustomerOrdersResponseDTO result = orderOmsController.orderListAll(requestHeaders, validRequest, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        assertEquals(result.getStatusCode(), "200");
        assertEquals(result.getStatusMsg(), "Success");
        verify(firebaseAuthentication, never()).verifyToken(any(HttpServletRequest.class));
        verify(salesOrderService).getCustomerOmsOrders(validRequest);
    }

    @Test(testName = "testOmsList_EmptyRequest_Success")
    public void testOmsList_EmptyRequest_Success() throws Exception {
        // Arrange
        OmsOrderListRequest emptyRequest = new OmsOrderListRequest();
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getCustomerOmsOrders(any(OmsOrderListRequest.class)))
                .thenReturn(successResponse);

        // Act
        CustomerOrdersResponseDTO result = orderOmsController.orderListAll(requestHeaders, emptyRequest, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getCustomerOmsOrders(emptyRequest);
    }

    @Test(testName = "testOmsList_WithFilters_Success")
    public void testOmsList_WithFilters_Success() throws Exception {
        // Arrange
        OmsOrderListRequest requestWithFilters = createRequestWithFilters();
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getCustomerOmsOrders(any(OmsOrderListRequest.class)))
                .thenReturn(successResponse);

        // Act
        CustomerOrdersResponseDTO result = orderOmsController.orderListAll(requestHeaders, requestWithFilters, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getCustomerOmsOrders(requestWithFilters);
    }

    @Test(testName = "testOmsList_ServiceThrowsException")
    public void testOmsList_ServiceThrowsException() throws Exception {
        // Arrange
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getCustomerOmsOrders(any(OmsOrderListRequest.class)))
                .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        try {
            orderOmsController.orderListAll(requestHeaders, validRequest, httpServletRequest);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Service error");
            verify(salesOrderService).getCustomerOmsOrders(validRequest);
        }
    }

    @Test(testName = "testOmsList_NullRequestHeaders")
    public void testOmsList_NullRequestHeaders() throws Exception {
        // Arrange
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getCustomerOmsOrders(any(OmsOrderListRequest.class)))
                .thenReturn(successResponse);

        // Act
        CustomerOrdersResponseDTO result = orderOmsController.orderListAll(null, validRequest, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getCustomerOmsOrders(validRequest);
    }

    @Test(testName = "testOmsList_NullHttpServletRequest")
    public void testOmsList_NullHttpServletRequest() throws Exception {
        // Arrange
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getCustomerOmsOrders(any(OmsOrderListRequest.class)))
                .thenReturn(successResponse);

        // Act
        CustomerOrdersResponseDTO result = orderOmsController.orderListAll(requestHeaders, validRequest, null);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getCustomerOmsOrders(validRequest);
    }

    @Test(testName = "testOmsList_FirebaseAuthThrowsException")
    public void testOmsList_FirebaseAuthThrowsException() throws Exception {
        // Arrange
        orderCredentials.setFirebaseAuthEnable(true);
        doThrow(new RuntimeException("Firebase auth error"))
                .when(firebaseAuthentication).verifyToken(httpServletRequest);

        // Act & Assert
        try {
            orderOmsController.orderListAll(requestHeaders, validRequest, httpServletRequest);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Firebase auth error");
            verify(firebaseAuthentication).verifyToken(httpServletRequest);
            verify(salesOrderService, never()).getCustomerOmsOrders(any(OmsOrderListRequest.class));
        }
    }

    @Test(testName = "testOmsList_WithPagination")
    public void testOmsList_WithPagination() throws Exception {
        // Arrange
        OmsOrderListRequest paginatedRequest = new OmsOrderListRequest();
        paginatedRequest.setOffset(10);
        paginatedRequest.setPageSize(20);
        paginatedRequest.setUseArchive(true);
        
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getCustomerOmsOrders(any(OmsOrderListRequest.class)))
                .thenReturn(successResponse);

        // Act
        CustomerOrdersResponseDTO result = orderOmsController.orderListAll(requestHeaders, paginatedRequest, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getCustomerOmsOrders(paginatedRequest);
    }

    @Test(testName = "testOmsList_WithQueryParameter")
    public void testOmsList_WithQueryParameter() throws Exception {
        // Arrange
        OmsOrderListRequest queryRequest = new OmsOrderListRequest();
        queryRequest.setQuery("test query");
        
        orderCredentials.setFirebaseAuthEnable(false);
        when(salesOrderService.getCustomerOmsOrders(any(OmsOrderListRequest.class)))
                .thenReturn(successResponse);

        // Act
        CustomerOrdersResponseDTO result = orderOmsController.orderListAll(requestHeaders, queryRequest, httpServletRequest);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderService).getCustomerOmsOrders(queryRequest);
    }

    /**
     * Helper method to create a valid OmsOrderListRequest
     */
    private OmsOrderListRequest createValidOmsOrderListRequest() {
        OmsOrderListRequest request = new OmsOrderListRequest();
        request.setOffset(0);
        request.setPageSize(10);
        request.setUseArchive(false);
        return request;
    }

    /**
     * Helper method to create a request with filters
     */
    private OmsOrderListRequest createRequestWithFilters() {
        OmsOrderListRequest request = new OmsOrderListRequest();
        request.setOffset(0);
        request.setPageSize(10);
        request.setUseArchive(false);
        
        OmsRequestFilters filters = new OmsRequestFilters();
        filters.setStoreId(Arrays.asList(1, 2));
        filters.setStatus(Arrays.asList("pending", "processing"));
        filters.setCustomerEmail("test@example.com");
        filters.setFromDate("2023-01-01");
        filters.setToDate("2023-12-31");
        filters.setIncrementId("100000001");
        
        request.setFilters(filters);
        return request;
    }

    /**
     * Helper method to create a success response
     */
    private CustomerOrdersResponseDTO createSuccessResponse() {
        CustomerOrdersResponseDTO response = new CustomerOrdersResponseDTO();
        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg("Success");
        response.setTotalCount(1L);
        response.setTotalPageSize(1);
        
        List<OrderResponse> orderList = new ArrayList<>();
        OrderResponse order = new OrderResponse();
        order.setOrderId(1);
        order.setIncrementId("100000001");
        order.setStatus("pending");
        order.setEmail("test@example.com");
        orderList.add(order);
        
        response.setResponse(orderList);
        return response;
    }
}
