package org.styli.services.order.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import java.util.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.styli.services.order.pojo.order.RMAOrderItemV2Request;
import org.styli.services.order.pojo.order.RMAOrderV2Request;
import org.styli.services.order.pojo.response.Order.OrderResponse;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;
import org.styli.services.order.service.SalesOrderRMAService;
import org.styli.services.order.service.SalesOrderServiceV2;
import org.styli.services.order.service.SalesOrderServiceV3;
import org.styli.services.order.utility.OrderConstants;

/**
 * JUnit test cases for OrderWriteController.v2/rma endpoint
 *
 * @author Test Author
 */
public class OrderWriteControllerV2RmaTest extends AbstractTestNGSpringContextTests {

    @InjectMocks
    private OrderWriteController orderWriteController;

    @Mock
    private SalesOrderRMAService salesOrderRMAService;

    @Mock
    private SalesOrderServiceV2 salesOrderServiceV2;

    @Mock
    private SalesOrderServiceV3 salesOrderServiceV3;

    private RMAOrderV2Request validRequest;
    private OrderResponseDTO successResponse;
    private Map<String, String> requestHeaders;
    private String jwtFlag;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup JWT flag
        jwtFlag = "1";
        ReflectionTestUtils.setField(orderWriteController, "jwtFlag", jwtFlag);

        // Setup valid request
        validRequest = createValidRMAOrderV2Request();

        // Setup success response
        successResponse = createSuccessResponse();

