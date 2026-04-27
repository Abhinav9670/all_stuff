package org.styli.services.customer.pojo.registration.request;

import lombok.Data;

import java.util.List;

/**
 * @author Umesh, 29/03/2020
 * @project product-service
 */

@Data
public class ProductDetailsBySkuListV4Request {

    private List<String> skus;

    private Integer storeId;
}
