package org.styli.services.customer.utility.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.exception.CustomerException;
import org.styli.services.customer.utility.helper.AddressMapperHelper;
import org.styli.services.customer.utility.helper.AddressMapperHelperV2;
import org.styli.services.customer.utility.pojo.category.CategoryListResponse;
import org.styli.services.customer.utility.pojo.config.BaseConfig;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponseDTO;
import org.styli.services.customer.utility.service.CatlogCategoryEnityService;
import org.styli.services.customer.utility.service.ConfigService;
import org.styli.services.customer.utility.service.ConfigServiceV2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@RestController
@RequestMapping("/rest/utility/")
@Api(value = "/rest/utility/", produces = "application/json")
@JsonIgnoreProperties(ignoreUnknown = true)
public class UtilityController {

	private static final Logger LOGGER = LoggerFactory.getLogger(UtilityController.class);

	@Autowired
	ConfigService configService;

	@Autowired
	ConfigServiceV2 configServiceV2;

	@Autowired
	CatlogCategoryEnityService catlogCategoryEnityService;

	@Autowired
	AddressMapperHelper addressMapperHelper;

	@Autowired
	AddressMapperHelperV2 addressMapperHelperV2;

	@GetMapping("config/v1")
	public StoreConfigResponseDTO getStoreConfigsV1(HttpServletRequest httpServletRequest) {
		return configService.getStoreV1Configs(httpServletRequest, false);
	}
	
	@GetMapping("config/v2")
	public StoreConfigResponseDTO getStoreConfigsV2(HttpServletRequest httpServletRequest) {
		return configServiceV2.getStoreV2Configs(httpServletRequest, false, false);
	}

	@GetMapping("config/v1/push")
	public StoreConfigResponseDTO pushStoreConfigsV1(HttpServletRequest httpServletRequest) {
		return configService.getStoreV1Configs(httpServletRequest, true);
	}


	@GetMapping("config/v2/push/gcp")
	public StoreConfigResponseDTO pushStoreConfigsV2GCP(HttpServletRequest httpServletRequest) {
		return configServiceV2.getStoreV2Configs(httpServletRequest, false, true);
	}

	@ApiOperation(value = "Get Category List", response = CategoryListResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Product ListDetails Retrieved", response = CategoryListResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error"),
			@ApiResponse(code = 404, message = "Category not found") })

	@GetMapping("categories/store/{storeId}")

	public CategoryListResponse getAllCategories(@RequestHeader Map<String, String> requestHeader,
			@PathVariable Integer storeId) throws CustomerException {

		LOGGER.info("Inside getAllCategories");

		return catlogCategoryEnityService.findAllCategories(requestHeader, storeId, false);
	}

	@ApiOperation(value = "Push Category List", response = CategoryListResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Product ListDetails Retrieved", response = CategoryListResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error"),
			@ApiResponse(code = 404, message = "Category not found") })

	@GetMapping("categories/store/{storeId}/push")

	public CategoryListResponse pushCategories(@RequestHeader Map<String, String> requestHeader,
			@PathVariable Integer storeId) throws CustomerException {

		return catlogCategoryEnityService.findAllCategories(requestHeader, storeId, true);


	}

	@GetMapping("address/mapper/countrycode/{code}")
	public String getAddressMapper(@PathVariable String code, HttpServletRequest httpServletRequest) {
		return addressMapperHelper.getAddress(code, false, httpServletRequest);

	}

	@GetMapping("address/mapper/countrycode/{code}/push")
	public String pushAddressMapperToConsul(@PathVariable String code, HttpServletRequest httpServletRequest) {
		BaseConfig config = Constants.baseConfig;
		if (config.isAddressMapperPublishCronFlow()) {
			LOGGER.info("Address Change : Initiating updateAddressMapperDataToConsul");
			return addressMapperHelperV2.updateAddressMapperDataToConsul(code, httpServletRequest);
		}
		else {
			LOGGER.info("Address Change : Initiating getAddress" );
			return addressMapperHelper.getAddress(code, true, httpServletRequest);
		}
	}

	@GetMapping("address/mapper/push/gcp")
	public StoreConfigResponseDTO pushAddressMapperToGCP() {
		return addressMapperHelperV2.pushConsulAddressMapperToGCP();
	}
	
	@GetMapping("test")
	public String test() {
		return "test";
	}
	
	
}
