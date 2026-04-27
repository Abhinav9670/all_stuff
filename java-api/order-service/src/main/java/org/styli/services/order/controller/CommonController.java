package org.styli.services.order.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.CoreConfigDataServicePojo;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.Store;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.pojo.request.LockAndUnlockShukranRequest;
import org.styli.services.order.pojo.response.Customer;
import org.styli.services.order.pojo.response.LockAndUnlockShukranResponse;
import org.styli.services.order.service.CommonService;
import org.styli.services.order.service.SalesOrderCustomerService;
import org.styli.services.order.service.SalesOrderServiceV3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@RestController
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommonController {

	@Autowired
	CommonService commonService;

	@Autowired
	SalesOrderServiceV3 salesOrderV3Service;

	@Autowired
	SalesOrderCustomerService salesOrderCustomerService;

	@GetMapping("/findallstores")
	public List<Store> findAllStores() {
		return commonService.getAllStores();
	}

	@GetMapping("/findstorebystoreid/{storeId}")
	public Store findStoreByStoreId(@PathVariable @NotNull Integer storeId) {
		return commonService.findStoreByStoreId(storeId);
	}

	@GetMapping("/findbywebsiteid/{websiteId}")
	public List<Store> findByWebsiteId(@PathVariable @NotNull Integer websiteId) {
		return commonService.findByWebsiteId(websiteId);
	}

	@GetMapping("/getstoresarray")
	public List<Stores> getStoresArray() throws NotFoundException {
		return commonService.getStoresArray();
	}

	@GetMapping("/getcoreconfigdataservice/storeid/{storeId}/websiteid/{websiteId}/storecode/{storeCode}")
	public CoreConfigDataServicePojo getCoreConfigDataService(@PathVariable @NotNull Integer storeId,
			@PathVariable @NotNull Integer websiteId, @PathVariable @NotNull String storeCode)
			throws NotFoundException {
		return commonService.getCoreConfigDataService(storeId, websiteId, storeCode);
	}

	@GetMapping("/getattributelabels")
	public Map<String, String> getAttributeLabels() {
		return commonService.getAttributeLabels();
	}

	@GetMapping("/findbyemail/{email}")
	public CustomerEntity findByEmail(@PathVariable String email) {
		return commonService.findByEmail(email);
	}

	@GetMapping("/findbyentityId/{entityId}")
	public CustomerEntity findByEntityId(@PathVariable Integer entityId) {
		return commonService.findByEntityId(entityId);
	}

	@GetMapping("/salesorder/findbycustomeremail/{email}")
	public List<SalesOrder> findByCustomerEmailSalesOrder(@PathVariable String email) {
		return commonService.findByCustomerEmailSalesOrder(email);
	}

	@GetMapping("/salesordergrid/findbycustomeremail/{email}")
	public List<SalesOrderGrid> findByCustomerEmailSalesOrderGrid(@PathVariable String email) {
		return commonService.findByCustomerEmailSalesOrderGrid(email);
	}

	@PostMapping("/salesorder/findsalesorders")
	public void findSalesOrders(@RequestBody @Valid Customer customer) {
		salesOrderV3Service.findSalesOrdersAndSalesGrid(customer);
	}



}