package org.styli.services.customer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.styli.services.customer.pojo.AttributeValue;
import org.styli.services.customer.pojo.ProductStatusRequest;
import org.styli.services.customer.pojo.registration.response.Product.ProductValue;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@FeignClient(value = "order", url = "${order.ribbon.listOfServers}")
public interface OrderClient {

    @PostMapping("/inventory/status")
    @ResponseBody
    List<ProductValue> getProductQty(@Valid @RequestBody ProductStatusRequest productStatusReq);

    @GetMapping("/getattributestatus")
    Map<String, AttributeValue> getAttrStatusMap();
}
