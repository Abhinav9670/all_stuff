package org.styli.services.customer.pojo;

import lombok.Data;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class AttributeValue {

    private String attributeType;

    private Integer plpVisiable;

    private Integer pdpVisiable;

    private Integer attributeFor;

    private Integer isUserDefind;
}
