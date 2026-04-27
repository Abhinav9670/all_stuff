package org.styli.services.order.pojo.request;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Created on 20-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
public class CatalogProductEntityBySkuRequest {

    @NotNull
    public List<String> skus;

    @NotNull @Min(1)
    public Integer storeId;
}
