package org.styli.services.customer.pojo.address.response;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;

import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.testng.annotations.Test;

public class CustomerAddreesTest {
	@Test
	public void testCustomerAddrees() {
		// Create a new CustomerAddrees object.
		CustomerAddrees customerAddrees = new CustomerAddrees();

		// Set the properties of the CustomerAddrees object.
		customerAddrees.setCustomerId(1);
		customerAddrees.setAddressId(2);
		customerAddrees.setFirstName("John");
		customerAddrees.setLastName("Doe");
		customerAddrees.setMobileNumber("1234567890");
		customerAddrees.setCity("London");
		customerAddrees.setFax("0123456789");
		customerAddrees.setStreetAddress("123 Main Street");
		customerAddrees.setTelephone("02079456789");
		customerAddrees.setCountry("UK");
		customerAddrees.setRegion("England");
		customerAddrees.setDefaultAddress(true);
		customerAddrees.setPostCode("W1A 1AA");
		customerAddrees.setRegionId("1");
		customerAddrees.setArea("Westminster");
		customerAddrees.setLandMark("Big Ben");
		customerAddrees.setBuildingNumber("1");
		customerAddrees.setStoreId(1);
		customerAddrees.setEmail("john.doe@gmail.com");
		customerAddrees.setJwtToken("1234567890");
		customerAddrees.setLatitude(new BigDecimal(10));
		customerAddrees.setLongitude(new BigDecimal(10));
		customerAddrees.setFormattedAddress("123 Main Street, London, W1A 1AA, UK");
		
		// Set new national address format fields
		customerAddrees.setUnitNumber("101");
		customerAddrees.setPostalCode("12345");
		customerAddrees.setShortAddress("ABCD1234");
		customerAddrees.setKsaAddressCompliant(true);

		// Assert that the properties of the CustomerAddrees object are set correctly.
		assertEquals(customerAddrees.getCustomerId(), 1);
		assertEquals(customerAddrees.getAddressId(), 2);
		assertEquals(customerAddrees.getFirstName(), "John");
		assertEquals(customerAddrees.getLastName(), "Doe");
		assertEquals(customerAddrees.getMobileNumber(), "1234567890");
		assertEquals(customerAddrees.getCity(), "London");
		assertEquals(customerAddrees.getFax(), "0123456789");
		assertEquals(customerAddrees.getStreetAddress(), "123 Main Street");
		assertEquals(customerAddrees.getTelephone(), "02079456789");
		assertEquals(customerAddrees.getCountry(), "UK");
		assertEquals(customerAddrees.getRegion(), "England");
		assertEquals(customerAddrees.isDefaultAddress(), true);
		assertEquals(customerAddrees.getPostCode(), "W1A 1AA");
		assertEquals(customerAddrees.getRegionId(), "1");
		assertEquals(customerAddrees.getArea(), "Westminster");
		assertEquals(customerAddrees.getLandMark(), "Big Ben");
		assertEquals(customerAddrees.getBuildingNumber(), "1");
		assertEquals(customerAddrees.getStoreId(), 1);
		assertEquals(customerAddrees.getEmail(), "john.doe@gmail.com");
		assertEquals(customerAddrees.getJwtToken(), "1234567890");
		assertEquals(customerAddrees.getUnitNumber(), "101");
		assertEquals(customerAddrees.getPostalCode(), "12345");
		assertEquals(customerAddrees.getShortAddress(), "ABCD1234");
		assertTrue(customerAddrees.getKsaAddressCompliant());
	}
	
	@Test
	public void testCustomerAddreesNationalAddressFormat() {
		CustomerAddrees customerAddrees = new CustomerAddrees();
		
		// Test unitNumber (non-mandatory)
		customerAddrees.setUnitNumber("Apt 5B");
		assertEquals(customerAddrees.getUnitNumber(), "Apt 5B");
		
		// Test postalCode (mandatory for KSA)
		customerAddrees.setPostalCode("12345");
		assertEquals(customerAddrees.getPostalCode(), "12345");
		
		// Test shortAddress (non-mandatory, format: 4 alphabets + 4 digits)
		customerAddrees.setShortAddress("ABCD1234");
		assertEquals(customerAddrees.getShortAddress(), "ABCD1234");
		
		// Test ksaAddressCompliant
		customerAddrees.setKsaAddressCompliant(true);
		assertTrue(customerAddrees.getKsaAddressCompliant());
		
		customerAddrees.setKsaAddressCompliant(false);
		assertTrue(!customerAddrees.getKsaAddressCompliant());
	}

