package org.styli.services.order.pojo.eas;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EASKafkaReturnOrder {

	private Integer customerId;
    private Integer storeId;
    private Integer orderId;
    private Integer spendCoin;
    private Integer requestId;
    private List<ReturnProduct> returnProduct;
    private Double returnFee;
}
