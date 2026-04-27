package org.styli.services.customer.service.impl;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.styli.services.customer.model.NonServiceableAddress;
import org.styli.services.customer.pojo.GenericApiResponse;
import org.styli.services.customer.pojo.address.response.NonServiceableAddressDTO;
import org.styli.services.customer.repository.Customer.NonServiceableAddressRepository;

/**
 * Customer address services
 * 
 * @author Chandan Behera
 *
 */
@Service
public class AddressService {

	private static final Log LOGGER = LogFactory.getLog(AddressService.class);

	@Autowired
	private NonServiceableAddressRepository addressRepository;
	
	public GenericApiResponse<String> saveNonServiceableAddress(NonServiceableAddressDTO address) {
		GenericApiResponse<String> response = new GenericApiResponse<>();
		try {
			NonServiceableAddress entity = new NonServiceableAddress();
			entity.setCreatedAt(new Date());
			BeanUtils.copyProperties(address, entity);
			addressRepository.save(entity);
			
			response.setStatus(true);
			response.setStatusCode("200");
			response.setResponse("Success");
			return response;
		} catch (Exception e) {
			LOGGER.error("Error in saving non serviceable address. ", e);
		}
		response.setStatus(false);
		response.setStatusCode("206");
		response.setResponse("Failure");
		return response;
	}
}
