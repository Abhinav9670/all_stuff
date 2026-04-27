package org.styli.services.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.db.product.config.firebase.FirebaseAuthentication;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.pojo.AccountDeletionEligibleRequest;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.KsaCredentials;
import org.styli.services.order.pojo.NavikDetails;
import org.styli.services.order.pojo.OrderKeyDetails;
import org.styli.services.order.pojo.OrderRestrictionDetails;
import org.styli.services.order.pojo.PayfortDetails;
import org.styli.services.order.pojo.PendingOrderNotfcnDetails;
import org.styli.services.order.pojo.WmsDetails;
import org.styli.services.order.pojo.order.AddStoreCreditRequest;
import org.styli.services.order.pojo.order.RMAOrderItemV2Request;
import org.styli.services.order.pojo.order.RMAOrderV2Request;
import org.styli.services.order.pojo.order.StoreCredit;
import org.styli.services.order.pojo.ratings.CustomerRatings;
import org.styli.services.order.pojo.ratings.DeleteCustomerRatingsReq;
import org.styli.services.order.pojo.ratings.RatingsResponse;
import org.styli.services.order.pojo.ratings.RetrieveOrderRatings;
import org.styli.services.order.pojo.ratings.RetrieveRatingsRequest;
import org.styli.services.order.pojo.ratings.RetrieveRatingsResponse;
import org.styli.services.order.pojo.ratings.UpdateCustomerRatingsReq;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;
import org.styli.services.order.pojo.response.V3.GetShipmentV3Response;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.service.EmailService;
import org.styli.services.order.service.SalesOrderRMAService;
import org.styli.services.order.service.SalesOrderServiceV3;
import org.styli.services.order.service.impl.SalesOrderServiceV2Impl;
import org.styli.services.order.service.impl.SalesOrderServiceV3Impl;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.consulValues.ConsulValues;
import org.styli.services.order.utility.consulValues.DeleteCustomer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class RatingsControllerTest {

	@InjectMocks
	private RatingsController ratingsController;
	
	@InjectMocks
	OrderOmsController orderOmsController;
	
	@Mock
	SalesOrderServiceV3Impl salesOrderServiceV3;
	@Mock
	SalesOrderRMAService salesOrderRMAService;
	@Mock
	EmailService emailService;

	@InjectMocks
	private MulinHelper mulinHelper;

	@InjectMocks
	private SalesOrderServiceV2Impl salesOrderServiceV2;

	@InjectMocks
	org.styli.services.order.utility.Constants constants;

	@Mock
	private RestTemplate restTemplate;
	@Mock
	JwtValidator validator;
	@Mock
	SalesOrderRepository salesOrderRepository;
	@Mock
	FirebaseAuthentication FirebaseAuthentication;

	Map<String, String> requestHeader;
	private List<Stores> storeList;
	SalesOrder salesOrder;

	@BeforeClass
	public void beforeClass() {

		requestHeader = new HashMap<>();
		requestHeader.put("x-source", "android");
		requestHeader.put("x-client-version", "11");
		requestHeader.put("token", "token");
		requestHeader.put("X-Header-Token", "test@mail.com");
		try {
			// Prepare mock Data
			Gson g = new Gson();
			String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/store_list.json")));
			Type listType = new TypeToken<ArrayList<Stores>>() {
			}.getType();
			storeList = g.fromJson(storeData, listType);
		} catch (Exception e) {
		}
	}

	@BeforeTest
	public void beforeTest() {
		System.out.println("Initialise @BeforeTest ");
		MockitoAnnotations.initMocks(this);
		when(salesOrderRepository.updateRatingStatus(anyString(), anyInt())).thenReturn(1);
//		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "token,dwd,1,2");
//		ReflectionTestUtils.setField(configService, "externalAuthBearerToken", "token,ds,1,1");
	}

	@Test
	public void deleteRatingsTest() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		DeleteCustomerRatingsReq req = new DeleteCustomerRatingsReq();
		req.setCustomerId("1");
		req.setOrderId("1");
		CustomerRatings customerRatings = new CustomerRatings();
		customerRatings.setCustomerId("1");
		customerRatings.setOrderId("1");
		RatingsResponse body = new RatingsResponse();
		body.setStatus(true);
		body.setStatusCode("200");
		ResponseEntity<RatingsResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.DELETE),
				Mockito.any(HttpEntity.class), Mockito.eq(RatingsResponse.class))).thenReturn(response);

		RatingsResponse respo = ratingsController.deleteRatings(requestHeader, req);

		// Verify
		assertTrue(respo.isStatus());
		assertNotNull(respo);
	}

	@Test
	public void retrieveRatingsTest() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		RetrieveRatingsRequest customerRatings = new RetrieveRatingsRequest();
		customerRatings.setCustomerId("1");
		customerRatings.setOrderId("1");
		RetrieveOrderRatings rating = new RetrieveOrderRatings();
		RetrieveRatingsResponse body = new RetrieveRatingsResponse();
		body.setResponse(rating);
		ResponseEntity<RetrieveRatingsResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(RetrieveRatingsResponse.class))).thenReturn(response);

		RetrieveRatingsResponse respo = ratingsController.retrieveRatings(requestHeader, customerRatings);

		// Verify
		assertNotNull(respo);
	}

	@Test
	public void saveRatingsTest() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		CustomerRatings customerRatings = new CustomerRatings();

		customerRatings.setCustomerId("1");
		customerRatings.setOrderId("1");
		RatingsResponse body = new RatingsResponse();
		body.setStatus(true);
		body.setStatusCode("200");
		ResponseEntity<RatingsResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(RatingsResponse.class))).thenReturn(response);

		RatingsResponse respo = ratingsController.saveRatings(requestHeader, customerRatings);

		// Verify
		assertTrue(respo.isStatus());
		assertNotNull(respo);

	}

	@Test
	public void updateRatingsTest() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		UpdateCustomerRatingsReq req = new UpdateCustomerRatingsReq();
		req.setCustomerId("1");
		req.setOrderId("1");
		CustomerRatings customerRatings = new CustomerRatings();
		customerRatings.setCustomerId("1");
		customerRatings.setOrderId("1");
		RatingsResponse body = new RatingsResponse();
		body.setStatus(true);
		body.setStatusCode("200");
		ResponseEntity<RatingsResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.PUT),
				Mockito.any(HttpEntity.class), Mockito.eq(RatingsResponse.class))).thenReturn(response);

		RatingsResponse respo = ratingsController.updateRatings(requestHeader, req);

		// Verify
		assertTrue(respo.isStatus());
		assertNotNull(respo);
	}
	
	@Test
	public void getReturnShipment() {

		setStaticData();
		setSalseOrderData();
		GetOrderConsulValues val = new GetOrderConsulValues();
		val.setExternalAuthEnable(false);
		val.setInternalAuthEnable(true);
		val.setFirebaseAuthEnable(true);
		ReflectionTestUtils.setField(constants, "orderCredentials", val);
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		ConsulValues orderConsulValues = new ConsulValues();
		DeleteCustomer deletedcus = new DeleteCustomer();
		orderConsulValues.setDeleteCustomer(deletedcus);

		Constants.setOrderConsulValues(new Gson().toJson(orderConsulValues).toString());

		AccountDeletionEligibleRequest req = new AccountDeletionEligibleRequest();
		req.setCustomerId(1);
		GetShipmentV3Response respo=new GetShipmentV3Response();respo.setHasError(false);		
		when(salesOrderServiceV3.getReturnShipment(requestHeader, "1")).thenReturn(respo);
		GetShipmentV3Response respose = orderOmsController.getReturnShipment(requestHeader, "1",null);
		assertNotNull(respose);
	}
	
	@Test
	public void getReturnShipment2() {

		setStaticData();
		setSalseOrderData();
		GetOrderConsulValues val = new GetOrderConsulValues();
		val.setExternalAuthEnable(false);
		val.setInternalAuthEnable(true);
		val.setFirebaseAuthEnable(false);
		ReflectionTestUtils.setField(constants, "orderCredentials", val);
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		ConsulValues orderConsulValues = new ConsulValues();
		DeleteCustomer deletedcus = new DeleteCustomer();
		orderConsulValues.setDeleteCustomer(deletedcus);

		Constants.setOrderConsulValues(new Gson().toJson(orderConsulValues).toString());

		AccountDeletionEligibleRequest req = new AccountDeletionEligibleRequest();
		req.setCustomerId(1);
		GetShipmentV3Response respo=new GetShipmentV3Response();respo.setHasError(false);		
		when(salesOrderServiceV3.getReturnShipment(requestHeader, "1")).thenReturn(respo);
		GetShipmentV3Response respose = orderOmsController.getReturnShipment(requestHeader, "1",null);
		assertNotNull(respose);
	}
	
	@Test
	public void sendSMSAndMailForCaptureDropOff() {

		setStaticData();
		OrderKeyDetails keydetail = new OrderKeyDetails();
		keydetail.setMaximumOrderPedningOrderThreshold(10);
		keydetail.setReferralOrderLastHours(1);
		keydetail.setPendingOrderNotfcnDetails(new PendingOrderNotfcnDetails());
		LocalDateTime currentTime = LocalDateTime.now();
		LocalDateTime previousDay = currentTime.minus(2, ChronoUnit.DAYS);
		Timestamp timestamp = Timestamp.valueOf(previousDay);
		keydetail.setPendingOrderNotificationInMins(timestamp.getNanos());
		keydetail.setOmsServiceBaseUrl("url");
		keydetail.setBrazeAttributePushLimit(1);
		keydetail.setEmail("abc@mail.com");
		GetOrderConsulValues val = new GetOrderConsulValues();
		val.setOrderDetails(keydetail);
		val.setExternalAuthEnable(false);
		val.setInternalAuthEnable(true);
		val.setFirebaseAuthEnable(false);
		ReflectionTestUtils.setField(constants, "orderCredentials", val);
		setSalseOrderData();
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		ConsulValues orderConsulValues = new ConsulValues();
		DeleteCustomer deletedcus = new DeleteCustomer();
		orderConsulValues.setDeleteCustomer(deletedcus);

		Constants.setOrderConsulValues(new Gson().toJson(orderConsulValues).toString());
		
		when(salesOrderRepository.findAuthorizationCaptureDropOffOrderList())
		.thenReturn(Arrays.asList(salesOrder));
		
		AddStoreCreditRequest req = new AddStoreCreditRequest();
		StoreCredit strc = new StoreCredit();
		strc.setStore("1");
		strc.setStoreId(1);
		strc.setEmailId("test@mailinator.com");
		strc.setStoreCredit(new BigDecimal(10));

		req.setStoreCredits(Arrays.asList(strc));
		orderOmsController.sendSMSAndMailForCaptureDropOff(null, "token");
	}
	
	@Test
	public void rmaOrderV2Test() {

		setStaticData();
		GetOrderConsulValues val = new GetOrderConsulValues();
		val.setExternalAuthEnable(false);
		val.setInternalAuthEnable(true);
		val.setFirebaseAuthEnable(true);
		ReflectionTestUtils.setField(constants, "orderCredentials", val);
		setSalseOrderData();
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		ConsulValues orderConsulValues = new ConsulValues();
		DeleteCustomer deletedcus = new DeleteCustomer();
		orderConsulValues.setDeleteCustomer(deletedcus);

		Constants.setOrderConsulValues(new Gson().toJson(orderConsulValues).toString());
		
		when(salesOrderRepository.findAuthorizationCaptureDropOffOrderList())
		.thenReturn(Arrays.asList(salesOrder));
		OrderResponseDTO dto=new OrderResponseDTO();
		dto.setStatus(true);
		when(salesOrderRMAService.rmaOrderVersionTwo(any(), anyString())).thenReturn(dto);
		
		
		AddStoreCreditRequest req = new AddStoreCreditRequest();
		StoreCredit strc = new StoreCredit();
		strc.setStore("1");
		strc.setStoreId(1);
		strc.setEmailId("test@mailinator.com");
		strc.setStoreCredit(new BigDecimal(10));
		RMAOrderItemV2Request v2req = new RMAOrderItemV2Request();
		v2req.setParentOrderItemId(1);
		v2req.setReasonId(1);
		v2req.setReturnQuantity(1);
		List<RMAOrderItemV2Request> items = new ArrayList<>();
		items.add(v2req);
		RMAOrderV2Request rma = new RMAOrderV2Request();
		rma.setItems(items);
		rma.setCustomerId(1);
		rma.setStoreId(1);
		rma.setOrderId(1);
		rma.setIsDropOffRequest(true);

		req.setStoreCredits(Arrays.asList(strc));
		OrderResponseDTO respo = orderOmsController.rmaOrderV2(requestHeader,rma, null);
	}
	
	@Test
	public void rmaOrderV22Test() {

		setStaticData();
		GetOrderConsulValues val = new GetOrderConsulValues();
		val.setExternalAuthEnable(false);
		val.setInternalAuthEnable(true);
		val.setFirebaseAuthEnable(false);
		ReflectionTestUtils.setField(constants, "orderCredentials", val);
		setSalseOrderData();
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		ConsulValues orderConsulValues = new ConsulValues();
		DeleteCustomer deletedcus = new DeleteCustomer();
		orderConsulValues.setDeleteCustomer(deletedcus);

		Constants.setOrderConsulValues(new Gson().toJson(orderConsulValues).toString());
		
		when(salesOrderRepository.findAuthorizationCaptureDropOffOrderList())
		.thenReturn(Arrays.asList(salesOrder));
		OrderResponseDTO dto=new OrderResponseDTO();
		dto.setStatus(true);
		when(salesOrderRMAService.rmaOrderVersionTwo(any(), anyString())).thenReturn(dto);
		
		
		AddStoreCreditRequest req = new AddStoreCreditRequest();
		StoreCredit strc = new StoreCredit();
		strc.setStore("1");
		strc.setStoreId(1);
		strc.setEmailId("test@mailinator.com");
		strc.setStoreCredit(new BigDecimal(10));
		RMAOrderItemV2Request v2req = new RMAOrderItemV2Request();
		v2req.setParentOrderItemId(1);
		v2req.setReasonId(1);
		v2req.setReturnQuantity(1);
		List<RMAOrderItemV2Request> items = new ArrayList<>();
		items.add(v2req);
		RMAOrderV2Request rma = new RMAOrderV2Request();
		rma.setItems(items);
		rma.setCustomerId(1);
		rma.setStoreId(1);
		rma.setOrderId(1);
		rma.setIsDropOffRequest(true);

		req.setStoreCredits(Arrays.asList(strc));
		OrderResponseDTO respo = orderOmsController.rmaOrderV2(requestHeader,rma, null);
	}

	public void prepareOrderConstant() {
		OrderKeyDetails keydetail = new OrderKeyDetails();
		keydetail.setMaximumOrderPedningOrderThreshold(10);
		OrderRestrictionDetails dtl = new OrderRestrictionDetails();
		dtl.setMdpayfoirt(new BigDecimal(10));
		keydetail.setOrderRestrictionDetails(dtl);
		GetOrderConsulValues val = new GetOrderConsulValues();
		val.setOrderDetails(keydetail);
		val.setExternalAuthEnable(false);
		val.setInternalAuthEnable(false);
		val.setInternalAuthEnable(true);
		NavikDetails nav = new NavikDetails();
		nav.setReturnAwbCreateClubbingHrs(1);
		val.setNavik(nav);
		WmsDetails wmsd = new WmsDetails();
		wmsd.setWmsOrderPushMinutes(new Integer(10));
		val.setWms(wmsd);
		PayfortDetails payfort = new PayfortDetails();
		KsaCredentials cred = new KsaCredentials();
		cred.setPayfortKsaCardAccessCode(null);
		cred.setPayfortksaAmountMultiplier("1");
		payfort.setKsaCredentials(cred);
		val.setPayfort(payfort);
		ReflectionTestUtils.setField(constants, "orderCredentials", val);
		System.out.println("String: " + Constants.getPlainPhoneNo("+918783267890"));
	}

	private void setAuthenticateData() {
		// TODO Auto-generated method stub
		JwtUser jwtUser = new JwtUser();
		jwtUser.setCustomerId(1);
		when(validator.validate(anyString())).thenReturn(jwtUser);
	}

	private void setSalseOrderData() {
		salesOrder = new SalesOrder();
		salesOrder.setCustomerId(1);
		salesOrder.setStoreId(1);
		salesOrder.setStatus("packed");
		salesOrder.setGrandTotal(new BigDecimal(10));
		salesOrder.setWmsStatus(new Integer(1));
		salesOrder.setAmstorecreditAmount(new BigDecimal(10));
		salesOrder.setStoreToBaseRate(new BigDecimal(1));
		salesOrder.setAmstorecreditAmount(new BigDecimal(1));
		SalesOrderPayment pay = new SalesOrderPayment();
		pay.setMethod("payfort_fort_cc");
		Set<SalesOrderPayment> payitem = new HashSet<>();
		payitem.add(pay);
		salesOrder.setSalesOrderPayment(payitem);
		SalesOrderItem items = new SalesOrderItem();
		items.setSku("01");
		items.setItemId(1);
		items.setProductType("jcsdc");
		items.setPriceInclTax(new BigDecimal(10));
		items.setQtyOrdered(new BigDecimal(2));
		items.setQtyCanceled(new BigDecimal(1));
		items.setDiscountAmount(new BigDecimal(5));
		items.setRowTotalInclTax(new BigDecimal(10));
		items.setParentOrderItem(items);

		Set<SalesOrderItem> setitem = new HashSet<>();
		setitem.add(items);
		salesOrder.setSalesOrderItem(setitem);
		Set<SalesShipmentTrack> set = new HashSet<>();
		SalesShipmentTrack track = new SalesShipmentTrack();
		track.setTrackNumber("1");
		salesOrder.setSalesShipmentTrack(set);
		set.add(track);
	}

	private void setStaticData() {
		// TODO Auto-generated method stub
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		ReflectionTestUtils.setField(ratingsController, "mulinHelper", mulinHelper);
		ReflectionTestUtils.setField(ratingsController, "salesOrderServiceV2", salesOrderServiceV2);
	}
}
