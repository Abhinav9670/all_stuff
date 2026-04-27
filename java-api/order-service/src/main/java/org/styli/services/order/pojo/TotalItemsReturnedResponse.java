package org.styli.services.order.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TotalItemsReturnedResponse {
    private List<Integer> allIds;
    private BigDecimal totalQuantity;
}