        // Setup request headers
        requestHeaders = new HashMap<>();
        requestHeaders.put("Content-Type", "application/json");
        requestHeaders.put("Authorization", "Bearer test-token");
        requestHeaders.put("x-client-version", "2.1.0");
    }

    @Test(testName = "testRmaOrderV2_JwtEnabled_Success")
    public void testRmaOrderV2_JwtEnabled_Success() throws Exception {
        // Arrange
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, validRequest.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(requestHeaders, validRequest, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        assertEquals(result.getStatusCode(), "200");
        assertEquals(result.getStatusMsg(), "RMA order created successfully");
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, validRequest.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwo(validRequest, xClientVersion);
    }

    @Test(testName = "testRmaOrderV2_JwtEnabled_AuthenticationThrowsException")
    public void testRmaOrderV2_JwtEnabled_AuthenticationThrowsException() throws Exception {
        // Arrange
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, validRequest.getCustomerId()))
                .thenThrow(new RuntimeException("Authentication failed"));

        // Act & Assert
        try {
            orderWriteController.rmaOrderV2(requestHeaders, validRequest, xClientVersion);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Authentication failed");
            verify(salesOrderServiceV2).authenticateCheck(requestHeaders, validRequest.getCustomerId());
            verify(salesOrderRMAService, never()).rmaOrderVersionTwo(any(RMAOrderV2Request.class), anyString());
        }
    }

    @Test(testName = "testRmaOrderV2_JwtDisabled_Success")
    public void testRmaOrderV2_JwtDisabled_Success() throws Exception {
        // Arrange
        jwtFlag = "0";
        ReflectionTestUtils.setField(orderWriteController, "jwtFlag", jwtFlag);
        String xClientVersion = "2.1.0";
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(requestHeaders, validRequest, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        assertEquals(result.getStatusCode(), "200");
        assertEquals(result.getStatusMsg(), "RMA order created successfully");
        verify(salesOrderServiceV2, never()).authenticateCheck(anyMap(), anyInt());
        verify(salesOrderRMAService).rmaOrderVersionTwo(validRequest, xClientVersion);
    }

    @Test(testName = "testRmaOrderV2_WithItems_Success")
    public void testRmaOrderV2_WithItems_Success() throws Exception {
        // Arrange
        RMAOrderV2Request requestWithItems = createRequestWithItems();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, requestWithItems.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(requestHeaders, requestWithItems, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, requestWithItems.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwo(requestWithItems, xClientVersion);
    }

    @Test(testName = "testRmaOrderV2_WithSplitOrderId_Success")
    public void testRmaOrderV2_WithSplitOrderId_Success() throws Exception {
        // Arrange
        RMAOrderV2Request requestWithSplitOrder = createRequestWithSplitOrder();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, requestWithSplitOrder.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(requestHeaders, requestWithSplitOrder, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, requestWithSplitOrder.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwo(requestWithSplitOrder, xClientVersion);
    }

    @Test(testName = "testRmaOrderV2_WithDropOffRequest_Success")
    public void testRmaOrderV2_WithDropOffRequest_Success() throws Exception {
        // Arrange
        RMAOrderV2Request requestWithDropOff = createRequestWithDropOff();
        OrderResponseDTO responseWithDropOff = createResponseWithDropOff();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, requestWithDropOff.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(responseWithDropOff);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(requestHeaders, requestWithDropOff, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, requestWithDropOff.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwo(requestWithDropOff, xClientVersion);
        verify(salesOrderServiceV3).createDropOff(eq("RMA-12345"), eq("return"), 
                eq(OrderConstants.SMS_TEMPLATE_RETURN_DROP_OFF), eq(responseWithDropOff));
        verify(salesOrderServiceV3).sendSms(eq("RMA-12345"), eq("return"), 
                eq(OrderConstants.SMS_TEMPLATE_RETURN_DROP_OFF), eq(responseWithDropOff));
    }

    @Test(testName = "testRmaOrderV2_WithNonDropOffRequest_Success")
    public void testRmaOrderV2_WithNonDropOffRequest_Success() throws Exception {
        // Arrange
        RMAOrderV2Request requestWithNonDropOff = createRequestWithNonDropOff();
        OrderResponseDTO responseWithNonDropOff = createResponseWithNonDropOff();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, requestWithNonDropOff.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(responseWithNonDropOff);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(requestHeaders, requestWithNonDropOff, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, requestWithNonDropOff.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwo(requestWithNonDropOff, xClientVersion);
        verify(salesOrderServiceV3).sendSms(eq("RMA-12345"), eq("return"), 
                eq(OrderConstants.SMS_TEMPLATE_RETURN_CREATE), isNull());
        verify(salesOrderServiceV3, never()).createDropOff(anyString(), anyString(), anyString(), any());
    }

    @Test(testName = "testRmaOrderV2_WithOmsRequest_Success")
    public void testRmaOrderV2_WithOmsRequest_Success() throws Exception {
        // Arrange
        RMAOrderV2Request requestWithOms = createRequestWithOms();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, requestWithOms.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(requestHeaders, requestWithOms, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, requestWithOms.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwo(requestWithOms, xClientVersion);
    }

    @Test(testName = "testRmaOrderV2_WithRefundAmounts_Success")
    public void testRmaOrderV2_WithRefundAmounts_Success() throws Exception {
        // Arrange
        RMAOrderV2Request requestWithRefundAmounts = createRequestWithRefundAmounts();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, requestWithRefundAmounts.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(requestHeaders, requestWithRefundAmounts, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, requestWithRefundAmounts.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwo(requestWithRefundAmounts, xClientVersion);
    }

    @Test(testName = "testRmaOrderV2_WithReturnFeeAmount_Success")
    public void testRmaOrderV2_WithReturnFeeAmount_Success() throws Exception {
        // Arrange
        RMAOrderV2Request requestWithReturnFee = createRequestWithReturnFee();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, requestWithReturnFee.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(requestHeaders, requestWithReturnFee, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, requestWithReturnFee.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwo(requestWithReturnFee, xClientVersion);
    }

    @Test(testName = "testRmaOrderV2_ServiceThrowsException")
    public void testRmaOrderV2_ServiceThrowsException() throws Exception {
        // Arrange
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, validRequest.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        try {
            orderWriteController.rmaOrderV2(requestHeaders, validRequest, xClientVersion);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Service error");
            verify(salesOrderServiceV2).authenticateCheck(requestHeaders, validRequest.getCustomerId());
            verify(salesOrderRMAService).rmaOrderVersionTwo(validRequest, xClientVersion);
        }
    }

    @Test(testName = "testRmaOrderV2_NullXClientVersion_Success")
    public void testRmaOrderV2_NullXClientVersion_Success() throws Exception {
        // Arrange
        String xClientVersion = null;
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, validRequest.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(requestHeaders, validRequest, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, validRequest.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwo(validRequest, xClientVersion);
    }

    @Test(testName = "testRmaOrderV2_NullRequestHeaders_Success")
    public void testRmaOrderV2_NullRequestHeaders_Success() throws Exception {
        // Arrange
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(null, validRequest.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(null, validRequest, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(null, validRequest.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwo(validRequest, xClientVersion);
    }

    @Test(testName = "testRmaOrderV2_ResponseWithNullStatus_NoSmsSent")
    public void testRmaOrderV2_ResponseWithNullStatus_NoSmsSent() throws Exception {
        // Arrange
        OrderResponseDTO responseWithNullStatus = createResponseWithNullStatus();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, validRequest.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(responseWithNullStatus);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(requestHeaders, validRequest, xClientVersion);

        // Assert
        assertNotNull(result);
        assertNull(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, validRequest.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwo(validRequest, xClientVersion);
        verify(salesOrderServiceV3, never()).sendSms(anyString(), anyString(), anyString(), any());
        verify(salesOrderServiceV3, never()).createDropOff(anyString(), anyString(), anyString(), any());
    }

    @Test(testName = "testRmaOrderV2_ResponseWithFalseStatus_NoSmsSent")
    public void testRmaOrderV2_ResponseWithFalseStatus_NoSmsSent() throws Exception {
        // Arrange
        OrderResponseDTO responseWithFalseStatus = createResponseWithFalseStatus();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, validRequest.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(responseWithFalseStatus);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(requestHeaders, validRequest, xClientVersion);

        // Assert
        assertNotNull(result);
        assertFalse(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, validRequest.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwo(validRequest, xClientVersion);
        verify(salesOrderServiceV3, never()).sendSms(anyString(), anyString(), anyString(), any());
        verify(salesOrderServiceV3, never()).createDropOff(anyString(), anyString(), anyString(), any());
    }

    @Test(testName = "testRmaOrderV2_ResponseWithNullResponse_NoSmsSent")
    public void testRmaOrderV2_ResponseWithNullResponse_NoSmsSent() throws Exception {
        // Arrange
        OrderResponseDTO responseWithNullResponse = createResponseWithNullResponse();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, validRequest.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(responseWithNullResponse);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(requestHeaders, validRequest, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        assertNull(result.getResponse());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, validRequest.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwo(validRequest, xClientVersion);
        verify(salesOrderServiceV3, never()).sendSms(anyString(), anyString(), anyString(), any());
        verify(salesOrderServiceV3, never()).createDropOff(anyString(), anyString(), anyString(), any());
    }

    @Test(testName = "testRmaOrderV2_WithNullDropOffRequest_NoSmsSent")
    public void testRmaOrderV2_WithNullDropOffRequest_NoSmsSent() throws Exception {
        // Arrange
        RMAOrderV2Request requestWithNullDropOff = createRequestWithNullDropOff();
        OrderResponseDTO responseWithNullDropOff = createResponseWithNullDropOff();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, requestWithNullDropOff.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwo(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(responseWithNullDropOff);

        // Act
        OrderResponseDTO result = orderWriteController.rmaOrderV2(requestHeaders, requestWithNullDropOff, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, requestWithNullDropOff.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwo(requestWithNullDropOff, xClientVersion);
        verify(salesOrderServiceV3, never()).sendSms(anyString(), anyString(), anyString(), any());
        verify(salesOrderServiceV3, never()).createDropOff(anyString(), anyString(), anyString(), any());
    }

    /**
     * Helper method to create a valid RMAOrderV2Request
     */
    private RMAOrderV2Request createValidRMAOrderV2Request() {
        RMAOrderV2Request request = new RMAOrderV2Request();
        request.setCustomerId(12345);
        request.setOrderId(67890);
        request.setStoreId(1);
        request.setIsDropOffRequest(false);
        request.setOmsRequest(false);
        request.setReturnFeeAmount(0.0);
        return request;
    }

    /**
     * Helper method to create a request with items
     */
    private RMAOrderV2Request createRequestWithItems() {
        RMAOrderV2Request request = new RMAOrderV2Request();
        request.setCustomerId(12345);
        request.setOrderId(67890);
        request.setStoreId(1);

        List<RMAOrderItemV2Request> items = new ArrayList<>();
        RMAOrderItemV2Request item = new RMAOrderItemV2Request();
        item.setParentOrderItemId(1001);
        item.setReturnQuantity(2);
        item.setReasonId(1);
        items.add(item);

        request.setItems(items);
        return request;
    }

    /**
     * Helper method to create a request with split order ID
     */
    private RMAOrderV2Request createRequestWithSplitOrder() {
        RMAOrderV2Request request = new RMAOrderV2Request();
        request.setCustomerId(12345);
        request.setOrderId(67890);
        request.setStoreId(1);
        request.setSplitOrderId(10001);
        return request;
    }

    /**
     * Helper method to create a request with drop off
     */
    private RMAOrderV2Request createRequestWithDropOff() {
        RMAOrderV2Request request = new RMAOrderV2Request();
        request.setCustomerId(12345);
        request.setOrderId(67890);
        request.setStoreId(1);
        request.setIsDropOffRequest(true);
        request.setDropOffDetails("Drop off at store location");
        request.setCityName("Riyadh");
        request.setCpId("CP123");
        return request;
    }

    /**
     * Helper method to create a request with non-drop off
     */
    private RMAOrderV2Request createRequestWithNonDropOff() {
        RMAOrderV2Request request = new RMAOrderV2Request();
        request.setCustomerId(12345);
        request.setOrderId(67890);
        request.setStoreId(1);
        request.setIsDropOffRequest(false);
        return request;
    }

    /**
     * Helper method to create a request with OMS flag
     */
    private RMAOrderV2Request createRequestWithOms() {
        RMAOrderV2Request request = new RMAOrderV2Request();
        request.setCustomerId(12345);
        request.setOrderId(67890);
        request.setStoreId(1);
        request.setOmsRequest(true);
        return request;
    }

    /**
     * Helper method to create a request with refund amounts
     */
    private RMAOrderV2Request createRequestWithRefundAmounts() {
        RMAOrderV2Request request = new RMAOrderV2Request();
        request.setCustomerId(12345);
        request.setOrderId(67890);
        request.setStoreId(1);
        request.setRefundAmountDebited(100.0);
        request.setRefundAmountCredited(50.0);
        request.setTotalRefundAmount(150.0);
        request.setRmaPaymentMethod("CREDIT_CARD");
        request.setReturnIncPayfortId("PAYFORT123");
        return request;
    }

    /**
     * Helper method to create a request with return fee amount
     */
    private RMAOrderV2Request createRequestWithReturnFee() {
        RMAOrderV2Request request = new RMAOrderV2Request();
        request.setCustomerId(12345);
        request.setOrderId(67890);
        request.setStoreId(1);
        request.setReturnFeeAmount(25.0);
        return request;
    }

    /**
     * Helper method to create a request with null drop off request
     */
    private RMAOrderV2Request createRequestWithNullDropOff() {
        RMAOrderV2Request request = new RMAOrderV2Request();
        request.setCustomerId(12345);
        request.setOrderId(67890);
        request.setStoreId(1);
        request.setIsDropOffRequest(null);
        return request;
    }

    /**
     * Helper method to create a success response
     */
    private OrderResponseDTO createSuccessResponse() {
        OrderResponseDTO response = new OrderResponseDTO();
        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg("RMA order created successfully");

        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setRmaIncId("RMA-12345");
        orderResponse.setOrderId(67890);
        orderResponse.setStatus("pending");
        orderResponse.setEmail("test@example.com");
        orderResponse.setCustomerId("12345");
        orderResponse.setStoreId("1");

        response.setResponse(orderResponse);
        return response;
    }

    /**
     * Helper method to create a response with drop off
     */
    private OrderResponseDTO createResponseWithDropOff() {
        OrderResponseDTO response = new OrderResponseDTO();
        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg("RMA order created successfully");

        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setRmaIncId("RMA-12345");
        orderResponse.setOrderId(67890);
        orderResponse.setStatus("pending");
        orderResponse.setEmail("test@example.com");
        orderResponse.setCustomerId("12345");
        orderResponse.setStoreId("1");

        response.setResponse(orderResponse);
        return response;
    }

    /**
     * Helper method to create a response with non-drop off
     */
    private OrderResponseDTO createResponseWithNonDropOff() {
        OrderResponseDTO response = new OrderResponseDTO();
        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg("RMA order created successfully");

        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setRmaIncId("RMA-12345");
        orderResponse.setOrderId(67890);
        orderResponse.setStatus("pending");
        orderResponse.setEmail("test@example.com");
        orderResponse.setCustomerId("12345");
        orderResponse.setStoreId("1");

        response.setResponse(orderResponse);
        return response;
    }

    /**
     * Helper method to create a response with null status
     */
    private OrderResponseDTO createResponseWithNullStatus() {
        OrderResponseDTO response = new OrderResponseDTO();
        response.setStatus(null);
        response.setStatusCode("200");
        response.setStatusMsg("RMA order created successfully");
        return response;
    }

    /**
     * Helper method to create a response with false status
     */
    private OrderResponseDTO createResponseWithFalseStatus() {
        OrderResponseDTO response = new OrderResponseDTO();
        response.setStatus(false);
        response.setStatusCode("400");
        response.setStatusMsg("RMA order creation failed");
        return response;
    }

    /**
     * Helper method to create a response with null response
     */
    private OrderResponseDTO createResponseWithNullResponse() {
        OrderResponseDTO response = new OrderResponseDTO();
        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg("RMA order created successfully");
        response.setResponse(null);
        return response;
    }

    /**
     * Helper method to create a response with null drop off request
     */
    private OrderResponseDTO createResponseWithNullDropOff() {
        OrderResponseDTO response = new OrderResponseDTO();
        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg("RMA order created successfully");

        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setRmaIncId("RMA-12345");
        orderResponse.setOrderId(67890);
        orderResponse.setStatus("pending");
        orderResponse.setEmail("test@example.com");
        orderResponse.setCustomerId("12345");
        orderResponse.setStoreId("1");

        response.setResponse(orderResponse);
        return response;
    }
}
