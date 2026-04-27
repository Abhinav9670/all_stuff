package org.styli.services.order.pojo.request;

import lombok.Data;

/**
 * Created on 20-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class ProductInfo {

    private Integer productId;

    private String sku;

    private Integer storeId;

    private String productName;

    private String imageUrl;

    private String price;

    private String brand;

}