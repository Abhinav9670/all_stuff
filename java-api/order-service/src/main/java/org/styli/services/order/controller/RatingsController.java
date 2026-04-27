package org.styli.services.order.controller;

import java.util.Map;

import javax.validation.Valid;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.config.aop.ConfigurableDatasource;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.pojo.ratings.CustomerRatings;
import org.styli.services.order.pojo.ratings.DeleteCustomerRatingsReq;
import org.styli.services.order.pojo.ratings.RatingsResponse;
import org.styli.services.order.pojo.ratings.RetrieveRatingsRequest;
import org.styli.services.order.pojo.ratings.RetrieveRatingsResponse;
import org.styli.services.order.pojo.ratings.UpdateCustomerRatingsReq;
import org.styli.services.order.service.SalesOrderServiceV2;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@RestController
@RequestMapping("/rest/order/ratings/")
@Api(value = "/rest/order/ratings/", produces = "application/json")
public class RatingsController {
	
	@Autowired
	MulinHelper mulinHelper;
	
	@Autowired
    SalesOrderServiceV2 salesOrderServiceV2;
	
	@Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;
    
	@ApiOperation(value = "Customer Retrieve Ratings", response = RatingsResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Ratings found", response = RatingsResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error") })
	@ApiImplicitParams({ @ApiImplicitParam(name = "Content-Type", value = "application/json", paramType = "header"),
			 			 @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@ConfigurableDatasource
	@PostMapping("list")
	public RetrieveRatingsResponse retrieveRatings(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody RetrieveRatingsRequest customerRatings) {

		if(!StringUtils.isEmpty(customerRatings.getCustomerId()))
			salesOrderServiceV2.authenticateCheck(requestHeader, Integer.parseInt(customerRatings.getCustomerId()));
		RetrieveRatingsResponse response = new RetrieveRatingsResponse();
		try {
			response = mulinHelper.retrieveRatings(customerRatings, restTemplate);
			
		} catch (Exception exception) {

			response.setStatus(false);
			response.setStatusCode("201");
			response.setStatusMsg("Error occurred while updating ratings for customer");
		}
		return response;
	}

	@ApiOperation(value = "Customer Save Ratings", response = RatingsResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Ratings saved successfully", response = RatingsResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error") })
	@ApiImplicitParams({ @ApiImplicitParam(name = "Content-Type", value = "application/json", paramType = "header"),
			 			 @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("update")
	@ConfigurableDatasource
	public RatingsResponse saveRatings(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody CustomerRatings customerRatings) {
		RatingsResponse response = new RatingsResponse();
		if (null != customerRatings && ObjectUtils.isNotEmpty(customerRatings.getCustomerId())) {
			salesOrderServiceV2.authenticateCheck(requestHeader, Integer.parseInt(customerRatings.getCustomerId()));

			try {
				response = mulinHelper.saveRatings(customerRatings, restTemplate);
				salesOrderServiceV2.updateRatingStatus("1", Integer.parseInt(customerRatings.getOrderId()));

			} catch (Exception exception) {

				response.setStatus(false);
				response.setStatusCode("201");
				response.setStatusMsg("Error occurred while saving ratings for customer");
			}
		}
		return response;
	}
	
	@ApiOperation(value = "Customer Update Ratings", response = RatingsResponse.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Ratings updated successfully", response = RatingsResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error") })
	@ApiImplicitParams({ @ApiImplicitParam(name = "Content-Type", value = "application/json", paramType = "header"),
			 			 @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PutMapping("update")
	@ConfigurableDatasource
	public RatingsResponse updateRatings(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody UpdateCustomerRatingsReq customerRatings) {

		salesOrderServiceV2.authenticateCheck(requestHeader, Integer.parseInt(customerRatings.getCustomerId()));
		RatingsResponse response = new RatingsResponse();
		try {
			response = mulinHelper.updateRatings(customerRatings, restTemplate);
			
		} catch (Exception exception) {

			response.setStatus(false);
			response.setStatusCode("201");
			response.setStatusMsg("Error occurred while updating ratings for customer");
		}
		return response;
	}
	
	@ApiOperation(value = "Customer Delete Ratings", response = RatingsResponse.class)
	@ConfigurableDatasource
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "Ratings deleted successfully", response = RatingsResponse.class),
			@ApiResponse(code = 500, message = "Internal Server Error") })
	@ApiImplicitParams({ @ApiImplicitParam(name = "Content-Type", value = "application/json", paramType = "header"),
			 			 @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@DeleteMapping("update")
	public RatingsResponse deleteRatings(@RequestHeader Map<String, String> requestHeader,
			@Valid @RequestBody DeleteCustomerRatingsReq customerRatings) {

		salesOrderServiceV2.authenticateCheck(requestHeader, Integer.parseInt(customerRatings.getCustomerId()));
		RatingsResponse response = new RatingsResponse();
		try {
			response = mulinHelper.deleteRatings(customerRatings, restTemplate);
			
		} catch (Exception exception) {

			response.setStatus(false);
			response.setStatusCode("201");
			response.setStatusMsg("Error occurred while deleting ratings for customer");
		}
		return response;
	}

}
