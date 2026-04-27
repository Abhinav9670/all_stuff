package org.styli.services.order.pojo.mulin;

import lombok.Data;

import java.util.List;

@Data
public class GetProductsBySkuRequest {

    private List<String> skus;

    private Boolean searchVariants = true;
}
