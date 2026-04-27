package org.styli.services.order.pojo.request;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * Created on 20-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class ChildPriceDetailsRequest implements Serializable {
    private static final long serialVersionUID = -9032060666196322070L;
    @NotNull
    private List<Integer> childProductIds;

    @NotNull @Min(1)
    public Integer storeId;
}
