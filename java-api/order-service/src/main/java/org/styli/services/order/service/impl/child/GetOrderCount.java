package org.styli.services.order.service.impl.child;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.pojo.request.Order.OrderListRequest;
import org.styli.services.order.pojo.response.Order.CustomerOrdersCountResponseDTO;
import org.styli.services.order.pojo.response.Order.OrderCountResponse;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.utility.Constants;

@Component
public class GetOrderCount {
	
	@Autowired
	OrderHelper orderHelper;

    @Autowired
    SplitSalesOrderRepository splitSalesOrderRepository;

    public CustomerOrdersCountResponseDTO get(OrderListRequest request, StaticComponents staticComponents,
            SalesOrderRepository salesOrderRepository, CustomerEntityRepository customerEntityRepository,
            ConfigService configService, Boolean isForOrderPage) {

        CustomerOrdersCountResponseDTO resp = new CustomerOrdersCountResponseDTO();
        OrderCountResponse orderCountResponse = new OrderCountResponse();

        if(isForOrderPage ) {
        	
        	CustomerEntity customerEntity = orderHelper.getCustomerDetails(request.getCustomerId(), null);
             if (null != customerEntity && null == customerEntity.getEntityId() && !request.isUseArchive()) {
                 resp.setStatus(false);
                 resp.setStatusCode("203");
                 resp.setStatusMsg("Invalid customer ID!");
                 return resp;
             }
        }else {
        	
        	 if (StringUtils.isBlank(request.getCustomerEmail())) {
        		 
                 resp.setStatus(false);
                 resp.setStatusCode("203");
                 resp.setStatusMsg("Empty customer Email!");
                 return resp;
             }
        }
        
//        if(request.isUseArchive()) {
//
//        	resp.setStatus(true);
//            resp.setStatusCode("200");
//            resp.setStatusMsg("Order count fetched successfully!");
//            orderCountResponse.setAllOrderCount("0");
//            resp.setResponse(orderCountResponse);
//
//            return resp;
//        }
       

        List<Stores> stores = Constants.getStoresList();
        Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
                .findAny().orElse(null);

        if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
            resp.setStatus(false);
            resp.setStatusCode("202");
            resp.setStatusMsg("Store not found!");
            return resp;
        }
        Integer orderCount = 0;
        List<Integer> storeIds = configService.getWebsiteStoresByStoreId(request.getStoreId());
        if(isForOrderPage) {
            List<Integer> salesOrderIds =  salesOrderRepository.findSalesOrderIds(request.getCustomerId(), storeIds);
             Set<Integer> salesOrdersWithoutSplits = new HashSet<>(salesOrderIds);
             orderCount = salesOrdersWithoutSplits.size();
        }else {
            List<Integer> salesOrderIds = salesOrderRepository.findSalesOrderIdsWithCustomerEmail(request.getCustomerEmail(), storeIds);
            Set<Integer> salesOrdersWithoutSplits = new HashSet<>(salesOrderIds);
            orderCount = salesOrdersWithoutSplits.size();
        }

        if (null != orderCount) {
            orderCountResponse.setAllOrderCount(orderCount.toString());
        }

        resp.setStatus(true);
        resp.setStatusCode("200");
        resp.setStatusMsg("Order count fetched successfully!");
        resp.setResponse(orderCountResponse);
        return resp;

    }

}
