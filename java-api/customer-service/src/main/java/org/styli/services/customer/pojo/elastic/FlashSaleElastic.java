package org.styli.services.customer.pojo.elastic;

import lombok.Data;

import java.io.Serializable;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class FlashSaleElastic implements Serializable {

    private Boolean active;
    private String start;
    private String end;
    private String color;

}
