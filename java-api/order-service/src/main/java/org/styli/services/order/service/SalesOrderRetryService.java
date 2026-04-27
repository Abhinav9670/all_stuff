package org.styli.services.order.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.pojo.request.Order.ReOrderRequest;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;
import java.util.Map;

/**
 * @author Umesh, 14/05/2020
 * @project product-service
 */

@Service
public interface SalesOrderRetryService {

   
    OrderResponseDTO reOrderV2(Map<String, String> requestHeader, ReOrderRequest request, String mode,
                               String tokenHeader, String xHeaderToken, String xSource, String xClientVersion, RestTemplate restTemplate) throws NotFoundException;

}