	@Test
	public void testCustomerAddreesResponse() {
		// Create a new CustomerAddreesResponse object.
		CustomerAddreesResponse customerAddreesResponse = new CustomerAddreesResponse();

		// Set the properties of the CustomerAddreesResponse object.
		customerAddreesResponse.setStatus(true);
		customerAddreesResponse.setStatusCode("200");
		customerAddreesResponse.setStatusMsg("OK");
		customerAddreesResponse.setResponse(new CustomerAddressBody());
		ErrorType type = new ErrorType();
		customerAddreesResponse.setError(type);
		customerAddreesResponse.getError().setErrorCode("1");
		customerAddreesResponse.getError().setErrorMessage("Error message");
		customerAddreesResponse.getResponse().setKsaAddressCompliant(true);

		// Assert that the properties of the CustomerAddreesResponse object are set
		// correctly.
		assertEquals(customerAddreesResponse.getStatusCode(), "200");
		assertEquals(customerAddreesResponse.getStatusMsg(), "OK");
		assertNotNull(customerAddreesResponse.getResponse());
		assertEquals(customerAddreesResponse.getError().getErrorCode(), "1");
		assertEquals(customerAddreesResponse.getError().getErrorMessage(), "Error message");
		assertTrue(customerAddreesResponse.getResponse().getKsaAddressCompliant());
	}

	@Test
	public void testCustomerAddressBody() {
		// Create a new CustomerAddressBody object.
		CustomerAddressBody customerAddressBody = new CustomerAddressBody();

		// Set the properties of the CustomerAddressBody object.
		customerAddressBody.setAddress(new CustomerAddrees());
		customerAddressBody.setMessage("Success message");
		customerAddressBody.setStatus(true);
		customerAddressBody.setUserMessage("User message");
		customerAddressBody.setAddresses(Arrays.asList(new CustomerAddrees(), new CustomerAddrees()));

		// Assert that the properties of the CustomerAddressBody object are set
		// correctly.
		assertNotNull(customerAddressBody.getAddress());
		assertEquals(customerAddressBody.getMessage(), "Success message");
		assertTrue(customerAddressBody.isStatus());
		assertEquals(customerAddressBody.getUserMessage(), "User message");
		assertEquals(customerAddressBody.getAddresses().size(), 2);
	}

	@Test
	public void testNonServiceableAddressDTO() {
		// Create a new NonServiceableAddressDTO object.
		NonServiceableAddressDTO nonServiceableAddressDTO = new NonServiceableAddressDTO();
		nonServiceableAddressDTO.setCity("London");
		nonServiceableAddressDTO.setCountry("UK");
		nonServiceableAddressDTO.setFirstName("John");
		nonServiceableAddressDTO.setLastName("Doe");
		nonServiceableAddressDTO.setAddressId("1");
		nonServiceableAddressDTO.setArea("area");
		nonServiceableAddressDTO.setBuildingNumber("10");
		nonServiceableAddressDTO.setCustomerType("type");
		nonServiceableAddressDTO.setDefaultAddress("add");
		nonServiceableAddressDTO.setEmail("mail");
		nonServiceableAddressDTO.setFax("21");
		nonServiceableAddressDTO.setFormattedAddress("add");
		nonServiceableAddressDTO.setId(1);
		nonServiceableAddressDTO.setLandMark("mark");
		nonServiceableAddressDTO.setLatitude("10");
		nonServiceableAddressDTO.setLongitude("10");
		// Assert that the properties of the NonServiceableAddressDTO object are set
		// correctly.
		assertEquals(nonServiceableAddressDTO.getCity(), "London");
		assertEquals(nonServiceableAddressDTO.getCountry(), "UK");
		assertEquals(nonServiceableAddressDTO.getFirstName(), "John");
		assertEquals(nonServiceableAddressDTO.getLastName(), "Doe");
		assertEquals(nonServiceableAddressDTO.getAddressId(), "1");
		assertEquals(nonServiceableAddressDTO.getArea(), "area");
		assertEquals(nonServiceableAddressDTO.getBuildingNumber(), "10");
		assertEquals(nonServiceableAddressDTO.getCustomerType(), "type");
		assertEquals(nonServiceableAddressDTO.getDefaultAddress(), "add");
		assertEquals(nonServiceableAddressDTO.getEmail(), "mail");
		assertEquals(nonServiceableAddressDTO.getFax(), "21");
		assertEquals(nonServiceableAddressDTO.getFormattedAddress(), "add");
		assertEquals(nonServiceableAddressDTO.getId(), 1);
		assertEquals(nonServiceableAddressDTO.getLandMark(), "mark");
		assertEquals(nonServiceableAddressDTO.getLatitude(), "10");
		assertEquals(nonServiceableAddressDTO.getLongitude(), "10");
	}

}
