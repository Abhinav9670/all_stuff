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

import org.styli.services.order.pojo.cancel.Reason;
import org.styli.services.order.pojo.order.RMAOrderInitV2Response;
import org.styli.services.order.pojo.order.RMAOrderInitV2ResponseDTO;
import org.styli.services.order.pojo.order.RMAOrderItemV2Request;
import org.styli.services.order.pojo.order.RMAOrderV2Request;
import org.styli.services.order.pojo.response.Order.OrderAddress;
import org.styli.services.order.pojo.response.Order.RMAItem;
import org.styli.services.order.service.SalesOrderRMAService;
import org.styli.services.order.service.SalesOrderServiceV2;
import org.styli.services.order.utility.Constants;

/**
 * JUnit test cases for OrderWriteController.v2/rma/init endpoint
 *
 * @author Test Author
 */
public class OrderWriteControllerV2RmaInitTest extends AbstractTestNGSpringContextTests {

    @InjectMocks
    private OrderWriteController orderWriteController;

    @Mock
    private SalesOrderRMAService salesOrderRMAService;

    @Mock
    private SalesOrderServiceV2 salesOrderServiceV2;

    @Mock
    private Constants constants;

    private RMAOrderV2Request validRequest;
    private RMAOrderInitV2ResponseDTO successResponse;
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

