package org.styli.services.customer.pojo.response;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.pojo.registration.response.Product.ProductValue;
import org.testng.annotations.Test;

public class CustomerOmsResponseTest {
	@Test
	public void testCustomerOmsResponse() {
		CustomerOmsResponse response = new CustomerOmsResponse();
		response.setCustomerId(1);
		response.setCustomerName("John Doe");
		response.setCustomerEmail("johndoe@example.com");
		response.setGroup("VIP");
		response.setMobileNumber("+919876543210");
		response.setCountryName("India");
		response.setProvinceName("Maharashtra");
		response.setCustomerSince("2022-05-01");
		response.setWebsite("www.example.com");
		response.setGender(1); // Male
		response.setOptedForwhatsapp("Yes");
		response.setIsReferral("No");
		response.setSignUpBy("John Doe");
		response.setCurrentSignInBy("John Doe");
		response.setLastSignedInTimestamp(new Date());

		assertEquals(response.getCustomerId(), 1);
		assertEquals(response.getCustomerName(), "John Doe");
		assertEquals(response.getCustomerEmail(), "johndoe@example.com");
		assertEquals(response.getGroup(), "VIP");
		assertEquals(response.getMobileNumber(), "+919876543210");
		assertEquals(response.getCountryName(), "India");
		assertEquals(response.getProvinceName(), "Maharashtra");
		assertEquals(response.getCustomerSince(), "2022-05-01");
		assertEquals(response.getWebsite(), "www.example.com");
		assertEquals(response.getGender(), 1);
		assertEquals(response.getOptedForwhatsapp(), "Yes");
		assertEquals(response.getIsReferral(), "No");
		assertEquals(response.getSignUpBy(), "John Doe");
		assertEquals(response.getCurrentSignInBy(), "John Doe");
		assertNotNull(response.getLastSignedInTimestamp());
	}

	@Test
	public void testIdTokenPayload() {
		IdTokenPayload payload = new IdTokenPayload();
		payload.setIss("https://example.com");
		payload.setAud("https://client.example.com");
		payload.setExp(System.currentTimeMillis() + 1000 * 60 * 60 * 24);
		payload.setIat(System.currentTimeMillis());
		payload.setSub("1234567890");
		payload.setAt_hash("1234567890");
		payload.setAuth_time(System.currentTimeMillis());
		payload.setNonce_supported(true);
		payload.setEmail_verified(true);
		payload.setEmail("johndoe@example.com");

		assertEquals(payload.getIss(), "https://example.com");
		assertEquals(payload.getAud(), "https://client.example.com");
		assertNotNull(payload.getExp());
		assertNotNull(payload.getIat());
		assertEquals(payload.getSub(), "1234567890");
		assertEquals(payload.getAt_hash(), "1234567890");
		assertNotNull(payload.getAuth_time());
		assertEquals(payload.getNonce_supported(), true);
		assertEquals(payload.getEmail_verified(), true);
		assertEquals(payload.getEmail(), "johndoe@example.com");
	}

	@Test
	public void testMulinProductDescRes() {
		MulinProductDescRes res = new MulinProductDescRes();
		List<MulinProductDetails> details = new ArrayList<>();
		MulinProductDetails detail1 = new MulinProductDetails();
		detail1.setBeautyCode("Color");
		detail1.setLabel("Red");
		detail1.setName("name");
		details.add(detail1);
		MulinProductDetails detail2 = new MulinProductDetails();
		detail2.setBeautyCode("Color");
		detail2.setLabel("Red");
		detail2.setName("name");
		details.add(detail2);
		res.setBeautyAttrs(details);
		res.setStatusCode(200);

		assertEquals(res.getBeautyAttrs().size(), 2);
		assertEquals(res.getBeautyAttrs().get(0).getBeautyCode(), "Color");
		assertEquals(res.getBeautyAttrs().get(0).getLabel(), "Red");
		assertEquals(res.getStatusCode(), 200);
	}

	@Test
	public void testMulinProductDetails() {
		MulinProductDetails details = new MulinProductDetails();
		details.setBeautyCode("123456");
		details.setLabel("Red");
		details.setName("Lipstick");

		assertEquals(details.getBeautyCode(), "123456");
		assertEquals(details.getLabel(), "Red");
		assertEquals(details.getName(), "Lipstick");
	}

	@Test
	public void testProductInfoValue() {
		ProductInfoValue value = new ProductInfoValue();
		value.setName("Lipstick");
		value.setBeautyCode("123456");

		assertEquals(value.getName(), "Lipstick");
		assertEquals(value.getBeautyCode(), "123456");
	}

	@Test
	public void testProductInventoryRes() {
		ProductInventoryRes res = new ProductInventoryRes();
		InventoryMetaData meta = new InventoryMetaData();
		meta.setCode("10");
		meta.setDetails("detail");
		meta.setMessage("msg");
		meta.setStatus(true);
		res.setMeta(meta);
		res.setStatusCode("200");
		res.setStatus(true);
		res.setStatusMsg("Success");
		List<ProductValue> details = new ArrayList<>();
		ProductValue detail1 = new ProductValue();
		detail1.setAllSkuNonZero(true);
		detail1.setProcuctId("1");
		detail1.setValue("10");
		detail1.setSku("1");
		details.add(detail1);
		ProductValue detail2 = new ProductValue();
		detail1.setAllSkuNonZero(true);
		detail1.setProcuctId("1");
		detail1.setValue("10");
		details.add(detail2);
		res.setResponse(details);
		ErrorType type = new ErrorType();
		res.setError(type);

		assertEquals(res.getMeta().getCode(), "10");
		assertEquals(res.getMeta().getDetails(), "detail");
		assertEquals(res.getMeta().getMessage(), "msg");
		assertEquals(res.getStatusCode(), "200");
		assertEquals(res.getStatus(), true);
		assertEquals(res.getStatusMsg(), "Success");
		assertEquals(res.getResponse().size(), 2);
		assertEquals(res.getResponse().get(0).getProcuctId(), "1");
		assertEquals(res.getResponse().get(0).getValue(), "10");
		assertEquals(res.getResponse().get(0).getSku(), "1");
		assertNotNull(res.getError());
	}

	@Test
	public void testTokenResponse() {
		TokenResponse res = new TokenResponse();
		res.setAccess_token("1234567890");
		res.setToken_type("Bearer");
		res.setExpires_in(1000 * 60 * 60 * 24l);
		res.setRefresh_token("9876543210");
		res.setId_token("abcdefghijklmnopqrstuvwxyz");

		assertEquals(res.getAccess_token(), "1234567890");
		assertEquals(res.getToken_type(), "Bearer");
		assertEquals(res.getExpires_in(), 1000 * 60 * 60 * 24);
		assertEquals(res.getRefresh_token(), "9876543210");
		assertEquals(res.getId_token(), "abcdefghijklmnopqrstuvwxyz");
	}
}
