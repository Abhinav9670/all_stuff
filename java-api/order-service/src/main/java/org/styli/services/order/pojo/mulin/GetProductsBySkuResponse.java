package org.styli.services.order.pojo.mulin;

import lombok.Data;

import java.util.Map;

@Data
public class GetProductsBySkuResponse {

    private boolean status;

    private String statusCode;

    private String statusMsg;

    private Map<String, ProductResponseBody> response;


}
