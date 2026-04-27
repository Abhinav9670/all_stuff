package org.styli.services.customer.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Created on 15-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetProductDescRequest {

    
    private String product_id;

    private String store_id;
    
    private String sku;
    
    
    
}
