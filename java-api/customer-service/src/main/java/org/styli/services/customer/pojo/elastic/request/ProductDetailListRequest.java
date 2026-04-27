package org.styli.services.customer.pojo.elastic.request;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * produc detail list API request (/api/productDetail/list)
 */

@Getter
@Setter
public class ProductDetailListRequest {

    private Integer storeId;
    private String cityId;
    private List<String> skus;
    private Boolean isWishListCall;
}
