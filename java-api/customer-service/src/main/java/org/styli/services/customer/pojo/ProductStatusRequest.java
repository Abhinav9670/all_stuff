package org.styli.services.customer.pojo;

import lombok.Data;

import java.util.List;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class ProductStatusRequest {

    private Integer storeId;
    private List<ProductInfo> products;
    private List<String> skus;

}
