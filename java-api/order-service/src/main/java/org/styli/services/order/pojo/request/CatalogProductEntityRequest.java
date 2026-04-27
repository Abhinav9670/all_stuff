package org.styli.services.order.pojo.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Created on 16-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CatalogProductEntityRequest {

    public Integer productId;

    @NotNull @Min(1)
    public Integer storeId;
}
