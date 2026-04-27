package org.styli.services.order.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.CoreConfigDataServicePojo;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.Store;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.pojo.AttributeValue;
import org.styli.services.order.pojo.request.LockAndUnlockShukranRequest;
import org.styli.services.order.pojo.response.LockAndUnlockShukranResponse;


@Service
public interface CommonService {

	List<Store> getAllStores();

	Store findStoreByStoreId(Integer storeId);

	BigDecimal customerShukranBalance(String profileId);

	List<Store> findByWebsiteId(Integer websiteId);

	List<Stores> getStoresArray() throws NotFoundException;

	CoreConfigDataServicePojo getCoreConfigDataService(Integer storeId, Integer websiteId, String storeCode)
			throws NotFoundException;


	Map<String, String> getAttributeLabels();

	CustomerEntity findByEmail(String email);

	CustomerEntity findByEntityId(Integer enityId);

	Map<Integer, String> getAttrMap();

	List<SalesOrder> findByCustomerEmailSalesOrder(String email);

	List<SalesOrderGrid> findByCustomerEmailSalesOrderGrid(String email);

	Map<String, AttributeValue> getAttributeStatus();

	LockAndUnlockShukranResponse lockAndUnlockShukran(LockAndUnlockShukranRequest lockAndUnlockShukranRequest);

	LockAndUnlockShukranResponse unlockShukranPoints();


}
