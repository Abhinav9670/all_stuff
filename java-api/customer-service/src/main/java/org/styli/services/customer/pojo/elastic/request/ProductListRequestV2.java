package org.styli.services.customer.pojo.elastic.request;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
public class ProductListRequestV2 {

    private String query;

    private Map<String, List<String>> filters;

    private List<NumericFilter> numericFilters;

    private Integer pageSize;
    private Integer pageOffset;
    private SortKeyENUM sortKey;
    private SortOrderENUM sortOrder;

    @NotNull
    @Min(1)
    private Integer storeId;

    @Min(0)
    private Integer categoryLevel;

    //    used for vm request
    private String contextRuleId;
    private String env;
}
