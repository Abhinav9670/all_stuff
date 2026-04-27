package org.styli.services.order.pojo.request;

import lombok.Data;

import java.util.List;

/**
 * Created on 20-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class ProductStatusRequestV2 {

    private Integer storeId;
    private List<String> skus;
    private String city_id;
    private String country_id;
}
