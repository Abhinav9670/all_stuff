package org.styli.services.order.service.impl.child;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.request.Order.OrderListRequest;
import org.styli.services.order.pojo.response.Order.CustomerOrdersResponseDTO;
import org.styli.services.order.pojo.response.Order.OrderResponse;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;

import java.util.*;

@Component
public class GetFailedOrderList {
	
	@Autowired
	OrderHelper orderHelper;

    public CustomerOrdersResponseDTO get(OrderListRequest request, StaticComponents staticComponents,
                                         SalesOrderRepository salesOrderRepository,
                                         OrderEntityConverter orderEntityConverter,
                                         CustomerEntityRepository customerEntityRepository,
                                         ConfigService configService, MulinHelper mulinHelper, RestTemplate restTemplate) {

        CustomerOrdersResponseDTO resp = new CustomerOrdersResponseDTO();

        CustomerEntity customerEntity = orderHelper.getCustomerDetails(request.getCustomerId(), null);

        if (null == customerEntity.getEntityId()) {

            resp.setStatus(false);
            resp.setStatusCode("203");
            resp.setStatusMsg("Invalid customer ID!");
            return resp;
        }

        List<Stores> stores = Constants.getStoresList();
        Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
                .findAny().orElse(null);

        if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
            resp.setStatus(false);
            resp.setStatusCode("202");
            resp.setStatusMsg("Store not found!");
            return resp;
        }

        List<Integer> storeIds = configService.getWebsiteStoresByStoreId(request.getStoreId());

        if (request.getOffSet() < 0)
            request.setOffSet(0);

        Pageable pageableSize = PageRequest.of(request.getOffSet(), request.getPageSize());
        List<SalesOrder> customerOrders = salesOrderRepository.findFailedOrderList(request.getCustomerId(), storeIds,
                OrderConstants.FAILED_ORDER_STATUS, pageableSize);

        List<OrderResponse> orders = new ArrayList<>();

        Map<String, ProductResponseBody> productsFromMulin = mulinHelper
                .getMulinProductsFromOrder(customerOrders, restTemplate);

        ObjectMapper mapper = new ObjectMapper();
        for (SalesOrder order : customerOrders) {
            OrderResponse orderResponseBody = orderEntityConverter.convertOrder(order, false, mapper,
                    request.getStoreId(), productsFromMulin, "", false);
            orders.add(orderResponseBody);
        }

        resp.setStatus(true);
        resp.setStatusCode("200");
        resp.setStatusMsg("Customer orders fetched successfully!");
        resp.setResponse(orders);

        return resp;

    }

}
