package org.styli.services.order.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * Created on 16-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@EqualsAndHashCode
public class SizesDTO implements Serializable {

    private Integer sizeOptionId;

    private String label;

    private String productId;

    private String sku;

    private Integer price;

    private Integer specialPrice;

    private Integer quantity;
}
