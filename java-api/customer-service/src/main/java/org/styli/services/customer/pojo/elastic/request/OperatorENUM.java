package org.styli.services.customer.pojo.elastic.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Getter
@AllArgsConstructor
public enum OperatorENUM {

    GREATER_THAN(">"),
    GREATER_THAN_AND_EQUALS(">="),
    LESS_THAN("<"),
    LESS_THAN_AND_EQUALS("<="),
    EQUALS("="),
    NOT_EQUALS("!=");

    public String value;

}
