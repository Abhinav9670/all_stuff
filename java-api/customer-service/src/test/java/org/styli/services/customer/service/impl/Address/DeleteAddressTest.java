package org.styli.services.customer.service.impl.Address;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.address.response.CustomerAddreesResponse;
import org.styli.services.customer.pojo.address.response.CustomerAddressBody;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.service.impl.CustomerV4ServiceImpl;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeleteAddressTest {

	@InjectMocks
	private DeleteAddress deleteAddress;
	@InjectMocks
	CustomerV4ServiceImpl customerV4ServiceImpl;

	@Mock
	private CustomerEntityRepository customerEntityRepository;

	@Mock
	private CustomerAddressEntityRepository customerAddressEntityRepository;

	@BeforeMethod
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testDeleteAddressSuccess() {
		CustomerAddrees customerAddRequest = new CustomerAddrees();
		customerAddRequest.setAddressId(1);
		customerAddRequest.setCustomerId(2);

		CustomerEntity customer = new CustomerEntity();
		customer.setDefaultShipping(customerAddRequest.getAddressId());
		customer.setDefaultBilling(customerAddRequest.getAddressId());

		when(customerEntityRepository.existsById(customerAddRequest.getCustomerId())).thenReturn(true);
		when(customerAddressEntityRepository.existsById(customerAddRequest.getAddressId())).thenReturn(true);
		when(customerEntityRepository.findByEntityId(customerAddRequest.getCustomerId())).thenReturn(customer);

		CustomerAddreesResponse response = deleteAddress.delete(customerAddRequest, customerEntityRepository,
				customerAddressEntityRepository);

		verify(customerAddressEntityRepository).deleteByEntityIdAndCustomerId(customerAddRequest.getAddressId(),
				customerAddRequest.getCustomerId());
		verify(customerEntityRepository).save(customer);

		Assert.assertTrue(response.isStatus());
		Assert.assertEquals(response.getStatusCode(), "200");
		Assert.assertEquals(response.getStatusMsg(), "SUCCESS");

		CustomerAddressBody responseBody = response.getResponse();
		Assert.assertEquals(responseBody.getMessage(),
				"Address ID " + customerAddRequest.getAddressId() + " Deleted Successfully");
	}

	@Test
	public void testDeleteAddressInvalidIds() {
		CustomerAddrees customerAddRequest = new CustomerAddrees();
		customerAddRequest.setAddressId(1);
		customerAddRequest.setCustomerId(2);

		when(customerEntityRepository.existsById(customerAddRequest.getCustomerId())).thenReturn(false);
		when(customerAddressEntityRepository.existsById(customerAddRequest.getAddressId())).thenReturn(false);

		CustomerAddreesResponse response = deleteAddress.delete(customerAddRequest, customerEntityRepository,
				customerAddressEntityRepository);

		verify(customerAddressEntityRepository, never()).deleteByEntityIdAndCustomerId(anyInt(), anyInt());
		verify(customerEntityRepository, never()).save(any());

		Assert.assertFalse(response.isStatus());
		Assert.assertEquals(response.getStatusCode(), "201");
		Assert.assertEquals(response.getStatusMsg(), "Invalid Address/Customer ID");
	}

}