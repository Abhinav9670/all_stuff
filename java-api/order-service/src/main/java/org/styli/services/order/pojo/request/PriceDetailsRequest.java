package org.styli.services.order.pojo.request;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Created on 13-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
public class PriceDetailsRequest {

    @NotNull
    public List<Integer> ParentProductIds;

    @NotNull @Min(1)
    public Integer storeId;
}
