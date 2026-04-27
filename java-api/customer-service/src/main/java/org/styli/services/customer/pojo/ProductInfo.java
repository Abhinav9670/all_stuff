package org.styli.services.customer.pojo;

import lombok.Data;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class ProductInfo {

    private String productId;

    private String sku;

    private Integer storeId;

    private String productName;

    private String imageUrl;

    private String price;

    private String brand;

}