    @Test(testName = "testRmaInitV2_JwtEnabled_Success")
    public void testRmaInitV2_JwtEnabled_Success() throws Exception {
        // Arrange
        String xClientVersion = "2.1.0";
        // Changed from doNothing() to when().thenReturn() - assuming authenticateCheck returns something
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, validRequest.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwoInit(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        RMAOrderInitV2ResponseDTO result = orderWriteController.rmaOrderInitV2(requestHeaders, validRequest, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        assertEquals(result.getStatusCode(), "200");
        assertEquals(result.getStatusMsg(), "RMA initialization successful");
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, validRequest.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwoInit(validRequest, xClientVersion);
    }

    @Test(testName = "testRmaInitV2_JwtEnabled_AuthenticationThrowsException")
    public void testRmaInitV2_JwtEnabled_AuthenticationThrowsException() throws Exception {
        // Arrange
        String xClientVersion = "2.1.0";
        doThrow(new RuntimeException("Authentication failed"))
                .when(salesOrderServiceV2).authenticateCheck(requestHeaders, validRequest.getCustomerId());

        // Act & Assert
        try {
            orderWriteController.rmaOrderInitV2(requestHeaders, validRequest, xClientVersion);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Authentication failed");
            verify(salesOrderServiceV2).authenticateCheck(requestHeaders, validRequest.getCustomerId());
            verify(salesOrderRMAService, never()).rmaOrderVersionTwoInit(any(RMAOrderV2Request.class), anyString());
        }
    }

    @Test(testName = "testRmaInitV2_JwtDisabled_Success")
    public void testRmaInitV2_JwtDisabled_Success() throws Exception {
        // Arrange
        jwtFlag = "0";
        ReflectionTestUtils.setField(orderWriteController, "jwtFlag", jwtFlag);
        String xClientVersion = "2.1.0";
        when(salesOrderRMAService.rmaOrderVersionTwoInit(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        RMAOrderInitV2ResponseDTO result = orderWriteController.rmaOrderInitV2(requestHeaders, validRequest, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        assertEquals(result.getStatusCode(), "200");
        assertEquals(result.getStatusMsg(), "RMA initialization successful");
        verify(salesOrderServiceV2, never()).authenticateCheck(anyMap(), anyInt());
        verify(salesOrderRMAService).rmaOrderVersionTwoInit(validRequest, xClientVersion);
    }

    @Test(testName = "testRmaInitV2_WithItems_Success")
    public void testRmaInitV2_WithItems_Success() throws Exception {
        // Arrange
        RMAOrderV2Request requestWithItems = createRequestWithItems();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, requestWithItems.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwoInit(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        RMAOrderInitV2ResponseDTO result = orderWriteController.rmaOrderInitV2(requestHeaders, requestWithItems, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, requestWithItems.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwoInit(requestWithItems, xClientVersion);
    }

    @Test(testName = "testRmaInitV2_WithSplitOrderId_Success")
    public void testRmaInitV2_WithSplitOrderId_Success() throws Exception {
        // Arrange
        RMAOrderV2Request requestWithSplitOrder = createRequestWithSplitOrder();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, requestWithSplitOrder.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwoInit(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        RMAOrderInitV2ResponseDTO result = orderWriteController.rmaOrderInitV2(requestHeaders, requestWithSplitOrder, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, requestWithSplitOrder.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwoInit(requestWithSplitOrder, xClientVersion);
    }

    @Test(testName = "testRmaInitV2_WithDropOffRequest_Success")
    public void testRmaInitV2_WithDropOffRequest_Success() throws Exception {
        // Arrange
        RMAOrderV2Request requestWithDropOff = createRequestWithDropOff();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, requestWithDropOff.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwoInit(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        RMAOrderInitV2ResponseDTO result = orderWriteController.rmaOrderInitV2(requestHeaders, requestWithDropOff, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, requestWithDropOff.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwoInit(requestWithDropOff, xClientVersion);
    }

    @Test(testName = "testRmaInitV2_WithOmsRequest_Success")
    public void testRmaInitV2_WithOmsRequest_Success() throws Exception {
        // Arrange
        RMAOrderV2Request requestWithOms = createRequestWithOms();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, requestWithOms.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwoInit(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        RMAOrderInitV2ResponseDTO result = orderWriteController.rmaOrderInitV2(requestHeaders, requestWithOms, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, requestWithOms.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwoInit(requestWithOms, xClientVersion);
    }

    @Test(testName = "testRmaInitV2_WithRefundAmounts_Success")
    public void testRmaInitV2_WithRefundAmounts_Success() throws Exception {
        // Arrange
        RMAOrderV2Request requestWithRefundAmounts = createRequestWithRefundAmounts();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, requestWithRefundAmounts.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwoInit(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        RMAOrderInitV2ResponseDTO result = orderWriteController.rmaOrderInitV2(requestHeaders, requestWithRefundAmounts, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, requestWithRefundAmounts.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwoInit(requestWithRefundAmounts, xClientVersion);
    }

    @Test(testName = "testRmaInitV2_WithReturnFeeAmount_Success")
    public void testRmaInitV2_WithReturnFeeAmount_Success() throws Exception {
        // Arrange
        RMAOrderV2Request requestWithReturnFee = createRequestWithReturnFee();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, requestWithReturnFee.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwoInit(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        RMAOrderInitV2ResponseDTO result = orderWriteController.rmaOrderInitV2(requestHeaders, requestWithReturnFee, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, requestWithReturnFee.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwoInit(requestWithReturnFee, xClientVersion);
    }

    @Test(testName = "testRmaInitV2_ServiceThrowsException")
    public void testRmaInitV2_ServiceThrowsException() throws Exception {
        // Arrange
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, validRequest.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwoInit(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        try {
            orderWriteController.rmaOrderInitV2(requestHeaders, validRequest, xClientVersion);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Service error");
            verify(salesOrderServiceV2).authenticateCheck(requestHeaders, validRequest.getCustomerId());
            verify(salesOrderRMAService).rmaOrderVersionTwoInit(validRequest, xClientVersion);
        }
    }

    @Test(testName = "testRmaInitV2_NullXClientVersion_Success")
    public void testRmaInitV2_NullXClientVersion_Success() throws Exception {
        // Arrange
        String xClientVersion = null;
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, validRequest.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwoInit(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        RMAOrderInitV2ResponseDTO result = orderWriteController.rmaOrderInitV2(requestHeaders, validRequest, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, validRequest.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwoInit(validRequest, xClientVersion);
    }

    @Test(testName = "testRmaInitV2_NullRequestHeaders_Success")
    public void testRmaInitV2_NullRequestHeaders_Success() throws Exception {
        // Arrange
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(null, validRequest.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwoInit(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(successResponse);

        // Act
        RMAOrderInitV2ResponseDTO result = orderWriteController.rmaOrderInitV2(null, validRequest, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        verify(salesOrderServiceV2).authenticateCheck(null, validRequest.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwoInit(validRequest, xClientVersion);
    }

    @Test(testName = "testRmaInitV2_DetailedResponseValidation_Success")
    public void testRmaInitV2_DetailedResponseValidation_Success() throws Exception {
        // Arrange
        RMAOrderInitV2ResponseDTO detailedResponse = createDetailedResponse();
        String xClientVersion = "2.1.0";
        when(salesOrderServiceV2.authenticateCheck(requestHeaders, validRequest.getCustomerId())).thenReturn(null);
        when(salesOrderRMAService.rmaOrderVersionTwoInit(any(RMAOrderV2Request.class), eq(xClientVersion)))
                .thenReturn(detailedResponse);

        // Act
        RMAOrderInitV2ResponseDTO result = orderWriteController.rmaOrderInitV2(requestHeaders, validRequest, xClientVersion);

        // Assert
        assertNotNull(result);
        assertTrue(result.getStatus());
        assertEquals(result.getStatusCode(), "200");
        assertEquals(result.getStatusMsg(), "RMA initialization successful");

        RMAOrderInitV2Response response = result.getResponse();
        assertNotNull(response);
        assertEquals(response.getCustomerId(), "12345");
        assertEquals(response.getOrderIncrementId(), "ORD-12345");
        assertEquals(response.getReturnRequestId(), "RMA-12345");
        assertEquals(response.getRmaCount(), 1);
        assertNotNull(response.getReasons());
        assertNotNull(response.getItems());
        assertNotNull(response.getPickupAddress());

        verify(salesOrderServiceV2).authenticateCheck(requestHeaders, validRequest.getCustomerId());
        verify(salesOrderRMAService).rmaOrderVersionTwoInit(validRequest, xClientVersion);
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
     * Helper method to create a success response
     */
    private RMAOrderInitV2ResponseDTO createSuccessResponse() {
        RMAOrderInitV2ResponseDTO response = new RMAOrderInitV2ResponseDTO();
        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg("RMA initialization successful");

        RMAOrderInitV2Response rmaResponse = new RMAOrderInitV2Response();
        rmaResponse.setCustomerId("12345");
        rmaResponse.setOrderIncrementId("ORD-12345");
        rmaResponse.setReturnRequestId("RMA-12345");
        rmaResponse.setRmaCount(1);

        response.setResponse(rmaResponse);
        return response;
    }

    /**
     * Helper method to create a detailed response
     */
    private RMAOrderInitV2ResponseDTO createDetailedResponse() {
        RMAOrderInitV2ResponseDTO response = new RMAOrderInitV2ResponseDTO();
        response.setStatus(true);
        response.setStatusCode("200");
        response.setStatusMsg("RMA initialization successful");

        RMAOrderInitV2Response rmaResponse = new RMAOrderInitV2Response();
        rmaResponse.setCustomerId("12345");
        rmaResponse.setOrderIncrementId("ORD-12345");
        rmaResponse.setReturnRequestId("RMA-12345");
        rmaResponse.setRmaCount(1);

        // Mock reasons
        List<Reason> reasons = new ArrayList<>();
        Reason reason = new Reason("1", "Defective Product");
        reasons.add(reason);
        rmaResponse.setReasons(reasons);

        // Mock items
        List<RMAItem> items = new ArrayList<>();
        RMAItem item = new RMAItem();
        item.setParentOrderItemId("1001");
        item.setName("Test Product");
        item.setSku("TEST-SKU-001");
        item.setQty("2");
        items.add(item);
        rmaResponse.setItems(items);

        // Mock pickup address
        OrderAddress pickupAddress = new OrderAddress();
        pickupAddress.setFirstName("John");
        pickupAddress.setLastName("Doe");
        pickupAddress.setStreetAddress("123 Main St");
        pickupAddress.setCity("Riyadh");
        pickupAddress.setPostCode("12345");
        pickupAddress.setCountry("SA");
        rmaResponse.setPickupAddress(pickupAddress);

        // Mock payment information
        rmaResponse.setPaymentMethod("CREDIT_CARD");
        rmaResponse.setCardNumber("****1234");
        rmaResponse.setPaymentOption("VISA");

        // Mock refund information
        rmaResponse.setRefundGrandTotal("150.00");
        rmaResponse.setRefundStoreCreditTotal("50.00");
        rmaResponse.setRefundPrepaidTotal("100.00");
        rmaResponse.setReturnChargeApplicable(true);
        rmaResponse.setReturnAmountFee(25.0);
        rmaResponse.setReturnAmountToBePay(25.0);

        response.setResponse(rmaResponse);
        return response;
    }
}