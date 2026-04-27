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
public enum SortKeyENUM {

    PRICE("price_default"),
    CREATED_AT("created_at"),
    DISCOUNT_PERCENTAGE("discount_percentage");

    public String value;

}
