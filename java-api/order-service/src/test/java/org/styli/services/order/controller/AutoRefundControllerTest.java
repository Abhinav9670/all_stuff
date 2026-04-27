package org.styli.services.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.styli.services.order.model.sales.RtoAutoRefund;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.TabbyDetails;
import org.styli.services.order.pojo.autoRefund.AutoRefundDTO;
import org.styli.services.order.pojo.autoRefund.RefundResponseDTO;
import org.styli.services.order.repository.SalesOrder.RtoAutoRefundRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.service.impl.AutoRefundServiceImpl;
import org.styli.services.order.service.impl.ConfigServiceImpl;
import org.styli.services.order.utility.Constants;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@SpringBootTest(classes = { AutoRefundControllerTest.class })
public class AutoRefundControllerTest extends AbstractTestNGSpringContextTests {

	@InjectMocks
	private AutoRefundServiceImpl autoRefundService;

	@InjectMocks
	private ConfigServiceImpl configService;

	@InjectMocks
	private AutoRefundController autoRefundController;

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	@InjectMocks
	Constants constants;

	@Mock
	RtoAutoRefundRepository rtoAutoRefundRepository;

	@Mock
	SalesOrderRepository salesOrderRepository;

	AutoRefundDTO autoRefundDTO;

	SalesOrder order;

	RtoAutoRefund rtoAutoRefund;

	@BeforeMethod
	public void setUp() {
		System.out.println("Initialise 	BeforeMethod ");
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
	}

	@BeforeTest
	public void beforeTest() {
		System.out.println("Initialise @BeforeTest ");
		MockitoAnnotations.initMocks(this);
		autoRefundDTO = new AutoRefundDTO();
		autoRefundDTO.setOffset(-1);
		autoRefundDTO.setPageSize(-1);
		autoRefundDTO.setIncrementIds(Arrays.asList("1", "2", "3"));
		autoRefundDTO.setStatus(Arrays.asList("completed", "pending", "completed"));
	}

	@Test
	public void testRefundOrderPendingList() {
		// Mock data
		setData(false);
		autoRefundDTO.setStatus(Arrays.asList("completed", "pending", "completed"));
		Page<SalesOrder> page = new PageImpl<>(Arrays.asList(order));
		when(salesOrderRepository.getRtoOrdersByStatus(any(), any())).thenReturn(page);
		when(salesOrderRepository.getRtoOrders(any(), any())).thenReturn(page);
		// Test the method
		RefundResponseDTO actualResponse = autoRefundController.refundOrderList(autoRefundDTO);

		// Verify the result
		assertEquals(actualResponse.getStatus(), true);
	}

	@Test
	public void testRefundOrdercompletedList() {
		// Mock data
		setData(false);
		autoRefundDTO.setStatus(Arrays.asList("completed", "completed", "completed"));
		Page<RtoAutoRefund> page = new PageImpl<>(Arrays.asList(rtoAutoRefund));
		when(rtoAutoRefundRepository.findByStatusIn(any(), any())).thenReturn(page);
		// Test the method
		RefundResponseDTO actualResponse = autoRefundController.refundOrderList(autoRefundDTO);

		// Verify the result
		assertEquals(actualResponse.getStatus(), true);
	}

	@Test
	public void testinitiateRefund() {
		// Mock data
		setData(false);
		autoRefundDTO.setStatus(Arrays.asList("completed", "completed", "completed"));
		Page<RtoAutoRefund> page = new PageImpl<>(Arrays.asList(rtoAutoRefund));
		when(salesOrderRepository.findByIncrementIdIn(any())).thenReturn(Arrays.asList(order));
		when(rtoAutoRefundRepository.findByIncrementId(any())).thenReturn(rtoAutoRefund);
		// Test the method
		RefundPaymentRespone actualResponse = autoRefundController.initiateRefund(autoRefundDTO, "token:salt:1:1");

		// Verify the result
		assertEquals(actualResponse.isStatus(), true);
		assertEquals(actualResponse.getStatusCode(), "200");
	}

	@Test
	public void testinitiateRefundprepaid() {
		// Mock data
		setData(true);
		ReflectionTestUtils.setField(autoRefundController, "configService", configService);
		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "ffyg,token");
		autoRefundDTO.setStatus(Arrays.asList("completed", "completed", "completed"));
		Page<RtoAutoRefund> page = new PageImpl<>(Arrays.asList(rtoAutoRefund));
		when(salesOrderRepository.findByIncrementIdIn(any())).thenReturn(Arrays.asList(order));
		when(rtoAutoRefundRepository.findByIncrementId(any())).thenReturn(null);
		// Test the method
		RefundPaymentRespone actualResponse = autoRefundController.initiateRefund(autoRefundDTO, "token");

		// Verify the result
		assertEquals(actualResponse.isStatus(), true);
		assertEquals(actualResponse.getStatusCode(), "200");
	}

	private void setData(Boolean flag) {
		// TODO Auto-generated method stub

		GetOrderConsulValues getOrderConsulValues = new GetOrderConsulValues();

		TabbyDetails tabbyDetails = new TabbyDetails();
		tabbyDetails.setRtoFromDate(new Date(1));
		getOrderConsulValues.setTabby(tabbyDetails);
		getOrderConsulValues.setInternalAuthEnable(flag);
		ReflectionTestUtils.setField(autoRefundController, "autoRefundService", autoRefundService);
		ReflectionTestUtils.setField(constants, "orderCredentials", getOrderConsulValues);

		Set<SalesOrderPayment> salesOrderPaymentlst = new HashSet<>();
		SalesOrderPayment salesOrderPayment = new SalesOrderPayment();
		salesOrderPayment.setMethod("free");
		salesOrderPaymentlst.add(salesOrderPayment);

		order = new SalesOrder();
		order.setEntityId(1);
		order.setGrandTotal(new BigDecimal(10));
		order.setSalesOrderPayment(salesOrderPaymentlst);
		rtoAutoRefund = new RtoAutoRefund();
		rtoAutoRefund.setStatus("true");
		order.setRtoAutoRefund(new HashSet<>((Collection) rtoAutoRefund));

	}

}
