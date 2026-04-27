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
public enum SortOrderENUM {

    ASC("asc"), DESC("desc");

    public String value;

}
