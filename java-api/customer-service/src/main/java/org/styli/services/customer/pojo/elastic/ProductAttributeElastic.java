package org.styli.services.customer.pojo.elastic;

import lombok.Data;

import java.io.Serializable;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class ProductAttributeElastic implements Serializable {

    private static final long serialVersionUID = -712239679663126009L;
    private String label;
    private String name;
}
