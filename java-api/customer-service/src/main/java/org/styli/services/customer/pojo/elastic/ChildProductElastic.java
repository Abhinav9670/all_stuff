package org.styli.services.customer.pojo.elastic;

import lombok.Data;

import java.io.Serializable;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class ChildProductElastic implements Serializable {

    private static final long serialVersionUID = -6524352941995188969L;
    private String id;
    private String sku;
    private String size;
    private String superAttributeId;
    private String sizeOptionId;
}
