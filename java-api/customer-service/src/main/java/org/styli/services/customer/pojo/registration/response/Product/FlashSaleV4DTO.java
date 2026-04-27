package org.styli.services.customer.pojo.registration.response.Product;

import lombok.Data;

import java.io.Serializable;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class FlashSaleV4DTO implements Serializable {

    private static final long serialVersionUID = -8481494281976255014L;
    Boolean active;
    String start;
    String end;
    String color;
}
