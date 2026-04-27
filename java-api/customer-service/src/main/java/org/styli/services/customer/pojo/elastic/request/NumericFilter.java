package org.styli.services.customer.pojo.elastic.request;

import lombok.Data;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
public class NumericFilter {
    private String type;
    private Integer value;
    private OperatorENUM operator;
}
