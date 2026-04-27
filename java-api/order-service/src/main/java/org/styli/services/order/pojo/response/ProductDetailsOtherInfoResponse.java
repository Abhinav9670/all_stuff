package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Created on 16-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDetailsOtherInfoResponse {


    private Integer productId;

    private String productName;

    private String sku;

    private String productType;

    private Integer storeId;

    private Boolean isReturnApplicable = false;

    private Double price;

    private Double specialPrice;

    private String superConfigAttributeName;

    private String imageUrl;

    private String brandName;

    private List<ConfigProduct> configProducts;

    private List<String> images;

    private Boolean productStatus;

    private String soldBy;


}
