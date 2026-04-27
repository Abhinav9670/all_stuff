package org.styli.services.customer.service.impl;

import static org.mockito.Mockito.when;

import java.util.Date;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.BeanUtils;
import org.styli.services.customer.model.NonServiceableAddress;
import org.styli.services.customer.pojo.GenericApiResponse;
import org.styli.services.customer.pojo.address.response.NonServiceableAddressDTO;
import org.styli.services.customer.repository.Customer.NonServiceableAddressRepository;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class AddressServiceTest {

	@Mock
	private NonServiceableAddressRepository addressRepositoryMock;

	@InjectMocks
	private AddressService addressService;

	@BeforeMethod
	public void initMocks() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testSaveNonServiceableAddress_Success() {
		// Arrange
		NonServiceableAddressDTO addressDTO = new NonServiceableAddressDTO();

		NonServiceableAddress entity = new NonServiceableAddress();
		entity.setId("1");
		entity.setCreatedAt(new Date());
		BeanUtils.copyProperties(addressDTO, entity);

		when(addressRepositoryMock.save(entity)).thenReturn(entity);

		// Act
		GenericApiResponse<String> response = addressService.saveNonServiceableAddress(addressDTO);

		// Assert
		Assert.assertEquals(response.getStatusCode(), "200");
		Assert.assertEquals(response.getResponse(), "Success");
	}

	@Test
	public void testSaveNonServiceableAddress_Failure() {
		// Arrange
		NonServiceableAddressDTO addressDTO = new NonServiceableAddressDTO();

		when(addressRepositoryMock.save(Mockito.any())).thenThrow(new RuntimeException("Error saving address"));

		// Act
		GenericApiResponse<String> response = addressService.saveNonServiceableAddress(addressDTO);

		// Assert
		Assert.assertEquals(response.getStatusCode(), "206");
		Assert.assertEquals(response.getResponse(), "Failure");
	}

}
