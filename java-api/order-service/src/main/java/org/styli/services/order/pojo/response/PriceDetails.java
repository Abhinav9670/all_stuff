package org.styli.services.order.pojo.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Created on 13-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class PriceDetails implements Serializable {

    private static final long serialVersionUID = 6025562885389214951L;

    private String price;
    private String specialPrice;   
    private String droppedPrice;
}
